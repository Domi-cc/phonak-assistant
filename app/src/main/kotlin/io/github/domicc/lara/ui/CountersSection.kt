package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.core.stats.DayStats

/** For the folded counters: the two numbers a measurement is about. */
fun countersSummary(stats: DayStats): String = "Shelly ${stats.shellyEvents} · verpasst ${stats.shellyMissed}"

/**
 * Today's numbers, counted from the journal (see [io.github.domicc.lara.journal.DayCounters]).
 * Simulated presses are not in them.
 */
@Composable
fun CountersSection() {
    val stats by LaraApp.instance.counters.today.collectAsState()
    CounterRow("Shelly-Ereignisse", "${stats.shellyEvents} empfangen · ${stats.shellyMissed} verpasst")
    CounterRow("Hörgeräte-Taps", stats.taps.toString())
    CounterRow(
        "Sitzungen",
        "${stats.sessionsOk} ok · ${stats.sessionsCancelled} abgebrochen · ${stats.sessionsFailed} fehlgeschlagen",
    )
    Text(
        "Aus dem Protokoll von heute gezählt. „Verpasst“ zählt die Lücken in den Paketnummern des Shelly; simulierte Drücke zählen nicht.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun CounterRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
    }
}
