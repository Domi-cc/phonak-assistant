package io.github.domicc.lara.core.count

import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputSource

/** What the count session does with one input. */
sealed interface CountStep {
    /**
     * Say the running number.
     *
     * @param missed events the remote sent right before this press that never arrived
     */
    data class Announce(val number: Int, val missed: Int) : CountStep

    /** Run the echo test inside the session. */
    data class Echo(val missed: Int) : CountStep

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
 * Buttons 1, 2 and 4 count, whatever the gesture: the remote decides on single, double, long
 * and so on before it sends, and each of them is one event of the remote. Button 3 runs the
 * echo test without leaving the session. A hearing-aid tap ends the session.
 *
 * The screen, adb and the assist gesture can ask for the echo test as well while the session
 * runs. The router hands on nothing else from them, so an input of theirs can only mean that.
 */
class CountSession {

    /** The number of the last press counted; 0 before the first. */
    var count = 0
        private set

    fun onInput(input: Input): CountStep = when {
        input.source == InputSource.HEARING_AID_TAP -> CountStep.End
        input.source in ECHO_REQUESTERS -> CountStep.Echo(0)
        input.source != InputSource.SHELLY -> CountStep.Ignore
        input.button == ECHO_BUTTON -> CountStep.Echo(input.missedBefore)
        input.button in COUNT_BUTTONS -> CountStep.Announce(++count, input.missedBefore)
        else -> CountStep.Ignore
    }

    /** True if [input] would get a number. Does not count it. */
    fun counts(input: Input): Boolean = input.source == InputSource.SHELLY && input.button in COUNT_BUTTONS

    companion object {
        val COUNT_BUTTONS = setOf(1, 2, 4)
        const val ECHO_BUTTON = 3
        private val ECHO_REQUESTERS = setOf(InputSource.SCREEN, InputSource.ADB, InputSource.ASSIST_GESTURE)
    }
}
