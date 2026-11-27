package io.github.domicc.lara.core.scan

/**
 * How much of the time the phone listens for advertisements, in Android's three steps. A mode
 * is a request: what the radio really does also depends on the display, on the app's standing
 * and on the hourly scan allowance. The numbers below are read from the Android 17 source for
 * an app that is not on the screen; measuring them is what this app is for.
 *
 * @param word the name used in the preferences, in the journal and in commands
 */
enum class ScanMode(val word: String) {
    /** The allowance never runs out, but with the display off it means half a second in ten. */
    LOW_POWER("low_power"),

    /** About a quarter of the time; the allowance covers roughly 24 minutes per hour. */
    BALANCED("balanced"),

    /** All the time; the allowance covers roughly 6 minutes per hour. */
    LOW_LATENCY("low_latency");

    companion object {
        fun fromWord(word: String?): ScanMode? = entries.firstOrNull { it.word == word }
    }
}

/**
 * The scan settings the owner can change, so that each of them can be measured against the
 * others. The defaults spend allowance only while a session listens to the remote, which is
 * when a missed press is noticed at once.
 *
 * @param idle the mode while no session listens to the remote
 * @param session the mode while a remote session or a count session is open, and for a few
 * seconds after it
 * @param passive hand over every advertisement at once (true), or only together with the
 * answer to Android's scan request (false, Android's default). Users of this remote report
 * that most presses get lost the second way; the switch exists to measure exactly that.
 * @param rescue also register the second scan that delivers through a broadcast and so reaches
 * the app when its process is gone. Off by default: it draws on the same allowance.
 * @param refreshMinutes how often the scans are registered anew as a precaution; 0 for never.
 * Off by default, because it is part of what is measured: registering anew means stopping the
 * running scan, and in the Android 17.0 source a scan that is stopped before its accounting
 * job has fired (after 24 minutes in the balanced mode) books no allowance at all. A refresh
 * every few minutes would therefore keep a scan from ever being throttled, and the alarm
 * behind it wakes a dozing phone. Results are only comparable at the same value.
 */
data class ScanConfig(
    val idle: ScanMode = ScanMode.LOW_POWER,
    val session: ScanMode = ScanMode.BALANCED,
    val passive: Boolean = true,
    val rescue: Boolean = false,
    val refreshMinutes: Int = DEFAULT_REFRESH_MINUTES,
) {
    fun modeFor(sessionOpen: Boolean): ScanMode = if (sessionOpen) session else idle

    companion object {
        const val DEFAULT_REFRESH_MINUTES = 0
        const val MAX_REFRESH_MINUTES = 24 * 60

        /**
         * How long the scan stays in its session mode after a session has closed. A change of
         * mode is a scan start, and [ScanStartBudget] allows four of them in thirty seconds.
         * With this long between the way into the session mode and the way back, sessions
         * alone never use the budget up, however quickly they follow each other (other scan
         * starts, from the screen or a changed setting, still can). And a session that opens
         * within this time costs no start at all.
         */
        const val SESSION_LINGER_MS = 15_000L
    }
}
