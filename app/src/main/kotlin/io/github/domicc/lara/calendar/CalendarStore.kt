package io.github.domicc.lara.calendar

import android.content.Context
import androidx.core.content.edit
import io.github.domicc.lara.core.calendar.CalendarChoice
import io.github.domicc.lara.core.calendar.InstanceKey
import io.github.domicc.lara.core.calendar.ReminderPlanner
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app remembers about the calendar reminders between starts: whether they are
 * switched on, which calendars the owner ticked or unticked by hand, and which occurrences
 * are done with. Kept in app-private preferences.
 *
 * The two settings are flows: [CalendarReminders] plans anew as soon as one of them changes,
 * so the screen only has to change the setting. Every change is journaled, with the number
 * Android gave the calendar and never with its name: the name of a calendar is often a
 * person's name, and its account is an e-mail address.
 *
 * Off by default: the app reads nothing from the calendar until the owner asks for it.
 */
class CalendarStore(context: Context, private val journal: Journal) {

    private val prefs = context.getSharedPreferences("calendar", Context.MODE_PRIVATE)
    private val enabledState = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    private val choiceState = MutableStateFlow(CalendarChoice(ids(KEY_ON), ids(KEY_OFF)))

    /** The announced occurrences; the copy in memory is the one that is read. Guarded by this. */
    private var handledKeys: Set<InstanceKey> =
        prefs.getStringSet(KEY_HANDLED, null).orEmpty().mapNotNull(InstanceKey::decode).toSet()

    /** "Termine ansagen". */
    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    /** What the owner chose by hand; calendars he never touched follow the calendar app. */
    val choice: StateFlow<CalendarChoice> = choiceState.asStateFlow()

    @Synchronized
    fun setEnabled(on: Boolean) {
        if (on == enabledState.value) return
        prefs.edit { putBoolean(KEY_ENABLED, on) }
        journal.log("calendar_enabled", mapOf("on" to on))
        enabledState.value = on
    }

    /** The owner ticked ([selected]) or unticked a calendar on the screen. */
    @Synchronized
    fun setSelected(calendarId: Long, selected: Boolean) {
        val choice = choiceState.value.with(calendarId, selected)
        if (choice == choiceState.value) return
        save(choice)
        journal.log(
            "calendar_selection",
            mapOf("calendarId" to calendarId, "selected" to selected, "on" to choice.on.size, "off" to choice.off.size),
        )
        choiceState.value = choice
    }

    /**
     * Forgets what the owner chose for calendars that are no longer on the phone. Android
     * gives the number of a removed calendar to the next new one, and that one must not
     * inherit a tick or a missing tick ([CalendarChoice.onlyFor]). Called with every list of
     * calendars the app reads. A calendar that goes and whose number comes back between two
     * reads is not noticed; while the reminders are on, the app reads at every change.
     *
     * @param present the ids of every calendar on the phone
     */
    @Synchronized
    fun forgetAllBut(present: Set<Long>) {
        val before = choiceState.value
        val choice = before.onlyFor(present)
        if (choice == before) return
        save(choice)
        journal.log(
            "calendar_selection_forgotten",
            mapOf(
                "calendars" to before.on.size + before.off.size - choice.on.size - choice.off.size,
                "on" to choice.on.size,
                "off" to choice.off.size,
            ),
        )
        choiceState.value = choice
    }

    private fun save(choice: CalendarChoice) = prefs.edit {
        putStringSet(KEY_ON, choice.on.map(Long::toString).toSet())
        putStringSet(KEY_OFF, choice.off.map(Long::toString).toSet())
    }

    /** The occurrences that are done with (announced, given up or missed) and must not come up again. */
    @Synchronized
    fun handled(): Set<InstanceKey> = handledKeys

    /**
     * Remembers [keys] as done with and forgets those whose appointment began more than a day
     * ago. Written to storage before this returns, not some time later: a process that ends
     * right after an announcement must not say it again when it comes back. Not for the main
     * thread.
     */
    @Synchronized
    fun markHandled(keys: Collection<InstanceKey>, nowMs: Long) {
        val kept = ReminderPlanner.prune(handledKeys + keys, nowMs)
        if (kept == handledKeys) return
        val written = prefs.edit().putStringSet(KEY_HANDLED, kept.map(InstanceKey::encode).toSet()).commit()
        // The copy in memory holds for as long as the process lives; after that an
        // announcement could come a second time.
        if (!written) journal.log("calendar_store_error", mapOf("what" to "handled", "keys" to kept.size))
        handledKeys = kept
    }

    private fun ids(key: String): Set<Long> = prefs.getStringSet(key, null).orEmpty().mapNotNull(String::toLongOrNull).toSet()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ON = "calendars_on"
        const val KEY_OFF = "calendars_off"
        const val KEY_HANDLED = "handled"
    }
}
