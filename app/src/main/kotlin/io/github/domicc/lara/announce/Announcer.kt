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
import io.github.domicc.lara.core.announce.AnnounceTexts
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Whether [outputs] is the media channel of the hearing aid with [address] and nothing besides. */
internal fun onlyTheAid(outputs: List<AudioDeviceInfo>, address: String): Boolean =
    outputs.isNotEmpty() && outputs.all {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && it.address.equals(address, ignoreCase = true)
    }

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
 *
 * What an announcement is played as is a setting ("Lautstärke-Kanal",
 * [io.github.domicc.lara.core.announce.AnnounceChannel]): as media, which is the default, or
 * as assistant speech, which is what every build before the setting did. Android decides by
 * that under which of its volumes the sound runs, and also where it goes: played as media,
 * an announcement follows the phone's choice of a media output, and with "Dieses
 * Smartphone" chosen there the checks here skip it. The choice is read when it is an
 * announcement's turn and holds for all of it: chime and speech are played with the same
 * attributes, and every question about the output (the checks before, the guard, the audio
 * focus) is asked with them.
 *
 * How loud it is, is the business of [AnnounceVolume]: asked once before the first sound
 * ([AnnounceVolume.before]) and once after the last ([AnnounceVolume.after]), whichever way
 * the announcement ends. In between the guard lets it look at the volume every time it looks
 * at the output itself ([AnnounceVolume.watch]).
 *
 * The request for the audio focus carries the attributes of the announcement, like
 * everything else of it. That has a price in the media channel: Android turns other apps'
 * music down by about 35 dB under a request made as an assistant and by about 14 dB under
 * any other (read from the Android 17 source), so an announcement over music is harder to
 * follow there than it was.
 */
class Announcer(
    private val context: Context,
    private val journal: Journal,
    private val headsetLink: HeadsetLink,
    private val volume: AnnounceVolume,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val oneAtATime = Mutex()
    private var engine: TextToSpeech? = null

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
            .apply { acquire(MAX_DURATION_MS + WAKE_MARGIN_MS) }
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

    /**
     * Plays the loudness sample "Lautstärke Stufe N" after a move of the slider: an
     * announcement like every other, with its own reason, so that it takes the same checks,
     * the same volume step, the same guard and the same restore. Only the newest sample
     * plays: one that still waits behind another announcement is dropped, and one that
     * plays is cut off by the next.
     */
    fun sample(step: Int) = announce(AnnounceTexts.sample(step), AnnounceTexts.SAMPLE_REASON, stopIf = volume.newestSample())

    @SuppressLint("MissingPermission")
    private suspend fun speak(text: String, reason: String, logText: Boolean, stopIf: (() -> String?)?) {
        val startedAt = SystemClock.elapsedRealtime()
        fun ms() = SystemClock.elapsedRealtime() - startedAt
        fun skipped(why: String, more: Map<String, Any?> = emptyMap()) {
            journal.log("announce_skipped", mapOf("reason" to reason, "why" to why) + more)
            volume.notPlayed(reason, why)
        }

        // What this announcement is played as, and with how much silence in front of it:
        // read now, when it is its turn, and the same for all of it.
        val settings = volume.store.settings.value
        val channel = settings.channel
        val leadInMs = settings.leadIn.ms
        val attributes = volume.attributes(channel)

        // Android counts a track that has just stopped as playing until its buffers have
        // played out, a few hundred milliseconds and more over Bluetooth. The volume step
        // would take this app's own sound of a moment ago for somebody's music and leave
        // the volume alone (`why=other_media`): two moves of the slider in a row would do
        // it. So an announcement right behind another one waits that tail out first.
        lastEnded?.let { delay(OWN_TAIL_MS - (SystemClock.elapsedRealtime() - it.elapsedMs)) }
        // An announcement that had to wait for the one before it may have been overtaken.
        stopIf?.invoke()?.let { return skipped(it) }
        if (headsetLink.store.address == null) return skipped("no_hearing_aid_selected")
        val headset = headsetLink.await(2_000)
        val aid = headset?.let { headsetLink.hearingAid(it) } ?: return skipped("hearing_aid_not_connected")
        // Where would Android play this right now?
        val outputs = outputs(attributes)
        if (!onlyTheAid(outputs, aid.address)) {
            return skipped("output_is_not_the_hearing_aid", mapOf("outputTypes" to outputs.map { it.type }, "channel" to channel.word))
        }
        val output = outputs.first()

        val tts = engine() ?: return skipped("no_speech_engine")
        // How the announcement ended. Whoever completes it first decides, and the guard does
        // so the moment the sound is no longer the hearing aid's alone.
        val done = CompletableDeferred<String>()
        val guard = OutputGuard(aid.address, attributes, stopIf) { why -> if (done.complete(why)) tts.stop() }
        // completed at the first word
        val speaking = CompletableDeferred<Unit>()
        var midLook: Job? = null
        var focus: AudioFocusRequest? = null
        var sounding = false
        var result: String? = null
        // The first start of the engine in a process can take seconds. The hearing aid may
        // have gone in that time, or a second device may have connected: ask again.
        stopIf?.invoke()?.let { return skipped(it) }
        val again = outputs(attributes)
        if (!onlyTheAid(again, aid.address)) return skipped("output_changed", mapOf("outputTypes" to again.map { it.type }))

        // The volume, in the same slice of the main thread as the check above: nothing
        // suspends between that check and the write. Before the audio focus and before the
        // lead-in silence, so that the value stands when the stream opens.
        val applied = volume.before(reason, channel, attributes, silence = false)
        // From here on whatever happens ends in the finally block: the volume step may have
        // changed the phone's media volume, and that has to be put back on every way out.
        try {
            if (applied.routeLost) {
                // the volume step asked the route once more, and the hearing aid was gone
                return skipped("output_changed", mapOf("outputTypes" to outputs(attributes).map { it.type }, "at" to "volume"))
            }
            // Half a second into the speech the journal gets a look at the volumes.
            midLook = scope.launch {
                speaking.await()
                delay(MID_LOOK_MS)
                volume.mid(reason)
            }
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
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
                    "channel" to channel.word,
                    "leadInMs" to leadInMs,
                    "chimeAmplitude" to CHIME_AMPLITUDE,
                ),
            )
            playing = reason
            sounding = true
            guard.start()
            // The chime opens the stream to the hearing aid, so that the speech is not clipped.
            playChime(output, attributes, leadInMs, done)
            // once more by hand: the guard looks every [WATCH_MS], and the title comes now
            guard.check()
            if (!done.isCompleted) {
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        journal.log("announce_speaking", mapOf("reason" to reason, "ms" to ms()))
                        speaking.complete(Unit)
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
                // Before every speak, with this announcement's attributes: the engine keeps
                // what it was given last, and that may be the other channel's. Speech with
                // other attributes than the guard watches could go where nobody looks.
                if (tts.setAudioAttributes(attributes) != TextToSpeech.SUCCESS) {
                    done.complete("engine_attributes_refused")
                } else {
                    val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "announce-" + startedAt)
                    if (queued != TextToSpeech.SUCCESS) done.complete("engine_refused")
                }
            }
            val ending = withTimeoutOrNull(MAX_DURATION_MS) { done.await() } ?: "timeout"
            result = ending
            // After this nobody watches the output any more, so the engine must not speak on.
            if (ending == "timeout") tts.stop()
            journal.log("announce_end", mapOf("reason" to reason, "result" to ending, "ms" to ms()))
        } finally {
            if (sounding) ended(reason)
            guard.stop()
            midLook?.cancel()
            // only now can the screen say whether it was played, and how it ended
            volume.ended(reason, result)
            // The phone's media volume back, if the volume step changed it: after the last
            // word, before the audio focus is given back and while the wake lock still
            // holds. Not to be cancelled: a coroutine that is cancelled would be thrown out
            // at the wait in there, and the owner's music would stay at the loudness of an
            // announcement.
            try {
                withContext(NonCancellable) { volume.after(applied, result) }
            } finally {
                focus?.let { audio.abandonAudioFocusRequest(it) }
            }
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

        // played as an announcement is played, so that it opens the same stream
        val channel = volume.store.settings.value.channel
        val attributes = volume.attributes(channel)

        // The questions [speak] asks before its first sound, word for word. Silence that came
        // out of the phone's speaker would harm nobody, but the test would then say nothing
        // about the hearing aid.
        if (headsetLink.store.address == null) return skipped("no_hearing_aid_selected")
        val headset = headsetLink.await(2_000)
        val aid = headset?.let { headsetLink.hearingAid(it) } ?: return skipped("hearing_aid_not_connected")
        val outputs = outputs(attributes)
        if (!onlyTheAid(outputs, aid.address)) {
            return skipped("output_is_not_the_hearing_aid", mapOf("outputTypes" to outputs.map { it.type }, "channel" to channel.word))
        }
        // Never writes a volume for silence; the journal still gets the line with the
        // volumes the stream is opened under.
        volume.before(reason, channel, attributes, silence = true)
        journal.log(
            "announce_start",
            mapOf(
                "reason" to reason,
                "silence" to true,
                "durationMs" to ms,
                // other media was playing already, so the stream was open anyway
                "musicActive" to audio.isMusicActive,
                "channel" to channel.word,
            ),
        )
        val done = CompletableDeferred<String>()
        val guard = OutputGuard(aid.address, attributes, null) { why -> done.complete(why) }
        try {
            playing = reason
            guard.start()
            play(Tones.silence(ms), outputs.first(), attributes, done)
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

    /** Where Android would play a sound with [attributes] right now. */
    private fun outputs(attributes: AudioAttributes): List<AudioDeviceInfo> = audio.getAudioDevicesForAttributes(attributes)

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
     * A limit, read from the Android 17 source and not measured: while the app's own
     * track plays (the lead-in silence and the chime), that track is tied to the hearing aid,
     * and Android answers the question for the output with the tie of the tracks that play,
     * as long as all of them have one. A change of the route that leaves the hearing aid
     * connected (the owner picks "Dieses Smartphone" in the output switcher during the
     * lead-in) is therefore not seen before the chime is over: the check by hand after the
     * chime or the next look, [WATCH_MS] later, stops the speech then, normally before the
     * engine has brought out its first word. A hearing aid that goes away is seen at once.
     * A lead-in of 3 s makes that blind stretch 3.3 s long instead of 0.7 s.
     *
     * @param attributes what the announcement is played with: the output is asked for these
     * @param stopIf the caller's own reason to stop, see [announce]
     * @param lost called with the reason, possibly more than once, between [start] and [stop]
     */
    private inner class OutputGuard(
        private val aidAddress: String,
        private val attributes: AudioAttributes,
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
            val why = stopIf?.invoke() ?: if (onlyTheAid(outputs(attributes), aidAddress)) null else "output_lost"
            if (why != null) lost(why)
            // While the app looks after the volume of this announcement it reads it at
            // every look: the volume keys may have changed it. Does nothing otherwise.
            else volume.watch()
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
     * A pause, then one soft tone, played by this app on the same output.
     *
     * @param leadInMs the length of the pause, the setting "Vorlauf vor der Ansage"
     * @param stop ends the chime early: the stream is tied to [output], but if that device
     * goes away Android plays the rest wherever it likes
     */
    private suspend fun playChime(output: AudioDeviceInfo, attributes: AudioAttributes, leadInMs: Int, stop: CompletableDeferred<String>) =
        play(Tones.silence(leadInMs) + Tones.tone(659.0, 160, volume = CHIME_AMPLITUDE) + Tones.silence(120), output, attributes, stop)

    /**
     * Plays [pcm] on [output] with [attributes], by this app, and returns when it is through
     * or [stop] is completed. What the chime is played with, and the silence of the silence
     * test.
     */
    private suspend fun play(pcm: ShortArray, output: AudioDeviceInfo, attributes: AudioAttributes, stop: CompletableDeferred<String>) {
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
        // what it plays with is set before every speak: it differs between announcements
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

        /**
         * How much longer than [MAX_DURATION_MS] the wake lock of an announcement lasts at
         * most: for the wait for the hands-free service and the speech engine before the
         * first sound, the longest lead-in, and the restore of the volume after the last.
         */
        const val WAKE_MARGIN_MS = 15_000L

        /** The level of the chime, as a share of full scale. Journaled with every announcement. */
        const val CHIME_AMPLITUDE = 0.25

        /** How long after the first word the journal looks at the volumes. */
        const val MID_LOOK_MS = 500L

        /**
         * How long after the end of one announcement the next one waits before it asks
         * whether other media is playing. Read from the Android source, not measured: a
         * stopped track stays "active" for its buffer and the latency of the output.
         */
        const val OWN_TAIL_MS = 1_200L

        /** How often the guard asks where the sound goes while an announcement plays. */
        const val WATCH_MS = 200L
    }
}
