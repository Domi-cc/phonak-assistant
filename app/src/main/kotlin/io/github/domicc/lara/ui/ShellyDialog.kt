package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.shelly.ShellyLive
import kotlinx.coroutines.delay

/** Shows only the end of an address: enough to tell two remotes apart, not enough to track one. */
fun shortAddress(address: String): String = "…" + address.takeLast(5)

/**
 * Pick the Shelly remote and watch live what it sends. The scan behind it only runs while this
 * dialog is open.
 */
@Composable
fun ShellyDialog(onClose: () -> Unit) {
    val app = LaraApp.instance
    val live = app.shellyLive
    val remote by app.shelly.remote.collectAsState()
    val mode by live.mode.collectAsState()
    val candidates by live.candidates.collectAsState()
    val lines by live.lines.collectAsState()
    val totals by live.totals.collectAsState()

    DisposableEffect(Unit) { onDispose { live.stop() } }
    LaunchedEffect(mode) {
        while (mode == ShellyLive.Mode.LIVE) {
            live.tick()
            delay(1_000)
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Shelly-Fernbedienung") },
        confirmButton = { TextButton(onClick = onClose) { Text("Schließen") } },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    remote?.let { "Angelernt: ${shortAddress(it.address)}" } ?: "Noch keine Fernbedienung angelernt.",
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { live.startSearch() }) { Text("Suchen") }
                    OutlinedButton(onClick = { live.startLive() }, enabled = remote != null) { Text("Live-Test") }
                    if (mode != null) TextButton(onClick = { live.stop() }) { Text("Stopp") }
                }
                Spacer(Modifier.height(8.dp))

                when (mode) {
                    ShellyLive.Mode.SEARCH -> {
                        Text("Drück eine Taste auf der Fernbedienung, dann tippe sie hier an.")
                        if (candidates.isEmpty()) {
                            Text("Noch nichts empfangen …", style = MaterialTheme.typography.bodySmall)
                        }
                        candidates.forEach { candidate ->
                            TextButton(
                                onClick = {
                                    if (app.shelly.enrol(candidate.address)) live.startLive()
                                },
                            ) {
                                Column {
                                    Text(
                                        shortAddress(candidate.address) + "   " + candidate.rssi + " dBm" +
                                            (candidate.batteryPercent?.let { "   Akku $it %" } ?: ""),
                                    )
                                    Text(
                                        "${candidate.buttonSlots} Tasten · ${candidate.packets} Pakete" +
                                            if (candidate.lastPress.isEmpty()) "" else " · zuletzt ${candidate.lastPress}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    ShellyLive.Mode.LIVE -> {
                        Text(
                            "empfangen ${totals.received}   verpasst ${totals.missed}   Baken ${totals.beacons}",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Verpasst zählt die App selbst: Die Fernbedienung nummeriert ihre Ereignisse durch.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        lines.forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }

                    null -> Text(
                        if (remote == null) "„Suchen“ zeigt alle Shelly-Tasten in Reichweite."
                        else "„Live-Test“ zeigt jeden Tastendruck. „Suchen“ wählt eine andere Fernbedienung.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (remote != null && mode == null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { app.shelly.forget() }) { Text("Fernbedienung vergessen") }
                }
            }
        },
    )
}
