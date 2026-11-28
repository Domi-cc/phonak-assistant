package io.github.domicc.lara.core.calendar

/**
 * Which calendars of the phone are announced. Only what the owner chose by hand is kept: a
 * calendar he never touched is announced if the calendar app shows it. So a calendar that is
 * added to the phone later behaves like it does in the calendar app, without anyone having to
 * open this app first.
 *
 * @param on the ids of the calendars switched on by hand
 * @param off the ids of the calendars switched off by hand
 */
data class CalendarChoice(val on: Set<Long> = emptySet(), val off: Set<Long> = emptySet()) {

    /**
     * @param visible whether the calendar app shows this calendar
     */
    fun selects(calendarId: Long, visible: Boolean): Boolean = when (calendarId) {
        in on -> true
        in off -> false
        else -> visible
    }

    /** This choice after the owner has ticked ([selected]) or unticked the calendar. */
    fun with(calendarId: Long, selected: Boolean): CalendarChoice =
        if (selected) CalendarChoice(on + calendarId, off - calendarId) else CalendarChoice(on - calendarId, off + calendarId)

    /**
     * This choice without the calendars that are no longer on the phone. Android numbers a
     * new calendar one above the highest number in use, so the number of a calendar that was
     * removed can come back with another one. A choice made for the old calendar must not
     * be waiting for it there: the new one is a calendar the owner never touched.
     *
     * @param present the ids of every calendar on the phone
     */
    fun onlyFor(present: Set<Long>): CalendarChoice = CalendarChoice(on intersect present, off intersect present)

    /**
     * The ids of the calendars that are announced.
     *
     * @param visible every calendar on the phone by id, with whether the calendar app shows it
     */
    fun selected(visible: Map<Long, Boolean>): Set<Long> =
        visible.filter { (id, shown) -> selects(id, shown) }.keys
}
