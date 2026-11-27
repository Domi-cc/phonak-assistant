package io.github.domicc.lara.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaitingInputsTest {

    private val inputs = WaitingInputs()
    private val limit = WaitingInputs.WAIT_LIMIT_MS

    private fun press(atMs: Long, button: Int = 1) =
        Input(InputSource.SHELLY, atMs, button, Gesture.PRESS, InputPath.BLE_CALLBACK)

    private fun tap(atMs: Long) = Input(InputSource.HEARING_AID_TAP, atMs)

    /** Routes [input] at the moment it was made, with "Shelly weckt auf" as given. */
    private fun route(input: Input, wakes: Boolean = true, tap: TapProgram = TapProgram.COUNT) =
        inputs.route(input, input.elapsedMs, wakes, tap)

    /** What the router does for a route that starts a count session. */
    private fun routeAndAsk(input: Input, wakes: Boolean = true): WaitingInputs.Routed =
        route(input, wakes).also { if (it.route == Route.WAKE_COUNT || it.route == Route.START_COUNT) inputs.asked(input.elapsedMs) }

    @Test
    fun `the press that woke the app is handed to the session it opened`() {
        val first = press(1_000)
        assertEquals(WaitingInputs.Routed(Route.WAKE_COUNT, SessionState.NONE, emptyList()), routeAndAsk(first))
        assertEquals(1, inputs.waitingCount)
        assertEquals(SessionState.OPENING, inputs.state(1_500))

        assertEquals(WaitingInputs.Opened(listOf(first), emptyList()), inputs.open(2_200))
        assertEquals(0, inputs.waitingCount)
        assertEquals(SessionState.OPEN, inputs.state(2_200))
    }

    @Test
    fun `presses that arrive while the session opens wait in their order`() {
        val first = press(1_000)
        val second = press(1_600, button = 2)
        routeAndAsk(first)
        // asked again, turned down as busy by the service: the press waits like the first
        assertEquals(Route.WAKE_COUNT, routeAndAsk(second).route)
        assertEquals(listOf(first, second), inputs.open(2_500).handOver)
    }

    @Test
    fun `a press during a session is not kept here`() {
        inputs.open(0)
        val routed = route(press(5_000))
        assertEquals(Route.TO_SESSION, routed.route)
        assertEquals(SessionState.OPEN, routed.state)
        assertEquals(0, inputs.waitingCount)
    }

    @Test
    fun `a tap opens a session without waiting in it`() {
        assertEquals(Route.START_COUNT, routeAndAsk(tap(1_000)).route)
        assertEquals(0, inputs.waitingCount)
        assertTrue(inputs.open(2_000).handOver.isEmpty())
        // the second tap ends the session
        assertEquals(Route.TO_SESSION, route(tap(9_000)).route)
    }

    @Test
    fun `a tap starts the echo test if that is what the owner chose`() {
        val routed = route(tap(1_000), tap = TapProgram.ECHO)
        assertEquals(Route.START_ECHO, routed.route)
        // an echo test takes no inputs: nothing is being opened as far as presses are concerned
        assertEquals(SessionState.NONE, inputs.state(1_100))
    }

    @Test
    fun `in the silent mode a press waits only for a session a tap is opening`() {
        assertEquals(Route.NONE, route(press(500), wakes = false).route)
        assertEquals(0, inputs.waitingCount)

        routeAndAsk(tap(1_000), wakes = false)
        val during = press(1_400)
        assertEquals(WaitingInputs.Routed(Route.HOLD, SessionState.OPENING, emptyList()), route(during, wakes = false))
        assertEquals(listOf(during), inputs.open(2_000).handOver)
    }

    @Test
    fun `a press whose session did not come is handed back as stale with the next input`() {
        val lost = press(1_000)
        routeAndAsk(lost)
        // exactly at the limit it still waits
        assertTrue(inputs.takeStale(1_000 + limit).isEmpty())
        val next = press(1_000 + limit + 1)
        val routed = routeAndAsk(next)
        assertEquals(listOf(lost), routed.stale)
        assertEquals(SessionState.NONE, routed.state, "the request of the lost press is too old to count as opening")
        assertEquals(1, inputs.waitingCount)
    }

    @Test
    fun `a stale press is not handed to a session that opens later`() {
        val old = press(1_000)
        val fresh = press(15_000)
        routeAndAsk(old)
        routeAndAsk(fresh)
        val opened = inputs.open(1_000 + limit + 500)
        assertEquals(listOf(old), opened.stale)
        assertEquals(listOf(fresh), opened.handOver)
    }

    @Test
    fun `stale presses can be collected without a new input`() {
        val lost = press(1_000)
        routeAndAsk(lost)
        assertEquals(listOf(lost), inputs.takeStale(1_000 + limit + 100))
        assertTrue(inputs.takeStale(1_000 + limit + 200).isEmpty(), "handed back once")
    }

    @Test
    fun `opening ends when the session opens, and nothing is opening after it has closed`() {
        routeAndAsk(tap(1_000))
        inputs.open(2_000)
        inputs.close(30_000)
        assertFalse(inputs.isOpen)
        assertEquals(SessionState.NONE, inputs.state(30_001))
        // a request made while a session was open does not count as opening afterwards
        inputs.open(40_000)
        inputs.asked(41_000)
        inputs.close(42_000)
        assertEquals(SessionState.NONE, inputs.state(42_001))
    }

    @Test
    fun `a busy answer is only reported while a session is opening or runs`() {
        routeAndAsk(press(1_000))
        // the session being opened is the one that is busy: its program will fetch the press
        assertEquals(WaitingInputs.Busy(sessionTakesInput = false, waiting = 1, askAgainInMs = null), inputs.busy(1_100))
        inputs.open(2_000)
        assertEquals(WaitingInputs.Busy(sessionTakesInput = true, waiting = 0, askAgainInMs = null), inputs.busy(3_000))
    }

    @Test
    fun `a press right after the closing tone asks again until the old session is gone`() {
        inputs.open(0)
        inputs.close(20_000)
        // the service is still playing the closing tone and answers busy
        val press = press(20_300)
        assertEquals(Route.WAKE_COUNT, routeAndAsk(press).route)
        val busy = inputs.busy(20_310)
        assertEquals(WaitingInputs.RETRY_MS, busy.askAgainInMs)
        // a second busy answer in the meantime starts no second chain
        assertNull(inputs.busy(20_320).askAgainInMs)

        assertEquals(press, inputs.askAgain())
        inputs.asked(20_710)
        // busy once more, then the new session opens and takes the press
        assertEquals(WaitingInputs.RETRY_MS, inputs.busy(20_720).askAgainInMs)
        assertEquals(press, inputs.askAgain())
        inputs.asked(21_120)
        assertEquals(listOf(press), inputs.open(22_000).handOver)
    }

    @Test
    fun `nobody asks again once the session has opened or the press is gone`() {
        inputs.open(0)
        inputs.close(20_000)
        routeAndAsk(press(20_300))
        assertEquals(WaitingInputs.RETRY_MS, inputs.busy(20_310).askAgainInMs)
        inputs.open(20_600)
        assertNull(inputs.askAgain())

        inputs.close(50_000)
        val lost = press(50_100)
        routeAndAsk(lost)
        assertEquals(WaitingInputs.RETRY_MS, inputs.busy(50_110).askAgainInMs)
        assertEquals(listOf(lost), inputs.takeStale(50_100 + limit + 1))
        assertNull(inputs.askAgain())
    }

    @Test
    fun `asking again stops when the closing time is over`() {
        inputs.open(0)
        inputs.close(20_000)
        routeAndAsk(press(20_000 + WaitingInputs.CLOSING_MS - 100))
        assertEquals(WaitingInputs.RETRY_MS, inputs.busy(20_000 + WaitingInputs.CLOSING_MS - 90).askAgainInMs)
        inputs.askAgain()
        // whatever is busy now is not the session that closed
        assertNull(inputs.busy(20_000 + WaitingInputs.CLOSING_MS).askAgainInMs)
    }

    @Test
    fun `the limits are parameters`() {
        val quick = WaitingInputs(waitLimitMs = 1_000, closingMs = 500, retryMs = 50)
        val press = press(0)
        quick.route(press, 0, shellyWakes = true, tap = TapProgram.ECHO)
        quick.asked(0)
        assertEquals(SessionState.OPENING, quick.state(999))
        assertEquals(SessionState.NONE, quick.state(1_000))
        assertEquals(listOf(press), quick.takeStale(1_001))
        quick.open(2_000)
        quick.close(3_000)
        quick.route(press(3_100), 3_100, shellyWakes = true, tap = TapProgram.ECHO)
        assertEquals(50, quick.busy(3_499).askAgainInMs)
        quick.askAgain()
        assertNull(quick.busy(3_500).askAgainInMs)
    }
}
