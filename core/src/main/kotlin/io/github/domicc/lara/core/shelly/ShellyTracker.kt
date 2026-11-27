package io.github.domicc.lara.core.shelly

import io.github.domicc.lara.core.bthome.BthomePacket
import io.github.domicc.lara.core.bthome.ButtonEvent
import io.github.domicc.lara.core.input.Gesture
import kotlin.math.max
import kotlin.math.min

/**
 * Turns the stream of received BTHome packets into events of the remote.
 *
 * Why it works this way:
 * - The remote repeats one packet many times per gesture. A packet with a packet id that is
 *   still "open" for its sender is a repeat; it only adds to the burst statistics.
 * - A burst stays open until no repeat has arrived for [windowMs]. The window slides with every
 *   repeat, so a long burst (held button) stays one event however long it lasts.
 * - Nothing is acted on beyond that window, apart from one last id per sender. A remote that
 *   restarts can legitimately send the same id again, and it must not be swallowed then; it is
 *   reported with [IdStep.SAME] or [IdStep.RESTART] instead.
 * - Several ids can be open per sender at once, because the same packets may reach the app on
 *   two delivery paths (callback scan and rescue scan) with different delays.
 * - A delivery path can also be late by more than the window (a broadcast into a dozing
 *   phone). Such a packet still carries the time at which the radio received it, so it is
 *   recognised as a repeat of a burst that is closed already: same id, received within the
 *   window of that burst's last packet. It is reported as [ShellyUpdate.late] and starts
 *   nothing. Without this it would look like a counter restart, be acted on a second time and
 *   make the next real event look like a loss.
 * - Packets without a press (beacon) are events too, with empty [ShellyEvent.presses]: they use
 *   the same counter, and following them keeps a received beacon from looking like a loss.
 * - A packet whose buttons could not be read (cut off, or an object this app cannot step over
 *   in front of them) is no event at all. Taken for a beacon it would open the burst, and the
 *   press in the next, complete packet of the same burst would be swallowed as its repeat.
 *   The caller sees [BthomePacket.stop] and journals the raw bytes.
 * - Encrypted packets are ignored here; the caller sees [BthomePacket.encrypted] and journals it.
 *
 * Times are milliseconds on a clock that keeps counting while the phone sleeps. The timestamp
 * of the scan result itself is the best source, because results can be delivered late or in
 * batches. The class has no timer of its own: call [takeFinished] about [windowMs] after the
 * last packet to collect the burst statistics.
 *
 * All methods may be called from any thread. One small record per sender address is kept for as
 * long as the tracker lives (the last id, for loss counting after hours of silence, and the
 * last few closed bursts), so feed it the remotes the app cares about, not every BTHome device
 * in range.
 *
 * @param windowMs how long after its last packet an id counts as still the same burst. It must
 * be longer than the longest pause inside one burst as the scan hears it.
 * @param maxGap the largest number of events lost in a row that is still believed. A bigger
 * jump is taken for a counter restart. The two cannot be told apart with certainty: with 32, a
 * restart is mistaken for a loss in about one case out of eight, and a real run of more than 32
 * lost events is not counted.
 */
class ShellyTracker(
    val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxGap: Int = DEFAULT_MAX_GAP,
) {
    /** A packet without an id can only be recognised as a repeat by its content. */
    private data class BurstKey(val packetId: Int?, val buttons: List<ButtonEvent>?)

    private class Burst(val event: ShellyEvent) {
        var firstMs = event.elapsedMs
        var lastMs = event.elapsedMs
        var packets = 1
    }

    /** What is kept of a burst that was handed out, to recognise its late repeats. */
    private class Closed(val key: BurstKey, val lastMs: Long)

    private class Remote {
        var lastId: Int? = null
        val open = LinkedHashMap<BurstKey, Burst>()
        val closed = ArrayDeque<Closed>()
    }

    private val remotes = LinkedHashMap<String, Remote>()

    /**
     * Tells the tracker the last packet id the app received from [address] before this process
     * started. Without it the first event of a new process has nothing to compare with, and
     * whatever was lost while the process was gone would never be counted. Does nothing once
     * a packet of that sender has been fed.
     */
    @Synchronized
    fun seed(address: String, lastId: Int) {
        val remote = remotes.getOrPut(address) { Remote() }
        if (remote.lastId == null) remote.lastId = lastId and 0xFF
    }

    /** Feeds one received packet of the sender [address]. */
    @Synchronized
    fun onPacket(address: String, packet: BthomePacket, elapsedMs: Long): ShellyUpdate {
        val finished = takeFinished(elapsedMs)
        if (packet.encrypted) return ShellyUpdate(null, finished)

        val remote = remotes.getOrPut(address) { Remote() }
        val id = packet.packetId
        val key = BurstKey(id, if (id == null) packet.buttons else null)
        val presses = packet.buttons.mapIndexedNotNull { index, button ->
            gestureOf(button)?.let { ShellyPress(index + 1, it) }
        }
        val open = remote.open[key]
        if (open != null) {
            // the two delivery paths are not in order with each other
            open.firstMs = min(open.firstMs, elapsedMs)
            open.lastMs = max(open.lastMs, elapsedMs)
            open.packets++
            return ShellyUpdate(null, finished)
        }
        if (packet.stoppedEarly && presses.isEmpty()) return ShellyUpdate(null, finished)
        if (remote.closed.any { it.key == key && elapsedMs - it.lastMs < windowMs }) {
            return ShellyUpdate(null, finished, late = true)
        }

        var step = IdStep.UNKNOWN
        var missedBefore = 0
        if (id != null) {
            val previous = remote.lastId
            if (previous != null) {
                val ahead = (id - previous) and 0xFF
                step = when {
                    ahead == 0 -> IdStep.SAME
                    ahead == 1 -> IdStep.NEXT
                    ahead - 1 <= maxGap -> IdStep.GAP
                    else -> IdStep.RESTART
                }
                if (step == IdStep.GAP) missedBefore = ahead - 1
            }
            remote.lastId = id
        }
        val event = ShellyEvent(address, id, presses, step, missedBefore, elapsedMs)
        remote.open[key] = Burst(event)
        return ShellyUpdate(event, finished)
    }

    /**
     * Closes every burst whose last packet is at least [windowMs] older than [elapsedMs] and
     * returns them, oldest first. Each burst is returned once, here or by [onPacket].
     */
    @Synchronized
    fun takeFinished(elapsedMs: Long): List<ShellyBurst> {
        val finished = ArrayList<ShellyBurst>()
        for (remote in remotes.values) {
            val bursts = remote.open.entries.iterator()
            while (bursts.hasNext()) {
                val (key, burst) = bursts.next()
                if (elapsedMs - burst.lastMs >= windowMs) {
                    bursts.remove()
                    finished += ShellyBurst(burst.event, burst.packets, burst.lastMs - burst.firstMs)
                    remote.closed.addLast(Closed(key, burst.lastMs))
                    while (remote.closed.size > KEEP_CLOSED) remote.closed.removeFirst()
                }
            }
        }
        finished.sortBy { it.event.elapsedMs }
        return finished
    }

    private fun gestureOf(button: ButtonEvent): Gesture? = when (button) {
        ButtonEvent.NONE -> null
        ButtonEvent.PRESS -> Gesture.PRESS
        ButtonEvent.DOUBLE -> Gesture.DOUBLE
        ButtonEvent.TRIPLE -> Gesture.TRIPLE
        ButtonEvent.LONG -> Gesture.LONG
        ButtonEvent.LONG_DOUBLE -> Gesture.LONG_DOUBLE
        ButtonEvent.LONG_TRIPLE -> Gesture.LONG_TRIPLE
        ButtonEvent.HOLD -> Gesture.HOLD
        ButtonEvent.HOLD_RELEASE -> Gesture.HOLD_RELEASE
        ButtonEvent.UNKNOWN -> Gesture.UNKNOWN
    }

    companion object {
        /** The reference parser of BTHome uses the same four seconds. */
        const val DEFAULT_WINDOW_MS = 4_000L
        const val DEFAULT_MAX_GAP = 32

        /** Closed bursts remembered per sender; a late delivery path lags by a few events at most. */
        private const val KEEP_CLOSED = 16
    }
}
