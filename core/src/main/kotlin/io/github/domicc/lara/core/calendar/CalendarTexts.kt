package io.github.domicc.lara.core.calendar

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * What the app says about appointments, and how it shows a time on the screen. These lines go
 * to the speech engine as they are; they are never written to the journal.
 */
object CalendarTexts {

    /** What an appointment without a title is called. */
    const val UNTITLED = "Termin"

    /** A title is cut to this many characters: a reminder is a hint, not a reading. */
    const val MAX_TITLE = 80

    /**
     * Of appointments that begin together this many are named, the rest is counted. An
     * announcement must stay well inside the time the announcer gives one.
     */
    const val MAX_TITLES = 3

    /** The line of the test button when the next day holds nothing. */
    const val NO_EVENT = "Kein Termin in den nächsten 24 Stunden"

    /** Blanks, line breaks, tabs, control characters and the blanks of other scripts, in a row. */
    private val gaps = Regex("[\\s\\p{Z}\\p{Cc}]+")

    /**
     * The title as it is spoken and shown: without white space around it, with one blank
     * where there were several or a line break, cut to [MAX_TITLE] characters; [UNTITLED] for
     * a title that is missing, empty or has nothing in it to say.
     */
    fun cleanTitle(raw: String?): String {
        val clean = raw.orEmpty().replace(gaps, " ").trim()
        if (!hasSomethingToSay(clean)) return UNTITLED
        if (clean.length <= MAX_TITLE) return clean
        var cut = clean.take(MAX_TITLE)
        // half of a character that takes two places (an emoji) would be read as rubbish
        if (cut.last().isHighSurrogate()) cut = cut.dropLast(1)
        return cut.trimEnd()
    }

    /**
     * Whether a speech engine finds anything to speak in [title]: a letter, a digit or a
     * pictogram. Punctuation alone and characters without a shape (a zero-width space) are
     * passed over in silence, and "In 10 Minuten:" with nothing after it helps nobody.
     */
    private fun hasSomethingToSay(title: String): Boolean =
        title.codePoints().anyMatch { Character.isLetterOrDigit(it) || Character.getType(it) == Character.OTHER_SYMBOL.toInt() }

    /**
     * The titles of appointments that begin together: cleaned, each one once, in their order.
     * Upper and lower case make no difference to the ear: the same appointment in two
     * calendars is often written both ways.
     */
    fun titles(raw: List<String?>): List<String> = raw.map(::cleanTitle).distinctBy { it.lowercase() }

    /** "A", "A und B", "A, B und C", and from the fourth on "A, B, C und 2 weitere". */
    fun join(titles: List<String>): String {
        val more = titles.size - MAX_TITLES
        return when {
            titles.isEmpty() -> UNTITLED
            titles.size == 1 -> titles[0]
            more == 1 -> titles.take(MAX_TITLES).joinToString(", ") + " und ein weiterer"
            more > 1 -> titles.take(MAX_TITLES).joinToString(", ") + " und $more weitere"
            else -> titles.dropLast(1).joinToString(", ") + " und " + titles.last()
        }
    }

    /**
     * The minutes a reminder names: what is really left, rounded to the nearest minute, and
     * none at all under one minute. By the time the chime and the first words are through,
     * "in einer Minute" would be wrong then.
     */
    fun minutes(remainingMs: Long): Int =
        if (remainingMs < MINUTE_MS) 0 else ((remainingMs + MINUTE_MS / 2) / MINUTE_MS).toInt()

    /**
     * The reminder: "In 10 Minuten: Zahnarzt", "In einer Minute: Zahnarzt", "Jetzt: Zahnarzt".
     *
     * @param remainingMs from the moment of speaking to the begin of the appointment
     * @param titles cleaned, see [titles]
     */
    fun reminder(remainingMs: Long, titles: List<String>): String {
        val lead = when (val minutes = minutes(remainingMs)) {
            0 -> "Jetzt"
            1 -> "In einer Minute"
            else -> "In $minutes Minuten"
        }
        return "$lead: ${join(titles)}"
    }

    /**
     * The line of the test button: "Nächster Termin um 14 Uhr 30: Zahnarzt", with "morgen"
     * for an appointment on the next day. The time is said like the hourly announcement says it.
     *
     * @param titles cleaned, see [titles]
     * @param zone the phone's time zone: it decides what "morgen" is
     */
    fun nextEvent(nowMs: Long, beginMs: Long, titles: List<String>, zone: ZoneId): String {
        val begin = local(beginMs, zone)
        val time = if (begin.minute == 0) "${begin.hour} Uhr" else "${begin.hour} Uhr ${begin.minute}"
        val what = if (titles.size > 1) "Nächste Termine" else "Nächster Termin"
        return "$what${day(nowMs, begin, zone)?.let { " $it" }.orEmpty()} um $time: ${join(titles)}"
    }

    /** A time for the screen: "13:50", or "morgen 08:50" on the next day. */
    fun clock(nowMs: Long, atMs: Long, zone: ZoneId): String {
        val at = local(atMs, zone)
        val time = at.hour.toString().padStart(2, '0') + ":" + at.minute.toString().padStart(2, '0')
        return day(nowMs, at, zone)?.let { "$it $time" } ?: time
    }

    private fun local(ms: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    /**
     * The word for the day of [at] if it is not today. What the app plans lies within the
     * next 24 hours and ten minutes, and that can reach the day after tomorrow: from just
     * before midnight, and on the night the clocks go forward.
     */
    private fun day(nowMs: Long, at: ZonedDateTime, zone: ZoneId): String? =
        when (ChronoUnit.DAYS.between(local(nowMs, zone).toLocalDate(), at.toLocalDate())) {
            1L -> "morgen"
            2L -> "übermorgen"
            else -> null
        }

    private const val MINUTE_MS = 60_000L
}
