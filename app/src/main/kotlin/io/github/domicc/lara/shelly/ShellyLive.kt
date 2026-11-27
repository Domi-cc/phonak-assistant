package io.github.domicc.lara.shelly

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.SystemClock
import io.github.domicc.lara.core.bthome.BthomeParser
import io.github.domicc.lara.core.bthome.ButtonEvent
import io.github.domicc.lara.core.scan.ScanMode
import io.github.domicc.lara.core.shelly.ShellyBurst
import io.github.domicc.lara.core.shelly.ShellyTotals
import io.github.domicc.lara.core.shelly.ShellyTracker
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scanning while the app is on the screen: to find and pick the remote, and to watch live what
 * it sends. This is the best case for reception (fastest scan mode, app in the foreground, so
 * no background throttling), which makes it the reference that the later measurements with a
 * dark phone are compared against.
 *
 * Everything received in the live test is journaled with origin "screen_scan".
 */
class ShellyLive(
    private val context: Context,
    private val journal: Journal,
    private val store: ShellyStore,
) {
    /** A BTHome sender seen during the search. */
    data class Candidate(
        val address: String,
        val rssi: Int,
        val packets: Int,
        val buttonSlots: Int,
        val lastPress: String,
        val batteryPercent: Int?,
    )

    enum class Mode { SEARCH, LIVE }

    private val tracker = ShellyTracker()
    private var callback: ScanCallback? = null

    private val modeState = MutableStateFlow<Mode?>(null)
    private val candidateState = MutableStateFlow<List<Candidate>>(emptyList())
    private val lineState = MutableStateFlow<List<String>>(emptyList())
    private val totalState = MutableStateFlow(ShellyTotals())

    /** Null while no scan of this class is running. */
    val mode: StateFlow<Mode?> = modeState.asStateFlow()
    val candidates: StateFlow<List<Candidate>> = candidateState.asStateFlow()

    /** The newest lines of the live test, newest first. */
    val lines: StateFlow<List<String>> = lineState.asStateFlow()
    val totals: StateFlow<ShellyTotals> = totalState.asStateFlow()

    /** Looks for every BTHome sender in range. */
    fun startSearch(): Boolean {
        candidateState.value = emptyList()
        return start(Mode.SEARCH, BleScans.anyBthomeFilter())
    }

    /** Listens to the enrolled remote only. */
    fun startLive(): Boolean {
        val remote = store.remote.value ?: return false
        tracker.resetTotals()
        totalState.value = tracker.totals()
        lineState.value = emptyList()
        return start(Mode.LIVE, BleScans.remoteFilter(remote.address))
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val running = callback ?: return
        callback = null
        runCatching { BleScans.scanner(context)?.stopScan(running) }
        closeBursts(force = true)
        journal.log("shelly_scan_stopped", mapOf("mode" to modeState.value?.name?.lowercase(), "origin" to "screen"))
        modeState.value = null
    }

    /** Call about once a second while the live test runs: closes bursts that have ended. */
    fun tick() {
        if (modeState.value == Mode.LIVE) closeBursts(force = false)
    }

    @SuppressLint("MissingPermission")
    private fun start(mode: Mode, filter: ScanFilter): Boolean {
        stop()
        val word = mode.name.lowercase()
        if (!BleScans.hasPermission(context)) {
            journal.log("shelly_scan_refused", mapOf("mode" to word, "why" to "no_permission"))
            return false
        }
        val scanner = BleScans.scanner(context) ?: run {
            journal.log("shelly_scan_refused", mapOf("mode" to word, "why" to "bluetooth_off"))
            return false
        }
        val passive = store.scan.value.passive
        val newCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (callback === this) onResult(mode, result)
            }

            override fun onScanFailed(errorCode: Int) {
                journal.log("shelly_scan_failed", mapOf("mode" to word, "error" to BleScans.failureName(errorCode)))
                if (callback === this) {
                    callback = null
                    modeState.value = null
                }
            }
        }
        callback = newCallback
        modeState.value = mode
        scanner.startScan(listOf(filter), BleScans.settings(ScanMode.LOW_LATENCY, passive), newCallback)
        journal.log(
            "shelly_scan_started",
            mapOf(
                "mode" to word,
                "scanMode" to ScanMode.LOW_LATENCY.word,
                "scanType" to BleScans.scanTypeWord(passive),
                "importance" to BleScans.importance(),
                "origin" to "screen",
            ),
        )
        return true
    }

    private fun onResult(mode: Mode, result: ScanResult) {
        val address = result.device.address
        val data = result.scanRecord?.getServiceData(BleScans.BTHOME)
        val packet = data?.let(BthomeParser::parse)

        if (mode == Mode.SEARCH) {
            val known = candidateState.value.firstOrNull { it.address == address }
            val press = packet?.buttons
                ?.mapIndexedNotNull { index, event -> if (event == ButtonEvent.NONE) null else "Taste ${index + 1}" }
                ?.joinToString(" + ")
                .orEmpty()
            val updated = Candidate(
                address = address,
                rssi = result.rssi,
                packets = (known?.packets ?: 0) + 1,
                buttonSlots = packet?.buttons?.size ?: known?.buttonSlots ?: 0,
                lastPress = press.ifEmpty { known?.lastPress.orEmpty() },
                batteryPercent = packet?.batteryPercent ?: known?.batteryPercent,
            )
            candidateState.value = (candidateState.value.filter { it.address != address } + updated)
                .sortedByDescending { it.rssi }
            return
        }

        val problem = BleScans.problemOf(data, packet)
        if (packet == null || problem != null) {
            journal.log("shelly_packet_unreadable", mapOf("why" to problem, "hex" to data?.toHex(), "origin" to "screen_scan"))
            addLine("unlesbares Paket ($problem)")
            return
        }
        val update = tracker.onPacket(address, packet, SystemClock.elapsedRealtime())
        update.finished.forEach(::reportBurst)
        update.event?.let { event ->
            val presses = event.presses.map { "${it.button}:${it.gesture.name.lowercase()}" }
            journal.log(
                "shelly_press",
                mapOf(
                    "packetId" to event.packetId,
                    "presses" to presses,
                    "idStep" to event.idStep.name.lowercase(),
                    "missedBefore" to event.missedBefore,
                    "rssi" to result.rssi,
                    "battery" to packet.batteryPercent,
                    "hex" to data.toHex(),
                    "origin" to "screen_scan",
                ),
            )
            val what = if (event.presses.isEmpty()) "Bake (kein Tastendruck)"
            else event.presses.joinToString(" + ") { "Taste ${it.button} ${gestureWord(it.gesture.name)}" }
            val missed = if (event.missedBefore > 0) "   ⚠ davor ${event.missedBefore} verpasst" else ""
            addLine("#${event.packetId}  $what   ${result.rssi} dBm$missed")
        }
        totalState.value = tracker.totals()
    }

    private fun closeBursts(force: Boolean) {
        val now = SystemClock.elapsedRealtime() + if (force) FAR_FUTURE_MS else 0
        tracker.takeFinished(now).forEach(::reportBurst)
    }

    private fun reportBurst(burst: ShellyBurst) {
        journal.log(
            "shelly_burst",
            mapOf(
                "packetId" to burst.event.packetId,
                "packets" to burst.packets,
                "spanMs" to burst.spanMs,
                "origin" to "screen_scan",
            ),
        )
        addLine("    Sendestoß #${burst.event.packetId}: ${burst.packets} Pakete in ${burst.spanMs} ms")
    }

    private fun addLine(line: String) {
        lineState.value = (listOf(line) + lineState.value).take(KEEP_LINES)
    }

    private fun gestureWord(name: String): String = when (name) {
        "PRESS" -> "kurz"
        "DOUBLE" -> "doppelt"
        "TRIPLE" -> "dreifach"
        "LONG" -> "lang"
        "LONG_DOUBLE" -> "lang doppelt"
        "LONG_TRIPLE" -> "lang dreifach"
        "HOLD" -> "gehalten"
        "HOLD_RELEASE" -> "losgelassen"
        else -> "unbekannt"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEEP_LINES = 40
        const val FAR_FUTURE_MS = 60_000L
    }
}
