package io.github.domicc.lara.shelly

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.SystemClock
import io.github.domicc.lara.core.bthome.BthomeParser
import io.github.domicc.lara.core.scan.ScanMode
import io.github.domicc.lara.core.shelly.ShellyEvent
import io.github.domicc.lara.core.shelly.ShellyTracker
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.channels.Channel

/**
 * Listens to the enrolled remote for the length of one audio session.
 *
 * Started from the session's foreground service, so Android rates the app as "foreground
 * service" when the scan starts and does not put the scan into its slow screen-off mode. Under
 * Android 17's scan allowance a scan like this may run for minutes per hour, which is enough for
 * sessions but not for listening all day.
 *
 * Everything received is journaled with origin "session_scan"; new button events also go to
 * [events].
 */
class SessionScan(
    private val context: Context,
    private val journal: Journal,
    private val address: String,
    private val mode: ScanMode,
    private val passive: Boolean,
) {
    val events = Channel<ShellyEvent>(Channel.UNLIMITED)

    private val tracker = ShellyTracker()
    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!BleScans.hasPermission(context)) return refused("no_permission")
        val scanner = BleScans.scanner(context) ?: return refused("bluetooth_off")
        val newCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)

            override fun onScanFailed(errorCode: Int) {
                journal.log("shelly_scan_failed", mapOf("mode" to "session", "error" to BleScans.failureName(errorCode)))
            }
        }
        callback = newCallback
        scanner.startScan(listOf(BleScans.remoteFilter(address)), BleScans.settings(mode, passive), newCallback)
        journal.log(
            "shelly_scan_started",
            mapOf(
                "mode" to "session",
                "scanMode" to mode.word,
                "scanType" to BleScans.scanTypeWord(passive),
                "importance" to BleScans.importance(),
                "origin" to "session",
            ),
        )
        return true
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val running = callback ?: return
        callback = null
        runCatching { BleScans.scanner(context)?.stopScan(running) }
        closeBursts(SystemClock.elapsedRealtime() + 60_000)
        val totals = tracker.totals()
        journal.log(
            "shelly_scan_stopped",
            mapOf("mode" to "session", "received" to totals.received, "missed" to totals.missed, "origin" to "session"),
        )
        events.close()
    }

    /** Call now and then: journals bursts that have ended. */
    fun tick() = closeBursts(SystemClock.elapsedRealtime())

    private fun refused(why: String): Boolean {
        journal.log("shelly_scan_refused", mapOf("mode" to "session", "why" to why))
        return false
    }

    private fun onResult(result: ScanResult) {
        val data = result.scanRecord?.getServiceData(BleScans.BTHOME)
        val packet = data?.let(BthomeParser::parse)
        val problem = BleScans.problemOf(data, packet)
        if (packet == null || problem != null) {
            journal.log("shelly_packet_unreadable", mapOf("why" to problem, "origin" to "session_scan"))
            return
        }
        val update = synchronized(tracker) {
            tracker.onPacket(result.device.address, packet, SystemClock.elapsedRealtime())
        }
        update.finished.forEach { burst ->
            journal.log(
                "shelly_burst",
                mapOf("packetId" to burst.event.packetId, "packets" to burst.packets, "spanMs" to burst.spanMs, "origin" to "session_scan"),
            )
        }
        val event = update.event ?: return
        journal.log(
            "shelly_press",
            mapOf(
                "packetId" to event.packetId,
                "presses" to event.presses.map { "${it.button}:${it.gesture.name.lowercase()}" },
                "idStep" to event.idStep.name.lowercase(),
                "missedBefore" to event.missedBefore,
                "rssi" to result.rssi,
                // how long the packet waited between the radio and this app
                "deliveryMs" to (SystemClock.elapsedRealtimeNanos() - result.timestampNanos) / 1_000_000,
                "origin" to "session_scan",
            ),
        )
        events.trySend(event)
    }

    private fun closeBursts(now: Long) {
        val finished = synchronized(tracker) { tracker.takeFinished(now) }
        finished.forEach { burst ->
            journal.log(
                "shelly_burst",
                mapOf("packetId" to burst.event.packetId, "packets" to burst.packets, "spanMs" to burst.spanMs, "origin" to "session_scan"),
            )
        }
    }
}
