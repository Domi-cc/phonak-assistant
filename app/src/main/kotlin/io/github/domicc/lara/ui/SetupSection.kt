package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.domicc.lara.LaraApp

/** The items the screen can take the owner to a remedy for. */
private val fixable = setOf(SetupStatus.PERMISSIONS, SetupStatus.ROLE, SetupStatus.BATTERY)

/** For the folded section: one line that says whether anything needs attention. */
fun setupSummary(items: List<SetupItem>): String {
    val open = items.count { it.ok == false }
    return if (open == 0) "alles in Ordnung" else "$open offen"
}

/**
 * The setup checklist.
 *
 * @param onFix called for an item that is not in order and that the screen can help with, and
 * for the hearing aid whenever the owner wants to choose it
 */
@Composable
fun SetupSection(items: List<SetupItem>, onFix: (SetupItem) -> Unit) {
    items.forEach { item ->
        when {
            // the hearing aid can always be chosen again
            item.id == SetupStatus.HEARING_AID -> SetupRow(item, "Wählen") { onFix(item) }
            item.ok != true && item.id in fixable -> SetupRow(item, "Beheben") { onFix(item) }
            else -> SetupRow(item, null, null)
        }
    }
}

@Composable
private fun SetupRow(item: SetupItem, action: String?, onFix: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (item.ok) {
                true -> "✓"
                false -> "✗"
                null -> "–"
            },
            color = if (item.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onFix != null && action != null) TextButton(onClick = onFix) { Text(action) }
    }
}

/**
 * The owner says which of the connected hands-free devices is the hearing aid. Everything
 * audible depends on this one choice, so it is made by hand and never guessed.
 *
 * @param onClose called when the dialog goes away, with or without a choice
 */
@Composable
fun HearingAidDialog(onClose: () -> Unit) {
    val app = LaraApp.instance
    val devices = app.headset.connectedDevices()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Welches Gerät ist das Hörgerät?") },
        text = {
            Column {
                Text(
                    "Nur an dieses Gerät gehen Töne, Ansagen und Sitzungen. Angezeigt werden die gerade " +
                        "verbundenen Freisprech-Geräte.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                if (devices.isEmpty()) Text("Kein Freisprech-Gerät verbunden.")
                devices.forEach { (address, name) ->
                    TextButton(
                        onClick = {
                            app.hearingAid.select(address, name)
                            app.journal.log("hearing_aid_selected", mapOf("name" to name))
                            onClose()
                        },
                    ) { Text(name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Abbrechen") } },
    )
}
