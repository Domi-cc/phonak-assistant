package io.github.domicc.lara.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.OperationCanceledException
import android.provider.CalendarContract
import io.github.domicc.lara.core.calendar.CalendarInstance
import io.github.domicc.lara.journal.Journal

/**
 * One calendar on the phone, for the list on the screen.
 *
 * Deliberately not a data class: its name and its account (an e-mail address) are shown on
 * the phone's own screen and go nowhere else, so nothing may print them by accident.
 *
 * @param id the number Android's calendar storage gave it; the only thing of a calendar that
 * the journal ever names
 * @param visible whether the calendar app shows it
 */
class PhoneCalendar(val id: Long, val name: String, val account: String, val visible: Boolean)

/**
 * Reads appointments from Android's calendar storage (`CalendarContract`). That storage holds
 * every calendar that is synchronised to the phone, whichever account it belongs to and
 * whichever app shows it; reading it needs the permission `READ_CALENDAR` and no network.
 *
 * Every call into the storage is guarded: without the permission Android throws, and a
 * storage that is being updated or is not there throws other things. A storage that does not
 * answer is given [READ_TIMEOUT_MS] and then asked to drop the question; Android itself would
 * wait for good, and with it every later plan. A failure is journaled and returned as null.
 * The journal gets the kind of failure only, never its message, and never a title, a calendar
 * name or an account. The calls block; not for the main thread.
 */
class CalendarReader(private val context: Context, private val journal: Journal) {

    private val mainThread = Handler(Looper.getMainLooper())

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Every calendar on the phone, sorted by account and name; null if they could not be read. */
    fun calendars(): List<PhoneCalendar>? = read("calendars", CalendarContract.Calendars.CONTENT_URI, CALENDAR_COLUMNS, CALENDAR_ORDER) {
        PhoneCalendar(
            id = it.getLong(0),
            name = it.getString(1).orEmpty(),
            account = it.getString(2).orEmpty(),
            visible = it.getInt(3) != 0,
        )
    }

    /**
     * Every occurrence that overlaps the time from [fromMs] to [toMs], of every calendar; null
     * if they could not be read. Android works out the occurrences of recurring appointments
     * itself. What is announced of them is decided in
     * [io.github.domicc.lara.core.calendar.ReminderPlanner].
     */
    fun instances(fromMs: Long, toMs: Long): List<CalendarInstance>? {
        val range = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(range, fromMs)
        ContentUris.appendId(range, toMs)
        return read("instances", range.build(), INSTANCE_COLUMNS, CalendarContract.Instances.BEGIN + " ASC") {
            CalendarInstance(
                eventId = it.getLong(0),
                calendarId = it.getLong(1),
                beginMs = it.getLong(2),
                allDay = it.getInt(3) != 0,
                cancelled = it.getInt(4) == CalendarContract.Events.STATUS_CANCELED,
                // an appointment without guests has no answer of the owner's; that reads as 0
                declined = it.getInt(5) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                title = it.getString(6),
            )
        }
    }

    /**
     * @param what which list this is, for the journal
     * @param row reads one row; the columns are in the order of [columns]
     */
    private fun <T> read(what: String, uri: Uri, columns: Array<String>, order: String, row: (Cursor) -> T): List<T>? {
        if (!hasPermission()) {
            journal.log("calendar_provider_error", mapOf("what" to what, "why" to "no_permission"))
            return null
        }
        // The reading thread is stuck in the question, so the end of the wait comes from another one.
        val giveUp = CancellationSignal()
        val timeout = Runnable { giveUp.cancel() }
        mainThread.postDelayed(timeout, READ_TIMEOUT_MS)
        return try {
            val cursor = context.contentResolver.query(uri, columns, null, null, order, giveUp)
            if (cursor == null) {
                journal.log("calendar_provider_error", mapOf("what" to what, "why" to "no_answer"))
                return null
            }
            cursor.use { rows -> buildList { while (rows.moveToNext()) add(row(rows)) } }
        } catch (e: OperationCanceledException) {
            journal.log("calendar_provider_error", mapOf("what" to what, "why" to "timeout", "afterMs" to READ_TIMEOUT_MS))
            null
        } catch (e: Exception) {
            // The class of the exception only: its message may quote what was asked or read.
            // A SecurityException is the permission taken away between the check and the call.
            journal.log("calendar_provider_error", mapOf("what" to what, "why" to "exception", "exception" to e.javaClass.name))
            null
        } finally {
            mainThread.removeCallbacks(timeout)
        }
    }

    private companion object {
        /**
         * How long one question to the calendar storage may take. A plan asks two, and the
         * alarm that leads to a plan keeps its broadcast open for 8 s.
         */
        const val READ_TIMEOUT_MS = 3_000L

        val CALENDAR_COLUMNS = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.VISIBLE,
        )
        const val CALENDAR_ORDER =
            CalendarContract.Calendars.ACCOUNT_NAME + " ASC, " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC"

        val INSTANCE_COLUMNS = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.TITLE,
        )
    }
}
