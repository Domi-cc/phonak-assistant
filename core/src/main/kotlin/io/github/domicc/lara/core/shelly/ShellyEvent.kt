package io.github.domicc.lara.core.shelly

import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputPath
import io.github.domicc.lara.core.input.InputSource

/**
 * Something new the remote sent, reported with the first packet that arrives, because the
 * wearer is waiting for the answer. The remote repeats the packet for about a second; what was
 * received of those repeats follows later as a [ShellyBurst].
 *
 * @param address the sender, as given to the tracker
 * @param packetId the remote's own event counter (0 to 255); null if the packet carries none
 * @param presses the buttons with a gesture, usually one; two if two buttons were pressed
 * together. Empty for a packet without a press, such as the remote's periodic beacon.
 * @param idStep how [packetId] relates to the previous event of the same remote
 * @param missedBefore how many events the remote sent between the previous received one and
 * this one, counted by packet id; above 0 only for [IdStep.GAP]. A missed event is not
 * necessarily a missed press: the beacon uses the same counter.
 * @param elapsedMs the time of the first packet received
 */
data class ShellyEvent(
    val address: String,
    val packetId: Int?,
    val presses: List<ShellyPress>,
    val idStep: IdStep,
    val missedBefore: Int,
    val elapsedMs: Long,
) {
    /**
     * One input per pressed button, for the router. The loss count goes to the first of them
     * only, so that two buttons pressed together do not report the same loss twice.
     *
     * @param path how the packet reached the app
     */
    fun toInputs(path: InputPath): List<Input> =
        presses.mapIndexed { index, press ->
            Input(InputSource.SHELLY, elapsedMs, press.button, press.gesture, path, if (index == 0) missedBefore else 0)
        }
}

/** @param button 1 to 4: the position of the button object in the packet */
data class ShellyPress(val button: Int, val gesture: Gesture)

/**
 * How the packet id of an event relates to the previous event of the same remote. The id is the
 * only ground truth for losses the phone has, but it wraps at 255 and starts again when the
 * remote restarts (battery change, brown-out), so not every jump is a loss.
 */
enum class IdStep {
    /** Nothing to compare: first event of this remote, or the packet has no id. */
    UNKNOWN,

    /** One more than the previous id: nothing was lost. */
    NEXT,

    /** The id skipped ahead by a plausible amount: [ShellyEvent.missedBefore] events were lost. */
    GAP,

    /**
     * The same id again after the duplicate window. Either more repeats of the previous event
     * (a held button sends for up to a minute and a sparse scan hears it in pieces) or the
     * remote restarted and happened to reach the same number. Callers decide whether to act.
     */
    SAME,

    /** The id went backwards or jumped implausibly far: the remote restarted its counter. */
    RESTART,
}

/**
 * What the phone received of one burst, known once the burst is over. Together the two numbers
 * show which scan duty cycle was really in effect: this remote repeats a packet for about one
 * second at about 20 ms spacing, so a continuous scan sees some 50 packets over one second and
 * a throttled scan a few packets within one short listening window.
 *
 * @param packets packets received, the first one included
 * @param spanMs time between the first and the last packet received
 */
data class ShellyBurst(val event: ShellyEvent, val packets: Int, val spanMs: Long)

/**
 * The result of feeding one packet to the tracker.
 *
 * @param event non-null if the packet started a new event; null for a repeat
 * @param finished bursts that ended in the meantime; each burst is handed out exactly once
 */
data class ShellyUpdate(val event: ShellyEvent?, val finished: List<ShellyBurst>)

/**
 * Running totals since the last reset.
 *
 * @param received events with at least one press
 * @param missed events lost according to the packet id (see [ShellyEvent.missedBefore])
 * @param beacons events without a press
 * @param restarts events with [IdStep.RESTART]
 * @param pressesByButton button number to presses received
 */
data class ShellyTotals(
    val received: Int = 0,
    val missed: Int = 0,
    val beacons: Int = 0,
    val restarts: Int = 0,
    val pressesByButton: Map<Int, Int> = emptyMap(),
)
