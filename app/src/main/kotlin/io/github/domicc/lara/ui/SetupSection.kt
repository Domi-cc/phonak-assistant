package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
 * @param onFix called for an item that is not in order and that the screen can help with
 */
@Composable
fun SetupSection(items: List<SetupItem>, onFix: (SetupItem) -> Unit) {
    items.forEach { item ->
        SetupRow(item, onFix = if (item.ok != true && item.id in fixable) ({ onFix(item) }) else null)
    }
}

@Composable
private fun SetupRow(item: SetupItem, onFix: (() -> Unit)?) {
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
        if (onFix != null) TextButton(onClick = onFix) { Text("Beheben") }
    }
}
