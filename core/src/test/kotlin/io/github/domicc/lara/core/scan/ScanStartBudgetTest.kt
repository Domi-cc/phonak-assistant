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
    fun `nothing is owed after a long pause`() {
        for (i in 0 until 10) budget.record(i * 10L)
        assertEquals(0, budget.waitMs(3_600_000))
    }
}
