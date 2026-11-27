package io.github.domicc.lara.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.annotation.SuppressLint
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.R
import io.github.domicc.lara.core.journal.JournalNames
import io.github.domicc.lara.shelly.ListenerService
import io.github.domicc.lara.speech.SessionPhrases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service (type microphone) that runs one audio session with the hearing aid and
 * stops itself afterwards. Every trigger (hearing-aid tap, Shelly remote, button on screen,
 * adb, assist gesture) reaches it through the input router, which is the only caller of
 * [start].
 *
 * Starting a microphone foreground service from the background is only allowed while the
 * system has the app's assistant service bound, or for a few seconds after the tap activity
 * was started. A refusal is journaled as `session_refused` instead of crashing, and signalled
 * by vibration: nothing audible exists before the link to the hearing aid is up.
 *
 * One session at a time. The service is busy from the moment it accepts a session until that
 * session is completely over: closing phrase said, link closed, and the hands-free audio of
 * the hearing aid really down ([awaitLinkDown]). A trigger in that time is turned down as
 * `session_busy` ([busyHandler]). Every session that is over is reported through [endHandler].
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The session this service is busy with, until [finished] has run for it. Main thread
     * only, like everything that decides whether a start is accepted.
     */
    private var session: Job? = null

    /** The newest start Android has delivered; main thread only. See [finished]. */
    private var lastStartId = 0
    private var linkWasUp = false

    /** The wearer ended the running session with a second tap on the hearing aid. */
    @Volatile
    private var endedByTap = false

    /** Journals link changes and ends the session when the hearing aid closes the link. */
    private val linkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val journal = LaraApp.instance.journal
            when (intent.action) {
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile_EXTRA_STATE, -1)
                    journal.log("hfp_audio_state", mapOf("state" to audioStateName(state)))
                    if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) linkWasUp = true
                    if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED && linkWasUp) session?.cancel()
                }
                ACTION_STOP_VOICE_COMMAND -> {
                    // a second tap on the hearing aid during a session
                    journal.log("tap_stop")
                    endedByTap = true
                    session?.cancel()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
            addAction(ACTION_STOP_VOICE_COMMAND)
        }
        registerReceiver(linkReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    @OptIn(DelicateCoroutinesApi::class) // CoroutineStart.ATOMIC, see where the session is launched
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val app = LaraApp.instance
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: "unknown"
        val device = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException or SecurityException: no background right
            app.journal.log(
                JournalNames.SESSION_REFUSED,
                mapOf("trigger" to trigger, "where" to "startForeground", "exception" to e.toString()),
            )
            if (session == null) {
                stopSelf(startId)
                refused(this, trigger)
            }
            return START_NOT_STICKY
        }
        // Not "is the job still active": a session that was cancelled (second tap, link lost)
        // is no longer active while it still closes the link. A start accepted in that time
        // would be torn down by the end of the old session.
        if (session != null) {
            app.journal.log("session_busy", mapOf("trigger" to trigger))
            busyHandler?.invoke(trigger)
            return START_NOT_STICKY
        }
        // Held by this session alone and released at its end. It is a local of this call, not
        // a field, so that the end of one session can never release the lock of the next.
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:session")
            .apply { acquire(SESSION_WAKE_MS) }
        linkWasUp = false
        endedByTap = false
        val programName = intent?.getStringExtra(EXTRA_PROGRAM)
        val program = SessionPrograms.create(programName)
        val listens = SessionPrograms.takesInputs(programName)
        busy = true
        // ATOMIC: the block starts even if the session is cancelled before a thread has picked
        // it up (a "stop" or a link broadcast handled right after this call returns). With the
        // default start mode such a session would never reach its clean-up: the service would
        // stay busy and turn every later tap down until the process is restarted. Started
        // that way, a cancelled session leaves at link.open() and ends as "cancelled".
        session = scope.launch(start = CoroutineStart.ATOMIC) {
            val link = AudioLink(this@SessionService, app.journal, app.headset, trigger, device)
            link.onInterrupted = { why ->
                app.journal.log("session_interrupted", mapOf("why" to why))
                session?.cancel()
            }
            var result = "error"
            try {
                // For a program that listens to the remote the listener's scan changes to its
                // session mode, while the link opens. Set here and not by the caller, so that
                // it is certain to come before the reset in the finally block.
                if (listens) ListenerService.setSessionOpen(true)
                markOpen(this@SessionService, true)
                link.open()
                result = withTimeoutOrNull(MAX_SESSION_MS) { program.run(link) } ?: "max_duration"
            } catch (e: AudioLink.Failed) {
                result = e.reason
            } catch (e: CancellationException) {
                result = JournalNames.RESULT_CANCELLED
                throw e
            } catch (e: Exception) {
                link.step("error", mapOf("exception" to e.toString()))
            } finally {
                // After a second tap or a lost link this coroutine is cancelled, and a cancelled
                // coroutine is thrown out of its clean-up at the first point where it would
                // wait: without NonCancellable nothing after closing the link would run.
                withContext(NonCancellable) {
                    try {
                        // The program has stopped listening. The listener takes the scan out of
                        // its session mode a little later by itself, see setSessionOpen.
                        if (listens) ListenerService.setSessionOpen(false)
                        link.close(result, app.prompts.get(SessionPhrases.END))
                        markOpen(this@SessionService, false)
                        awaitLinkDown(app, link)
                    } finally {
                        // Read before the service is free: the next session resets the flag.
                        val sayOverMedia = result == JournalNames.RESULT_CANCELLED && endedByTap
                        try {
                            // Still under the session's wake lock: this frees the service and
                            // lets the router open the next session for a press that waits.
                            withContext(Dispatchers.Main) { finished(trigger, result) }
                            if (sayOverMedia) farewellOverMedia(app)
                        } finally {
                            if (wakeLock.isHeld) wakeLock.release()
                        }
                    }
                }
            }
        }
        // The listener's scan is the only one a session has. If the listener is gone, this is
        // the moment to bring it back; after the launch, so that the answer to the hearing
        // aid does not wait for it.
        if (listens && !ListenerService.running) ListenerService.start(this, "session")
        return START_NOT_STICKY
    }

    /**
     * The very end of a session. On the main thread, like [onStartCommand], so that the two
     * cannot overlap: a start that is delivered before this has run is turned down as busy,
     * one that is delivered after it finds the service free.
     */
    private fun finished(trigger: String, result: String) {
        session = null
        busy = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        // By start id, not plain stopSelf(): if Android has already accepted a newer start
        // that is not delivered yet, the service stays and takes it.
        stopSelf(lastStartId)
        endHandler?.invoke(trigger, result)
    }

    override fun onDestroy() {
        unregisterReceiver(linkReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_session), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_session_title))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun audioStateName(state: Int): String = when (state) {
        BluetoothHeadset.STATE_AUDIO_CONNECTED -> "connected"
        BluetoothHeadset.STATE_AUDIO_CONNECTING -> "connecting"
        BluetoothHeadset.STATE_AUDIO_DISCONNECTED -> "disconnected"
        else -> state.toString()
    }

    companion object {
        private const val EXTRA_TRIGGER = "trigger"
        private const val EXTRA_PROGRAM = "program"

        /** The result reported through [endHandler] for a session Android did not let start. */
        const val RESULT_REFUSED = "refused"

        /** Called when a trigger arrives while a session is already running or still closing. */
        @Volatile
        var busyHandler: ((trigger: String) -> Unit)? = null

        /**
         * Called once for every session that is over, when the link is closed and the service
         * is free again: with the result of the session, or with [RESULT_REFUSED] if Android
         * did not let it start. Not called for a request that was turned down as busy. A new
         * session may be asked for from inside the call.
         */
        @Volatile
        var endHandler: ((trigger: String, result: String) -> Unit)? = null

        /** True from the moment a session is accepted until it is completely over. */
        @Volatile
        var busy: Boolean = false
            private set
        private const val CHANNEL_ID = "session"
        private const val NOTIFICATION_ID = 1
        /** No session may last longer than this, whatever its program does. */
        private const val MAX_SESSION_MS = 5 * 60_000L
        private const val SESSION_WAKE_MS = MAX_SESSION_MS + 30_000L
        private const val PREFERENCES = "session"
        private const val KEY_OPEN = "open"
        private const val HANDOVER_WAKE_MS = 10_000L
        private const val AFTER_TAP_MS = 500L
        private const val LINK_DOWN_TIMEOUT_MS = 1_500L
        private const val LINK_DOWN_POLL_MS = 20L
        private const val ACTION_STOP_VOICE_COMMAND = "android.intent.action.STOP_VOICE_COMMAND"
        private const val BluetoothProfile_EXTRA_STATE = "android.bluetooth.profile.extra.STATE"

        /**
         * Keeps the phone awake for the hand-over from a trigger to the service. Android's own
         * wake lock for the hearing-aid tap ends as soon as the app answers.
         */
        fun holdAwake(context: Context) {
            context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:handover")
                .acquire(HANDOVER_WAKE_MS)
        }

        /** Remembers across a process death that a voice link may be open. */
        private fun markOpen(context: Context, open: Boolean) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY_OPEN, open).commit()
        }

        /**
         * Call once at process start. If the previous process died in the middle of a session,
         * nobody closed the voice link: the Bluetooth stack does not notice that the app is
         * gone, the hearing aid stays in "call" mode and the next tap is refused. Close it now.
         */
        @SuppressLint("MissingPermission")
        suspend fun recoverAfterCrash(app: LaraApp) {
            val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            if (!preferences.getBoolean(KEY_OPEN, false)) return
            val headset = app.headset.await(5_000)
            val stopped = headset?.connectedDevices?.map { device ->
                runCatching { headset.stopVoiceRecognition(device) }.getOrDefault(false)
            }
            app.getSystemService(AudioManager::class.java).clearCommunicationDevice()
            preferences.edit().putBoolean(KEY_OPEN, false).apply()
            app.journal.log("session_recovered_after_crash", mapOf("voiceRecognitionStopped" to stopped))
        }

        /**
         * Waits until the hands-free audio of the session that was just closed is really
         * down, and journals how long that took (`session_link_down`).
         *
         * Why (read from the Android 17 source of the Bluetooth stack, not measured): while
         * that audio is still connected or on its way down, `startVoiceRecognition` is refused
         * ("audio is still active, please wait"). [AudioLink] would then take its fallback,
         * the virtual call, which is exactly what rule 4 keeps a session out of. Closing the
         * link takes a moment after `stopVoiceRecognition` has returned, and the router asks
         * for the next session the instant this service is free (a press made during
         * "Beende"). So the service stays busy until the link is gone. After a second tap the
         * link is on its way down already and the wait is short.
         *
         * The wait is limited: if the audio stays up for another reason (a phone call that
         * interrupted the session), the service is freed all the same.
         */
        @SuppressLint("MissingPermission")
        private suspend fun awaitLinkDown(app: LaraApp, link: AudioLink) {
            val headset = app.headset.current() ?: return
            // Android reports "connected" also while the link is being closed.
            fun up(): Boolean =
                runCatching { app.headset.hearingAid(headset)?.let(headset::isAudioConnected) }.getOrNull() == true
            val since = SystemClock.elapsedRealtime()
            var down = !up()
            while (!down && SystemClock.elapsedRealtime() - since < LINK_DOWN_TIMEOUT_MS) {
                delay(LINK_DOWN_POLL_MS)
                down = !up()
            }
            link.step("link_down", mapOf("down" to down, "waitedMs" to SystemClock.elapsedRealtime() - since))
        }

        /**
         * "Beende" after a second tap. The tap makes the hearing aid close the voice link
         * itself, so nothing can be said over it any more; it is said over the media channel
         * instead, a moment later, when Android routes media to the hearing aid again.
         *
         * The service is free by then, on purpose: a tap in this half second (the wearer
         * starts anew) has to be answered, not turned down as busy, or the hearing aid gets
         * no answer and Android rejects its taps for five seconds. If a new session has begun
         * in the meantime the phrase is left out: it would talk into the opening of that
         * session.
         */
        private suspend fun farewellOverMedia(app: LaraApp) {
            delay(AFTER_TAP_MS)
            if (busy) {
                app.journal.log("announce_skipped", mapOf("reason" to "session_end", "why" to "next_session_started"))
                return
            }
            app.announcer.announce(SessionPhrases.END, "session_end")
        }

        /**
         * Asks for a session. Only the input router calls this: it is the one place that
         * knows what else is going on.
         *
         * @param trigger where the request came from, the origin word of the input
         * @param device the hearing aid named by the trigger, if any
         * @param program a name from [SessionPrograms]
         */
        fun start(context: Context, trigger: String, device: BluetoothDevice?, program: String) {
            val intent = Intent(context, SessionService::class.java)
                .putExtra(EXTRA_TRIGGER, trigger)
                .putExtra(EXTRA_PROGRAM, program)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                LaraApp.instance.journal.log(
                    JournalNames.SESSION_REFUSED,
                    mapOf("trigger" to trigger, "where" to "startForegroundService", "exception" to e.toString()),
                )
                refused(context, trigger)
            }
        }

        /**
         * Android did not let a session start. At this point nothing can be played into the
         * hearing aid, so the wearer is told like for every failure before the link is up: by
         * vibration. Not while a session runs: that one is none the worse for a request that
         * was refused next to it, and it has not ended.
         */
        private fun refused(context: Context, trigger: String) {
            if (busy) return
            AudioLink.vibrateFailure(context)
            endHandler?.invoke(trigger, RESULT_REFUSED)
        }
    }
}
