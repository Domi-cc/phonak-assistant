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
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.R
import io.github.domicc.lara.speech.SessionPhrases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service (type microphone) that runs one audio session with the hearing aid and
 * stops itself afterwards. Every trigger (hearing-aid tap, button on screen, assist gesture,
 * later the Shelly and media keys) goes through [start].
 *
 * Starting a microphone foreground service from the background is only allowed while the
 * system has the app's assistant service bound, or for a few seconds after the tap activity
 * was started. A refusal is journaled as `session_refused` instead of crashing.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var session: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = LaraApp.instance
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: "unknown"
        val device = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException or SecurityException: no background right
            app.journal.log("session_refused", mapOf("trigger" to trigger, "where" to "startForeground", "exception" to e.toString()))
            if (session?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        if (session?.isActive == true) {
            app.journal.log("session_busy", mapOf("trigger" to trigger))
            busyHandler?.invoke(trigger)
            return START_NOT_STICKY
        }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:session")
            .apply { acquire(SESSION_WAKE_MS) }
        linkWasUp = false
        endedByTap = false
        val program = SessionPrograms.create(intent?.getStringExtra(EXTRA_PROGRAM))
        session = scope.launch {
            val link = AudioLink(this@SessionService, app.journal, app.headset, trigger, device)
            link.onInterrupted = { why ->
                app.journal.log("session_interrupted", mapOf("why" to why))
                session?.cancel()
            }
            var result = "error"
            try {
                markOpen(this@SessionService, true)
                link.open()
                result = withTimeoutOrNull(MAX_SESSION_MS) { program.run(link) } ?: "max_duration"
            } catch (e: AudioLink.Failed) {
                result = e.reason
            } catch (e: CancellationException) {
                result = "cancelled"
                throw e
            } catch (e: Exception) {
                link.step("error", mapOf("exception" to e.toString()))
            } finally {
                link.close(result, app.prompts.get(SessionPhrases.END))
                markOpen(this@SessionService, false)
                // A second tap makes the hearing aid close the voice link itself, so nothing can
                // be said over it any more. Say it over the media channel instead.
                if (result == "cancelled" && endedByTap) {
                    delay(AFTER_TAP_MS)
                    app.announcer.announce(SessionPhrases.END, "session_end")
                }
                wakeLock?.let { if (it.isHeld) it.release() }
                wakeLock = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
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

        /** Called when a trigger arrives while a session is already running. */
        @Volatile
        var busyHandler: ((trigger: String) -> Unit)? = null
        private const val CHANNEL_ID = "session"
        private const val NOTIFICATION_ID = 1
        /** No session may last longer than this, whatever its program does. */
        private const val MAX_SESSION_MS = 5 * 60_000L
        private const val SESSION_WAKE_MS = MAX_SESSION_MS + 30_000L
        private const val PREFERENCES = "session"
        private const val KEY_OPEN = "open"
        private const val HANDOVER_WAKE_MS = 10_000L
        private const val AFTER_TAP_MS = 500L
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
         * Starts a session.
         * @param device the hearing aid named by the trigger, if any
         * @param program a name from [SessionPrograms]
         */
        fun start(context: Context, trigger: String, device: BluetoothDevice?, program: String = SessionPrograms.ECHO) {
            val intent = Intent(context, SessionService::class.java)
                .putExtra(EXTRA_TRIGGER, trigger)
                .putExtra(EXTRA_PROGRAM, program)
                .putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                LaraApp.instance.journal.log(
                    "session_refused",
                    mapOf("trigger" to trigger, "where" to "startForegroundService", "exception" to e.toString()),
                )
            }
        }
    }
}
