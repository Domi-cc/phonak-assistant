package io.github.domicc.lara.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.github.domicc.lara.LaraApp
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fired by the one alarm of the calendar reminders ([CalendarReminders]): a reminder falls
 * due, a reminder that waited for the end of a session is tried again, or it is time to read
 * the calendar again. All of them do the same thing, a new plan; the plan finds out what is due.
 *
 * The plan reads the calendar on another thread. Until it is through, the broadcast is kept
 * open, so that Android does not treat the process as done with its work the moment this
 * method returns. The plan and the announcer each hold a wake lock of their own for what they do.
 * A plan that is not through in time is journaled; it may still follow.
 */
class CalendarAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = LaraApp.instance
        val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, 0L)
        app.journal.log(
            "calendar_alarm",
            mapOf("kind" to intent.getStringExtra(EXTRA_KIND), "lateMs" to System.currentTimeMillis() - scheduledFor),
        )
        val pending = goAsync()
        val finished = AtomicBoolean()
        fun finish(inTime: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            if (!inTime) app.journal.log("calendar_plan_timeout", mapOf("reason" to "alarm", "waitedMs" to OPEN_AT_MOST_MS))
            pending.finish()
        }
        // Android allows a receiver a limited time. A plan that hangs, or that waits behind
        // one that does, must not turn this into "app not responding".
        Handler(Looper.getMainLooper()).postDelayed({ finish(inTime = false) }, OPEN_AT_MOST_MS)
        app.calendar.requestPlan("alarm") { finish(inTime = true) }
    }

    companion object {
        /** The wall-clock time the alarm was set for; the journal says how late it fired. */
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"

        /** Why the alarm was set, a word of [io.github.domicc.lara.core.calendar.WakeKind]. */
        const val EXTRA_KIND = "kind"

        private const val OPEN_AT_MOST_MS = 8_000L
    }
}
