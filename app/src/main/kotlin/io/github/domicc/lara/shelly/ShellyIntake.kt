package io.github.domicc.lara.shelly

import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import io.github.domicc.lara.core.bthome.BthomePacket
import io.github.domicc.lara.core.bthome.BthomeParser
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputPath
import io.github.domicc.lara.core.journal.JournalNames
import io.github.domicc.lara.core.shelly.ShellyBurst
import io.github.domicc.lara.core.shelly.ShellyEvent
import io.github.domicc.lara.core.shelly.ShellyTracker
import io.github.domicc.lara.journal.Journal

/**
 * Which of the two background scans handed an advertisement to the app.
 *
 * @param path what an input made from such an advertisement says about its way into the app
 */
enum class ScanSource(val word: String, val path: InputPath) {
    /** The scan of the listener service. */
    CALLBACK("callback", InputPath.BLE_CALLBACK),

    /** The scan that delivers through a broadcast, also into a process that was just started. */
    RESCUE("rescue", InputPath.BLE_RESCUE),
}

/**
 * What happens to an advertisement of the enrolled remote once a scan has delivered it:
 * journal it, find out whether it begins a new press, journal that press and publish it as
 * input, and later journal how much of the remote's burst arrived.
 *
 * Why this is one object for the process and not part of the service: the rescue scan delivers
 * through a broadcast, possibly before the service runs again. Both scans have to feed the same
 * tracker, or the same press would be reported once per scan.
 *
 * What is published:
 * - A new press: once, with the first packet that arrives, because the wearer is waiting. It
 *   is journaled as `shelly_press` first, so that the journal shows the press before what it
 *   caused.
 * - The same packet id again after a pause ([ShellyEvent.repeatsPrevious]): journaled as
 *   `shelly_repeat` and not published. According to the vendor a held button is sent for up
 *   to a minute, and a throttled scan hears that in pieces several seconds apart; acting on
 *   every piece would repeat the action.
 * - A packet without a press (the remote's beacon): journaled as `shelly_beacon`.
 *
 * The packet id received last is kept in the preferences and given to the tracker of a new
 * process, so that what was lost while the process was gone is counted with the first event
 * that arrives again.
 *
 * Main thread only. Both scans deliver there, and the work is done before the delivery returns:
 * with the display off the phone may fall asleep again right after it.
 *
 * @param publish hands an input to the app's input bus
 */
class ShellyIntake(
    context: Context,
    private val journal: Journal,
    private val store: ShellyStore,
    private val publish: (Input) -> Unit,
) {
    /** The one tracker of the process. */
    private val tracker = ShellyTracker()

    /** When the last new press arrived, on the elapsed-realtime clock; null since process start. */
    @Volatile
    var lastPressElapsedMs: Long? = null
        private set

    private val handler = Handler(Looper.getMainLooper())

    /** Packets per scan for every burst that is still open, by packet id. */
    private val perSource = HashMap<Int?, IntArray>()

    private val power = context.getSystemService(PowerManager::class.java)

    /** The tracker has no clock of its own: this collects the bursts that have ended since. */
    private val collectBursts = Runnable {
        tracker.takeFinished(SystemClock.elapsedRealtime()).forEach(::journalBurst)
    }

    init {
        val remote = store.remote.value
        val lastId = store.lastPacketId
        if (remote != null && lastId != null) tracker.seed(remote.address, lastId)
    }

    /** Takes one scan result of [source]. Results of other senders are journaled and dropped. */
    fun onScanResult(result: ScanResult, source: ScanSource) {
        val arrivedNanos = SystemClock.elapsedRealtimeNanos()
        val address = result.device.address
        val remote = store.remote.value
        if (remote == null || !remote.address.equals(address, ignoreCase = true)) {
            // only a scan registered for an earlier remote can deliver this
            journal.log("shelly_packet_ignored", mapOf("address" to address, "scan" to source.word))
            return
        }
        // The time the Bluetooth stack took the advertisement from the controller. Results can
        // arrive late or in a bunch, so the burst statistics are built on this, not on "now".
        val stampMs = result.timestampNanos / 1_000_000
        val delayMs = (arrivedNanos - result.timestampNanos) / 1_000_000
        val data = result.scanRecord?.getServiceData(BleScans.BTHOME)
        val packet = data?.let(BthomeParser::parse)
        val update = packet?.let { tracker.onPacket(remote.address, it, stampMs) }

        update?.finished?.forEach(::journalBurst)
        val event = update?.event
        if (packet != null && !packet.encrypted && update?.late != true) {
            if (event != null) perSource[packet.packetId] = IntArray(ScanSource.entries.size)
            perSource[packet.packetId]?.let { it[source.ordinal]++ }
        }

        val fields = LinkedHashMap<String, Any?>()
        fields["id"] = packet?.packetId
        fields["buttons"] = packet?.buttons?.joinToString(",") { it.name.lowercase() }
        // without service data the whole advertisement is the only evidence there is
        fields["raw"] = (data ?: result.scanRecord?.bytes)?.toHexString()
        fields["rssi"] = result.rssi
        fields["tsMs"] = stampMs
        fields["delayMs"] = delayMs
        fields["scan"] = source.word
        fields["mode"] = ListenerService.status.word
        fields["session"] = ListenerService.sessionOpen
        fields["new"] = event != null
        // received in time, delivered after its burst was closed: in no burst statistics
        if (update?.late == true) fields["late"] = true
        BleScans.problemOf(data, packet)?.let { fields["problem"] = it }
        // the scan on the screen makes the radio listen all the time, whatever "mode" says
        if (ForegroundScan.active) fields[JournalNames.CONTROL_SCAN] = true
        // a small number means: the process was started for this very packet
        if (source == ScanSource.RESCUE) {
            fields["processAgeMs"] = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        }
        journal.log("shelly_packet", fields)

        if (packet != null && event != null) onEvent(event, packet, result.rssi, source, delayMs)

        handler.removeCallbacks(collectBursts)
        handler.postDelayed(collectBursts, tracker.windowMs + BURST_MARGIN_MS)
    }

    private fun onEvent(event: ShellyEvent, packet: BthomePacket, rssi: Int, source: ScanSource, delayMs: Long) {
        event.packetId?.let(store::rememberPacketId)
        val common = buildMap {
            put(JournalNames.ID, event.packetId)
            put("idStep", event.idStep.name.lowercase())
            put(JournalNames.MISSED_BEFORE, event.missedBefore)
            put("rssi", rssi)
            put("battery", packet.batteryPercent)
            put("scan", source.word)
            put("mode", ListenerService.status.word)
            put(JournalNames.TS_MS, event.elapsedMs)
            put("delayMs", delayMs)
            put("session", ListenerService.sessionOpen)
            // as in `shelly_packet`: such a press says nothing about the background
            if (ForegroundScan.active) put(JournalNames.CONTROL_SCAN, true)
        }
        if (event.presses.isEmpty()) {
            journal.log(JournalNames.SHELLY_BEACON, common)
            return
        }
        val repeat = event.repeatsPrevious
        if (!repeat) {
            holdAwake()
            lastPressElapsedMs = event.elapsedMs
        }
        for (press in event.presses) {
            val what = mapOf("button" to press.button, "gesture" to press.gesture.name.lowercase())
            journal.log(if (repeat) "shelly_repeat" else JournalNames.SHELLY_PRESS, what + common)
        }
        if (!repeat) event.toInputs(source.path).forEach(publish)
    }

    /**
     * Bridges the moment between publishing a press and its consumer starting to work: nothing
     * else keeps the phone awake once the advertisement has been handed over. Timed and short;
     * whoever acts on the press takes care of the time after that. A new lock for every press,
     * so that each one gets its full time.
     */
    private fun holdAwake() {
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:shelly_press").acquire(HANDOVER_MS)
    }

    private fun journalBurst(burst: ShellyBurst) {
        val counts = perSource.remove(burst.event.packetId)
        journal.log(
            "shelly_burst",
            mapOf(
                "id" to burst.event.packetId,
                "packets" to burst.packets,
                "spanMs" to burst.spanMs,
                "callback" to counts?.get(ScanSource.CALLBACK.ordinal),
                "rescue" to counts?.get(ScanSource.RESCUE.ordinal),
                "presses" to burst.event.presses.joinToString(",") { "${it.button}:${it.gesture.name.lowercase()}" },
                "tsMs" to burst.event.elapsedMs,
            ),
        )
    }

    private companion object {
        const val BURST_MARGIN_MS = 250L
        const val HANDOVER_MS = 3_000L
    }
}
