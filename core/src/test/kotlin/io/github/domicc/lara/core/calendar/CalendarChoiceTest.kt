package io.github.domicc.lara.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarChoiceTest {

    @Test
    fun `a calendar the owner never touched follows the calendar app`() {
        val untouched = CalendarChoice()
        assertTrue(untouched.selects(1, visible = true))
        assertFalse(untouched.selects(2, visible = false))
    }

    @Test
    fun `a choice made by hand wins over what the calendar app shows`() {
        val choice = CalendarChoice(on = setOf(2L), off = setOf(1L))
        assertFalse(choice.selects(1, visible = true))
        assertTrue(choice.selects(2, visible = false))
        // and it stays when the calendar app changes its mind
        assertFalse(choice.selects(1, visible = false))
        assertTrue(choice.selects(2, visible = true))
    }

    @Test
    fun `ticking and unticking moves a calendar between the two sets`() {
        val ticked = CalendarChoice().with(5, selected = true)
        assertEquals(CalendarChoice(on = setOf(5L), off = emptySet()), ticked)
        val unticked = ticked.with(5, selected = false)
        assertEquals(CalendarChoice(on = emptySet(), off = setOf(5L)), unticked)
        assertEquals(ticked, unticked.with(5, selected = true))
    }

    @Test
    fun `a choice for one calendar leaves the others alone`() {
        val choice = CalendarChoice(on = setOf(1L), off = setOf(2L)).with(3, selected = false)
        assertEquals(CalendarChoice(on = setOf(1L), off = setOf(2L, 3L)), choice)
        // a calendar that appears later is still decided by the calendar app
        assertTrue(choice.selects(9, visible = true))
        assertFalse(choice.selects(9, visible = false))
    }

    @Test
    fun `a choice for a calendar that is gone from the phone is forgotten`() {
        val choice = CalendarChoice(on = setOf(2L, 8L), off = setOf(1L, 7L))
        assertEquals(CalendarChoice(on = setOf(2L), off = setOf(1L)), choice.onlyFor(setOf(1L, 2L, 3L)))
        // Android gives the number of a removed calendar to the next new one: that one is
        // a calendar the owner never touched, and the calendar app decides about it again
        val after = choice.onlyFor(setOf(1L, 2L, 3L))
        assertTrue(after.selects(7, visible = true))
        assertFalse(after.selects(8, visible = false))
    }

    @Test
    fun `a choice survives while its calendar is on the phone`() {
        val choice = CalendarChoice(on = setOf(2L), off = setOf(1L))
        assertEquals(choice, choice.onlyFor(setOf(1L, 2L, 3L)))
        assertEquals(CalendarChoice(), choice.onlyFor(emptySet()))
        assertEquals(CalendarChoice(), CalendarChoice().onlyFor(setOf(1L)))
    }

    @Test
    fun `the calendars that are announced`() {
        val choice = CalendarChoice(on = setOf(2L), off = setOf(1L))
        val visible = mapOf(1L to true, 2L to false, 3L to true, 4L to false)
        assertEquals(setOf(2L, 3L), choice.selected(visible))
        assertEquals(emptySet(), choice.selected(emptyMap()))
    }
}
