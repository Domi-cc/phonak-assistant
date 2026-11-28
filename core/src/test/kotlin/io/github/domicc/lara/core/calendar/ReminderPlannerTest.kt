package io.github.domicc.lara.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderPlannerTest {

    private val now = 1_800_000_000_000L
    private val second = 1_000L
    private val minute = 60_000L
    private val hour = 3_600_000L
    private val lead = ReminderPlanner.LEAD_MS

    /** An appointment that begins [beginsIn] milliseconds after [now], in calendar 1 unless said otherwise. */
    private fun event(
        id: Long,
        beginsIn: Long,
        title: String? = "Zahnarzt",
        calendar: Long = 1,
        allDay: Boolean = false,
        cancelled: Boolean = false,
        declined: Boolean = false,
    ) = CalendarInstance(id, calendar, now + beginsIn, allDay, cancelled, declined, title)

    private fun plan(
        vararg instances: CalendarInstance,
        handled: Set<InstanceKey> = emptySet(),
        selected: Set<Long> = setOf(1L),
        busy: Boolean = false,
        waiting: Set<InstanceKey> = emptySet(),
        expected: Set<InstanceKey>? = null,
    ) = ReminderPlanner.plan(now, instances.toList(), selected, handled, busy, waiting, expected)

    /** The same, [later] milliseconds after [now]: the next plan of a story. */
    private fun planLater(
        later: Long,
        vararg instances: CalendarInstance,
        handled: Set<InstanceKey> = emptySet(),
        busy: Boolean = false,
        waiting: Set<InstanceKey> = emptySet(),
        expected: Set<InstanceKey>? = null,
    ) = ReminderPlanner.plan(now + later, instances.toList(), setOf(1L), handled, busy, waiting, expected)

    /** What the hearing aid would say for the one reminder that is due. */
    private fun spoken(plan: ReminderPlan): String {
        val reminder = plan.due.single()
        return CalendarTexts.reminder(reminder.beginMs - now, reminder.titles)
    }

    @Test
    fun `ten minutes before the begin the reminder is due`() {
        val plan = plan(event(7, lead))
        assertEquals("In 10 Minuten: Zahnarzt", spoken(plan))
        assertEquals(listOf(InstanceKey(7, now + lead)), plan.due.single().keys)
        assertFalse(plan.held)
        assertEquals(1, plan.eligible)
    }

    @Test
    fun `a millisecond before that nothing is due and the wake is the reminder time`() {
        val plan = plan(event(7, lead + 1))
        assertEquals(emptyList(), plan.due)
        assertEquals(now + 1, plan.wakeAtMs)
        assertEquals(WakeKind.REMINDER, plan.wakeKind)
        assertTrue(plan.wakeExact)
        assertEquals(listOf("Zahnarzt"), plan.upcoming?.titles)
        assertEquals(1, plan.eligible)
    }

    @Test
    fun `a late wake-up says the minutes that are really left`() {
        assertEquals("In 7 Minuten: Zahnarzt", spoken(plan(event(7, 7 * minute))))
        // the alarm of the hourly test was 48 ms late: that is still ten minutes
        assertEquals("In 10 Minuten: Zahnarzt", spoken(plan(event(7, lead - 48))))
        assertEquals("In 7 Minuten: Zahnarzt", spoken(plan(event(7, 6 * minute + 30 * second))))
        assertEquals("In 6 Minuten: Zahnarzt", spoken(plan(event(7, 6 * minute + 29 * second))))
        assertEquals("In 2 Minuten: Zahnarzt", spoken(plan(event(7, 90 * second))))
    }

    @Test
    fun `one minute is said as a word`() {
        assertEquals("In einer Minute: Zahnarzt", spoken(plan(event(7, 89 * second))))
        assertEquals("In einer Minute: Zahnarzt", spoken(plan(event(7, minute))))
    }

    @Test
    fun `under a minute it says now`() {
        assertEquals("Jetzt: Zahnarzt", spoken(plan(event(7, minute - 1))))
        assertEquals("Jetzt: Zahnarzt", spoken(plan(event(7, 1))))
    }

    @Test
    fun `an event that has begun is not announced`() {
        val atItsBegin = plan(event(7, 0))
        assertEquals(emptyList(), atItsBegin.due)
        assertEquals(0, atItsBegin.eligible)

        // still running, so the calendar lists it for the window
        val running = plan(event(8, -5 * minute))
        assertEquals(emptyList(), running.due)
        assertNull(running.upcoming)
        assertEquals(WakeKind.LOOK_AGAIN, running.wakeKind)
    }

    @Test
    fun `all-day, cancelled and declined events are left out`() {
        val plan = plan(
            event(1, 5 * minute, allDay = true),
            event(2, 5 * minute, cancelled = true),
            event(3, 5 * minute, declined = true),
            event(4, 2 * hour, allDay = true),
        )
        assertEquals(emptyList(), plan.due)
        assertNull(plan.upcoming)
        assertEquals(0, plan.eligible)
        assertEquals(WakeKind.LOOK_AGAIN, plan.wakeKind)
    }

    @Test
    fun `events of a calendar that is not selected are left out`() {
        val plan = plan(event(1, 5 * minute, title = "A", calendar = 1), event(2, 5 * minute, title = "B", calendar = 2), selected = setOf(2L))
        assertEquals(listOf("B"), plan.due.single().titles)
        assertEquals(1, plan.eligible)

        assertEquals(emptyList(), plan(event(1, 5 * minute), selected = emptySet()).due)
    }

    @Test
    fun `the plan counts what it leaves out and why`() {
        val plan = plan(
            event(1, 5 * minute),
            event(2, 5 * minute, allDay = true),
            event(3, 2 * hour, allDay = true),
            event(4, 30 * minute, cancelled = true),
            event(5, 40 * minute, declined = true),
            event(6, 50 * minute, calendar = 2),
            // two reasons: counted under each
            event(7, 3 * hour, calendar = 2, declined = true),
            // not in the window any more, or not yet: not counted at all
            event(8, -5 * minute, cancelled = true),
            event(9, 25 * hour, allDay = true),
        )
        assertEquals(LeftOut(allDay = 2, cancelled = 1, declined = 2, unselected = 2), plan.leftOut)
        assertEquals(1, plan.eligible)
        assertEquals(LeftOut(0, 0, 0, 0), plan(event(1, 5 * minute)).leftOut)
    }

    @Test
    fun `an instance that was announced is not announced again`() {
        val announced = event(7, 4 * minute)
        val plan = plan(announced, handled = setOf(announced.key))
        assertEquals(emptyList(), plan.due)
        assertNull(plan.upcoming)
        assertEquals(1, plan.eligible, "it still lies in the window")
        assertEquals(WakeKind.LOOK_AGAIN, plan.wakeKind)
        assertEquals(emptyList(), plan.settled)
    }

    @Test
    fun `an event that shares its begin with an announced one is said alone`() {
        val announced = event(1, 4 * minute, title = "Zahnarzt")
        // synchronised a moment after the reminder for the first one
        val added = event(2, 4 * minute, title = "Bank")
        val plan = plan(announced, added, handled = setOf(announced.key))
        assertEquals("In 4 Minuten: Bank", spoken(plan))
        assertEquals(listOf(added.key), plan.settled)
    }

    @Test
    fun `a moved event is announced again for its new time`() {
        val before = event(7, 5 * minute)
        val moved = event(7, 2 * hour)
        val plan = plan(moved, handled = setOf(before.key))
        assertEquals(listOf(moved.key), plan.upcoming?.keys)
        assertEquals(moved.beginMs - lead, plan.wakeAtMs)

        // and when it is moved to a time that is close already, at once
        val close = event(7, 8 * minute)
        assertEquals("In 8 Minuten: Zahnarzt", spoken(plan(close, handled = setOf(before.key))))
    }

    @Test
    fun `every instance of a recurring event has a reminder of its own`() {
        val today = event(7, 3 * minute, title = "Stand-up")
        val tomorrow = event(7, 3 * minute + 24 * hour, title = "Stand-up")
        val plan = plan(today, tomorrow, handled = setOf(today.key))
        assertEquals(emptyList(), plan.due)
        assertEquals(listOf(tomorrow.key), plan.upcoming?.keys)
        assertEquals(2, plan.eligible)
    }

    @Test
    fun `one occurrence of a series that was changed is a new event and is announced again`() {
        // Android keeps a changed occurrence as an event of its own, with a new id and the
        // old begin. The app cannot tell it from a new appointment at that time.
        val announced = event(40, 6 * minute, title = "Stand-up")
        val changed = event(97, 6 * minute, title = "Stand-up")
        assertEquals("In 6 Minuten: Stand-up", spoken(plan(changed, handled = setOf(announced.key))))
    }

    @Test
    fun `events with the same begin are one announcement`() {
        val two = plan(event(1, lead, title = "Zahnarzt"), event(2, lead, title = "Anruf Bank"))
        assertEquals("In 10 Minuten: Zahnarzt und Anruf Bank", spoken(two))
        assertEquals(2, two.due.single().keys.size)

        val three = plan(event(1, lead, title = "A"), event(2, lead, title = "B"), event(3, lead, title = "C"))
        assertEquals("In 10 Minuten: A, B und C", spoken(three))
    }

    @Test
    fun `the same title at the same begin is said once and both count as announced`() {
        // the same appointment in two calendars
        val plan = plan(event(1, lead, title = "Zahnarzt"), event(2, lead, title = " Zahnarzt "), event(3, lead, title = "Bank"))
        assertEquals("In 10 Minuten: Zahnarzt und Bank", spoken(plan))
        assertEquals(setOf(1L, 2L, 3L), plan.due.single().keys.map { it.eventId }.toSet())
        assertEquals(setOf(1L, 2L, 3L), plan.settled.map { it.eventId }.toSet())
    }

    @Test
    fun `events with different begins are announced one after the other, the earliest first`() {
        // the app was switched on late: both lie inside the ten minutes
        val plan = plan(event(2, 8 * minute, title = "B"), event(1, 5 * minute, title = "A"))
        assertEquals(listOf(listOf("A"), listOf("B")), plan.due.map { it.titles })
        assertEquals(listOf(now + 5 * minute, now + 8 * minute), plan.due.map { it.beginMs })
        assertEquals(plan.due, plan.toAnnounce)
    }

    @Test
    fun `a missing title becomes Termin`() {
        assertEquals("In 10 Minuten: Termin", spoken(plan(event(7, lead, title = null))))
        assertEquals("In 10 Minuten: Termin", spoken(plan(event(7, lead, title = "  \n "))))
    }

    @Test
    fun `the next wake is the earliest reminder that is not due yet`() {
        val plan = plan(
            event(1, 5 * minute, title = "jetzt fällig"),
            event(2, 3 * hour, title = "später"),
            event(3, 45 * minute, title = "bald"),
            event(4, 30 * minute, title = "abgesagt", cancelled = true),
        )
        assertEquals(listOf("jetzt fällig"), plan.due.single().titles)
        assertEquals(now + 35 * minute, plan.wakeAtMs)
        assertEquals(WakeKind.REMINDER, plan.wakeKind)
        assertEquals(listOf("bald"), plan.upcoming?.titles)
        assertEquals(3, plan.eligible)
    }

    @Test
    fun `without a reminder ahead the app looks again after six hours at the latest`() {
        val empty = plan()
        assertEquals(now + 6 * hour, empty.wakeAtMs)
        assertEquals(WakeKind.LOOK_AGAIN, empty.wakeKind)
        assertFalse(empty.wakeExact, "nothing hangs on the minute of this look")
        assertEquals(ReminderPlanner.LOOK_AGAIN_MS, 6 * hour)

        // the last reminder of the window was just handed over
        val afterTheLast = plan(event(1, 5 * minute))
        assertEquals(1, afterTheLast.due.size)
        assertEquals(now + 6 * hour, afterTheLast.wakeAtMs)
        assertEquals(WakeKind.LOOK_AGAIN, afterTheLast.wakeKind)
    }

    @Test
    fun `with a reminder more than six hours ahead the app still looks again after six hours`() {
        // tomorrow morning's appointment must not be the next time the calendar is read: one
        // added for this afternoon would go unnoticed if its change report got lost
        val far = plan(event(1, 23 * hour + 45 * minute + lead))
        assertEquals(now + 6 * hour, far.wakeAtMs)
        assertEquals(WakeKind.LOOK_AGAIN, far.wakeKind)
        assertTrue(far.wakeExact, "a look that came late could run past the reminder")
        assertEquals(now + 23 * hour + 45 * minute + lead, far.upcoming?.beginMs)

        val justBeyond = plan(event(1, 6 * hour + lead + 1))
        assertEquals(now + 6 * hour, justBeyond.wakeAtMs)
        assertEquals(WakeKind.LOOK_AGAIN, justBeyond.wakeKind)

        val atSixHours = plan(event(1, 6 * hour + lead))
        assertEquals(now + 6 * hour, atSixHours.wakeAtMs)
        assertEquals(WakeKind.REMINDER, atSixHours.wakeKind)
    }

    @Test
    fun `the window ends where a reminder would be more than a day away`() {
        val last = plan(event(1, 24 * hour + lead))
        assertEquals(1, last.eligible)
        assertEquals(now + 24 * hour + lead, last.upcoming?.beginMs)

        val beyond = plan(event(1, 24 * hour + lead + 1))
        assertEquals(0, beyond.eligible)
        assertNull(beyond.upcoming)
        assertFalse(beyond.wakeExact)
    }

    @Test
    fun `while a session runs a due reminder is held back and tried again after twenty seconds`() {
        val plan = plan(event(1, 5 * minute), event(2, 3 * hour), busy = true)
        assertTrue(plan.held)
        assertEquals(1, plan.due.size, "what is held back is still listed")
        assertEquals(now + 20 * second, plan.wakeAtMs)
        assertEquals(WakeKind.RETRY, plan.wakeKind)
        assertTrue(plan.wakeExact)
    }

    @Test
    fun `a held reminder is tried again after twenty seconds also when nothing else lies ahead`() {
        val plan = plan(event(1, 5 * minute), busy = true)
        assertTrue(plan.held)
        assertNull(plan.upcoming)
        assertEquals(now + 20 * second, plan.wakeAtMs)
        assertEquals(WakeKind.RETRY, plan.wakeKind)
        assertTrue(plan.wakeExact)
    }

    @Test
    fun `a reminder that falls due before the next try is the wake`() {
        val plan = plan(event(1, 5 * minute), event(2, lead + 5 * second), busy = true)
        assertTrue(plan.held)
        assertEquals(now + 5 * second, plan.wakeAtMs)
        assertEquals(WakeKind.REMINDER, plan.wakeKind)
    }

    @Test
    fun `a running session holds nothing back while nothing is due`() {
        val plan = plan(event(2, 3 * hour), busy = true)
        assertFalse(plan.held)
        assertEquals(emptySet(), plan.waiting)
        assertEquals(now + 3 * hour - lead, plan.wakeAtMs)
        assertEquals(WakeKind.REMINDER, plan.wakeKind)
    }

    @Test
    fun `a held reminder is neither handed over nor remembered as announced`() {
        val held = event(1, 5 * minute)
        val plan = plan(held, busy = true)
        assertEquals(emptyList(), plan.toAnnounce)
        assertEquals(emptyList(), plan.settled)
        assertEquals(setOf(held.key), plan.waiting)
        assertEquals(emptyList(), plan.gaveUp)
    }

    @Test
    fun `when the session is over the held reminder is announced and remembered`() {
        val held = event(1, 5 * minute)
        val first = plan(held, busy = true)
        val next = planLater(20 * second, held, waiting = first.waiting)
        assertFalse(next.held)
        assertEquals(listOf(held.key), next.toAnnounce.single().keys)
        assertEquals(listOf(held.key), next.settled)
        assertEquals(emptySet(), next.waiting)
        assertEquals(emptyList(), next.gaveUp)
        assertEquals(emptyList(), next.missed)
    }

    @Test
    fun `a reminder that still waits when its event begins is given up, once`() {
        val held = event(1, 15 * second)
        val first = plan(held, busy = true)
        assertEquals(setOf(held.key), first.waiting)

        // the session still runs when the appointment begins
        val atTheBegin = planLater(15 * second, held, busy = true, waiting = first.waiting)
        assertFalse(atTheBegin.held)
        assertEquals(emptyList(), atTheBegin.due)
        assertEquals(listOf(held.key), atTheBegin.gaveUp)
        assertEquals(emptyList(), atTheBegin.missed, "it is given up, not missed as well")
        assertEquals(listOf(held.key), atTheBegin.settled)
        assertEquals(emptySet(), atTheBegin.waiting)
        assertEquals(WakeKind.LOOK_AGAIN, atTheBegin.wakeKind)

        // remembered now, so the next plan says nothing about it any more
        val after = planLater(35 * second, held, busy = true, handled = atTheBegin.settled.toSet())
        assertEquals(emptyList(), after.gaveUp)
        assertEquals(emptyList(), after.missed)
        assertEquals(emptyList(), after.settled)
    }

    @Test
    fun `a waiting reminder whose event is no longer listed is still given up at its begin`() {
        // deleted or moved while it waited: before the begin nothing is said about it
        val gone = InstanceKey(1, now + 15 * second)
        assertEquals(emptyList(), plan(busy = true, waiting = setOf(gone)).gaveUp)
        assertEquals(listOf(gone), planLater(15 * second, busy = true, waiting = setOf(gone)).gaveUp)
    }

    @Test
    fun `of two held reminders the one whose event begins is given up and the other waits on`() {
        val soon = event(1, 30 * second, title = "A")
        val later = event(2, 8 * minute, title = "B")
        val first = plan(soon, later, busy = true)
        assertEquals(setOf(soon.key, later.key), first.waiting)
        assertEquals(emptyList(), first.settled)

        val next = planLater(40 * second, soon, later, busy = true, waiting = first.waiting)
        assertEquals(listOf(soon.key), next.gaveUp)
        assertTrue(next.held)
        assertEquals(setOf(later.key), next.waiting)
        assertEquals(listOf(soon.key), next.settled)
        assertEquals(WakeKind.RETRY, next.wakeKind)

        // the session ends: the one that is left is said
        val last = planLater(60 * second, soon, later, handled = next.settled.toSet(), waiting = next.waiting)
        assertEquals(listOf(listOf("B")), last.toAnnounce.map { it.titles })
        assertEquals(listOf(later.key), last.settled)
        assertEquals(emptyList(), last.gaveUp)
    }

    @Test
    fun `a plan tells the next one what it still has ahead`() {
        val due = event(1, 5 * minute)
        val soon = event(2, 30 * minute)
        val tomorrow = event(3, 23 * hour)
        val done = event(4, 2 * hour)
        val plan = plan(due, soon, tomorrow, done, event(5, hour, cancelled = true), handled = setOf(done.key))
        assertEquals(setOf(soon.key, tomorrow.key), plan.ahead, "what is handed over now is not ahead any more")

        // what is held back still is
        assertEquals(setOf(due.key, soon.key, tomorrow.key), plan(due, soon, tomorrow, busy = true).ahead)
    }

    @Test
    fun `an event the app saw coming and did not get to is reported as missed, once`() {
        val soon = event(7, 30 * minute)
        val first = plan(soon, event(8, 3 * hour))
        assertEquals(now + 20 * minute, first.wakeAtMs)

        // the alarm came twelve minutes late, or the calendar could not be read in between
        val late = planLater(32 * minute, soon, event(8, 3 * hour), expected = first.ahead)
        assertEquals(listOf(soon.key), late.missed)
        assertEquals(listOf(soon.key), late.settled)
        assertEquals(emptyList(), late.due)
        assertEquals(1, late.eligible, "only what can still be announced counts as eligible")

        val after = planLater(33 * minute, soon, event(8, 3 * hour), handled = late.settled.toSet(), expected = late.ahead)
        assertEquals(emptyList(), after.missed)
    }

    @Test
    fun `an event the app never saw coming is not missed`() {
        // entered after its begin, synchronised late, or its calendar was ticked only now
        val begun = event(7, -2 * minute)
        assertEquals(emptyList(), plan(begun, expected = emptySet()).missed)
        assertEquals(emptyList(), plan(begun, expected = setOf(InstanceKey(8, now + hour))).missed)
        assertEquals(emptyList(), plan(begun, expected = emptySet()).settled)
    }

    @Test
    fun `the first plan of a process counts everything that began in the last hour as missed`() {
        // the phone was off, or the process was gone: nobody knows what it would have seen
        val begun = event(7, -2 * minute)
        val plan = plan(begun, event(8, 3 * hour))
        assertEquals(listOf(begun.key), plan.missed)
        assertEquals(listOf(begun.key), plan.settled)
    }

    @Test
    fun `missed are only events that would have been announced`() {
        val plan = plan(
            event(1, -2 * minute, allDay = true),
            event(2, -2 * minute, cancelled = true),
            event(3, -2 * minute, declined = true),
            event(4, -2 * minute, calendar = 2),
        )
        assertEquals(emptyList(), plan.missed)

        // announced in time
        val announced = event(5, -2 * minute)
        assertEquals(emptyList(), plan(announced, handled = setOf(announced.key)).missed)
        // at its very begin an appointment has begun: too late for a reminder
        assertEquals(listOf(InstanceKey(6, now)), plan(event(6, 0)).missed)
    }

    @Test
    fun `what began more than an hour ago is not reported any more`() {
        assertEquals(hour, ReminderPlanner.MISSED_MS)
        val plan = plan(event(1, -hour), event(2, -hour + 1))
        assertEquals(listOf(InstanceKey(2, now - hour + 1)), plan.missed)
    }

    @Test
    fun `after a failed read the calendar is read again soon, then less and less often`() {
        assertEquals(minute, ReminderPlanner.READ_RETRY_MS)
        assertEquals(now + 1 * minute, ReminderPlanner.retryAfterFailure(now, failures = 1, standingWakeAtMs = null))
        assertEquals(now + 2 * minute, ReminderPlanner.retryAfterFailure(now, failures = 2, standingWakeAtMs = null))
        assertEquals(now + 4 * minute, ReminderPlanner.retryAfterFailure(now, failures = 3, standingWakeAtMs = null))
        assertEquals(now + 256 * minute, ReminderPlanner.retryAfterFailure(now, failures = 9, standingWakeAtMs = null))
        // never further away than the look into a calendar that holds nothing
        assertEquals(now + 6 * hour, ReminderPlanner.retryAfterFailure(now, failures = 10, standingWakeAtMs = null))
        assertEquals(now + 6 * hour, ReminderPlanner.retryAfterFailure(now, failures = 500, standingWakeAtMs = null))
        assertEquals(now + 1 * minute, ReminderPlanner.retryAfterFailure(now, failures = 0, standingWakeAtMs = null))
    }

    @Test
    fun `a failed read never pushes the wake behind the one that was planned`() {
        // the reminder alarm of the last good plan stands at now + 30 minutes
        val standing = now + 30 * minute
        assertEquals(now + 1 * minute, ReminderPlanner.retryAfterFailure(now, failures = 1, standingWakeAtMs = standing))
        assertEquals(now + 16 * minute, ReminderPlanner.retryAfterFailure(now, failures = 5, standingWakeAtMs = standing))
        assertEquals(standing, ReminderPlanner.retryAfterFailure(now, failures = 6, standingWakeAtMs = standing))
        assertEquals(standing, ReminderPlanner.retryAfterFailure(now, failures = 10, standingWakeAtMs = standing))
        // a wake that has passed (the alarm that led to this read) holds nothing back
        assertEquals(now + 4 * minute, ReminderPlanner.retryAfterFailure(now, failures = 3, standingWakeAtMs = now))
        assertEquals(now + 4 * minute, ReminderPlanner.retryAfterFailure(now, failures = 3, standingWakeAtMs = now - minute))
    }

    @Test
    fun `remembered instances are dropped a day after their begin`() {
        val day = ReminderPlanner.KEEP_HANDLED_MS
        assertEquals(24 * hour, day)
        val old = InstanceKey(1, now - day)
        val recent = InstanceKey(2, now - day + 1)
        val ahead = InstanceKey(3, now + 5 * minute)
        assertEquals(setOf(recent, ahead), ReminderPlanner.prune(setOf(old, recent, ahead), now))
        assertEquals(emptySet(), ReminderPlanner.prune(emptySet(), now))
    }

    @Test
    fun `a key survives being written down and read back`() {
        val key = InstanceKey(4711, now)
        assertEquals("4711@$now", key.encode())
        assertEquals(key, InstanceKey.decode(key.encode()))
        assertEquals(InstanceKey(-3, -1), InstanceKey.decode("-3@-1"))
        for (broken in listOf("", "4711", "4711@", "@5", "a@5", "5@b", "1@2@3")) assertNull(InstanceKey.decode(broken), broken)
    }

    @Test
    fun `the test button gets the next event, also one that was announced already`() {
        val announced = event(1, 4 * minute, title = "Zahnarzt")
        val later = event(2, 2 * hour, title = "Bank")
        val next = ReminderPlanner.nextEvent(now, listOf(later, announced, event(3, 1 * minute, cancelled = true)), setOf(1L))
        assertEquals(listOf("Zahnarzt"), next?.titles)
        assertEquals(announced.beginMs, next?.beginMs)

        val together = ReminderPlanner.nextEvent(now, listOf(event(1, hour, title = "A"), event(2, hour, title = "B")), setOf(1L))
        assertEquals(listOf("A", "B"), together?.titles)
    }

    @Test
    fun `the test button leaves out what a reminder leaves out`() {
        val next = ReminderPlanner.nextEvent(
            now,
            listOf(
                event(1, 1 * minute, title = "ganztägig", allDay = true),
                event(2, 2 * minute, title = "abgelehnt", declined = true),
                event(3, 3 * minute, title = "abgesagt", cancelled = true),
                event(4, 4 * minute, title = "anderer Kalender", calendar = 2),
                event(5, 5 * minute, title = "Zahnarzt"),
            ),
            setOf(1L),
        )
        assertEquals(listOf("Zahnarzt"), next?.titles)
    }

    @Test
    fun `the test button finds nothing beyond a day and nothing that has begun`() {
        assertNull(ReminderPlanner.nextEvent(now, emptyList(), setOf(1L)))
        assertNull(ReminderPlanner.nextEvent(now, listOf(event(1, -minute), event(2, 0), event(3, 24 * hour + 1)), setOf(1L)))
        assertNull(ReminderPlanner.nextEvent(now, listOf(event(1, hour, calendar = 2)), setOf(1L)))
        assertEquals(now + 24 * hour, ReminderPlanner.nextEvent(now, listOf(event(1, 24 * hour)), setOf(1L))?.beginMs)
    }

    @Test
    fun `a title never shows up when an instance or a plan is printed`() {
        val secret = event(7, lead, title = "Scheidungsanwalt")
        val plan = plan(secret, event(8, -minute, title = "Scheidungsanwalt"))
        for (printed in listOf(secret.toString(), plan.toString(), plan.due.single().toString())) {
            assertFalse("Scheidungsanwalt" in printed, printed)
        }
        assertTrue("7" in secret.toString(), "the id is what a log line may carry")
    }
}
