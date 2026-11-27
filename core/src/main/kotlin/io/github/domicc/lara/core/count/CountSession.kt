package io.github.domicc.lara.core.count

import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputSource

/** What the count session does with one input. */
sealed interface CountStep {
    /**
     * A press got the running number. Whether the number is said is decided by
     * [CountSession.say], once the caller knows whether a newer press waits already.
     *
     * @param missed events of the remote lost inside this session right before this press
     */
    data class Announce(val number: Int, val missed: Int) : CountStep

    /**
     * Run the echo test inside the session.
     *
     * @param missed losses the wearer has not been told about yet; to be said first
     */
    data class Echo(val missed: Int) : CountStep

    /**
     * A button is held down, or was let go after holding. Nothing is said and nothing counts:
     * the remote sends the gesture that counts (long, or a plain press) when the button is
     * released.
     */
    data object Held : CountStep

    /** End the session normally. */
    data object End : CountStep

    /** Not an input this session knows what to do with. */
    data object Ignore : CountStep
}

/**
 * The counting of one count session, measurement 1 of the design: every press of the remote
 * that arrives is answered with its running number, so the wearer, who counts along, hears at
 * once when a press was lost.
 *
 * Buttons 1, 2 and 4 count, one number per event of the remote: the remote decides on single,
 * double, triple or long before it sends, so two quick presses that it merges into a "double"
 * get one number. Button 3 runs the echo test without leaving the session. A hearing-aid tap
 * ends the session.
 *
 * A held button is the exception. This remote sends "hold" while the button is down and then,
 * under the next packet id, "long" or "press" when it is released (seen in the journal of the
 * owner's remote on 27 November 2026). Counting both would give one physical press two
 * numbers, and on button 3 two echo tests, so "hold" and "hold release" do nothing here.
 *
 * Losses are counted from the packet ids of the remote ([Input.missedBefore]). The loss before
 * the first press of a session is not the session's: it lies between the last thing received,
 * maybe hours ago, and now, and is often no more than the remote's beacon. It stays in the
 * journal and is not said.
 *
 * The screen, adb and the assist gesture can ask for the echo test as well while the session
 * runs. The router hands on nothing else from them, so an input of theirs can only mean that.
 */
class CountSession {

    /**
     * What the wearer hears for one counted press.
     *
     * @param number the number to say; null if a newer number is on its way already
     * @param missed losses to say before the number; 0 for none
     */
    data class Said(val number: Int?, val missed: Int)

    /** The number of the last press counted; 0 before the first. */
    var count = 0
        private set

    /** Losses the wearer has not been told about yet. */
    private var owedMissed = 0

    /** No event of the remote has arrived in this session yet. */
    private var first = true

    fun onInput(input: Input): CountStep {
        if (input.source == InputSource.HEARING_AID_TAP) return CountStep.End
        if (input.source in ECHO_REQUESTERS) return CountStep.Echo(takeOwed())
        // the router keeps unknown gestures out; a second line of defence costs nothing
        if (input.source != InputSource.SHELLY || input.gesture == Gesture.UNKNOWN) return CountStep.Ignore
        val missed = if (first) 0 else input.missedBefore
        first = false
        owedMissed += missed
        return when {
            input.gesture in HELD -> CountStep.Held
            input.button == ECHO_BUTTON -> CountStep.Echo(takeOwed())
            input.button in COUNT_BUTTONS -> CountStep.Announce(++count, missed)
            else -> CountStep.Ignore
        }
    }

    /**
     * What is said for [step]. If presses come faster than numbers can be said, only the
     * newest number is said; the loss hint of a skipped number is kept and said with the next
     * number that is spoken.
     *
     * @param newerWaits a press that gets a newer number has arrived already
     */
    fun say(step: CountStep.Announce, newerWaits: Boolean): Said =
        if (newerWaits) Said(null, 0) else Said(step.number, takeOwed())

    /** True if [input] would get a number. Does not count it. */
    fun counts(input: Input): Boolean =
        input.source == InputSource.SHELLY && input.button in COUNT_BUTTONS &&
            input.gesture !in HELD && input.gesture != Gesture.UNKNOWN

    private fun takeOwed(): Int = owedMissed.also { owedMissed = 0 }

    companion object {
        val COUNT_BUTTONS = setOf(1, 2, 4)
        const val ECHO_BUTTON = 3
        private val ECHO_REQUESTERS = setOf(InputSource.SCREEN, InputSource.ADB, InputSource.ASSIST_GESTURE)
        private val HELD = setOf(Gesture.HOLD, Gesture.HOLD_RELEASE)
    }
}
