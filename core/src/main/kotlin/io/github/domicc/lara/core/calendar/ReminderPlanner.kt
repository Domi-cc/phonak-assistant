package io.github.domicc.lara.core.calendar

/**
 * One announcement: the appointments that begin at the same moment are said together.
 *
 * Like [CalendarInstance] it keeps its titles out of [toString].
 *
 * @param keys every occurrence this announcement stands for, also those whose title is said
 * only once because another one has the same
 * @param titles what is said: cleaned, each title once (see [CalendarTexts.titles])
 */
data class Reminder(val beginMs: Long, val keys: List<InstanceKey>, val titles: List<String>) {
    override fun toString(): String = "Reminder(beginMs=$beginMs, keys=$keys, titles=${titles.size})"
}

/**
 * Why the app wants to be woken.
 *
 * @param word the name used in the journal and in the answer to a command
 */
enum class WakeKind(val word: String) {
    /** A reminder falls due. */
    REMINDER("reminder"),

    /** A reminder is due but was held back because a session or a phone call runs; try again. */
    RETRY("retry"),

    /** Read the calendar again in case a change went unnoticed. */
    LOOK_AGAIN("look_again"),

    /** The calendar could not be read; try again. */
    READ_AGAIN("read_again"),
}

/**
 * How many appointments of the window a plan leaves out, by reason. An appointment with two
 * reasons is counted under both. For the journal: when a reminder does not come, these
 * numbers show whether the calendar storage marked the appointment the way the app expects.
 */
data class LeftOut(val allDay: Int, val cancelled: Int, val declined: Int, val unselected: Int)

/**
 * What to do now and when to look next. The app carries this out and decides nothing itself.
 *
 * @param due the reminders that are due, the earliest begin first
 * @param held true if [due] must not be spoken yet because a session with the hearing aid or
 * a phone call runs; they stay due and are not to be remembered as announced
 * @param gaveUp the occurrences that waited for the end of a session or a call and have begun
 * in the meantime: nothing is said about them any more
 * @param missed the occurrences that began without the app having got to them: the alarm
 * came too late, the phone was off, the calendar could not be read. For the journal.
 * @param ahead every occurrence that is still to be announced after this plan: what is held
 * back and what is not due yet. The next plan has to be told about them.
 * @param upcoming the first reminder that is not due yet, for the screen
 * @param eligible how many appointments in the window could be announced at all, whether or
 * not they have been: not begun, not all-day, not cancelled, not declined, calendar selected
 * @param leftOut how many appointments of the window those rules leave out
 * @param wakeAtMs when the app has to plan again
 * @param wakeExact whether that has to happen on the minute. Only a look into a calendar
 * with no reminder ahead has hours of slack.
 */
data class ReminderPlan(
    val due: List<Reminder>,
    val held: Boolean,
    val gaveUp: List<InstanceKey>,
    val missed: List<InstanceKey>,
    val ahead: Set<InstanceKey>,
    val upcoming: Reminder?,
    val eligible: Int,
    val leftOut: LeftOut,
    val wakeAtMs: Long,
    val wakeKind: WakeKind,
    val wakeExact: Boolean,
) {
    /** The reminders to hand to the announcer now: none while they are held back. */
    val toAnnounce: List<Reminder> get() = if (held) emptyList() else due

    /** The occurrences that wait from now on; the next plan has to be told about them. */
    val waiting: Set<InstanceKey> get() = if (held) due.flatMapTo(mutableSetOf()) { it.keys } else emptySet()

    /**
     * Every occurrence this plan is done with: handed to the announcer, given up or missed.
     * To be remembered before anything is spoken, so that none of it happens a second time.
     * What is held back is not among them.
     */
    val settled: List<InstanceKey> get() = toAnnounce.flatMap { it.keys } + gaveUp + missed
}

/**
 * Decides which appointments are announced and when. All of it is plain arithmetic on what the
 * calendar storage listed, so that every rule can be tested without a phone:
 *
 * - An appointment is announced from [LEAD_MS] before its begin until it begins. Whenever the
 *   app gets to it in that time (the alarm on time, the alarm late, the feature switched on
 *   five minutes before), the announcement names the minutes that are really left. Once the
 *   appointment has begun, nothing is said any more.
 * - Never announced: all-day appointments, cancelled ones, ones the owner declined, and
 *   those of a calendar that is not selected.
 * - Each occurrence is announced once ([InstanceKey]).
 * - Appointments with the same begin are one announcement.
 * - While a session or a phone call runs, what is due waits; what still waits when its
 *   appointment begins is given up.
 *
 * Times are wall-clock milliseconds.
 */
object ReminderPlanner {

    /** How long before its begin an appointment is announced. */
    const val LEAD_MS = 10 * 60_000L

    /** How far ahead the app plans: reminders that fall due within the next day. */
    const val WINDOW_MS = 24 * 3_600_000L

    /**
     * The app never goes longer than this without reading the calendar, whether or not a
     * reminder is planned. It is told about changes while it runs, but a report can get
     * lost, and an appointment added for this afternoon must not wait behind the alarm for
     * tomorrow morning's.
     */
    const val LOOK_AGAIN_MS = 6 * 3_600_000L

    /** While a session with the hearing aid or a phone call runs, a due reminder is tried again this often. */
    const val BUSY_RETRY_MS = 20_000L

    /** How long an announced occurrence is remembered after its begin. */
    const val KEEP_HANDLED_MS = 24 * 3_600_000L

    /**
     * How far back a plan looks for appointments that began without a reminder. The calendar
     * has to be asked for this much of the past as well.
     */
    const val MISSED_MS = 3_600_000L

    /** After a read of the calendar that failed, the first new try comes this soon. */
    const val READ_RETRY_MS = 60_000L

    /**
     * @param instances what the calendar storage lists from [MISSED_MS] before [nowMs] to
     * the end of the window; anything else in the list is ignored
     * @param selectedCalendars the ids of the calendars that are announced
     * @param handled the occurrences that are done with: announced, given up or missed
     * @param busy a session with the hearing aid or a phone call runs, so nothing may be
     * spoken now
     * @param waiting the occurrences the plan before this one held back ([ReminderPlan.waiting])
     * @param expected the occurrences the plan before this one still had ahead of it
     * ([ReminderPlan.ahead]): only what the app saw coming can be missed. An appointment
     * that is entered after its begin, or whose calendar is ticked only then, was never the
     * app's to announce. Null if this process has not planned before: nobody knows then what
     * it would have seen, and everything that began within [MISSED_MS] counts.
     */
    fun plan(
        nowMs: Long,
        instances: List<CalendarInstance>,
        selectedCalendars: Set<Long>,
        handled: Set<InstanceKey>,
        busy: Boolean = false,
        waiting: Set<InstanceKey> = emptySet(),
        expected: Set<InstanceKey>? = null,
    ): ReminderPlan {
        val inWindow = instances.filter { it.beginMs > nowMs && it.beginMs <= nowMs + WINDOW_MS + LEAD_MS }
        val eligible = inWindow.filter { announced(it, selectedCalendars) }.sortedBy { it.beginMs }
        val (dueNow, later) = eligible.filter { it.key !in handled }.partition { it.beginMs - LEAD_MS <= nowMs }
        val due = reminders(dueNow)
        val upcoming = reminders(later).firstOrNull()
        val held = busy && due.isNotEmpty()

        val gaveUp = begun(waiting, handled, nowMs)
        val missed = instances
            .filter { it.beginMs <= nowMs && it.beginMs > nowMs - MISSED_MS && announced(it, selectedCalendars) }
            .map { it.key }
            .filter { it !in handled && it !in gaveUp && (expected == null || it in expected) }
            .distinct()
            .sortedBy { it.beginMs }
        val ahead = (later + if (held) dueNow else emptyList()).mapTo(mutableSetOf()) { it.key }

        var wakeAtMs = nowMs + LOOK_AGAIN_MS
        var wakeKind = WakeKind.LOOK_AGAIN
        if (upcoming != null && upcoming.beginMs - LEAD_MS <= wakeAtMs) {
            wakeAtMs = upcoming.beginMs - LEAD_MS
            wakeKind = WakeKind.REMINDER
        }
        if (held && nowMs + BUSY_RETRY_MS < wakeAtMs) {
            wakeAtMs = nowMs + BUSY_RETRY_MS
            wakeKind = WakeKind.RETRY
        }
        // A look that came an hour late could run past a reminder that lies behind it.
        val wakeExact = wakeKind != WakeKind.LOOK_AGAIN || upcoming != null
        val leftOut = LeftOut(
            allDay = inWindow.count { it.allDay },
            cancelled = inWindow.count { it.cancelled },
            declined = inWindow.count { it.declined },
            unselected = inWindow.count { it.calendarId !in selectedCalendars },
        )
        return ReminderPlan(due, held, gaveUp, missed, ahead, upcoming, eligible.size, leftOut, wakeAtMs, wakeKind, wakeExact)
    }

    /**
     * Of the occurrences that [waiting] for the end of a session or a call, those whose
     * appointment has begun: they are given up. Whether the calendar still lists them does
     * not matter; they waited, and nothing was said.
     */
    fun begun(waiting: Set<InstanceKey>, handled: Set<InstanceKey>, nowMs: Long): List<InstanceKey> =
        waiting.filter { it.beginMs <= nowMs && it !in handled }.sortedBy { it.beginMs }

    /**
     * When to read the calendar again after a read that failed: after [READ_RETRY_MS], and
     * twice as long after every further failure, up to [LOOK_AGAIN_MS]. But never later than
     * the wake that was planned before: one failed read must not move a reminder's alarm.
     *
     * @param failures how many reads in a row have failed, this one included
     * @param standingWakeAtMs the wake set by the last plan that could read the calendar, if
     * there was one; ignored once it has passed
     */
    fun retryAfterFailure(nowMs: Long, failures: Int, standingWakeAtMs: Long?): Long {
        val step = (READ_RETRY_MS shl (failures - 1).coerceIn(0, 20)).coerceAtMost(LOOK_AGAIN_MS)
        val retryAtMs = nowMs + step
        return if (standingWakeAtMs != null && standingWakeAtMs > nowMs && standingWakeAtMs < retryAtMs) standingWakeAtMs else retryAtMs
    }

    /**
     * What the test button speaks: the next appointment within a day that could be announced,
     * together with those that begin at the same moment. Whether it has been announced
     * already does not matter here.
     */
    fun nextEvent(nowMs: Long, instances: List<CalendarInstance>, selectedCalendars: Set<Long>): Reminder? =
        reminders(
            instances.filter { it.beginMs > nowMs && it.beginMs <= nowMs + WINDOW_MS && announced(it, selectedCalendars) },
        ).firstOrNull()

    /** [handled] without the occurrences that began more than [KEEP_HANDLED_MS] ago. */
    fun prune(handled: Set<InstanceKey>, nowMs: Long): Set<InstanceKey> =
        handled.filterTo(mutableSetOf()) { it.beginMs > nowMs - KEEP_HANDLED_MS }

    /** Whether an appointment is one of those that get a reminder at all, whenever it begins. */
    private fun announced(instance: CalendarInstance, selectedCalendars: Set<Long>): Boolean =
        !instance.allDay && !instance.cancelled && !instance.declined && instance.calendarId in selectedCalendars

    /** One reminder per begin, the earliest first. */
    private fun reminders(instances: List<CalendarInstance>): List<Reminder> =
        instances.groupBy { it.beginMs }.toSortedMap().map { (beginMs, together) ->
            Reminder(beginMs, together.map { it.key }, CalendarTexts.titles(together.map { it.title }))
        }
}
