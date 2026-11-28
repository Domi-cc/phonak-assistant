package io.github.domicc.lara.announce

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
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
import kotlinx.coroutines.Job
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
 * announcement is skipped and the reason is journaled. That is asked before the first sound
 * and watched until the last word ([OutputGuard]): an announcement whose output changes on
 * the way is cut off.
 *
 * Speech comes from the phone's text-to-speech engine. Its audio is played by the engine's own
 * process, which is fine here because no voice link has to be kept alive.
 *
 * The same checks and the same guard serve the silence test ([playSilence]), which opens the
 * media channel with nothing audible in it.
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

    /**
     * The reason of the announcement that plays right now, from its first sound to its end;
     * null while none plays. Only to be read: with it and [lastEnded] the watch of the sound
     * channels ([io.github.domicc.lara.audio.AidChannels]) tells a media stream this app
     * opened from a sound of the phone.
     */
    @Volatile
    var playing: String? = null
        private set

    /** The announcement that ended last; null if none has played in this process. */
    @Volatile
    var lastEnded: Ended? = null
        private set

    /** @param elapsedMs when it ended, on the elapsed-realtime clock */
    class Ended(val reason: String, val elapsedMs: Long)

    /**
     * Speaks [text]. [reason] says who asked (for example "hourly" or "screen") and is journaled.
     *
     * @param logText false for a text that is nobody else's business (the title of an
     * appointment): the journal then gets its length and nothing of the text itself
     * @param stopIf asked when it is this announcement's turn, and again and again until its
     * last word: a reason not to speak, or not to speak on, or null. For a caller that must
     * not talk into something that can begin in the meantime, like a session with the
     * hearing aid. Called on the main thread.
     */
    fun announce(text: String, reason: String, logText: Boolean = true, stopIf: (() -> String?)? = null) {
        // The alarm that leads here does not keep the phone awake for the asynchronous part.
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:announce")
            .apply { acquire(MAX_DURATION_MS + 5_000) }
        scope.launch {
            try {
                oneAtATime.withLock { speak(text, reason, logText, stopIf) }
            } catch (e: Exception) {
                // the message of an exception may quote what was to be spoken
                journal.log("announce_error", mapOf("reason" to reason, "exception" to if (logText) e.toString() else e.javaClass.name))
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun speak(text: String, reason: String, logText: Boolean, stopIf: (() -> String?)?) {
        val startedAt = SystemClock.elapsedRealtime()
        fun ms() = SystemClock.elapsedRealtime() - startedAt
        fun skipped(why: String, more: Map<String, Any?> = emptyMap()) =
            journal.log("announce_skipped", mapOf("reason" to reason, "why" to why) + more)

        // An announcement that had to wait for the one before it may have been overtaken.
        stopIf?.invoke()?.let { return skipped(it) }
        if (headsetLink.store.address == null) return skipped("no_hearing_aid_selected")
        val headset = headsetLink.await(2_000)
        val aid = headset?.let { headsetLink.hearingAid(it) } ?: return skipped("hearing_aid_not_connected")
        // Where would Android play this right now?
        val outputs = outputs()
        if (!onlyTheAid(outputs, aid.address)) {
            return skipped("output_is_not_the_hearing_aid", mapOf("outputTypes" to outputs.map { it.type }))
        }
        val output = outputs.first()

        val tts = engine() ?: return skipped("no_speech_engine")
        // The first start of the engine in a process can take seconds. The hearing aid may
        // have gone in that time, or a second device may have connected: ask again.
        stopIf?.invoke()?.let { return skipped(it) }
        val again = outputs()
        if (!onlyTheAid(again, aid.address)) return skipped("output_changed", mapOf("outputTypes" to again.map { it.type }))

        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        val focusGranted = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        journal.log(
            "announce_start",
            mapOf(
                "reason" to reason,
                if (logText) "text" to text else "textLength" to text.length,
                "focusGranted" to focusGranted,
                "musicActive" to audio.isMusicActive,
            ),
        )
        // How the announcement ended. Whoever completes it first decides, and the guard does
        // so the moment the sound is no longer the hearing aid's alone.
        val done = CompletableDeferred<String>()
        val guard = OutputGuard(aid.address, stopIf) { why -> if (done.complete(why)) tts.stop() }
        try {
            playing = reason
            guard.start()
            // The chime opens the stream to the hearing aid, so that the speech is not clipped.
            playChime(output, done)
            // once more by hand: the guard looks every [WATCH_MS], and the title comes now
            guard.check()
            if (!done.isCompleted) {
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
                if (queued != TextToSpeech.SUCCESS) done.complete("engine_refused")
            }
            val result = withTimeoutOrNull(MAX_DURATION_MS) { done.await() } ?: "timeout"
            // After this nobody watches the output any more, so the engine must not speak on.
            if (result == "timeout") tts.stop()
            journal.log("announce_end", mapOf("reason" to reason, "result" to result, "ms" to ms()))
        } finally {
            ended(reason)
            guard.stop()
            audio.abandonAudioFocusRequest(focus)
        }
    }

    private fun ended(reason: String) {
        playing = null
        lastEnded = Ended(reason, SystemClock.elapsedRealtime())
    }

    /**
     * Plays [ms] of pure silence into the hearing aid over the media channel: a test for the
     * ear. Android opens the media stream for silence like for any sound, so the wearer can
     * tell whether the surroundings change although nothing is audible. That shows whether
     * the hearing aid reacts to the stream itself or only to sound in it, and so whether the
     * silent seconds Android adds after every announcement cost hearing at all.
     *
     * It keeps the rules of an announcement: the same checks before (a hearing aid is
     * selected, connected and the only output), the same guard while it plays, one at a
     * time with the announcements, and `announce_start` and `announce_end` in the journal,
     * here with `silence=true`. Left out are the chime, the speech engine and the request
     * for the audio focus: nothing is to be heard, so no other sound may be turned down
     * for it.
     *
     * @param reason who asked, as for [announce]
     */
    fun playSilence(ms: Int, reason: String) {
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:announce")
            .apply { acquire(ms + 10_000L) }
        scope.launch {
            try {
                oneAtATime.withLock { silence(ms, reason) }
            } catch (e: Exception) {
                journal.log("announce_error", mapOf("reason" to reason, "exception" to e.toString()))
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    private suspend fun silence(ms: Int, reason: String) {
        val startedAt = SystemClock.elapsedRealtime()
        fun skipped(why: String, more: Map<String, Any?> = emptyMap()) =
            journal.log("announce_skipped", mapOf("reason" to reason, "why" to why) + more)

        // The questions [speak] asks before its first sound, word for word. Silence that came
        // out of the phone's speaker would harm nobody, but the test would then say nothing
        // about the hearing aid.
        if (headsetLink.store.address == null) return skipped("no_hearing_aid_selected")
        val headset = headsetLink.await(2_000)
        val aid = headset?.let { headsetLink.hearingAid(it) } ?: return skipped("hearing_aid_not_connected")
        val outputs = outputs()
        if (!onlyTheAid(outputs, aid.address)) {
            return skipped("output_is_not_the_hearing_aid", mapOf("outputTypes" to outputs.map { it.type }))
        }
        journal.log(
            "announce_start",
            // musicActive: other media was playing already, so the stream was open anyway
            mapOf("reason" to reason, "silence" to true, "durationMs" to ms, "musicActive" to audio.isMusicActive),
        )
        val done = CompletableDeferred<String>()
        val guard = OutputGuard(aid.address, null) { why -> done.complete(why) }
        try {
            playing = reason
            guard.start()
            play(Tones.silence(ms), outputs.first(), done)
            // completed only by the guard: then the output changed on the way
            val result = if (done.isCompleted) done.await() else "ok"
            journal.log(
                "announce_end",
                mapOf("reason" to reason, "silence" to true, "result" to result, "ms" to SystemClock.elapsedRealtime() - startedAt),
            )
        } finally {
            ended(reason)
            guard.stop()
        }
    }

    private fun outputs(): List<AudioDeviceInfo> = audio.getAudioDevicesForAttributes(attributes)

    /** Whether [outputs] is the media channel of the hearing aid with [address] and nothing besides. */
    private fun onlyTheAid(outputs: List<AudioDeviceInfo>, address: String): Boolean =
        outputs.isNotEmpty() && outputs.all {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && it.address.equals(address, ignoreCase = true)
        }

    /**
     * Watches, from the chime to the last word, that the sound still goes to the hearing aid
     * and nowhere else, and that the caller still wants it spoken. The question before the
     * first sound is not enough. When the hearing aid is switched off, put into its charger
     * or carried out of range, Android moves a stream that is playing to the phone's
     * speaker; a car or a headset that connects can take the stream over. The speech engine
     * plays in its own process and cannot be tied to one output, so the only way to keep the
     * rest of a sentence from coming out somewhere else is to stop it.
     *
     * Three signals, because none of this could be tried on the phone yet:
     * - Android's notice that an output is about to go (`ACTION_AUDIO_BECOMING_NOISY`). By
     *   the Android source it is sent while something plays or holds the audio focus, as an
     *   announcement does, and about a second before the stream is moved.
     * - Android's reports of audio devices that came or went.
     * - Asking every [WATCH_MS] where the sound would go now. That also catches a change of
     *   the route that comes with neither of the two.
     *
     * Everything here runs on the main thread.
     *
     * @param stopIf the caller's own reason to stop, see [announce]
     * @param lost called with the reason, possibly more than once, between [start] and [stop]
     */
    private inner class OutputGuard(
        private val aidAddress: String,
        private val stopIf: (() -> String?)?,
        private val lost: (String) -> Unit,
    ) {
        /** The repeated question while the guard is on; null before [start] and after [stop]. */
        private var watching: Job? = null

        private val devices = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = check()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = check()
        }

        private val goingAway = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (watching != null) lost("output_going_away")
            }
        }

        fun start() {
            watching = scope.launch {
                while (true) {
                    check()
                    delay(WATCH_MS)
                }
            }
            // null: on the main thread. Android answers at once with the devices that are there.
            audio.registerAudioDeviceCallback(devices, null)
            context.registerReceiver(goingAway, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
        }

        fun check() {
            // A report that Android had on its way when the guard was switched off must not
            // stop the announcement after this one: they share the engine.
            if (watching == null) return
            val why = stopIf?.invoke() ?: if (onlyTheAid(outputs(), aidAddress)) null else "output_lost"
            if (why != null) lost(why)
        }

        fun stop() {
            watching?.cancel()
            watching = null
            // each on its own: if one of the two was never registered, the other still is
            runCatching { audio.unregisterAudioDeviceCallback(devices) }
            runCatching { context.unregisterReceiver(goingAway) }
        }
    }

    /**
     * A short pause, then one soft tone, played by this app on the same output.
     *
     * @param stop ends the chime early: the stream is tied to [output], but if that device
     * goes away Android plays the rest wherever it likes
     */
    private suspend fun playChime(output: AudioDeviceInfo, stop: CompletableDeferred<String>) =
        play(Tones.silence(STREAM_WARM_UP_MS) + Tones.tone(659.0, 160, volume = 0.25) + Tones.silence(120), output, stop)

    /**
     * Plays [pcm] on [output], by this app, and returns when it is through or [stop] is
     * completed. What the chime is played with, and the silence of the silence test.
     */
    private suspend fun play(pcm: ShortArray, output: AudioDeviceInfo, stop: CompletableDeferred<String>) {
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
            withTimeoutOrNull(pcm.size * 1000L / Tones.SAMPLE_RATE + 50) { stop.await() }
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

        /** How often the guard asks where the sound goes while an announcement plays. */
        const val WATCH_MS = 200L
    }
}
