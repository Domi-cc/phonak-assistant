package io.github.domicc.lara.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutingTest {

    private val tap = Input(InputSource.HEARING_AID_TAP, 1_000)
    private val press = Input(InputSource.SHELLY, 1_000, button = 1, gesture = Gesture.PRESS, path = InputPath.BLE_CALLBACK)

    private fun route(
        input: Input,
        state: SessionState,
        wakes: Boolean = true,
        tap: TapProgram = TapProgram.REMOTE,
        enrolled: Boolean = true,
    ) = Routing.route(input, state, wakes, tap, enrolled)

    @Test
    fun `a tap starts what the owner chose for it`() {
        for (wakes in listOf(true, false)) {
            assertEquals(Route.START_REMOTE, route(tap, SessionState.NONE, wakes, TapProgram.REMOTE))
            assertEquals(Route.START_ECHO, route(tap, SessionState.NONE, wakes, TapProgram.ECHO))
            assertEquals(Route.START_COUNT, route(tap, SessionState.NONE, wakes, TapProgram.COUNT))
            // Android normally drops a second tap that follows so soon; if one arrives, the
            // session service turns it down as busy
            assertEquals(Route.START_REMOTE, route(tap, SessionState.OPENING, wakes, TapProgram.REMOTE))
            assertEquals(Route.START_ECHO, route(tap, SessionState.OPENING, wakes, TapProgram.ECHO))
            assertEquals(Route.START_COUNT, route(tap, SessionState.OPENING, wakes, TapProgram.COUNT))
        }
    }

    @Test
    fun `without a remote a tap runs the echo test instead of a remote session`() {
        // what the app on the phone did before there was a setting: remote session with a
        // remote, echo test without one
        assertEquals(Route.START_ECHO, route(tap, SessionState.NONE, tap = TapProgram.REMOTE, enrolled = false))
        assertEquals(Route.START_ECHO, route(tap, SessionState.NONE, tap = TapProgram.ECHO, enrolled = false))
        // the count session also takes simulated presses, so it needs no remote
        assertEquals(Route.START_COUNT, route(tap, SessionState.NONE, tap = TapProgram.COUNT, enrolled = false))
    }

    @Test
    fun `a tap during a session that takes inputs goes to the session, which ends`() {
        for (program in TapProgram.entries) {
            for (enrolled in listOf(true, false)) {
                assertEquals(Route.TO_SESSION, route(tap, SessionState.OPEN, wakes = true, tap = program, enrolled = enrolled))
                assertEquals(Route.TO_SESSION, route(tap, SessionState.OPEN, wakes = false, tap = program, enrolled = enrolled))
            }
        }
    }

    @Test
    fun `a press during a session goes to the session whatever the setting`() {
        assertEquals(Route.TO_SESSION, route(press, SessionState.OPEN, wakes = true))
        assertEquals(Route.TO_SESSION, route(press, SessionState.OPEN, wakes = false))
    }

    @Test
    fun `a press without a session opens a remote session if the owner allows it`() {
        assertEquals(Route.WAKE_REMOTE, route(press, SessionState.NONE, wakes = true))
    }

    @Test
    fun `in the silent mode a press without a session does nothing`() {
        assertEquals(Route.NONE, route(press, SessionState.NONE, wakes = false))
    }

    @Test
    fun `a press waits for the session that is being opened, whatever the setting`() {
        // no second request: the session service reports it if the session does not come
        assertEquals(Route.HOLD, route(press, SessionState.OPENING, wakes = true))
        assertEquals(Route.HOLD, route(press, SessionState.OPENING, wakes = false))
    }

    @Test
    fun `what a tap starts does not change what a press does`() {
        for (state in SessionState.entries) {
            for (wakes in listOf(true, false)) {
                val routes = TapProgram.entries.map { route(press, state, wakes, it) }.toSet()
                assertEquals(1, routes.size, "$state wakes=$wakes")
            }
        }
    }

    @Test
    fun `a press never starts the count session or the echo test`() {
        // the owner asked for presses announced in words; counting is a tool he starts himself
        val started = buildSet {
            for (state in SessionState.entries) for (wakes in listOf(true, false)) for (program in TapProgram.entries) {
                for (gesture in Gesture.entries) for (button in 1..4) {
                    add(route(press.copy(button = button, gesture = gesture), state, wakes, program))
                }
            }
        }
        assertEquals(setOf(Route.WAKE_REMOTE, Route.HOLD, Route.TO_SESSION, Route.NONE), started)
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
    fun `a press is routed the same with and without an enrolled remote`() {
        // without a remote only a simulated press can arrive, and it must take the same way
        for (state in SessionState.entries) {
            for (wakes in listOf(true, false)) {
                assertEquals(route(press, state, wakes, enrolled = true), route(press, state, wakes, enrolled = false))
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
            listOf("start_echo", "start_remote", "start_count", "wake_remote", "hold", "to_session", "none", "too_late"),
            Route.entries.map { it.word },
        )
    }

    @Test
    fun `the routing itself never calls a press too late`() {
        // it does not know the time: that is decided where the waiting presses are kept
        val routes = buildSet {
            for (state in SessionState.entries) for (wakes in listOf(true, false)) for (program in TapProgram.entries) {
                for (source in InputSource.entries) add(route(press.copy(source = source), state, wakes, program))
            }
        }
        assertEquals(false, Route.TOO_LATE in routes)
    }

    @Test
    fun `the words of the tap programs are fixed and found again`() {
        // stored in the preferences, written to the journal, and the names of the session programs
        assertEquals(listOf("remote", "echo", "count"), TapProgram.entries.map { it.word })
        for (program in TapProgram.entries) assertEquals(program, TapProgram.fromWord(program.word))
        assertNull(TapProgram.fromWord("ECHO"))
        assertNull(TapProgram.fromWord(null))
    }
}
