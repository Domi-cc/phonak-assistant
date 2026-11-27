package io.github.domicc.lara.core.count

import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputPath
import io.github.domicc.lara.core.input.InputSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountSessionTest {

    private val session = CountSession()

    private fun press(button: Int, gesture: Gesture = Gesture.PRESS, missed: Int = 0) =
        Input(InputSource.SHELLY, 1_000, button, gesture, InputPath.BLE_CALLBACK, missed)

    @Test
    fun `buttons 1, 2 and 4 get the running number`() {
        assertEquals(CountStep.Announce(1, 0), session.onInput(press(1)))
        assertEquals(CountStep.Announce(2, 0), session.onInput(press(2)))
        assertEquals(CountStep.Announce(3, 0), session.onInput(press(4)))
        assertEquals(3, session.count)
    }

    @Test
    fun `every gesture is one count`() {
        Gesture.entries.forEachIndexed { index, gesture ->
            assertEquals(CountStep.Announce(index + 1, 0), session.onInput(press(1, gesture)), gesture.name)
        }
    }

    @Test
    fun `button 3 runs the echo test and is not counted`() {
        session.onInput(press(1))
        assertEquals(CountStep.Echo(0), session.onInput(press(3)))
        assertEquals(CountStep.Announce(2, 0), session.onInput(press(2)))
    }

    @Test
    fun `a loss before a press is passed on with it`() {
        assertEquals(CountStep.Announce(1, 2), session.onInput(press(1, missed = 2)))
        assertEquals(CountStep.Echo(1), session.onInput(press(3, missed = 1)))
        assertEquals(CountStep.Announce(2, 0), session.onInput(press(1)))
    }

    @Test
    fun `a tap ends the session`() {
        session.onInput(press(1))
        assertEquals(CountStep.End, session.onInput(Input(InputSource.HEARING_AID_TAP, 2_000)))
        assertEquals(1, session.count)
    }

    @Test
    fun `the screen, adb and the assist gesture ask for the echo test`() {
        for (source in listOf(InputSource.SCREEN, InputSource.ADB, InputSource.ASSIST_GESTURE)) {
            assertEquals(CountStep.Echo(0), session.onInput(Input(source, 1_000)), source.name)
        }
        assertEquals(0, session.count)
    }

    @Test
    fun `other inputs are ignored and do not count`() {
        assertEquals(CountStep.Ignore, session.onInput(Input(InputSource.MEDIA_KEY, 1_000, button = 85)))
        assertEquals(CountStep.Ignore, session.onInput(press(5)))
        assertEquals(CountStep.Ignore, session.onInput(Input(InputSource.SHELLY, 1_000)))
        assertEquals(0, session.count)
    }

    @Test
    fun `asking whether an input counts does not count it`() {
        assertTrue(session.counts(press(4)))
        assertFalse(session.counts(press(3)))
        assertFalse(session.counts(Input(InputSource.HEARING_AID_TAP, 1_000)))
        assertFalse(session.counts(Input(InputSource.SCREEN, 1_000)))
        assertEquals(0, session.count)
    }

    @Test
    fun `a simulated press counts like a real one`() {
        assertEquals(CountStep.Announce(1, 0), session.onInput(press(1).copy(path = InputPath.SIMULATED)))
    }
}
