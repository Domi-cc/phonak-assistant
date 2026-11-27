package io.github.domicc.lara.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One open voice link to the hearing aid: open it, play PCM into it, record from it, close it.
 * Session programs (echo test, count session ...) are built on top of this class.
 *
 * Why it is built this way (all measured or read from the Android 17 source):
 * - After a hearing-aid tap the app must answer with [BluetoothHeadset.startVoiceRecognition]
 *   within 5 s, otherwise the aid gets an error.
 * - Android drops a communication route about 6 s after the requesting app is neither playing
 *   nor recording. The app therefore registers for the route itself as well (but only once the
 *   link is up, see [open]) and keeps its own [AudioTrack] running; callers must not leave
 *   gaps: use [silence] between sounds.
 * - A recording only counts if Android reports the hearing aid as the device actually used and
 *   the capture was not silenced. There is no fallback to the phone microphone.
 * - [close] must run on every exit path; a link left open makes Android reject the next tap.
 *
 * Every step is journaled as `session_<step>` with the milliseconds since the trigger.
 * All audio is 16 kHz mono 16-bit PCM ([Tones.SAMPLE_RATE]).
 */
class AudioLink(
    private val context: Context,
    private val journal: Journal,
    private val headsetLink: HeadsetLink,
    val trigger: String,
    private val preferredDevice: BluetoothDevice?,
) {
    /** The link could not be opened or broke; [reason] becomes the session result. */
    class Failed(val reason: String) : Exception(reason)

    /** @param valid true only if the sound really came from the hearing-aid microphone */
    class Recording(val pcm: ShortArray, val valid: Boolean, val stats: Map<String, Any?>)

    private val audio = context.getSystemService(AudioManager::class.java)
    private val startedAt = SystemClock.elapsedRealtime()

    private val voiceAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var headset: BluetoothHeadset? = null
    private var device: BluetoothDevice? = null
    private var track: AudioTrack? = null
    private var voiceRecognitionStarted = false
    private var routeRequested = false
    private var focusRequest: AudioFocusRequest? = null
    private var closed = false

    val isOpen: Boolean get() = track != null && !closed

    /** Journals `session_<name>` with the time since the trigger. */
    fun step(name: String, fields: Map<String, Any?> = emptyMap()) {
        journal.log(
            "session_$name",
            fields + mapOf("ms" to SystemClock.elapsedRealtime() - startedAt, "trigger" to trigger),
        )
    }

    /** Opens the link and plays the start tone. Throws [Failed] if the hearing aid cannot be reached. */
    @SuppressLint("MissingPermission")
    suspend fun open() = withContext(Dispatchers.IO) {
        val headset = headsetLink.await(PROXY_TIMEOUT_MS) ?: throw Failed("no_hfp_proxy")
        this@AudioLink.headset = headset
        val device = headsetLink.pick(headset, preferredDevice) ?: throw Failed("no_hearing_aid_connected")
        this@AudioLink.device = device
        step(
            "start",
            mapOf(
                "device" to device.name,
                "audioMode" to audio.mode,
                "callVolume" to audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL),
                "callVolumeMax" to audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
            ),
        )

        // 1. Answer the hearing aid (or, without a tap, ask it) for a voice session. The
        //    Bluetooth stack then opens the audio link itself.
        voiceRecognitionStarted = headset.startVoiceRecognition(device)
        step("voice_recognition_requested", mapOf("accepted" to voiceRecognitionStarted))

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(voiceAttributes)
            .setOnAudioFocusChangeListener(
                { change -> journal.log("focus_change", mapOf("change" to change)) },
                Handler(Looper.getMainLooper()),
            )
            .build()
        focusRequest = request
        val focusResult = audio.requestAudioFocus(request)
        val track = newTrack().also { it.play() }
        this@AudioLink.track = track

        // 2. Wait for that link BEFORE asking for the route ourselves. Asking earlier makes the
        //    audio server open the link as a "virtual call", which cancels the voice-recognition
        //    session again (seen on the Pixel: "isScoAcceptable: rejected SCO"), and the link
        //    then never comes up.
        if (voiceRecognitionStarted) {
            val audioUp = waitUntil(track) { headset.isAudioConnected(device) }
            step(if (audioUp) "hfp_audio_up" else "hfp_audio_timeout")
            if (!audioUp) throw Failed("link_timeout")
        }

        // 3. Now register this app as a user of the route, so that Android keeps it for as long
        //    as this app plays or records. If the hearing aid refused voice recognition, this
        //    call is what opens the link (as a virtual call).
        val sco = audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        routeRequested = sco != null && audio.setCommunicationDevice(sco)
        step(
            "route_requested",
            mapOf(
                "focusGranted" to (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED),
                "hearingAidListed" to (sco != null),
                "accepted" to routeRequested,
            ),
        )
        val linkUp = waitUntil(track) {
            audio.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO && headset.isAudioConnected(device)
        }
        step(
            if (linkUp) "link_up" else "link_timeout",
            mapOf(
                "route" to deviceLabel(audio.communicationDevice),
                "hfpAudioConnected" to headset.isAudioConnected(device),
            ),
        )
        if (!linkUp) throw Failed("link_timeout")

        silence(PRE_ROLL_MS)
        play(Tones.sessionStart)
    }

    /** Plays [pcm] and returns when it has been handed to the audio system. */
    suspend fun play(pcm: ShortArray) = withContext(Dispatchers.IO) {
        val track = track ?: throw Failed("link_not_open")
        var offset = 0
        while (offset < pcm.size) {
            coroutineContext.ensureActive()
            val written = track.write(pcm, offset, min(pcm.size - offset, WRITE_CHUNK), AudioTrack.WRITE_BLOCKING)
            if (written <= 0) throw Failed("playback_failed_$written")
            offset += written
        }
    }

    /** Plays silence for [ms]; this is also how a program waits without losing the link. */
    suspend fun silence(ms: Int) = play(Tones.silence(ms))

    /** Records [ms] from the hearing-aid microphone while keeping the player alive. */
    @SuppressLint("MissingPermission")
    suspend fun record(ms: Int): Recording = withContext(Dispatchers.IO) {
        val track = track ?: throw Failed("link_not_open")
        val record = newRecord()
        try {
            val pcm = ShortArray(Tones.SAMPLE_RATE * ms / 1000)
            val buffer = ShortArray(Tones.SAMPLE_RATE / 50) // 20 ms
            val keepAlive = ShortArray(buffer.size)
            var offset = 0
            var firstSound = -1
            var peak = 0
            var sumOfSquares = 0.0
            record.startRecording()
            while (offset < pcm.size) {
                coroutineContext.ensureActive()
                val read = record.read(buffer, 0, min(buffer.size, pcm.size - offset), AudioRecord.READ_BLOCKING)
                if (read <= 0) throw Failed("recording_failed_$read")
                for (i in 0 until read) {
                    val level = abs(buffer[i].toInt())
                    if (level > SOUND_THRESHOLD && firstSound < 0) firstSound = offset + i
                    peak = max(peak, level)
                    sumOfSquares += level.toDouble() * level
                }
                System.arraycopy(buffer, 0, pcm, offset, read)
                offset += read
                // keep this app's player active while it records
                track.write(keepAlive, 0, read, AudioTrack.WRITE_NON_BLOCKING)
            }
            val routed = record.routedDevice
            val silenced = record.activeRecordingConfiguration?.isClientSilenced
            val fromHearingAid = routed?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            val rms = sqrt(sumOfSquares / pcm.size)
            val stats = mapOf(
                "microphone" to deviceLabel(routed),
                "fromHearingAid" to fromHearingAid,
                "silenced" to silenced,
                "sampleRate" to record.sampleRate,
                "peak" to peak,
                "rmsDbfs" to if (rms > 0) (20 * log10(rms / Short.MAX_VALUE)).toInt() else null,
                "firstSoundMs" to if (firstSound >= 0) firstSound * 1000 / Tones.SAMPLE_RATE else null,
            )
            Recording(pcm, valid = fromHearingAid && silenced != true && peak > SOUND_THRESHOLD, stats = stats)
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    /**
     * Closes everything and journals `session_end`. Safe to call more than once and after a
     * failed [open]. Plays the end tone for "ok" and the error tone for any other result
     * except "cancelled" (then the hearing aid has closed the link already).
     */
    @SuppressLint("MissingPermission")
    suspend fun close(result: String) = withContext(NonCancellable + Dispatchers.IO) {
        if (closed) return@withContext
        closed = true
        track?.let { track ->
            if (result != "cancelled") {
                val tone = (if (result == "ok") Tones.sessionEnd else Tones.error) + Tones.silence(TAIL_MS)
                runCatching { track.write(tone, 0, tone.size, AudioTrack.WRITE_BLOCKING) }
            }
            runCatching { track.stop() }
            track.release()
        }
        track = null
        if (routeRequested) audio.clearCommunicationDevice()
        val headset = headset
        val device = device
        val stopped = if (voiceRecognitionStarted && headset != null && device != null) {
            runCatching { headset.stopVoiceRecognition(device) }.getOrDefault(false)
        } else {
            null
        }
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        step("end", mapOf("result" to result, "voiceRecognitionStopped" to stopped))
    }

    /** Feeds silence (so this app counts as playing) until [condition] holds or time runs out. */
    private suspend fun waitUntil(track: AudioTrack, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + LINK_TIMEOUT_MS
        val chunk = Tones.silence(40)
        while (SystemClock.elapsedRealtime() < deadline) {
            coroutineContext.ensureActive()
            if (condition()) return true
            track.write(chunk, 0, chunk.size, AudioTrack.WRITE_NON_BLOCKING)
            delay(40)
        }
        return condition()
    }

    private fun newTrack(): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            Tones.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(voiceAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Tones.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBytes, Tones.SAMPLE_RATE * 2 / 5)) // about 200 ms
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun newRecord(): AudioRecord {
        val minBytes = AudioRecord.getMinBufferSize(
            Tones.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Tones.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBytes, Tones.SAMPLE_RATE)) // about 500 ms
            .build()
        audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?.let { record.preferredDevice = it }
        return record
    }

    private fun deviceLabel(device: AudioDeviceInfo?): String? =
        device?.let { "type=${it.type} ${it.productName}" }

    private companion object {
        const val PROXY_TIMEOUT_MS = 3_000L
        const val LINK_TIMEOUT_MS = 6_000L
        const val PRE_ROLL_MS = 300
        const val TAIL_MS = 500
        const val WRITE_CHUNK = Tones.SAMPLE_RATE / 10
        const val SOUND_THRESHOLD = 200
    }
}
