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
 * others. The defaults spend allowance only while an audio session is open, which is when a
 * missed press is noticed at once.
 *
 * @param idle the mode outside an audio session
 * @param session the mode while an audio session is open
 * @param passive hand over every advertisement at once (true), or only together with the
 * answer to Android's scan request (false, Android's default). Users of this remote report
 * that most presses get lost the second way; the switch exists to measure exactly that.
 * @param rescue also register the second scan that delivers through a broadcast and so reaches
 * the app when its process is gone. Off by default: it draws on the same allowance.
 * @param refreshMinutes how often the scans are registered anew as a precaution; 0 for never
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
        const val DEFAULT_REFRESH_MINUTES = 15
    }
}
