package io.github.domicc.lara.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutingTest {

    private val tap = Input(InputSource.HEARING_AID_TAP, 1_000)
    private val press = Input(InputSource.SHELLY, 1_000, button = 1, gesture = Gesture.PRESS, path = InputPath.BLE_CALLBACK)

    private fun route(input: Input, state: SessionState, wakes: Boolean = true, tap: TapProgram = TapProgram.ECHO) =
        Routing.route(input, state, wakes, tap)

    @Test
    fun `a tap starts the echo test unless the owner chose the count session`() {
        for (wakes in listOf(true, false)) {
            assertEquals(Route.START_ECHO, route(tap, SessionState.NONE, wakes, TapProgram.ECHO))
            assertEquals(Route.START_COUNT, route(tap, SessionState.NONE, wakes, TapProgram.COUNT))
            // Android normally drops a second tap that follows so soon; if one arrives, the
            // session service turns it down as busy
            assertEquals(Route.START_ECHO, route(tap, SessionState.OPENING, wakes, TapProgram.ECHO))
            assertEquals(Route.START_COUNT, route(tap, SessionState.OPENING, wakes, TapProgram.COUNT))
        }
    }

    @Test
    fun `a tap during a count session goes to the session, which ends`() {
        for (program in TapProgram.entries) {
            assertEquals(Route.TO_SESSION, route(tap, SessionState.OPEN, wakes = true, tap = program))
            assertEquals(Route.TO_SESSION, route(tap, SessionState.OPEN, wakes = false, tap = program))
        }
    }

    @Test
    fun `a press during a session goes to the session whatever the setting`() {
        assertEquals(Route.TO_SESSION, route(press, SessionState.OPEN, wakes = true))
        assertEquals(Route.TO_SESSION, route(press, SessionState.OPEN, wakes = false))
    }

    @Test
    fun `a press without a session wakes the app if the owner allows it`() {
        assertEquals(Route.WAKE_COUNT, route(press, SessionState.NONE, wakes = true))
        assertEquals(Route.WAKE_COUNT, route(press, SessionState.OPENING, wakes = true))
    }

    @Test
    fun `in the silent mode a press without a session does nothing`() {
        assertEquals(Route.NONE, route(press, SessionState.NONE, wakes = false))
    }

    @Test
    fun `in the silent mode a press still waits for the session a tap is opening`() {
        assertEquals(Route.HOLD, route(press, SessionState.OPENING, wakes = false))
    }

    @Test
    fun `what a tap starts does not change what a press does`() {
        for (state in SessionState.entries) {
            for (wakes in listOf(true, false)) {
                assertEquals(route(press, state, wakes, TapProgram.ECHO), route(press, state, wakes, TapProgram.COUNT))
            }
        }
    }

    @Test
    fun `a gesture code the app does not know never does anything`() {
        // a damaged or foreign packet must not open the voice link, count or start a recording
        for (button in 1..4) {
            val unknown = press.copy(button = button, gesture = Gesture.UNKNOWN)
            for (state in SessionState.entries) {
                for (wakes in listOf(true, false)) {
                    assertEquals(Route.NONE, route(unknown, state, wakes), "button $button $state wakes=$wakes")
                }
            }
        }
    }

    @Test
    fun `every gesture the app knows is routed like a plain press`() {
        for (gesture in Gesture.entries - Gesture.UNKNOWN) {
            for (state in SessionState.entries) {
                assertEquals(route(press, state), route(press.copy(gesture = gesture), state), "$gesture $state")
            }
        }
    }

    @Test
    fun `a simulated press is routed like a real one`() {
        val simulated = press.copy(path = InputPath.SIMULATED)
        for (state in SessionState.entries) {
            for (wakes in listOf(true, false)) {
                assertEquals(route(press, state, wakes), route(simulated, state, wakes))
            }
        }
    }

    @Test
    fun `sources that name their program themselves are not routed here`() {
        for (source in listOf(InputSource.SCREEN, InputSource.ADB, InputSource.ASSIST_GESTURE, InputSource.MEDIA_KEY)) {
            for (state in SessionState.entries) {
                assertEquals(Route.NONE, route(Input(source, 1_000), state), "$source $state")
            }
        }
    }

    @Test
    fun `the journal words of the routes are fixed`() {
        assertEquals(
            listOf("start_echo", "start_count", "wake_count", "hold", "to_session", "none"),
            Route.entries.map { it.word },
        )
    }

    @Test
    fun `the words of the tap programs are fixed and found again`() {
        // stored in the preferences, written to the journal, and the names of the session programs
        assertEquals(listOf("echo", "count"), TapProgram.entries.map { it.word })
        for (program in TapProgram.entries) assertEquals(program, TapProgram.fromWord(program.word))
        assertNull(TapProgram.fromWord("ECHO"))
        assertNull(TapProgram.fromWord(null))
    }
}
