package io.github.domicc.lara.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals

class ScanStartBudgetTest {

    private val budget = ScanStartBudget(maxStarts = 4, windowMs = 30_000)

    @Test
    fun `starts are free until the budget is used up`() {
        for (i in 0 until 4) {
            assertEquals(0, budget.waitMs(1_000L + i))
            budget.record(1_000L + i)
        }
        assertEquals(30_000 - 10, budget.waitMs(1_010), "until the oldest start leaves the window")
    }

    @Test
    fun `a start is free again when the oldest one has left the window`() {
        for (at in listOf(1_000L, 5_000L, 9_000L, 13_000L)) budget.record(at)
        assertEquals(1, budget.waitMs(30_999))
        assertEquals(0, budget.waitMs(31_000))
        budget.record(31_000)
        assertEquals(4_000, budget.waitMs(31_000), "now the start at 5 s is the oldest")
    }

    @Test
    fun `starts recorded without asking count as well`() {
        // a scan that needs no permission to start (the rescue scan) still uses up the budget
        for (i in 0 until 6) budget.record(2_000L + i * 100)
        assertEquals(30_000 - 800 + 200, budget.waitMs(2_800), "the fourth newest start decides")
    }

    @Test
    fun `sessions in a row never wait for their session mode`() {
        // The worst case for the default budget: every session is over at once (the hearing
        // aid is not connected), the scan goes back to idle as soon as the linger allows, and
        // the next session opens right after that. Two scan starts per session.
        val default = ScanStartBudget()
        var now = 0L
        repeat(20) { session ->
            assertEquals(0, default.waitMs(now), "session $session has to wait for its scan mode")
            default.record(now)
            // back to idle; this start may wait, that only keeps the session mode a little longer
            now += ScanConfig.SESSION_LINGER_MS
            now += default.waitMs(now)
            default.record(now)
            now += 1
        }
    }

    @Test
    fun `without the linger the third session in a row would wait`() {
        // what the linger is for: session mode and back for every session, a few seconds apart
        val default = ScanStartBudget()
        for (at in listOf(0L, 8_000L, 10_000L, 18_000L)) default.record(at)
        assertEquals(10_000, default.waitMs(20_000))
    }

    @Test
    fun `nothing is owed after a long pause`() {
        for (i in 0 until 10) budget.record(i * 10L)
        assertEquals(0, budget.waitMs(3_600_000))
    }
}
