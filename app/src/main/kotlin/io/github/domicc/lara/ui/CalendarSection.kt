package io.github.domicc.lara.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.calendar.CalendarStatus
import io.github.domicc.lara.calendar.PhoneCalendar
import io.github.domicc.lara.core.calendar.CalendarTexts
import io.github.domicc.lara.core.calendar.Reminder
import io.github.domicc.lara.core.calendar.ReminderPlanner
import io.github.domicc.lara.core.input.InputSource
import java.time.ZoneId

/** For the folded section: whether appointments are announced, and when the next one is. */
fun calendarSummary(status: CalendarStatus): String = when {
    !status.enabled -> "aus"
    !status.permission -> "Berechtigung fehlt"
    !status.planned -> "an"
    !status.readable -> "Kalender nicht lesbar"
    status.next == null -> "an · keine Ansage in den nächsten 24 Stunden"
    else -> "nächste Ansage " + announcedAt(status.next)
}

/** When [reminder] will be spoken, as the screen shows a time. */
private fun announcedAt(reminder: Reminder): String = clock(reminder.beginMs - ReminderPlanner.LEAD_MS)

private fun clock(atMs: Long): String = CalendarTexts.clock(System.currentTimeMillis(), atMs, ZoneId.systemDefault())

/**
 * The spoken reminders before appointments: the switch, the permission to read the calendar,
 * which calendars are announced, what is said next, a button that speaks the next
 * appointment at once, to hear that the app reads the calendars the owner means, and the
 * button "Erinnerung testen", which plays a reminder exactly as it will sound ten minutes
 * before the next appointment. That one is always there, also while the reminders are off:
 * it is how the owner tries out how loud a reminder is.
 *
 * This is the one place where titles of appointments and names of calendars are shown. They
 * are not in the journal below it.
 */
@Composable
fun CalendarSection() {
    val app = LaraApp.instance
    val context = LocalContext.current
    val enabled by app.calendarStore.enabled.collectAsState()
    val choice by app.calendarStore.choice.collectAsState()
    val status = app.calendar.status.collectAsState().value
    val next = status.next
    // Whether Android will not show its question any more, so that only its settings can
    // grant. It asks twice at most. Nothing says so beforehand: it shows in the answer to a
    // request, and after the section was folded the app has to ask once more to find out.
    var refused by rememberSaveable { mutableStateOf(false) }
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After a refusal Android offers to explain the permission for as long as it would
        // still ask. After the last one it does not. A question that was only waved away
        // looks the same as that last refusal; it costs one detour through the settings.
        refused = !granted && (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) == false
        app.calendar.requestPlan("permission_answered")
    }

    SwitchRow("Termine ansagen", "10 Minuten vorher ins Hörgerät", enabled) { on ->
        app.calendarStore.setEnabled(on)
        if (on && !app.calendar.hasPermission()) permission.launch(Manifest.permission.READ_CALENDAR)
    }
    // the rest describes the last plan; right after the switch or the start of the app that
    // plan is still on its way
    if (!enabled || !status.enabled || !status.planned) {
        TestButtons(withNext = false)
        return
    }

    if (!status.permission) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
                Text("Kalender-Berechtigung fehlt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Text(
                    if (refused) "In den Android-Einstellungen der App unter „Berechtigungen“ den Kalender erlauben."
                    else "Ohne sie kann die App keine Termine lesen und sagt nichts an.",
                    style = small,
                    color = muted,
                )
            }
            TextButton(
                onClick = {
                    if (refused) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    } else {
                        permission.launch(Manifest.permission.READ_CALENDAR)
                    }
                },
            ) { Text(if (refused) "Einstellungen" else "Erlauben") }
        }
        TestButtons(withNext = false)
        return
    }

    Text(
        when {
            !status.readable -> "Der Kalender des Telefons ließ sich nicht lesen, siehe Protokoll."
            next == null -> "Nächste Ansage: keine in den nächsten 24 Stunden"
            else -> "Nächste Ansage: ${announcedAt(next)} · ${CalendarTexts.join(next.titles)} (Beginn ${clock(next.beginMs)})"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (status.readable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 3.dp),
    )

    if (status.calendars.isEmpty() && status.readable) {
        Text("Auf dem Telefon ist kein Kalender eingerichtet.", style = small, color = muted)
    }
    status.calendars.forEach { calendar ->
        val checked = choice.selects(calendar.id, calendar.visible)
        CalendarRow(calendar, checked) { app.calendarStore.setSelected(calendar.id, it) }
    }
    if (status.calendars.isNotEmpty()) {
        Text(
            "Ohne eigene Auswahl gilt, was die Kalender-App anzeigt. Ganztägige, abgesagte und abgelehnte Termine werden nicht angesagt.",
            style = small,
            color = muted,
        )
    }

    TestButtons(withNext = true)
}

/**
 * The buttons that speak at once, next to each other where the screen has the room.
 * "Erinnerung testen" does not click: Android's click sound for a press would go into the
 * hearing aid right in front of the reminder that is to be judged.
 *
 * @param withNext also "Nächsten Termin jetzt ansagen", which needs the calendar and so is
 * only offered while the reminders are on and may read it
 */
@Composable
private fun TestButtons(withNext: Boolean) {
    val app = LaraApp.instance
    FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (withNext) {
            OutlinedButton(onClick = { app.calendar.announceNext(InputSource.SCREEN.word) }) { Text("Nächsten Termin jetzt ansagen") }
        }
        SoundEffectOnInteraction(enabled = false) {
            OutlinedButton(onClick = { app.calendar.rehearse(InputSource.SCREEN.word) }) { Text("Erinnerung testen") }
        }
    }
    Text(
        "„Erinnerung testen“ spielt die Erinnerung so, wie sie zehn Minuten vor dem nächsten Termin klingt; " +
            "ohne Termin oder ohne Kalender mit „Beispieltermin“. Wie laut: Abschnitt „Ansagen“.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One calendar with its tick; the whole line takes the touch. */
@Composable
private fun CalendarRow(calendar: PhoneCalendar, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
            Text(calendar.name.ifBlank { "(ohne Namen)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                calendar.account,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
