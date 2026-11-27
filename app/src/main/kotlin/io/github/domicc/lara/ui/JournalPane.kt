package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.core.journal.JournalRecord
import io.github.domicc.lara.journal.JournalShare
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

/**
 * Words in an event name that mean something went wrong. A plain "skipped" is not among them:
 * a scan that is skipped because no remote is enrolled is routine. An announcement that was
 * not spoken is not.
 */
private val badWords = listOf(
    "error", "refused", "timeout", "lost", "unbound", "failed", "dropped", "rejected", "gave_up", "unreadable", "unhandled",
    "announce_skipped", "no_scan",
)

/**
 * The live journal, newest line on top, and the two buttons that hand journal files to the
 * share sheet. Always on the screen: during a measurement this is what the owner watches.
 */
@Composable
fun JournalPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val journal = LaraApp.instance.journal
    val records by journal.recent.collectAsState()
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Protokoll (neueste oben)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { JournalShare.share(context, journal, listOf(journal.fileOf(System.currentTimeMillis()))) }) {
                Text("Heute teilen")
            }
            TextButton(onClick = { JournalShare.share(context, journal, journal.files()) }) { Text("Alle teilen") }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(records.asReversed()) { JournalLine(it) }
        }
    }
}

/**
 * Fields that hold a Bluetooth address. The screen shows them shortened, like every address;
 * the journal files have them in full.
 */
private val addressFields = setOf("address", "replaced")

/** A failure, or a loss: the two things a measurement looks for are shown in the error colour. */
private fun isBad(record: JournalRecord): Boolean =
    badWords.any { it in record.type } ||
        (record.type == "session_end" && record.fields["result"] != "ok") ||
        record.fields["problem"] != null ||
        listOf("missed", "missedBefore").any { key -> (record.fields[key] as? Number)?.let { it.toLong() > 0 } == true }

@Composable
private fun JournalLine(record: JournalRecord) {
    val state = buildString {
        if (record.ctx["screenOn"] == false) append(" ·dunkel")
        if (record.ctx["locked"] == true) append(" ·gesperrt")
        if (record.ctx["doze"] == true) append(" ·Doze")
    }
    val details = record.fields.entries.joinToString("  ") { (key, value) ->
        "$key=" + if (key in addressFields && value is String) shortAddress(value) else value
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            clock.format(Date(record.wallMs)) + "  " + record.type + state,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isBad(record) -> MaterialTheme.colorScheme.error
                // the owner's own notes should be easy to find again
                record.type == "marker" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (details.isNotEmpty()) {
            Text(details, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
