package io.github.domicc.lara.core.stats

/**
 * How the screen words the time the two sound channels to the hearing aid were open today.
 * Here and not in the screen's code, so that the wording is tested like the numbers.
 */
object ChannelTexts {

    /** "14 × · 1 min 32 s (App 41 s, anderes 51 s)": the media stream, and how much of it was this app's. */
    fun media(stats: DayStats): String =
        line(stats.mediaOpenings, "App", stats.mediaAnnouncingMs, stats.mediaOtherMs, stats.mediaUnknown)

    /** "3 × · 1 min 48 s (Sitzungen 1 min 48 s, anderes 0 s)": the voice link, sessions apart from phone calls. */
    fun voice(stats: DayStats): String =
        line(stats.voiceOpenings, "Sitzungen", stats.voiceSessionMs, stats.voiceOtherMs, stats.voiceUnknown)

    /** "Kanäle 3 min 20 s": both channels together, for the folded section. */
    fun summary(stats: DayStats): String = "Kanäle " + words(
        seconds(stats.mediaAnnouncingMs) + seconds(stats.mediaOtherMs) + seconds(stats.voiceSessionMs) + seconds(stats.voiceOtherMs),
    )

    /**
     * "41 s", "1 min 32 s", "2 h 5 min": rounded to the second, and from an hour on to the
     * minute below.
     */
    fun duration(ms: Long): String = words(seconds(ms))

    /** Rounded to the second; a length below zero is none. */
    private fun seconds(ms: Long): Long = (maxOf(ms, 0) + 500) / 1000

    private fun words(seconds: Long): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
        else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
    }

    /**
     * The two parts are rounded each for itself and the total is their sum, not the rounded
     * sum of the milliseconds: the three numbers stand next to each other and have to add up.
     */
    private fun line(openings: Int, ownWord: String, ownMs: Long, otherMs: Long, unknown: Int): String {
        val own = seconds(ownMs)
        val other = seconds(otherMs)
        return "$openings × · ${words(own + other)} ($ownWord ${words(own)}, anderes ${words(other)})" +
            // the sums are too small by these, so they are named
            if (unknown > 0) " · $unknown × ohne Dauer" else ""
    }
}
