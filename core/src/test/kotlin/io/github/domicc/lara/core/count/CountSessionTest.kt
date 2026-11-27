package io.github.domicc.lara.core.count

import io.github.domicc.lara.core.bthome.BthomeParser
import io.github.domicc.lara.core.bthome.BthomeVectors
import io.github.domicc.lara.core.count.CountSession.Said
import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputPath
import io.github.domicc.lara.core.input.InputSource
import io.github.domicc.lara.core.shelly.ShellyTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CountSessionTest {

    private val session = CountSession()

    private fun press(button: Int, gesture: Gesture = Gesture.PRESS, missed: Int = 0) =
        Input(InputSource.SHELLY, 1_000, button, gesture, InputPath.BLE_CALLBACK, missed)

    /** Counts a press and says its number at once, as when no newer press waits. */
    private fun said(input: Input): Said {
        val step = session.onInput(input) as CountStep.Announce
        return session.say(step, newerWaits = false)
    }

    @Test
    fun `buttons 1, 2 and 4 get the running number`() {
        assertEquals(CountStep.Announce(1, 0), session.onInput(press(1)))
        assertEquals(CountStep.Announce(2, 0), session.onInput(press(2)))
        assertEquals(CountStep.Announce(3, 0), session.onInput(press(4)))
        assertEquals(3, session.count)
    }

    @Test
    fun `every gesture that ends a press is one count`() {
        val counted = Gesture.entries - setOf(Gesture.HOLD, Gesture.HOLD_RELEASE, Gesture.UNKNOWN)
        counted.forEachIndexed { index, gesture ->
            assertEquals(CountStep.Announce(index + 1, 0), session.onInput(press(1, gesture)), gesture.name)
        }
    }

    @Test
    fun `a held button counts when it is released, not while it is held`() {
        for (button in 1..4) {
            assertEquals(CountStep.Held, session.onInput(press(button, Gesture.HOLD)), "button $button")
            assertEquals(CountStep.Held, session.onInput(press(button, Gesture.HOLD_RELEASE)), "button $button")
            assertFalse(session.counts(press(button, Gesture.HOLD)))
        }
        assertEquals(0, session.count)
        assertEquals(CountStep.Announce(1, 0), session.onInput(press(1, Gesture.LONG)))
    }

    @Test
    fun `the hold and the long of the owner's remote are one number`() {
        // C2 and C3: one long press of button 1 as the remote really sent it
        val tracker = ShellyTracker()
        val steps = listOf("C2" to 0L, "C3" to 1_500L).flatMap { (id, atMs) ->
            val packet = assertNotNull(BthomeParser.parse(BthomeVectors.byId(id).bytes))
            assertNotNull(tracker.onPacket(REMOTE, packet, atMs).event).toInputs(InputPath.BLE_CALLBACK)
        }.map(session::onInput)
        assertEquals(listOf(CountStep.Held, CountStep.Announce(1, 0)), steps)
    }

    @Test
    fun `button 3 runs the echo test and is not counted`() {
        session.onInput(press(1))
        assertEquals(CountStep.Echo(0), session.onInput(press(3)))
        assertEquals(CountStep.Announce(2, 0), session.onInput(press(2)))
    }

    @Test
    fun `holding button 3 runs one echo test, on release`() {
        assertEquals(CountStep.Held, session.onInput(press(3, Gesture.HOLD)))
        assertEquals(CountStep.Echo(0), session.onInput(press(3, Gesture.LONG)))
    }

    @Test
    fun `a loss inside the session is said with the number`() {
        assertEquals(Said(1, 0), said(press(1)))
        assertEquals(Said(2, 2), said(press(1, missed = 2)))
        assertEquals(Said(3, 0), said(press(1)), "said once")
    }

    @Test
    fun `the loss before the first press of a session is not the session's`() {
        // the gap since whatever was received last, maybe hours ago
        assertEquals(CountStep.Announce(1, 0), session.onInput(press(1, missed = 3)))
        assertEquals(Said(1, 0), session.say(CountStep.Announce(1, 0), newerWaits = false))
        assertEquals(CountStep.Announce(2, 1), session.onInput(press(1, missed = 1)))
    }

    @Test
    fun `a held button that opened the session is the first event, and its loss is not said either`() {
        assertEquals(CountStep.Held, session.onInput(press(1, Gesture.HOLD, missed = 2)))
        assertEquals(Said(1, 0), said(press(1, Gesture.LONG)))
    }

    @Test
    fun `if presses come faster than numbers only the newest is said, with every loss before it`() {
        session.onInput(press(1))
        val second = session.onInput(press(1, missed = 1)) as CountStep.Announce
        assertEquals(Said(null, 0), session.say(second, newerWaits = true))
        val third = session.onInput(press(2, missed = 2)) as CountStep.Announce
        assertEquals(Said(null, 0), session.say(third, newerWaits = true))
        val fourth = session.onInput(press(4)) as CountStep.Announce
        assertEquals(Said(4, 3), session.say(fourth, newerWaits = false))
        assertEquals(Said(5, 0), said(press(1)))
    }

    @Test
    fun `a loss that is still owed is said before the echo test`() {
        session.onInput(press(1))
        val skipped = session.onInput(press(1, missed = 1)) as CountStep.Announce
        session.say(skipped, newerWaits = true)
        assertEquals(CountStep.Echo(3), session.onInput(press(3, missed = 2)))
        assertEquals(CountStep.Echo(0), session.onInput(Input(InputSource.SCREEN, 2_000)))
        assertEquals(Said(3, 0), said(press(1)))
    }

    @Test
    fun `a loss seen at a held button is said with the next number`() {
        session.onInput(press(1))
        assertEquals(CountStep.Held, session.onInput(press(2, Gesture.HOLD, missed = 1)))
        assertEquals(Said(2, 1), said(press(2, Gesture.LONG)))
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
    fun `a gesture code the app does not know neither counts nor starts the echo test`() {
        for (button in 1..4) {
            assertEquals(CountStep.Ignore, session.onInput(press(button, Gesture.UNKNOWN)), "button $button")
            assertFalse(session.counts(press(button, Gesture.UNKNOWN)))
        }
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

    private companion object {
        const val REMOTE = "AA:BB:CC:DD:EE:FF"
    }
}
