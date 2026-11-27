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
    private fun route(input: Input, wakes: Boolean = true, tap: TapProgram = TapProgram.REMOTE) =
        inputs.route(input, input.elapsedMs, wakes, tap, remoteEnrolled = true)

    /** What the router does for a route that starts a session which takes inputs. */
    private fun routeAndAsk(input: Input, wakes: Boolean = true): WaitingInputs.Routed =
        route(input, wakes).also { if (it.route in ASKING) inputs.asked(input.elapsedMs) }

    @Test
    fun `the press that woke the app is handed to the session it opened`() {
        val first = press(1_000)
        assertEquals(WaitingInputs.Routed(Route.WAKE_REMOTE, SessionState.NONE, emptyList()), routeAndAsk(first))
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
        // no second request: the press waits like the first
        assertEquals(Route.HOLD, routeAndAsk(second).route)
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
        assertEquals(Route.START_REMOTE, routeAndAsk(tap(1_000)).route)
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
        inputs.close()
        assertFalse(inputs.isOpen)
        assertEquals(SessionState.NONE, inputs.state(30_001))
        // a request made while a session was open does not count as opening afterwards
        inputs.open(40_000)
        inputs.asked(41_000)
        inputs.close()
        assertEquals(SessionState.NONE, inputs.state(42_001))
    }

    @Test
    fun `a busy answer says whether a program listens and how many presses wait`() {
        routeAndAsk(press(1_000))
        // the session being opened is the one that is busy: its program will fetch the press
        assertEquals(WaitingInputs.Busy(sessionTakesInput = false, waiting = 1), inputs.busy())
        inputs.open(2_000)
        assertEquals(WaitingInputs.Busy(sessionTakesInput = true, waiting = 0), inputs.busy())
    }

    @Test
    fun `a session that failed to open takes its presses with it`() {
        val wake = press(1_000)
        val during = press(1_800, button = 2)
        routeAndAsk(wake)
        routeAndAsk(during)
        // the hearing aid was not connected: the session service reports the end, no program ever listened
        assertEquals(WaitingInputs.Ended(dropped = listOf(wake, during), askFor = null), inputs.ended(7_000))
        assertEquals(0, inputs.waitingCount)
        assertEquals(SessionState.NONE, inputs.state(7_001))
        // so the next session, whatever opens it, does not announce presses made for the failed one
        routeAndAsk(tap(9_000))
        assertTrue(inputs.open(9_800).handOver.isEmpty())
    }

    @Test
    fun `a session Android refused ends at once and takes the press with it`() {
        val wake = press(1_000)
        routeAndAsk(wake)
        assertEquals(WaitingInputs.Ended(listOf(wake), null), inputs.ended(1_001))
        // the next press asks anew instead of waiting for a session that is not coming
        assertEquals(WaitingInputs.Routed(Route.WAKE_REMOTE, SessionState.NONE, emptyList()), routeAndAsk(press(1_500)))
    }

    @Test
    fun `a press during an echo test is given up when the echo test ends`() {
        route(tap(1_000), tap = TapProgram.ECHO)
        val during = press(4_000)
        // asked for, turned down as busy by the session service
        assertEquals(Route.WAKE_REMOTE, routeAndAsk(during).route)
        assertEquals(WaitingInputs.Busy(sessionTakesInput = false, waiting = 1), inputs.busy())
        assertEquals(WaitingInputs.Ended(listOf(during), null), inputs.ended(11_000))
    }

    @Test
    fun `a press right after the closing phrase opens the next session when the old one is gone`() {
        routeAndAsk(tap(1_000))
        inputs.open(2_000)
        inputs.close()
        // the service is still saying "Beende" and answers busy
        val first = press(20_300)
        val second = press(20_900, button = 2)
        assertEquals(Route.WAKE_REMOTE, routeAndAsk(first).route)
        assertEquals(WaitingInputs.Busy(sessionTakesInput = false, waiting = 1), inputs.busy())
        assertEquals(Route.HOLD, routeAndAsk(second).route)

        val ended = inputs.ended(21_500)
        assertEquals(WaitingInputs.Ended(dropped = emptyList(), askFor = first), ended)
        assertEquals(SessionState.OPENING, inputs.state(21_501), "the router asks again at once")
        assertEquals(listOf(first, second), inputs.open(22_300).handOver)
    }

    @Test
    fun `the session asked for after a closing one is not asked for twice`() {
        inputs.open(0)
        inputs.close()
        val press = press(20_300)
        routeAndAsk(press)
        assertEquals(press, inputs.ended(21_000).askFor)
        // that session fails to open: the press is given up, nobody asks a third time
        assertEquals(WaitingInputs.Ended(listOf(press), null), inputs.ended(27_500))
        assertEquals(WaitingInputs.Ended(emptyList(), null), inputs.ended(27_600))
    }

    @Test
    fun `nothing is asked for when no press came during the closing`() {
        routeAndAsk(tap(1_000))
        inputs.open(2_000)
        inputs.close()
        assertEquals(WaitingInputs.Ended(emptyList(), null), inputs.ended(33_000))
        assertEquals(SessionState.NONE, inputs.state(33_001))
    }

    @Test
    fun `a session that ends with its inbox still open is closed by the report`() {
        inputs.open(0)
        assertEquals(WaitingInputs.Ended(emptyList(), null), inputs.ended(5_000))
        assertFalse(inputs.isOpen)
        assertEquals(Route.WAKE_REMOTE, route(press(6_000)).route)
    }

    @Test
    fun `a press that waited too long during the closing is not asked for`() {
        inputs.open(0)
        inputs.close()
        val old = press(20_000)
        routeAndAsk(old)
        assertEquals(WaitingInputs.Ended(dropped = listOf(old), askFor = null), inputs.ended(20_000 + limit + 1))
    }

    @Test
    fun `a press that reaches the app too late opens nothing and does not wait`() {
        // the scan delivered it out of Doze, or into a process that had to be started first
        val late = press(1_000)
        val routed = inputs.route(late, 1_000 + limit + 1, shellyWakes = true, tap = TapProgram.REMOTE, remoteEnrolled = true)
        assertEquals(WaitingInputs.Routed(Route.TOO_LATE, SessionState.NONE, emptyList()), routed)
        assertEquals(0, inputs.waitingCount)
        assertEquals(SessionState.NONE, inputs.state(1_000 + limit + 2))
        // exactly at the limit it is still acted on, like a press that waits
        val inTime = press(50_000)
        assertEquals(
            Route.WAKE_REMOTE,
            inputs.route(inTime, 50_000 + limit, shellyWakes = true, tap = TapProgram.REMOTE, remoteEnrolled = true).route,
        )
    }

    @Test
    fun `a press that arrives too late does not join the session that is being opened`() {
        routeAndAsk(tap(30_000))
        val late = press(5_000)
        val routed = inputs.route(late, 30_500, shellyWakes = true, tap = TapProgram.REMOTE, remoteEnrolled = true)
        assertEquals(WaitingInputs.Routed(Route.TOO_LATE, SessionState.OPENING, emptyList()), routed)
        assertTrue(inputs.open(31_000).handOver.isEmpty())
    }

    @Test
    fun `a session that is open takes a press whatever its age`() {
        // nothing is opened for it, and the program says how long it was on its way (waitMs)
        inputs.open(0)
        val late = press(1_000)
        val routed = inputs.route(late, 1_000 + limit + 1, shellyWakes = true, tap = TapProgram.REMOTE, remoteEnrolled = true)
        assertEquals(Route.TO_SESSION, routed.route)
    }

    @Test
    fun `presses the router gives up are not kept for a later session`() {
        // a phone call is running: the router does not ask for the session
        val wake = press(1_000)
        assertEquals(Route.WAKE_REMOTE, route(wake).route)
        assertEquals(listOf(wake), inputs.giveUp())
        assertEquals(0, inputs.waitingCount)
        assertEquals(SessionState.NONE, inputs.state(1_001))
        assertTrue(inputs.giveUp().isEmpty(), "handed back once")
        // the next session, whatever opens it, does not announce the press
        routeAndAsk(tap(9_000))
        assertTrue(inputs.open(9_800).handOver.isEmpty())
    }

    @Test
    fun `giving up the session after a closing one leaves nothing opening`() {
        inputs.open(0)
        inputs.close()
        val first = press(20_300)
        val second = press(20_900, button = 2)
        routeAndAsk(first)
        routeAndAsk(second)
        assertEquals(first, inputs.ended(21_000).askFor)
        // the router finds a phone call running and does not ask
        assertEquals(listOf(first, second), inputs.giveUp())
        assertEquals(SessionState.NONE, inputs.state(21_001))
        // a press after the call asks anew
        assertEquals(WaitingInputs.Routed(Route.WAKE_REMOTE, SessionState.NONE, emptyList()), routeAndAsk(press(90_000)))
    }

    @Test
    fun `the wait limit is a parameter`() {
        val quick = WaitingInputs(waitLimitMs = 1_000)
        val press = press(0)
        quick.route(press, 0, shellyWakes = true, tap = TapProgram.ECHO, remoteEnrolled = true)
        quick.asked(0)
        assertEquals(SessionState.OPENING, quick.state(999))
        assertEquals(SessionState.NONE, quick.state(1_000))
        assertEquals(listOf(press), quick.takeStale(1_001))
        assertNull(quick.ended(1_002).askFor)
    }

    private companion object {
        val ASKING = setOf(Route.WAKE_REMOTE, Route.START_REMOTE, Route.START_COUNT)
    }
}
