package io.github.domicc.lara.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import io.github.domicc.lara.announce.Announcer
import io.github.domicc.lara.core.audio.StuckVoiceLink
import io.github.domicc.lara.core.journal.JournalNames
import io.github.domicc.lara.input.InputRouter
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Journals when a sound channel to the hearing aid is really open, all day, and looks after a
 * voice link that did not go down when its session ended.
 *
 * Why: every sound channel the phone opens makes the hearing aid leave its hearing program for
 * a streaming program and come back afterwards (CLAUDE.md, "What a sound channel does to the
 * hearing aid"). The owner's rule is that the app must not get in the way of the aid's own
 * job. How long a channel was open, and how much of that was this app's doing, the journal
 * could not tell: it had the app's own `announce_*` and `session_*` lines, and nothing for the
 * seconds Android adds after the last sound, for the sounds of the phone and of other apps,
 * or for a voice link outside a session.
 *
 * Two reports of the Bluetooth stack are journaled, for the selected hearing aid only
 * ([HearingAidStore]). Reports about other devices are ignored.
 * - `aid_media_stream` with `state` = `playing` or `stopped`: the media stream (A2DP) was
 *   really started or suspended, whoever plays. `announcing` says whether an announcement of
 *   this app was playing or had ended within the last [ANNOUNCE_TAIL_MS], and
 *   `announceReason` which one. That tells the app's share from the phone's own sounds.
 *   A third state, `not_playing`, is no stream at all: Android reports "not playing" every
 *   time the hearing aid's media profile connects (when the aid is switched on, comes back
 *   into range or leaves its charger), and this is the word for such a report with no start
 *   before it. Nobody counts it.
 * - `hfp_audio_state` with `state` = `connecting`, `connected` or `disconnected`: the voice
 *   link (hands-free audio). `mode` is the phone's audio mode, where a phone call shows.
 *   `session` says whether a session of this app is busy and the phone is not in a call; on
 *   `disconnected` it is true if that held at any report about this link. A session frees
 *   itself as soon as it sees the link down, which can be a moment before the report of that
 *   arrives here, and a link that stayed up after its session is still that session's.
 *   A second `connected` for the same link carries `repeated=true`: Android sends one when
 *   closing the audio failed. A fourth state, `not_connected`, is Android's "disconnected"
 *   after "disconnected": an attempt that was refused or failed at once, and no link.
 *
 * The line that closes a channel carries `onMs`, how long it was open, if this process saw it
 * open. For the voice link it also carries `callMs` if the phone went into a call while the
 * link was up or coming up: how much of `onMs` came after that. A call that comes in during
 * a session can take the session's link over without a report in between, and that time is
 * not the session's. The day counters add all of this up
 * ([io.github.domicc.lara.core.stats.DayStatsCounter]).
 *
 * Both reports are sent by the Bluetooth app under its own uid, so the receiver has to be
 * registered as exported (read from the Android 17 source; [SessionService] receives the
 * hands-free one that way, and that has run on the phone). They go to holders of the
 * permission for nearby devices only. The receiver is therefore registered when that
 * permission is held, and the journal says from when on it listens (`aid_channels_watching`):
 * before that line, no report does not mean no channel. It is never unregistered. It lives as
 * long as the process, which the assistant binding and the listener service keep alive. A
 * process lives for days and the owner shares single days, so the first line of every day
 * file says it again ([watching]).
 *
 * What was open when the process started is known for the voice link only
 * (`aid_channels_at_start`, once the hands-free service can be asked). No connection to the
 * A2DP service is opened for the media side: whether a stream plays at process start is
 * unknown until the first report, and the end of such a stream reads `not_playing` like the
 * report at a connect. Its time is in no number.
 *
 * No address is journaled: every line here is about the one selected device.
 *
 * The time of a line is the time the report reached the app. Not tried on the phone: whether
 * a report can be held back while the phone sleeps. The journal's two clocks that count with
 * and without sleep show it if it happens.
 *
 * Nothing in this class makes a sound. Everything in it runs on the main thread, except
 * [followStuckLink], which may be called from anywhere.
 */
class AidChannels(
    private val context: Context,
    private val journal: Journal,
    private val headsetLink: HeadsetLink,
    private val announcer: Announcer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audio = context.getSystemService(AudioManager::class.java)

    /**
     * Whether the reports of the Bluetooth stack arrive. Written on the main thread; read
     * from any thread for the first line of a day file.
     */
    @Volatile
    var watching = false
        private set
    private var missingPermissionJournaled = false

    /** When the media stream was reported playing; null while it is not, or not known to be. */
    private var mediaSince: Long? = null

    /** When the voice link that is up was reported connected; null while none is, or not known since when. */
    private var voiceSince: Long? = null

    /**
     * A session was busy, and the phone not in a call, at one of the reports about the voice
     * link that is coming up or up: the link is that session's.
     */
    private var voiceInSession = false

    /**
     * When the phone went into a call while the voice link was up or coming up; null if it
     * has not. From then on the link is the call's, whoever opened it.
     */
    private var voiceCallSince: Long? = null

    /** When the voice link was last reported disconnected, on the elapsed-realtime clock. */
    @Volatile
    private var voiceDownAt = Long.MIN_VALUE

    /** The follow-up that is looking after a voice link right now, if any. */
    private val followUp = AtomicReference<Job?>(null)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (!headsetLink.store.isSelected(device)) return
            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            when (intent.action) {
                BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED -> onMediaStream(state)
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED ->
                    onVoiceLink(state, intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1))
            }
        }
    }

    /** Call once at process start, after the request for the hands-free service. */
    fun start() {
        val since = SystemClock.elapsedRealtime()
        watch("process_start")
        // A call that comes in during a session can take over the link that is up without a
        // report about the link in between (read from the Android 17 source; which way this
        // phone goes is not known). The change of the audio mode is the only sign of it.
        // Needs no permission and is never taken back, like the receiver.
        audio.addOnModeChangedListener(context.mainExecutor) { mode ->
            if (voiceSince != null) noteCall(mode, SystemClock.elapsedRealtime())
        }
        scope.launch {
            val headset = headsetLink.ready.filterNotNull().first()
            // The hands-free service only answers to holders of the permission: this is the
            // moment the receiver can be registered if it could not be at process start.
            watch("hfp_proxy_ready")
            val voiceLink = withContext(Dispatchers.Default) { voiceLinkUp(headset) }
            journal.log(
                "aid_channels_at_start",
                mapOf(
                    "voiceLink" to voiceLink,
                    "session" to SessionService.busy,
                    "mode" to modeWord(audio.mode),
                    // long if Bluetooth was off or the permission came later
                    "waitedMs" to SystemClock.elapsedRealtime() - since,
                ),
            )
        }
    }

    /**
     * The app came onto the screen. Nothing tells an app that a permission was granted in
     * Android's settings, so this is where it is noticed, like for the listener service.
     */
    fun onScreen() {
        // does nothing if the service was asked for before, or without the permission
        headsetLink.connect()
        watch("on_screen")
    }

    private fun watch(at: String) {
        if (watching) return
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // once: the journal must show that the channel lines are missing, and why
            if (!missingPermissionJournaled) journal.log("aid_channels_no_permission", mapOf("at" to at))
            missingPermissionJournaled = true
            return
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
        }
        try {
            // Exported: the sender is the Bluetooth app, not the system uid. With
            // RECEIVER_NOT_EXPORTED Android would drop both reports without a word.
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            watching = true
            journal.log("aid_channels_watching", mapOf("at" to at))
        } catch (e: Exception) {
            journal.log("aid_channels_error", mapOf("at" to at, "exception" to e.toString()))
        }
    }

    private fun onMediaStream(state: Int) {
        val now = SystemClock.elapsedRealtime()
        val fields = LinkedHashMap<String, Any?>()
        when (state) {
            BluetoothA2dp.STATE_PLAYING -> {
                fields[JournalNames.STATE] = "playing"
                // Also if a start is known already: Android reports "playing" only for a
                // stream that was not, so the stop in between was lost and this start is
                // the one the next stop belongs to.
                mediaSince = now
            }
            BluetoothA2dp.STATE_NOT_PLAYING -> {
                val since = mediaSince
                if (since == null) {
                    // No start was seen, so this is not the end of a stream anybody saw.
                    // Android sends this report every time the hearing aid's media profile
                    // connects ("upon connected, the audio starts out as stopped",
                    // A2dpStateMachine in the Android 17 source), and says "previous state:
                    // playing" then too. The end of a stream that began before this process
                    // looks the same. A word of its own, so that nobody counts it as a stream.
                    fields[JournalNames.STATE] = "not_playing"
                } else {
                    fields[JournalNames.STATE] = JournalNames.STATE_STOPPED
                    fields[JournalNames.ON_MS] = now - since
                }
                mediaSince = null
            }
            else -> fields[JournalNames.STATE] = state.toString()
        }
        val reason = announcer.playing ?: announcer.lastEnded?.takeIf { now - it.elapsedMs <= ANNOUNCE_TAIL_MS }?.reason
        fields[JournalNames.ANNOUNCING] = reason != null
        if (reason != null) fields["announceReason"] = reason
        journal.log(JournalNames.AID_MEDIA_STREAM, fields)
    }

    /**
     * @param previous the state before, as Android reports it with every change. It tells a
     * new link from a second report about the same one, which the state alone cannot.
     */
    private fun onVoiceLink(state: Int, previous: Int) {
        val now = SystemClock.elapsedRealtime()
        val mode = audio.mode
        val fields = LinkedHashMap<String, Any?>()
        when (state) {
            BluetoothHeadset.STATE_AUDIO_CONNECTING, BluetoothHeadset.STATE_AUDIO_CONNECTED -> {
                // A link begins here. Whatever is still remembered belongs to a link whose
                // end was never reported (the Bluetooth process died, say) and must not
                // lend its start or its session to this one.
                if (previous == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) forgetVoiceLink()
                noteCall(mode, now)
                // Not for a link that comes up in a call. A call that comes in during a
                // session ends it, and the link for the ringing can be on its way while
                // the session is still closing and the service still busy.
                val session = SessionService.busy && voiceCallSince == null
                voiceInSession = voiceInSession || session
                fields[JournalNames.SESSION] = session
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
                    fields[JournalNames.STATE] = JournalNames.STATE_CONNECTING
                } else {
                    fields[JournalNames.STATE] = JournalNames.STATE_CONNECTED
                    if (previous == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                        // "Connected" after "connected": Android did not get the audio
                        // closed and says so this way (HeadsetStateMachine in the Android 17
                        // source: back from AudioDisconnecting to AudioOn, or a failed
                        // disconnect). The link was up all the time, so the start stays. A
                        // clock that began anew here would leave out the time the link was
                        // stuck, which is the time this class is there to show.
                        fields["repeated"] = true
                    } else {
                        voiceSince = now
                    }
                }
            }
            BluetoothHeadset.STATE_AUDIO_DISCONNECTED -> {
                if (previous == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    // "Disconnected" after "disconnected" is not the end of a link: by the
                    // same source Android reports an attempt that was refused or failed at
                    // once this way. A word of its own, so that nobody counts an opening.
                    fields[JournalNames.STATE] = JournalNames.STATE_NOT_CONNECTED
                } else {
                    fields[JournalNames.STATE] = JournalNames.STATE_DISCONNECTED
                    val since = voiceSince
                    if (since != null) {
                        fields[JournalNames.ON_MS] = now - since
                        voiceCallSince?.let { fields[JournalNames.CALL_MS] = now - maxOf(it, since) }
                    }
                }
                fields[JournalNames.SESSION] = voiceInSession || (SessionService.busy && voiceCallSince == null)
                forgetVoiceLink()
                // for the follow-up, either way: Android says that no link is up now
                voiceDownAt = now
            }
            else -> {
                fields[JournalNames.STATE] = state.toString()
                fields[JournalNames.SESSION] = SessionService.busy
            }
        }
        fields["mode"] = modeWord(mode)
        journal.log(JournalNames.HFP_AUDIO_STATE, fields)
    }

    private fun forgetVoiceLink() {
        voiceSince = null
        voiceInSession = false
        voiceCallSince = null
    }

    /** Remembers the moment the phone went into a call, ringing included, for the link that is up or coming up. */
    private fun noteCall(mode: Int, now: Long) {
        if (voiceCallSince == null && InputRouter.isCall(mode)) voiceCallSince = now
    }

    /**
     * A session has closed its voice link and the hands-free audio of the hearing aid was
     * still up when the session gave up waiting for it (`session_link_down down=false`).
     * While that audio is up the hearing aid stays in a streaming program (probably its phone
     * program; nobody has shown that for a voice-recognition link) instead of its hearing
     * program. So the app looks again, three times ([StuckVoiceLink.LOOKS_AFTER_MS]). A look
     * that finds the link still up and still the session's writes `aid_voice_link_stuck`,
     * and the first such look of a follow-up vibrates. What a look does is decided by
     * [StuckVoiceLink.FollowUp]; it never touches a link that the session did not open.
     *
     * Only one look of a follow-up may stop voice recognition once more, and only if the
     * session's own stop was not taken (`action=stop_again`). What the stop can do, read
     * from the Android 17 source and not tried: it closes the audio only while Android has a
     * voice recognition marked as started, and every stop that reaches that mark clears it.
     * After a stop that Android took, a second one closes nothing and sends the hearing aid
     * an error result. An ordinary app has no stronger call, so in that case the line and
     * the vibration are all there is (`action=report_only`). By the same source Android
     * refuses a tap and every start of a voice recognition while the audio is up, so the
     * one stop cannot take away a tap that waits for its answer or end a recognition that
     * another app has started for another device.
     *
     * Every follow-up ends with one line: `aid_voice_link_follow_up` with the reason in
     * `end` (what the look found, `replaced` by the follow-up of a later session, or
     * `not_watching` if the reports about the link do not reach this class), or
     * `aid_voice_link_gave_up` if the third look still found the link up.
     *
     * It runs in this class's own scope and not in the session: the service must be free for
     * the next tap long before the last look. A wake lock of its own keeps the phone awake
     * for the looks, because the session's is released in a moment and the looks are timers.
     *
     * @param closingAtElapsedMs when the session began to close its link. A report that the
     * link went down at any moment from then on means it is not the session's any more.
     * @param sessionStopTaken what the session got when it stopped voice recognition, see
     * [AudioLink.voiceRecognitionStopped]
     */
    fun followStuckLink(closingAtElapsedMs: Long, sessionStopTaken: Boolean?) {
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:link_follow_up")
            .apply { acquire(StuckVoiceLink.LOOKS_AFTER_MS.last() + FOLLOW_UP_WAKE_MARGIN_MS) }
        val job = scope.launch { follow(closingAtElapsedMs, sessionStopTaken) }
        // also when it is cancelled before its first line has run
        job.invokeOnCompletion { if (wakeLock.isHeld) wakeLock.release() }
        // One at a time: the newest session's. The one before it would only find out at its
        // next look that a session has been there since.
        followUp.getAndSet(job)?.takeIf { it.isActive }?.let {
            it.cancel()
            journal.log("aid_voice_link_follow_up", mapOf("end" to "replaced"))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun follow(closingAtElapsedMs: Long, sessionStopTaken: Boolean?) {
        val since = SystemClock.elapsedRealtime()
        if (!watching) {
            // Without the reports nobody can say that the link which is up at a look is
            // still the session's, and then it is not this app's to stop. The link was up
            // a moment ago and nobody looks after it: shown as a problem on the screen.
            journal.log("aid_voice_link_follow_up", mapOf("end" to "not_watching", "problem" to "link_not_followed"))
            return
        }
        val looks = StuckVoiceLink.FollowUp(sessionStopTaken)
        for (afterMs in StuckVoiceLink.LOOKS_AFTER_MS) {
            delay(since + afterMs - SystemClock.elapsedRealtime())
            // One look runs on the main thread without a pause, from the questions to the
            // stop. A session is accepted on the main thread too ([SessionService.busy] is
            // set there), so none can begin between the question and the stop, and the stop
            // can never hit the link of a session that has just started.
            val headset = headsetLink.current()
            val aid = headset?.let { runCatching { headsetLink.hearingAid(it) }.getOrNull() }
            val connected =
                if (headset != null && aid != null) runCatching { headset.isAudioConnected(aid) }.getOrNull() else null
            val step = looks.look(
                sessionBusy = SessionService.busy,
                phoneCall = InputRouter.isCall(audio.mode),
                disconnectSeen = voiceDownAt >= closingAtElapsedMs,
                audioConnected = connected,
                // false also for a link that was up before the session began: another
                // app's audio on the hearing aid, which the session found and left there
                cameUpInSession = voiceInSession,
            )
            val fields = mapOf("look" to step.number, "afterMs" to SystemClock.elapsedRealtime() - since)
            if (!step.look.stuck || headset == null || aid == null) {
                journal.log("aid_voice_link_follow_up", fields + mapOf("end" to step.look.word, "voiceLink" to connected))
                return
            }
            val stopped = if (step.look == StuckVoiceLink.Look.STOP_AGAIN) {
                runCatching { headset.stopVoiceRecognition(aid) }.getOrDefault(false)
            } else {
                null
            }
            journal.log(
                "aid_voice_link_stuck",
                fields + mapOf("action" to step.look.word, "sessionStop" to sessionStopTaken, "voiceRecognitionStopped" to stopped),
            )
            // Nothing may be played here: no link of this app is open. Like every failure
            // outside a link, it is signalled by vibration, once for the whole follow-up.
            if (step.signal) AudioLink.vibrateFailure(context)
            if (step.gaveUp) {
                journal.log("aid_voice_link_gave_up", mapOf("looks" to step.number))
                return
            }
        }
    }

    /** Whether the hands-free audio of the selected hearing aid is up; null if the aid is not connected. */
    @SuppressLint("MissingPermission")
    private fun voiceLinkUp(headset: BluetoothHeadset): Boolean? =
        runCatching { headsetLink.hearingAid(headset)?.let(headset::isAudioConnected) }.getOrNull()

    private companion object {
        /**
         * How long after its end an announcement still counts as the reason for a report
         * about the media stream. By the Android 17 source the stream is suspended 3 s after
         * the last sound; the fourth second is for the report to arrive.
         */
        const val ANNOUNCE_TAIL_MS = 4_000L

        /** How much longer than the last look the follow-up's wake lock lasts at most. */
        const val FOLLOW_UP_WAKE_MARGIN_MS = 5_000L

        /** `AudioManager.mode` as a word; a mode this build does not know as its number. */
        fun modeWord(mode: Int): String = when (mode) {
            AudioManager.MODE_NORMAL -> "normal"
            AudioManager.MODE_RINGTONE -> "ringtone"
            AudioManager.MODE_IN_CALL -> "in_call"
            AudioManager.MODE_IN_COMMUNICATION -> "in_communication"
            AudioManager.MODE_CALL_SCREENING -> "call_screening"
            AudioManager.MODE_CALL_REDIRECT -> "call_redirect"
            AudioManager.MODE_COMMUNICATION_REDIRECT -> "communication_redirect"
            else -> mode.toString()
        }
    }
}
