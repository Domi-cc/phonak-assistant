package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.unit.dp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.announce.HourlyAnnouncement
import io.github.domicc.lara.audio.SessionPrograms
import io.github.domicc.lara.core.input.InputSource

private val buttonPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

/** Long enough to listen for a change in the room, short enough to hold still for. */
private const val SILENCE_TEST_MS = 5_000

/**
 * Starting something by hand: the three session programs, an unprompted announcement, the
 * two buttons of the ear test, the hourly test, and the free-text marker. The session buttons
 * go through the same router as the hearing-aid tap and the remote, so the screen tests the
 * real path.
 *
 * The ear test answers what no journal can: what the hearing aid does to the surroundings
 * while a sound channel is open (docs/measurements/hoerprobe.md). "Stille-Test" opens the
 * media channel with nothing audible in it: five seconds of silence, and the three seconds
 * for which Android keeps the stream open after any sound. "Klingt wieder normal" is pressed
 * the moment the room sounds as before; next to the lines of the channels that gives the
 * hearing aid's own way back as a time. For both the press itself has to be silent, so the
 * whole section is: see the first lines of the function.
 *
 * The marker is for whatever the journal cannot know: "ab jetzt 2 h Ruhe", "Telefon in der
 * Hosentasche", "50 Drücke, Taste 1". It becomes a line of its own between the events.
 */
@Composable
fun MeasureSection() {
    val app = LaraApp.instance
    val context = LocalContext.current
    val remote by app.shellyStore.remote.collectAsState()
    var marker by rememberSaveable { mutableStateOf("") }
    var hourly by remember { mutableStateOf(HourlyAnnouncement.isEnabled(context)) }

    // Nothing in this section clicks. Android plays its click sound for a press on a button,
    // and that sound goes to the hearing aid over the media channel like every sound of the
    // phone (docs/research/hearing-function.md, A7.a). A click would open, audibly, the very
    // channel the ear test listens for: the silence test would not be silent, and "Klingt
    // wieder normal" would switch the hearing aid away again at the moment it is pressed.
    SoundEffectOnInteraction(enabled = false) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { app.router.request(InputSource.SCREEN, SessionPrograms.ECHO) },
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1f),
            ) { Text("Echo-Test jetzt", maxLines = 1) }
            Button(
                // without a remote this session would only wait for its end
                enabled = remote != null,
                onClick = { app.router.request(InputSource.SCREEN, SessionPrograms.REMOTE) },
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1f),
            ) { Text("Shelly-Sitzung", maxLines = 1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { app.router.request(InputSource.SCREEN, SessionPrograms.COUNT) },
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1f),
            ) { Text("Zählsitzung starten", maxLines = 1) }
            OutlinedButton(
                onClick = { app.announcer.announce(HourlyAnnouncement.timeText(System.currentTimeMillis()), "screen") },
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1f),
            ) { Text("Ansage jetzt", maxLines = 1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { app.announcer.playSilence(SILENCE_TEST_MS, "screen_silence") },
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1f),
            ) { Text("Stille-Test (5 s)", maxLines = 1) }
            OutlinedButton(
                onClick = {
                    app.journal.log("marker", mapOf("text" to "surroundings_normal", "origin" to InputSource.SCREEN.word))
                },
                contentPadding = buttonPadding,
                modifier = Modifier.weight(1f),
            ) { Text("Klingt wieder normal", maxLines = 1) }
        }
        Text(
            "Hörprobe: Der Stille-Test spielt 5 s Stille über den Musik-Kanal; offen ist er etwa 8 s, weil Android ihn 3 s nachhält. " +
                "„Klingt wieder normal“ drücken, sobald die Umgebung wieder klingt wie vorher.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            "Stundenansage",
            "zur vollen Stunde die Uhrzeit ins Hörgerät (Musik-Kanal)",
            hourly,
        ) {
            hourly = it
            HourlyAnnouncement.setEnabled(context, it)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = marker,
                onValueChange = { marker = it },
                label = { Text("Marker fürs Protokoll") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                enabled = marker.isNotBlank(),
                onClick = {
                    app.journal.log("marker", mapOf("text" to marker.trim(), "origin" to InputSource.SCREEN.word))
                    marker = ""
                },
                contentPadding = buttonPadding,
            ) { Text("Schreiben") }
        }
    }
}
