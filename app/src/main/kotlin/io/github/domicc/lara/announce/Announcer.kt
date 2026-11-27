package io.github.domicc.lara.announce

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.domicc.lara.audio.HeadsetLink
import io.github.domicc.lara.audio.Tones
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Says something into the hearing aid without being asked: a short chime, then speech, over the
 * media channel (A2DP), not over the voice link. No microphone is involved and the hearing aid
 * does not see a "call".
 *
 * Privacy rule: an announcement is only spoken if Android would send it to the selected hearing
 * aid ([io.github.domicc.lara.audio.HearingAidStore]) and nowhere else. If the aid is not
 * connected, or the sound would come out of the phone speaker, a car or another headset, the
 * announcement is skipped and the reason is journaled.
 *
 * Speech comes from the phone's text-to-speech engine. Its audio is played by the engine's own
 * process, which is fine here because no voice link has to be kept alive.
 */
class Announcer(
    private val context: Context,
    private val journal: Journal,
    private val headsetLink: HeadsetLink,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val oneAtATime = Mutex()
    private var engine: TextToSpeech? = null

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Speaks [text]. [reason] says who asked (for example "hourly" or "screen") and is journaled. */
    fun announce(text: String, reason: String) {
        // The alarm that leads here does not keep the phone awake for the asynchronous part.
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:announce")
            .apply { acquire(MAX_DURATION_MS + 5_000) }
        scope.launch {
            try {
                oneAtATime.withLock { speak(text, reason) }
            } catch (e: Exception) {
                journal.log("announce_error", mapOf("reason" to reason, "exception" to e.toString()))
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun speak(text: String, reason: String) {
        val startedAt = SystemClock.elapsedRealtime()
        fun ms() = SystemClock.elapsedRealtime() - startedAt

        if (headsetLink.store.address == null) {
            journal.log("announce_skipped", mapOf("reason" to reason, "why" to "no_hearing_aid_selected"))
            return
        }
        val headset = headsetLink.await(2_000)
        val aid = headset?.let { headsetLink.hearingAid(it) }
        if (aid == null) {
            journal.log("announce_skipped", mapOf("reason" to reason, "why" to "hearing_aid_not_connected"))
            return
        }
        // Where would Android play this right now?
        val outputs = audio.getAudioDevicesForAttributes(attributes)
        val toAidOnly = outputs.isNotEmpty() && outputs.all {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && it.address.equals(aid.address, ignoreCase = true)
        }
        if (!toAidOnly) {
            journal.log(
                "announce_skipped",
                mapOf("reason" to reason, "why" to "output_is_not_the_hearing_aid", "outputTypes" to outputs.map { it.type }),
            )
            return
        }
        val output = outputs.first()

        val tts = engine() ?: run {
            journal.log("announce_skipped", mapOf("reason" to reason, "why" to "no_speech_engine"))
            return
        }

        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        val focusGranted = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        journal.log(
            "announce_start",
            mapOf("reason" to reason, "text" to text, "focusGranted" to focusGranted, "musicActive" to audio.isMusicActive),
        )
        try {
            // The chime opens the stream to the hearing aid, so that the speech is not clipped.
            playChime(output)

            val done = CompletableDeferred<String>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    journal.log("announce_speaking", mapOf("reason" to reason, "ms" to ms()))
                }

                override fun onDone(utteranceId: String?) {
                    done.complete("ok")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    done.complete("engine_error")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    done.complete("engine_error_$errorCode")
                }
            })
            val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "announce-" + startedAt)
            val result = if (queued != TextToSpeech.SUCCESS) "engine_refused"
            else withTimeoutOrNull(MAX_DURATION_MS) { done.await() } ?: "timeout"
            journal.log("announce_end", mapOf("reason" to reason, "result" to result, "ms" to ms()))
        } finally {
            audio.abandonAudioFocusRequest(focus)
        }
    }

    /** A short pause, then one soft tone, played by this app on the same output. */
    private suspend fun playChime(output: AudioDeviceInfo) {
        val pcm = Tones.silence(STREAM_WARM_UP_MS) + Tones.tone(659.0, 160, volume = 0.25) + Tones.silence(120)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Tones.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.preferredDevice = output
            track.write(pcm, 0, pcm.size)
            track.play()
            delay(pcm.size * 1000L / Tones.SAMPLE_RATE + 50)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Creates the speech engine on first use and keeps it; null if German speech is unavailable. */
    private suspend fun engine(): TextToSpeech? {
        engine?.let { return it }
        val ready = CompletableDeferred<Int>()
        val tts = TextToSpeech(context) { status -> ready.complete(status) }
        val status = withTimeoutOrNull(ENGINE_TIMEOUT_MS) { ready.await() }
        if (status != TextToSpeech.SUCCESS) {
            journal.log("announce_engine", mapOf("status" to (status ?: "timeout")))
            tts.shutdown()
            return null
        }
        val language = tts.setLanguage(Locale.GERMANY)
        tts.setAudioAttributes(attributes)
        journal.log("announce_engine", mapOf("status" to "ready", "german" to language, "engine" to tts.defaultEngine))
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.shutdown()
            return null
        }
        engine = tts
        return tts
    }

    private companion object {
        const val MAX_DURATION_MS = 20_000L
        const val ENGINE_TIMEOUT_MS = 6_000L
        const val STREAM_WARM_UP_MS = 450
    }
}
