package io.github.domicc.lara.announce

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.domicc.lara.LaraApp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Test feature: announce the time at every full hour. Its real purpose is measurement: once an
 * hour it shows, without anyone touching the phone, whether an alarm wakes the app from deep
 * sleep on time and whether unprompted speech reaches the hearing aid. Off by default.
 */
object HourlyAnnouncement {
    private const val PREFERENCES = "announce"
    private const val KEY_ENABLED = "hourly_enabled"
    const val EXTRA_SCHEDULED_FOR = "scheduled_for"

    /** An alarm that fires this late is not announced any more. */
    const val MAX_LATE_MS = 5 * 60_000L

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    /** Call at every process start: alarms do not survive a reboot or an app update. */
    fun ensureScheduled(context: Context) {
        if (isEnabled(context)) schedule(context)
    }

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = nextFullHour(System.currentTimeMillis())
        val exact = alarms.canScheduleExactAlarms()
        // "AllowWhileIdle" lets the alarm fire in Doze; an hourly alarm is far below Doze's limit.
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context, at))
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context, at))
        LaraApp.instance.journal.log(
            "announce_scheduled",
            mapOf("at" to SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(at)), "exact" to exact),
        )
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, 0))
        LaraApp.instance.journal.log("announce_cancelled")
    }

    private fun pendingIntent(context: Context, at: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, HourlyReceiver::class.java).putExtra(EXTRA_SCHEDULED_FOR, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun nextFullHour(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.HOUR_OF_DAY, 1)
    }.timeInMillis

    /** "8 Uhr" on the hour, "8 Uhr 23" otherwise. */
    fun timeText(at: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = at }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return if (minute == 0) "$hour Uhr" else "$hour Uhr $minute"
    }
}

/** Fired by the alarm at the full hour. */
class HourlyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = LaraApp.instance
        val scheduledFor = intent.getLongExtra(HourlyAnnouncement.EXTRA_SCHEDULED_FOR, 0L)
        val lateMs = System.currentTimeMillis() - scheduledFor
        app.journal.log("announce_alarm", mapOf("lateMs" to lateMs))
        HourlyAnnouncement.ensureScheduled(context)
        if (!HourlyAnnouncement.isEnabled(context)) return
        if (lateMs > HourlyAnnouncement.MAX_LATE_MS) {
            app.journal.log("announce_skipped", mapOf("reason" to "hourly", "why" to "alarm_too_late"))
            return
        }
        app.announcer.announce(HourlyAnnouncement.timeText(scheduledFor), "hourly")
    }
}
