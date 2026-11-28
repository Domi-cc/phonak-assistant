package io.github.domicc.lara.core.calendar

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarTextsTest {

    private val zone = ZoneId.of("Europe/Berlin")

    /** A moment on 28 November 2026 ([day] 28) or a day near it, on the clocks of [zone]. */
    private fun at(hour: Int, minute: Int = 0, day: Int = 28): Long =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `a title is trimmed and its white space collapsed`() {
        assertEquals("Zahnarzt Dr. Müller", CalendarTexts.cleanTitle("  Zahnarzt \t Dr.\n\nMüller "))
        // a no-break space and a line separator, as a pasted title may carry them
        assertEquals("Team Runde", CalendarTexts.cleanTitle("Team  Runde"))
        assertEquals("Zahnarzt", CalendarTexts.cleanTitle("Zahnarzt"))
    }

    @Test
    fun `an empty or missing title becomes Termin`() {
        assertEquals("Termin", CalendarTexts.cleanTitle(null))
        assertEquals("Termin", CalendarTexts.cleanTitle(""))
        assertEquals("Termin", CalendarTexts.cleanTitle(" \t\n"))
        assertEquals("Termin", CalendarTexts.UNTITLED)
    }

    @Test
    fun `a title with nothing in it to say becomes Termin`() {
        // a zero-width space, as an invitation may carry it, and titles of punctuation only
        assertEquals("Termin", CalendarTexts.cleanTitle("\u200B"))
        assertEquals("Termin", CalendarTexts.cleanTitle(" \uFEFF "))
        assertEquals("Termin", CalendarTexts.cleanTitle("???"))
        assertEquals("Termin", CalendarTexts.cleanTitle("- / -"))
        // a letter or a digit is enough, and so is a pictogram: the speech engine names those
        assertEquals("?!a", CalendarTexts.cleanTitle("?!a"))
        assertEquals("1:1", CalendarTexts.cleanTitle("1:1"))
        assertEquals("🎂", CalendarTexts.cleanTitle(" 🎂 "))
        assertEquals("☎", CalendarTexts.cleanTitle("☎"))
    }

    @Test
    fun `a long title is cut to 80 characters`() {
        assertEquals(80, CalendarTexts.MAX_TITLE)
        assertEquals("x".repeat(80), CalendarTexts.cleanTitle("x".repeat(200)))
        assertEquals("x".repeat(80), CalendarTexts.cleanTitle("x".repeat(80)))
        // in the middle of a word
        assertEquals("Abstimmung ".repeat(7) + "Abs", CalendarTexts.cleanTitle("Abstimmung ".repeat(20)))
        // behind a blank, which is dropped as well
        assertEquals("Protokoll ".repeat(8).trim(), CalendarTexts.cleanTitle("Protokoll ".repeat(20)))
    }

    @Test
    fun `the cut never leaves half a character`() {
        // an emoji takes two places; the 80th and 81st here
        val cut = CalendarTexts.cleanTitle("x".repeat(79) + "🎉" + "y")
        assertEquals("x".repeat(79), cut)
        assertFalse(cut.last().isHighSurrogate())
    }

    @Test
    fun `titles are cleaned and said once each, in their order`() {
        assertEquals(listOf("B", "A", "Termin"), CalendarTexts.titles(listOf(" B", "A", "B ", null, "", "A")))
        assertEquals(emptyList(), CalendarTexts.titles(emptyList()))
    }

    @Test
    fun `titles that differ only in upper and lower case are said once, as the first one is written`() {
        assertEquals(listOf("Zahnarzt", "Bank"), CalendarTexts.titles(listOf("Zahnarzt", "zahnarzt", "Bank", "ZAHNARZT ")))
    }

    @Test
    fun `titles are joined the way they are spoken`() {
        assertEquals("A", CalendarTexts.join(listOf("A")))
        assertEquals("A und B", CalendarTexts.join(listOf("A", "B")))
        assertEquals("A, B und C", CalendarTexts.join(listOf("A", "B", "C")))
        assertEquals("Termin", CalendarTexts.join(emptyList()))
    }

    @Test
    fun `more than three titles are not all read out`() {
        assertEquals(3, CalendarTexts.MAX_TITLES)
        assertEquals("A, B, C und ein weiterer", CalendarTexts.join(listOf("A", "B", "C", "D")))
        assertEquals("A, B, C und 2 weitere", CalendarTexts.join(listOf("A", "B", "C", "D", "E")))
        assertEquals(
            "In 10 Minuten: A, B, C und 4 weitere",
            CalendarTexts.reminder(600_000, listOf("A", "B", "C", "D", "E", "F", "G")),
        )
    }

    @Test
    fun `the remaining minutes are rounded and under a minute there are none`() {
        assertEquals(10, CalendarTexts.minutes(600_000))
        assertEquals(10, CalendarTexts.minutes(570_000))
        assertEquals(9, CalendarTexts.minutes(569_999))
        assertEquals(2, CalendarTexts.minutes(90_000))
        assertEquals(1, CalendarTexts.minutes(89_999))
        assertEquals(1, CalendarTexts.minutes(60_000))
        assertEquals(0, CalendarTexts.minutes(59_999))
        assertEquals(0, CalendarTexts.minutes(0))
        assertEquals(0, CalendarTexts.minutes(-5_000))
    }

    @Test
    fun `the reminder line`() {
        assertEquals("In 10 Minuten: Zahnarzt", CalendarTexts.reminder(600_000, listOf("Zahnarzt")))
        assertEquals("In 3 Minuten: Zahnarzt und Bank", CalendarTexts.reminder(180_000, listOf("Zahnarzt", "Bank")))
        assertEquals("In einer Minute: Zahnarzt", CalendarTexts.reminder(60_000, listOf("Zahnarzt")))
        assertEquals("Jetzt: Zahnarzt", CalendarTexts.reminder(20_000, listOf("Zahnarzt")))
    }

    @Test
    fun `the rehearsal is the reminder as it sounds ten minutes before the appointment`() {
        assertEquals("In 10 Minuten: Zahnarzt", CalendarTexts.rehearsal(listOf("Zahnarzt")))
        assertEquals("In 10 Minuten: Zahnarzt und Bank", CalendarTexts.rehearsal(listOf("Zahnarzt", "Bank")))
        assertEquals(CalendarTexts.reminder(ReminderPlanner.LEAD_MS, listOf("Zahnarzt")), CalendarTexts.rehearsal(listOf("Zahnarzt")))
    }

    @Test
    fun `without an appointment the rehearsal speaks a sample title`() {
        assertEquals("Beispieltermin", CalendarTexts.SAMPLE_TITLE)
        assertEquals("In 10 Minuten: Beispieltermin", CalendarTexts.rehearsal(null))
        // an appointment without anything to name is no reason to stay silent either
        assertEquals("In 10 Minuten: Beispieltermin", CalendarTexts.rehearsal(emptyList()))
    }

    @Test
    fun `the line of the test button says the time like the hourly announcement does`() {
        assertEquals("Nächster Termin um 14 Uhr 30: Zahnarzt", CalendarTexts.nextEvent(at(13), at(14, 30), listOf("Zahnarzt"), zone))
        assertEquals("Nächster Termin um 9 Uhr: Zahnarzt", CalendarTexts.nextEvent(at(8, 59), at(9), listOf("Zahnarzt"), zone))
        assertEquals("Nächster Termin um 0 Uhr 5: Zahnarzt", CalendarTexts.nextEvent(at(0, 1), at(0, 5), listOf("Zahnarzt"), zone))
    }

    @Test
    fun `the line of the test button says when the event is on the next day`() {
        assertEquals(
            "Nächster Termin morgen um 9 Uhr: Zahnarzt",
            CalendarTexts.nextEvent(at(18), at(9, day = 29), listOf("Zahnarzt"), zone),
        )
        // five minutes ahead, but past midnight
        assertEquals(
            "Nächster Termin morgen um 0 Uhr 2: Zahnarzt",
            CalendarTexts.nextEvent(at(23, 57), at(0, 2, day = 29), listOf("Zahnarzt"), zone),
        )
    }

    @Test
    fun `the day after tomorrow is not called tomorrow`() {
        // The night the clocks go forward: Sunday has 23 hours, so 24 hours from Saturday
        // 23:30 reach into Monday.
        val saturday = ZonedDateTime.of(2027, 3, 27, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val monday = ZonedDateTime.of(2027, 3, 29, 0, 15, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(monday - saturday < 24 * 3_600_000L)
        assertEquals("Nächster Termin übermorgen um 0 Uhr 15: Zahnarzt", CalendarTexts.nextEvent(saturday, monday, listOf("Zahnarzt"), zone))
        assertEquals("übermorgen 00:15", CalendarTexts.clock(saturday, monday, zone))

        // The screen shows the begin of the next reminder, which may lie ten minutes
        // further ahead than a day: Monday 23:55, an appointment on Wednesday at 00:03.
        assertEquals("übermorgen 00:03", CalendarTexts.clock(at(23, 55, day = 23), at(0, 3, day = 25), zone))
        assertEquals("morgen 23:45", CalendarTexts.clock(at(23, 55, day = 23), at(23, 45, day = 24), zone))
    }

    @Test
    fun `a time that has passed is shown without a day`() {
        // a reminder that waits for the end of a session was due a moment ago, also across midnight
        assertEquals("23:58", CalendarTexts.clock(at(0, 1, day = 29), at(23, 58), zone))
        assertEquals("13:50", CalendarTexts.clock(at(13, 55), at(13, 50), zone))
    }

    @Test
    fun `several events at that time are named together`() {
        assertEquals(
            "Nächste Termine um 14 Uhr 30: Zahnarzt und Bank",
            CalendarTexts.nextEvent(at(13), at(14, 30), listOf("Zahnarzt", "Bank"), zone),
        )
    }

    @Test
    fun `the line for no event at all`() {
        assertEquals("Kein Termin in den nächsten 24 Stunden", CalendarTexts.NO_EVENT)
    }

    @Test
    fun `the time on the screen names the next day`() {
        assertEquals("13:50", CalendarTexts.clock(at(13), at(13, 50), zone))
        assertEquals("09:05", CalendarTexts.clock(at(8), at(9, 5), zone))
        assertEquals("morgen 08:50", CalendarTexts.clock(at(18), at(8, 50, day = 29), zone))
        assertEquals("morgen 00:02", CalendarTexts.clock(at(23, 57), at(0, 2, day = 29), zone))
    }

    @Test
    fun `the day is the one of the phone's time zone`() {
        // 23:30 in Berlin is 22:30 in UTC: an event at 00:15 Berlin time is tomorrow there, today in UTC
        val now = at(23, 30)
        val begin = at(0, 15, day = 29)
        assertEquals("morgen 00:15", CalendarTexts.clock(now, begin, zone))
        assertEquals("23:15", CalendarTexts.clock(now, begin, ZoneId.of("UTC")))
    }
}
