package io.github.domicc.lara.core.input

import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingTest {

    private val tap = Input(InputSource.HEARING_AID_TAP, 1_000)
    private val press = Input(InputSource.SHELLY, 1_000, button = 1, gesture = Gesture.PRESS, path = InputPath.BLE_CALLBACK)

    @Test
    fun `a tap opens a count session`() {
        for (wakes in listOf(true, false)) {
            assertEquals(Route.START_COUNT, Routing.route(tap, SessionState.NONE, wakes))
            // Android normally drops a second tap that follows so soon; if one arrives, the
            // session service turns it down as busy
            assertEquals(Route.START_COUNT, Routing.route(tap, SessionState.OPENING, wakes))
        }
    }

    @Test
    fun `a tap during a session goes to the session, which ends`() {
        assertEquals(Route.TO_SESSION, Routing.route(tap, SessionState.OPEN, shellyWakes = true))
        assertEquals(Route.TO_SESSION, Routing.route(tap, SessionState.OPEN, shellyWakes = false))
    }

    @Test
    fun `a press during a session goes to the session whatever the setting`() {
        assertEquals(Route.TO_SESSION, Routing.route(press, SessionState.OPEN, shellyWakes = true))
        assertEquals(Route.TO_SESSION, Routing.route(press, SessionState.OPEN, shellyWakes = false))
    }

    @Test
    fun `a press without a session wakes the app if the owner allows it`() {
        assertEquals(Route.WAKE_COUNT, Routing.route(press, SessionState.NONE, shellyWakes = true))
        assertEquals(Route.WAKE_COUNT, Routing.route(press, SessionState.OPENING, shellyWakes = true))
    }

    @Test
    fun `in the silent mode a press without a session does nothing`() {
        assertEquals(Route.NONE, Routing.route(press, SessionState.NONE, shellyWakes = false))
    }

    @Test
    fun `in the silent mode a press still waits for the session a tap is opening`() {
        assertEquals(Route.HOLD, Routing.route(press, SessionState.OPENING, shellyWakes = false))
    }

    @Test
    fun `a simulated press is routed like a real one`() {
        val simulated = press.copy(path = InputPath.SIMULATED)
        for (state in SessionState.entries) {
            for (wakes in listOf(true, false)) {
                assertEquals(Routing.route(press, state, wakes), Routing.route(simulated, state, wakes))
            }
        }
    }

    @Test
    fun `sources that name their program themselves are not routed here`() {
        for (source in listOf(InputSource.SCREEN, InputSource.ADB, InputSource.ASSIST_GESTURE, InputSource.MEDIA_KEY)) {
            for (state in SessionState.entries) {
                assertEquals(Route.NONE, Routing.route(Input(source, 1_000), state, shellyWakes = true), "$source $state")
            }
        }
    }

    @Test
    fun `the journal words of the routes are fixed`() {
        assertEquals(
            listOf("start_count", "wake_count", "hold", "to_session", "none"),
            Route.entries.map { it.word },
        )
    }
}
