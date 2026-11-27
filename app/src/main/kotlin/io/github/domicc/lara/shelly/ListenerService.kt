package io.github.domicc.lara.shelly

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.R
import io.github.domicc.lara.core.scan.ScanConfig
import io.github.domicc.lara.core.scan.ScanStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Input path B. A foreground service (type connectedDevice) that runs all the time, owns the
 * background scan for the Shelly remote and journals what else happens to the phone while it
 * waits: display on and off, unlocking, Bluetooth on and off.
 *
 * Why a permanent foreground service (read from the Android 17 source): Android limits the
 * scans of an app it rates as background, with the display off to half a second of listening
 * in ten whatever mode was asked for. It takes that rating when a scan starts, and of all the
 * ways to be "foreground" an own foreground service is the only one whose loss or return it
 * reliably notices afterwards. So the order here is fixed: first startForeground, then the
 * scan. The service does not lift the hourly scan allowance; only an app on the screen is
 * exempt from that.
 *
 * It is started from several places, because none of them covers every case. Each one names
 * itself as the reason of the start:
 * - `assistant_ready`: the assistant service, when the system binds it (after unlocking, after
 *   a crash, after an update);
 * - `boot` and `app_update`: the boot and update receiver;
 * - `main_activity`: whenever the app comes onto the screen, the only start that works before
 *   the app is the assistant, and the one that picks up a permission granted in Android's
 *   settings;
 * - `permissions_answered`: after the app's own permission dialog;
 * - `rescue_scan`: the rescue scan delivered into a process without the service;
 * - `session`: a session that listens to the remote opens and finds the service gone;
 * - `alarm`: the periodic refresh, if the owner has switched it on.
 * Android itself brings the service back as `sticky_restart`. Starting a foreground service
 * from the background is a privilege, which the bound assistant service or the battery
 * exemption provides. Every start is journaled with its reason as `listener_start`, every
 * refusal as `listener_refused`.
 *
 * No wake lock is held. With a filter the Bluetooth controller is meant to do the matching
 * while the phone sleeps and to wake it for a match; the `delayMs` of every `shelly_packet`
 * line shows whether it does.
 */
class ListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var scan: RemoteScan

    /** True once startForeground has succeeded; nothing else is set up before that. */
    private var foreground = false

    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val journal = LaraApp.instance.journal
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> journal.log("screen_on")
                Intent.ACTION_SCREEN_OFF -> journal.log("screen_off")
                Intent.ACTION_USER_PRESENT -> journal.log("user_present")
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        // Switching Bluetooth off empties the list of scans inside the
                        // Bluetooth stack, the rescue scan included.
                        BluetoothAdapter.STATE_OFF -> {
                            journal.log("bluetooth_off")
                            scan.sync("bluetooth_off", force = false)
                        }
                        BluetoothAdapter.STATE_ON -> {
                            journal.log("bluetooth_on")
                            scan.sync("bluetooth_on", force = true)
                        }
                    }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val journal = LaraApp.instance.journal
        // Without an intent this is Android bringing the sticky service back after it killed
        // the process.
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "sticky_restart"
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException: no background privilege right now.
            // SecurityException: the Bluetooth permission is gone.
            journal.log("listener_refused", mapOf("reason" to reason, "where" to "startForeground", "exception" to e.toString()))
            if (foreground) return START_STICKY
            stopSelf()
            return START_NOT_STICKY
        }
        val first = !foreground
        journal.log("listener_start", mapOf("reason" to reason, "first" to first))
        if (first) {
            foreground = true
            scan = RemoteScan(this, LaraApp.instance) { status = it }
            instance = this
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            // Exported on purpose: a receiver that is not exported hears only the system's own
            // process, and the Bluetooth stack is a process of its own. All four actions are
            // protected, so no other app can send them.
            registerReceiver(events, filter, Context.RECEIVER_EXPORTED)
            followSettings()
        }
        // Only here, after startForeground has returned: see the class comment.
        scan.sync(reason, force = first)
        ListenerStarter.scheduleRefresh(this)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (foreground) {
            unregisterReceiver(events)
            scan.stop("service_destroyed")
            LaraApp.instance.journal.log("listener_stopped")
        }
        if (instance === this) {
            instance = null
            status = ScanStatus()
        }
        super.onDestroy()
    }

    /** Applies a change of remote, settings or session to the running scans at once. */
    private fun followSettings() {
        val store = LaraApp.instance.shellyStore
        // drop(1): the value at the time of the start is what onStartCommand applies itself
        scope.launch {
            store.remote.drop(1).collect { scan.sync(if (it == null) "forgotten" else "enrolled", force = false) }
        }
        scope.launch {
            store.scan.drop(1).collect {
                scan.sync("settings", force = false)
                ListenerStarter.scheduleRefresh(this@ListenerService)
            }
        }
        scope.launch {
            sessionScan.drop(1).collect { scan.sync(if (it) "session_open" else "session_closed", force = false) }
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_listener), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_listener_title))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val CHANNEL_ID = "listener"
        private const val NOTIFICATION_ID = 2

        /** How long the scan stays in its session mode after a session has closed. */
        private const val LINGER_MS = ScanConfig.SESSION_LINGER_MS

        private val main = Handler(Looper.getMainLooper())

        /** A session that listens to the remote is open. Changed on the main thread only. */
        private val session = MutableStateFlow(false)

        /** The scan is wanted in its session mode. Changed on the main thread only. */
        private val sessionScan = MutableStateFlow(false)

        private val backToIdle = Runnable { if (!session.value) sessionScan.value = false }

        @Volatile
        private var instance: ListenerService? = null

        /** True while the service runs in the foreground. */
        val running: Boolean get() = instance != null

        /** What is scanning for the remote right now. */
        @Volatile
        var status: ScanStatus = ScanStatus()
            private set

        /**
         * True while a session that listens to the remote is open (a remote session or a count
         * session), as told through [setSessionOpen]. An echo test on its own is no such
         * session: it takes no presses, so the scan stays as it is.
         */
        val sessionOpen: Boolean get() = session.value

        /**
         * True while the scan is wanted in its session mode: during a session that listens to
         * the remote, and for [LINGER_MS] after it. See [setSessionOpen].
         */
        val scanForSession: Boolean get() = sessionScan.value

        /**
         * The session service tells the listener that a session which listens to the remote
         * has opened or closed. This switch is the only scan a session has. Safe from any
         * thread, and also while the service does not run: the next start picks the value up.
         *
         * The scan goes into its session mode at once, and back to its idle mode only
         * [LINGER_MS] after the session has closed. Why not at once:
         * - A change of mode is a scan start, and Android allows an app only a few of them per
         *   half minute ([BleScans.starts]). With two per session the third session of a quick
         *   series would have to wait for its change, and with the display off the idle mode
         *   hears half a second in ten. Sessions that follow each other now cost no start.
         * - A press made while "Beende" is said opens the next session, so it has to be heard.
         *
         * A few seconds more in session mode cost little of the hour's scan allowance; a scan
         * left there for good would spend it within minutes. So the way back runs under a
         * timed wake lock: a handler's clock stands still while the phone sleeps. Every
         * `shelly_packet` line says in `mode` what the scan was, and in `session` whether a
         * session was really open.
         */
        fun setSessionOpen(open: Boolean) = onMain {
            if (session.value == open) return@onMain
            session.value = open
            main.removeCallbacks(backToIdle)
            if (open) {
                sessionScan.value = true
            } else {
                BleScans.holdAwake(LaraApp.instance, LINGER_MS)
                main.postDelayed(backToIdle, LINGER_MS)
            }
        }

        /**
         * Why a session would not hear the enrolled remote, and will not start to: there is no
         * scan for it, for a reason that does not go away by waiting. Null if all is well, and
         * also while no remote is enrolled: then only simulated presses can arrive, and they
         * need no scan. A start that failed or has to wait is not such a reason: it is tried
         * again within seconds, and the journal says what happened.
         *
         * @return a word of [ScanStatus] for the journal
         */
        fun deafBecause(remoteEnrolled: Boolean): String? {
            if (!remoteEnrolled) return null
            val now = status
            if (now.mode != null) return null
            return now.off?.takeIf { it in LASTING }
        }

        private val LASTING = setOf(ScanStatus.SERVICE_NOT_RUNNING, ScanStatus.NO_SCAN_PERMISSION, ScanStatus.BLUETOOTH_OFF)

        /**
         * Makes sure the service runs. Safe to call from anywhere and as often as wanted.
         *
         * @param reason who asks, for the journal: `assistant_ready`, `boot`, `main_activity` ...
         */
        fun start(context: Context, reason: String) {
            val journal = LaraApp.instance.journal
            if (running) {
                // No second trip through Android: from the background that can be refused
                // although the service is up. Just make sure the scans are as they should be.
                onMain {
                    journal.log("listener_start", mapOf("reason" to reason, "first" to false))
                    instance?.scan?.sync(reason, force = false)
                }
                return
            }
            if (!BleScans.hasPermission(context)) {
                // Android accepts a connectedDevice service only from an app that holds a
                // Bluetooth permission, and without this one there would be nothing to do.
                journal.log("listener_refused", mapOf("reason" to reason, "where" to "permission", "missing" to "BLUETOOTH_SCAN"))
                return
            }
            try {
                context.startForegroundService(Intent(context, ListenerService::class.java).putExtra(EXTRA_REASON, reason))
            } catch (e: Exception) {
                journal.log(
                    "listener_refused",
                    mapOf("reason" to reason, "where" to "startForegroundService", "exception" to e.toString()),
                )
            }
        }

        /**
         * Registers the scans anew, whether or not anything has changed; starts the service
         * first if it does not run.
         */
        fun refresh(context: Context, reason: String) {
            if (!running) return start(context, reason)
            onMain { instance?.scan?.sync(reason, force = true) }
        }

        /**
         * Runs [block] on the main thread, at once if already there: the callers that matter
         * are broadcast receivers, and the phone may fall asleep again as soon as they return.
         */
        private fun onMain(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
        }
    }
}
