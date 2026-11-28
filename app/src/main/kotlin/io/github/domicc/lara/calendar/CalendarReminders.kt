package io.github.domicc.lara.calendar

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.CalendarContract
import io.github.domicc.lara.announce.Announcer
import io.github.domicc.lara.audio.SessionService
import io.github.domicc.lara.core.calendar.CalendarTexts
import io.github.domicc.lara.core.calendar.InstanceKey
import io.github.domicc.lara.core.calendar.Reminder
import io.github.domicc.lara.core.calendar.ReminderPlanner
import io.github.domicc.lara.core.calendar.WakeKind
import io.github.domicc.lara.input.InputRouter
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * What the screen and the adb answer show about the calendar reminders, as of the last plan.
 *
 * Not a data class, like [PhoneCalendar]: it holds names and titles for the phone's own
 * screen, and nothing may print them by accident.
 *
 * @param permission whether the app may read the calendar
 * @param planned false only from the start of the process to its first plan: nothing below
 * is known yet then
 * @param readable false if the calendar storage could not be read at the last plan
 * @param calendars every calendar on the phone; empty while the feature is off or unreadable
 * @param selected how many of [calendars] are announced
 * @param events how many appointments in the planning window could be announced at all
 * @param next the next announcement that is planned, if any
 * @param wakeAtMs when the app plans next; null while no alarm is set
 */
class CalendarStatus(
    val enabled: Boolean,
    val permission: Boolean,
    val planned: Boolean = true,
    val readable: Boolean = true,
    val calendars: List<PhoneCalendar> = emptyList(),
    val selected: Int = 0,
    val events: Int = 0,
    val next: Reminder? = null,
    val wakeAtMs: Long? = null,
    val wakeKind: WakeKind? = null,
)

/**
 * Spoken reminders for appointments: ten minutes before one begins the hearing aid says
 * "In 10 Minuten: Zahnarzt". The appointments come from the phone's calendar storage
 * ([CalendarReader]), the decisions from [ReminderPlanner], the sound from [Announcer] and
 * from nothing else: chime and speech over the media channel, and only if the selected
 * hearing aid is the sole output. If it is not, the announcer skips and journals that, and
 * the appointment still counts as announced; nothing is tried again and nothing vibrates.
 *
 * One plan does everything: read the calendars, announce what is due, set one alarm for the
 * next time something has to happen ([CalendarAlarmReceiver]). A plan is asked for when the
 * process starts, when that alarm fires, when the switch or the choice of calendars changes,
 * when the permission is granted, and a few seconds after the calendar storage reports a
 * change. Plans run one after the other, each on what the calendar holds at that moment, so
 * the newest request decides which alarm stands. What a plan does is decided by
 * [ReminderPlanner.plan]; this class carries it out. An occurrence that is handed to the
 * announcer is remembered ([CalendarStore.markHandled]) before the announcer is called: a
 * restart, an update or the next plan never repeats it.
 *
 * While a session with the hearing aid runs, the voice link has the audio and an
 * announcement over the media channel would get in its way; during a phone call it would
 * talk into the call or go nowhere. A due reminder then waits: the alarm is set
 * [ReminderPlanner.BUSY_RETRY_MS] ahead, again and again, until the session or the call is
 * over or the appointment has begun. The announcer is told the same rule ([holdBack]), so a
 * session that starts while a reminder is spoken cuts it off.
 *
 * A plan that cannot read the calendar does not lose the alarm of the plan before it: it
 * sets the alarm again, for that time or for a new try to read before it
 * ([ReminderPlanner.retryAfterFailure]).
 *
 * Privacy: titles, calendar names and accounts go to the speech engine and to the phone's
 * own screen. The journal (and with it logcat and the files the owner shares) gets numbers
 * only: how many, which ids, when, how long.
 *
 * While the feature is off, nothing here touches the calendar storage.
 */
class CalendarReminders(
    private val context: Context,
    private val journal: Journal,
    private val store: CalendarStore,
    private val announcer: Announcer,
) {
    // IO: reading the calendar storage blocks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reader = CalendarReader(context, journal)
    private val power = context.getSystemService(PowerManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val onePlan = Mutex()
    private val state = MutableStateFlow(CalendarStatus(store.enabled.value, reader.hasPermission(), planned = false))

    /** Reminders that are due and wait for the end of a session or a call. Only touched inside a plan. */
    private var waiting = emptySet<InstanceKey>()

    /** What [waiting] waits for, a word of [holdBack]. Only touched inside a plan. */
    private var waitingFor: String? = null

    /**
     * What the last plan that could read the calendar still had ahead of it; null until this
     * process has planned once. An appointment from this set that has begun without being
     * announced was missed. Only touched inside a plan.
     */
    private var expected: Set<InstanceKey>? = null

    /** The alarm set by the last plan that could read the calendar. Only touched inside a plan. */
    private var standing: Wake? = null

    /** How many plans in a row could not read the calendar. Only touched inside a plan. */
    private var failures = 0

    /** Whether [observer] is registered. Only touched inside a plan. */
    private var observing = false

    /** The plan that follows a change in the calendar storage, while it still waits. Guarded by this. */
    private var afterChange: Job? = null

    /**
     * When the first change arrived that no plan has been asked for yet, on the clock that
     * counts from the start of the phone; null if there is none. Guarded by this.
     */
    private var changedSince: Long? = null

    /**
     * Keeps the phone awake from a change in the calendar storage until the plan that
     * follows it has been asked for: changes arrive while Android synchronises, which it also
     * does for a moment in Doze, and the plan must not be left for whenever the phone wakes
     * next. Not counted: a further change sets a new end for the lock, and it runs out by
     * itself.
     */
    private val changeWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:calendar_change")
        .apply { setReferenceCounted(false) }

    /** Called by Android on one of its own threads whenever something in the calendar storage changed. */
    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) = onCalendarChanged()
    }

    /** One setting of the alarm: when, why, and whether it has to be on the minute. */
    private class Wake(val atMs: Long, val kind: WakeKind, val exact: Boolean)

    /** The state after the last plan, for the screen. */
    val status: StateFlow<CalendarStatus> = state.asStateFlow()

    fun hasPermission(): Boolean = reader.hasPermission()

    /**
     * Call once at every process start: alarms do not survive a reboot or an app update, and
     * the calendar may have changed while the process was gone.
     */
    fun start() {
        scope.launch { store.enabled.drop(1).collect { requestPlan(if (it) "switched_on" else "switched_off") } }
        scope.launch { store.choice.drop(1).collect { requestPlan("selection") } }
        requestPlan("process_start")
    }

    /**
     * The app came onto the screen. Nothing tells an app that a permission was granted in
     * Android's settings, so this is where it is noticed.
     */
    fun onScreen() {
        if (store.enabled.value && !state.value.permission && reader.hasPermission()) requestPlan("permission_granted")
    }

    /**
     * Asks for a plan. Returns at once; the plan runs on another thread, after the plans
     * asked for before it.
     *
     * @param reason who asked, for the journal
     * @param onDone called with the state after this plan, also if it failed
     */
    fun requestPlan(reason: String, onDone: ((CalendarStatus) -> Unit)? = null) {
        // The alarm that leads here does not keep the phone awake for the asynchronous part.
        val wakeLock = if (!store.enabled.value) null
        else power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:calendar").apply { acquire(PLAN_WAKE_MS) }
        scope.launch {
            try {
                onePlan.withLock {
                    try {
                        plan(reason)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // without its message: this code handles titles
                        journal.log("calendar_plan_error", mapOf("reason" to reason, "exception" to e.javaClass.name))
                        // Still inside the lock: a plan that waited behind this one must not
                        // find its alarm and its state overwritten by this one's failure.
                        if (store.enabled.value) failed(reason, System.currentTimeMillis(), null)
                    }
                }
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
                onDone?.invoke(state.value)
            }
        }
    }

    /**
     * The button "Nächsten Termin jetzt ansagen": speaks the next appointment of the coming
     * day through the same path as a reminder, so that the owner can hear how one sounds and
     * that the app reads the calendars he means. Changes nothing about what is planned.
     *
     * @param origin where the request came from, for the journal
     */
    fun announceNext(origin: String) {
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val skipped = when {
                    !store.enabled.value -> "switched_off"
                    !reader.hasPermission() -> "no_permission"
                    else -> holdBack()
                }
                if (skipped != null) {
                    journal.log("calendar_test_skipped", mapOf("origin" to origin, "why" to skipped))
                    return@launch
                }
                // the reader has journaled why
                val calendars = reader.calendars() ?: return@launch
                val instances = reader.instances(now, now + ReminderPlanner.WINDOW_MS) ?: return@launch
                val next = ReminderPlanner.nextEvent(now, instances, selected(calendars))
                val text = if (next == null) CalendarTexts.NO_EVENT
                else CalendarTexts.nextEvent(now, next.beginMs, next.titles, ZoneId.systemDefault())
                journal.log(
                    "calendar_test",
                    mapOf(
                        "origin" to origin,
                        "events" to (next?.keys?.size ?: 0),
                        "beginsInMs" to next?.let { it.beginMs - now },
                        "textLength" to text.length,
                    ),
                )
                announcer.announce(text, REASON_TEST, logText = false, stopIf = ::holdBack)
            } catch (e: Exception) {
                journal.log("calendar_plan_error", mapOf("reason" to "test", "exception" to e.javaClass.name))
            }
        }
    }

    /**
     * Why nothing may be spoken right now, or null: a session has the voice link, or the
     * phone is in a call or rings. The router keeps a press of the remote out of a call for
     * the same reason. The word goes into the journal.
     */
    private fun holdBack(): String? = when {
        SessionService.busy -> "session_busy"
        InputRouter.isCall(audio.mode) -> "phone_call"
        else -> null
    }

    /** Reads the calendar, announces what is due and sets the next alarm. One at a time ([onePlan]). */
    private suspend fun plan(reason: String) {
        if (!store.enabled.value) {
            stopObserving()
            cancelAlarm()
            state.value = CalendarStatus(enabled = false, permission = reader.hasPermission())
            return
        }
        if (!reader.hasPermission()) {
            // never asked for, refused, or taken away again in Android's settings
            stopObserving()
            cancelAlarm()
            journal.log("calendar_no_permission", mapOf("reason" to reason))
            state.value = CalendarStatus(enabled = true, permission = false)
            return
        }
        observe()

        var hold = holdBack()
        if (hold == null && waiting.isNotEmpty()) {
            // Something waited for exactly this. But in the first moment after a session
            // Android may not send media to the hearing aid yet (the session service waits
            // half a second for that before its "Beende"); the announcer would find the
            // wrong output, skip, and the reminder would be gone.
            delay(AFTER_HOLD_MS)
            hold = holdBack()
        }
        val now = System.currentTimeMillis()
        val calendars = reader.calendars()
        val instances = if (calendars == null) null
        else reader.instances(now - ReminderPlanner.MISSED_MS, now + ReminderPlanner.WINDOW_MS + ReminderPlanner.LEAD_MS)
        if (calendars == null || instances == null) {
            // the reader has journaled why
            failed(reason, now, calendars)
            return
        }
        failures = 0
        store.forgetAllBut(calendars.mapTo(mutableSetOf()) { it.id })

        val selected = selected(calendars)
        val plan = ReminderPlanner.plan(
            now, instances, selected, store.handled(),
            busy = hold != null, waiting = waiting, expected = expected,
        )
        // Remembered first: whatever happens from here on, none of it happens a second time.
        store.markHandled(plan.settled, now)
        if (plan.gaveUp.isNotEmpty()) journal.log("calendar_gave_up", mapOf("why" to waitingFor) + about(plan.gaveUp, now))
        // Began without a reminder although the app had them ahead of it, or, at the first
        // plan of a process, may have had: this plan came too late for them.
        if (plan.missed.isNotEmpty()) journal.log("calendar_missed", mapOf("reason" to reason) + about(plan.missed, now))
        expected = plan.ahead
        waiting = plan.waiting
        waitingFor = hold.takeIf { plan.held }
        if (plan.held) {
            journal.log("calendar_waiting", mapOf("why" to hold, "events" to waiting.size, "eventIds" to waiting.map { it.eventId }))
        }
        plan.toAnnounce.forEach { announce(it, now) }

        val wake = Wake(plan.wakeAtMs, plan.wakeKind, plan.wakeExact)
        val exact = arm(wake)
        standing = wake
        journal.log(
            "calendar_plan",
            mapOf(
                "reason" to reason,
                "calendars" to selected.size,
                "calendarsOnPhone" to calendars.size,
                "instances" to instances.size,
                "eligible" to plan.eligible,
                "allDay" to plan.leftOut.allDay,
                "cancelled" to plan.leftOut.cancelled,
                "declined" to plan.leftOut.declined,
                "unselected" to plan.leftOut.unselected,
                "due" to plan.due.sumOf { it.keys.size },
                "held" to plan.held,
                "missed" to plan.missed.size,
                "remembered" to store.handled().size,
                "wakeAt" to clock(plan.wakeAtMs),
                "wakeKind" to plan.wakeKind.word,
                "exact" to exact,
            ),
        )
        state.value = CalendarStatus(
            enabled = true,
            permission = true,
            calendars = calendars,
            selected = selected.size,
            events = plan.eligible,
            // what waits for the end of a session is the next thing that will be said
            next = if (plan.held) plan.due.first() else plan.upcoming,
            wakeAtMs = plan.wakeAtMs,
            wakeKind = plan.wakeKind,
        )
    }

    /**
     * A plan that could not read the calendar, or that broke. Nothing can be said, but the
     * alarm must not be lost over it: the one this feature has is set again for the time the
     * last good plan chose, or for a new try to read before that, whichever comes first.
     *
     * @param calendars the list of calendars if at least that could be read, for the screen
     */
    private fun failed(reason: String, now: Long, calendars: List<PhoneCalendar>?) {
        failures++
        // What waited and has begun in the meantime gets its line here too, not hours later.
        val gaveUp = ReminderPlanner.begun(waiting, store.handled(), now)
        if (gaveUp.isNotEmpty()) {
            store.markHandled(gaveUp, now)
            journal.log("calendar_gave_up", mapOf("why" to waitingFor) + about(gaveUp, now))
            waiting = waiting - gaveUp.toSet()
        }
        val before = standing
        val at = ReminderPlanner.retryAfterFailure(now, failures, before?.atMs)
        val wake = if (before != null && before.atMs == at) before else Wake(at, WakeKind.READ_AGAIN, exact = true)
        val exact = try {
            arm(wake)
        } catch (e: Exception) {
            null
        }
        journal.log(
            "calendar_plan",
            mapOf(
                "reason" to reason,
                "readable" to false,
                "failures" to failures,
                "armed" to (exact != null),
                "wakeAt" to clock(at),
                "wakeKind" to wake.kind.word,
                "exact" to exact,
            ),
        )
        state.value = CalendarStatus(
            enabled = true, permission = reader.hasPermission(), readable = false, calendars = calendars.orEmpty(),
            wakeAtMs = at.takeIf { exact != null }, wakeKind = wake.kind.takeIf { exact != null },
        )
    }

    /** Hands one reminder to the announcer. It is remembered already: whatever becomes of it there, it is not said again. */
    private fun announce(reminder: Reminder, now: Long) {
        val remainingMs = reminder.beginMs - now
        journal.log(
            "calendar_announce",
            mapOf(
                "events" to reminder.keys.size,
                "eventIds" to reminder.keys.map { it.eventId },
                "minutes" to CalendarTexts.minutes(remainingMs),
                "beginsInMs" to remainingMs,
                "titles" to reminder.titles.size,
                "titleLength" to reminder.titles.sumOf { it.length },
            ),
        )
        announcer.announce(CalendarTexts.reminder(remainingMs, reminder.titles), REASON, logText = false, stopIf = ::holdBack)
    }

    /** What a journal line says about occurrences that have begun: numbers only. */
    private fun about(keys: List<InstanceKey>, now: Long): Map<String, Any?> = mapOf(
        "events" to keys.size,
        "eventIds" to keys.map { it.eventId },
        "beganAgoMs" to keys.map { now - it.beginMs },
    )

    private fun selected(calendars: List<PhoneCalendar>): Set<Long> =
        store.choice.value.selected(calendars.associate { it.id to it.visible })

    /**
     * Sets the one alarm of this feature, replacing the one that is waiting.
     *
     * Whatever a reminder hangs on needs the minute and gets an exact alarm that also fires
     * in Doze, like the hourly test. Only a look into a calendar with nothing ahead has
     * hours of slack and gets an inexact one.
     *
     * @return whether the alarm is exact
     */
    private fun arm(wake: Wake): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = pendingIntent(wake.atMs, wake.kind)
        if (wake.exact && alarms.canScheduleExactAlarms()) {
            try {
                setExact(alarms, wake.atMs, intent)
                return true
            } catch (e: SecurityException) {
                // the allowance was taken away between the question and the call
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wake.atMs, intent)
        return false
    }

    /**
     * The one call lint is told to leave alone, and nothing else is: lint asks for the
     * permission SCHEDULE_EXACT_ALARM here and does not know USE_EXACT_ALARM, which the
     * manifest holds and which allows the same without a question to the owner. [arm] asks
     * Android first whether exact alarms are allowed.
     */
    @SuppressLint("MissingPermission")
    private fun setExact(alarms: AlarmManager, atMs: Long, intent: PendingIntent) =
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent)

    /** Off, or no permission: no alarm, and nothing is left over from the plans before. */
    private fun cancelAlarm() {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(0, WakeKind.LOOK_AGAIN))
        standing = null
        failures = 0
        waiting = emptySet()
        waitingFor = null
        // from here on nothing is expected: what begins while the reminders are off is not missed
        expected = emptySet()
    }

    /** Android tells alarms apart by the receiver, not by the extras: there is one, and each new one replaces it. */
    private fun pendingIntent(atMs: Long, kind: WakeKind): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CalendarAlarmReceiver::class.java)
                .putExtra(CalendarAlarmReceiver.EXTRA_SCHEDULED_FOR, atMs)
                .putExtra(CalendarAlarmReceiver.EXTRA_KIND, kind.word),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Asks Android to report changes in the calendar storage. Only with the permission: for
     * an app that may not read a provider Android refuses the registration with a
     * SecurityException (Android source, `ContentService.registerContentObserver`).
     */
    private fun observe() {
        if (observing) return
        try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
            journal.log("calendar_observer", mapOf("registered" to true))
        } catch (e: Exception) {
            // The alarms still work; a change is then only seen at the next plan, which is
            // six hours away at most and tries to register again.
            journal.log("calendar_observer", mapOf("registered" to false, "exception" to e.javaClass.name))
        }
    }

    private fun stopObserving() {
        if (!observing) return
        context.contentResolver.unregisterContentObserver(observer)
        observing = false
        synchronized(this) {
            afterChange?.cancel()
            changedSince = null
        }
        journal.log("calendar_observer", mapOf("registered" to false))
    }

    /**
     * Something in the calendar storage changed. A synchronisation reports many changes in a
     * row, so the plan waits until they have stopped for [CHANGE_SETTLE_MS], but never
     * longer than [CHANGE_AT_MOST_MS] after the first of them: a storage that is written to
     * without a pause must not put the plan off for good, nor keep the phone awake for it.
     */
    private fun onCalendarChanged() {
        synchronized(this) {
            val now = SystemClock.elapsedRealtime()
            val since = changedSince ?: now.also { changedSince = it }
            val wait = (since + CHANGE_AT_MOST_MS - now).coerceIn(0, CHANGE_SETTLE_MS)
            changeWakeLock.acquire(wait + CHANGE_MARGIN_MS)
            afterChange?.cancel()
            afterChange = scope.launch {
                delay(wait)
                // the next change opens a new round
                synchronized(this@CalendarReminders) { changedSince = null }
                requestPlan("calendar_changed")
            }
        }
    }

    private fun clock(atMs: Long): String = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date(atMs))

    private companion object {
        /** The reason the announcer journals for a reminder, and for the line of the test button. */
        const val REASON = "calendar"
        const val REASON_TEST = "calendar_test"

        /** How long after the last reported change the calendar is read. */
        const val CHANGE_SETTLE_MS = 3_000L

        /** How long after the first reported change the calendar is read at the latest. */
        const val CHANGE_AT_MOST_MS = 20_000L

        /** How much longer than that the phone is kept awake, so that the plan gets its own wake lock in time. */
        const val CHANGE_MARGIN_MS = 1_000L

        /** How long a reminder that waited is kept back after the session or the call was seen to be over. */
        const val AFTER_HOLD_MS = 1_000L

        /**
         * The longest a plan may keep the phone awake. Reading the calendar is expected to
         * take a fraction of a second (not measured); two reads that both run into their
         * time limit and the wait after a session take seven.
         */
        const val PLAN_WAKE_MS = 30_000L
    }
}
