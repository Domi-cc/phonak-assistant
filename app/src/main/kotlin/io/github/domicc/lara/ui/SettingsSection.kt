package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.core.input.TapProgram
import io.github.domicc.lara.core.scan.ScanMode
import io.github.domicc.lara.speech.PromptCache

/** The names the screen uses for the scan modes, the same as in the setup checklist. */
fun modeLabel(mode: ScanMode): String = when (mode) {
    ScanMode.LOW_POWER -> "spar"
    ScanMode.BALANCED -> "mittel"
    ScanMode.LOW_LATENCY -> "schnell"
}

/**
 * The settings a measurement varies. Each change is stored, journaled and applied by the
 * running listener at once; nothing here needs a restart.
 */
@Composable
fun SettingsSection() {
    val app = LaraApp.instance
    val store = app.shellyStore
    val scan by store.scan.collectAsState()
    val wakes by store.wakes.collectAsState()
    val tap by app.tapStore.program.collectAsState()
    val prompts by app.prompts.status.collectAsState()

    ChoiceRow(
        "Hörgeräte-Tap startet",
        "Shelly-Sitzung sagt jeden Tastendruck an (ohne angelernten Shelly: Echo-Test) · Zählsitzung zählt für Messreihen",
        listOf(TapProgram.REMOTE to "Shelly-Sitzung", TapProgram.ECHO to "Echo-Test", TapProgram.COUNT to "Zählsitzung"),
        tap,
        app.tapStore::setProgram,
    )
    SwitchRow(
        "Shelly weckt auf",
        "ein Druck öffnet eine Shelly-Sitzung · aus = stiller Modus: Drücke außerhalb einer Sitzung werden nur protokolliert und gezählt",
        wakes,
        store::setWakes,
    )
    ModeRow("Scan ohne Sitzung", "unbegrenzt nur im Sparmodus", scan.idle) { store.setScan(scan.copy(idle = it)) }
    ModeRow("Scan in der Sitzung", "mittel und schnell verbrauchen das Stunden-Kontingent", scan.session) {
        store.setScan(scan.copy(session = it))
    }
    SwitchRow(
        "Rettungs-Scan",
        "zweiter Scan, der die App auch nach ihrem Ende wieder startet",
        scan.rescue,
    ) { store.setScan(scan.copy(rescue = it)) }
    OutlinedButton(
        enabled = prompts.state != PromptCache.State.WARMING,
        onClick = { app.prompts.rebuild("screen") },
        modifier = Modifier.padding(top = 4.dp),
    ) { Text("Ansagen neu erzeugen") }
    Text(
        "Nötig nach einem Wechsel der Stimme in den Android-Einstellungen. Bis die Ansagen fertig sind, spielt die App Töne.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeRow(label: String, detail: String, selected: ScanMode, onSelect: (ScanMode) -> Unit) =
    ChoiceRow(label, detail, ScanMode.entries.map { it to modeLabel(it) }, selected, onSelect)

/** A setting with a few values to choose from, each with the word the screen uses for it. */
@Composable
private fun <T> ChoiceRow(label: String, detail: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, word) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(word) })
            }
        }
    }
}
