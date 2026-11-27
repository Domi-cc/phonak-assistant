package io.github.domicc.lara.shelly

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Bundle
import android.os.SystemClock
import io.github.domicc.lara.core.bthome.BthomeParser
import io.github.domicc.lara.core.bthome.ButtonEvent
import io.github.domicc.lara.core.scan.ScanMode
import io.github.domicc.lara.core.shelly.ShellyEvent
import io.github.domicc.lara.core.shelly.ShellyTracker
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A BTHome sender the scan on the screen has heard, for the enrolment list: the owner presses
 * a button of the remote and picks the line on which that press shows up.
 *
 * @param rssi signal strength of the newest advertisement in dBm; closer to 0 is nearer
 * @param packets advertisements received since the scan started
 * @param lastButton the button of the newest press, counted from 1; null if none was seen yet
 * @param lastEvent how [lastButton] was pressed
 * @param lastEventMs when that press arrived, on the elapsed-realtime clock
 * @param encrypted the sender encrypts; this app cannot read its buttons
 * @param events presses heard since the scan started, each burst of repeats counted once
 * @param missed events the sender numbered that this scan did not hear. This scan is the
 * positive control, so a number above 0 here means the remote or the radio lost something,
 * not Android's throttling.
 */
data class SeenDevice(
    val address: String,
    val rssi: Int,
    val packets: Int,
    val lastButton: Int? = null,
    val lastEvent: ButtonEvent? = null,
    val lastEventMs: Long? = null,
    val encrypted: Boolean = false,
    val events: Int = 0,
    val missed: Int = 0,
)

/**
 * One advertisement as it arrived, for the raw-packet view.
 *
 * @param elapsedMs when the Bluetooth stack received it, on the elapsed-realtime clock
 * @param buttons one entry per button of the sender, [ButtonEvent.NONE] included
 * @param hex the BTHome service data as sent
 * @param problem why it is not a readable button packet, null if it is one
 */
data class RawPacket(
    val elapsedMs: Long,
    val address: String,
    val rssi: Int,
    val packetId: Int?,
    val buttons: List<ButtonEvent>,
    val hex: String,
    val problem: String?,
)

/**
 * The scan for the screen: every BTHome sender in range, in the fastest mode. It feeds the
 * enrolment list ([devices]) and the raw-packet view ([packets]).
 *
 * Why it exists next to the listener's scan:
 * - Before enrolment there is no address the listener could filter on.
 * - It is the positive control. For the app on the screen with the display on, Android applies
 *   neither its display-off modes nor the hourly allowance, so what arrives here is what the
 *   remote really sends. A press that shows up here but not in the background was lost to
 *   Android's throttling, not to the remote or the parser.
 *
 * While it runs, the radio listens all the time for every scan on the phone, the listener's
 * included; `shelly_packet` and `shelly_press` lines journaled in that time are marked with
 * `fg`. It therefore stops itself as soon as the app is no longer on the screen (which
 * includes the display going dark): running on in the background it would use up the hour's
 * allowance in minutes and spoil the measurements that follow. That is a safety net: the
 * screen should still call [stop] when its view goes away, and has to call [start] again when
 * it comes back.
 *
 * What it hears is journaled as `fg_event`, one line per press of any sender and per event of
 * the enrolled remote, so that a control run can be compared with a background run afterwards
 * and not only from memory of the screen. Sensors that merely send their readings are left out.
 *
 * Nothing it hears is acted on: no press from here becomes an input. Main thread only.
 */
@SuppressLint("MissingPermission") // start() checks BleScans.hasPermission
class ForegroundScan(
    private val application: Application,
    private val journal: Journal,
    private val store: ShellyStore,
) {
    enum class State {
        STOPPED,
        RUNNING,

        /** Not started: the permission for nearby devices is missing. */
        NO_PERMISSION,

        /** Not started: Bluetooth is switched off. */
        BLUETOOTH_OFF,

        /**
         * Not started: the app has started several scans within the last half minute, and
         * Android would drop one more without a word. A few seconds later it works again.
         */
        TOO_MANY_STARTS,

        /** Android refused or ended the scan; the journal has the code. */
        FAILED,
    }

    private val stateFlow = MutableStateFlow(State.STOPPED)
    private val deviceFlow = MutableStateFlow<List<SeenDevice>>(emptyList())
    private val packetFlow = MutableStateFlow<List<RawPacket>>(emptyList())

    val state: StateFlow<State> = stateFlow.asStateFlow()

    /** The senders heard since [start], in the order of their first advertisement. */
    val devices: StateFlow<List<SeenDevice>> = deviceFlow.asStateFlow()

    /** The newest advertisements, oldest first, of all senders. */
    val packets: StateFlow<List<RawPacket>> = packetFlow.asStateFlow()

    private val seen = LinkedHashMap<String, SeenDevice>()

    /** Its own tracker, a new one per scan: what is heard here must not touch the app's counts. */
    private var tracker = ShellyTracker()
    /** The callback of the running scan; null while none runs. */
    private var registered: ScanCallback? = null
    private var startedAtMs = 0L
    private var received = 0

    private val leavesScreen = object : Application.ActivityLifecycleCallbacks {
        /**
         * Stops when an activity of the app leaves the screen: Home, another app, the display
         * going dark. Decided from the lifecycle itself and not by asking Android how it rates
         * the process: Android lowers that rating a moment after this call (read from the
         * Android 17 source), so the question could still be answered with "foreground", and
         * nothing would ask again. An activity that is only rebuilt (rotation) disposes its
         * view, which stops the scan as well.
         *
         * The app's second activity, for the hearing-aid tap, has no window and finishes while
         * it is created, so it never gets here.
         */
        override fun onActivityStopped(activity: Activity) {
            if (!activity.isChangingConfigurations) stop("left_screen")
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /** Starts the scan with empty lists. Call it from an activity that is on the screen. */
    fun start() {
        if (registered != null) return
        if (!BleScans.hasPermission(application)) return skip(State.NO_PERMISSION)
        val scanner = BleScans.scanner(application) ?: return skip(State.BLUETOOTH_OFF)
        val now = SystemClock.elapsedRealtime()
        if (BleScans.starts.waitMs(now) > 0) return skip(State.TOO_MANY_STARTS)
        seen.clear()
        tracker = ShellyTracker()
        received = 0
        deviceFlow.value = emptyList()
        packetFlow.value = emptyList()

        val passive = store.scan.value.passive
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)
            override fun onBatchScanResults(results: List<ScanResult>) = results.forEach(::onResult)
            override fun onScanFailed(errorCode: Int) {
                journal.log(
                    "scan_failed",
                    mapOf("scan" to "foreground", "code" to errorCode, "name" to BleScans.failureName(errorCode)),
                )
                // a late failure of an earlier scan must not end the one that runs now
                if (registered === this) finish(State.FAILED, "scan_failed", stopScan = false)
            }
        }
        try {
            scanner.startScan(listOf(BleScans.anyBthomeFilter()), BleScans.settings(ScanMode.LOW_LATENCY, passive), callback)
        } catch (e: Exception) {
            journal.log("scan_failed", mapOf("scan" to "foreground", "exception" to e.toString()))
            stateFlow.value = State.FAILED
            return
        }
        startedAtMs = now
        BleScans.starts.record(now)
        registered = callback
        running++
        application.registerActivityLifecycleCallbacks(leavesScreen)
        stateFlow.value = State.RUNNING
        journal.log(
            "scan_started",
            mapOf(
                "scan" to "foreground",
                "mode" to ScanMode.LOW_LATENCY.word,
                "scanType" to BleScans.scanTypeWord(passive),
                "filter" to "service_data",
                "importance" to BleScans.importance(),
            ),
        )
    }

    /** Stops the scan; the lists keep what was heard. Does nothing if it is not running. */
    fun stop(reason: String = "screen") {
        if (registered != null) finish(State.STOPPED, reason, stopScan = true)
    }

    private fun skip(why: State) {
        journal.log("scan_skipped", mapOf("scan" to "foreground", "why" to why.name.lowercase()))
        stateFlow.value = why
    }

    private fun finish(state: State, reason: String, stopScan: Boolean) {
        val callback = registered ?: return
        registered = null
        application.unregisterActivityLifecycleCallbacks(leavesScreen)
        stateFlow.value = state
        val watched = watched()
        val about = mapOf(
            "scan" to "foreground",
            "reason" to reason,
            "packets" to received,
            "devices" to seen.size,
            "events" to watched.sumOf { it.events },
            "missed" to watched.sumOf { it.missed },
        )
        val startedAt = startedAtMs
        if (!stopScan) {
            // Android has ended the scan itself
            running--
            journal.log("scan_stopped", about + ("ms" to SystemClock.elapsedRealtime() - startedAt))
            return
        }
        // A scan that was started a moment ago is stopped a moment later (see BleScans); the
        // journal and the `fg` mark follow the real stop, not the tap on the button.
        BleScans.stopWhenRegistered(application, callback, startedAt) { failure ->
            running--
            val ended = about + ("ms" to SystemClock.elapsedRealtime() - startedAt)
            if (failure == null) {
                journal.log("scan_stopped", ended)
            } else {
                journal.log("scan_failed", ended + mapOf("where" to "stop", "exception" to failure.toString()))
            }
        }
    }

    /** The senders the numbers are about: the enrolled remote if there is one, else all. */
    private fun watched(): List<SeenDevice> {
        val enrolled = store.remote.value ?: return seen.values.toList()
        return seen.values.filter { it.address.equals(enrolled.address, ignoreCase = true) }
    }

    private fun journalEvent(event: ShellyEvent, rssi: Int, hex: String) {
        val enrolled = store.remote.value?.address.equals(event.address, ignoreCase = true)
        // a thermometer in range sends a new packet id every few seconds
        if (event.presses.isEmpty() && !enrolled) return
        journal.log(
            "fg_event",
            mapOf(
                "address" to event.address,
                "enrolled" to enrolled,
                "id" to event.packetId,
                "presses" to event.presses.joinToString(",") { "${it.button}:${it.gesture.name.lowercase()}" },
                "idStep" to event.idStep.name.lowercase(),
                "missedBefore" to event.missedBefore,
                "rssi" to rssi,
                "tsMs" to event.elapsedMs,
                "raw" to hex,
            ),
        )
    }

    private fun onResult(result: ScanResult) {
        if (registered == null) return // a result that was still on its way when the scan stopped
        val address = result.device.address
        val stampMs = result.timestampNanos / 1_000_000
        val data = result.scanRecord?.getServiceData(BleScans.BTHOME)
        val packet = data?.let(BthomeParser::parse)
        val buttons = packet?.buttons ?: emptyList()
        received++

        val before = seen[address]
        val pressed = buttons.indexOfFirst { it != ButtonEvent.NONE }
        val hex = data?.toHexString() ?: ""
        // the same id again after a pause is a held button heard in pieces, as in the intake
        val event = packet?.let { tracker.onPacket(address, it, stampMs).event }?.takeIf { !it.repeatsPrevious }
        if (event != null) journalEvent(event, result.rssi, hex)
        seen[address] = SeenDevice(
            address = address,
            rssi = result.rssi,
            packets = (before?.packets ?: 0) + 1,
            lastButton = if (pressed >= 0) pressed + 1 else before?.lastButton,
            lastEvent = if (pressed >= 0) buttons[pressed] else before?.lastEvent,
            lastEventMs = if (pressed >= 0) stampMs else before?.lastEventMs,
            encrypted = packet?.encrypted ?: (before?.encrypted ?: false),
            events = (before?.events ?: 0) + if (event != null && event.presses.isNotEmpty()) 1 else 0,
            missed = (before?.missed ?: 0) + (event?.missedBefore ?: 0),
        )
        deviceFlow.value = seen.values.toList()

        val raw = RawPacket(
            elapsedMs = stampMs,
            address = address,
            rssi = result.rssi,
            packetId = packet?.packetId,
            buttons = buttons,
            hex = hex,
            problem = BleScans.problemOf(data, packet),
        )
        packetFlow.value = (packetFlow.value + raw).takeLast(KEEP_PACKETS)
    }

    companion object {
        private const val KEEP_PACKETS = 200

        /** Scans of this kind that were started and not stopped yet; main thread only. */
        @Volatile
        private var running = 0

        /** True while a scan of this kind runs anywhere in the process. */
        val active: Boolean get() = running > 0
    }
}
