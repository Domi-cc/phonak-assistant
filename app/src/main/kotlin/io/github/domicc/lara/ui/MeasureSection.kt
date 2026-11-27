package io.github.domicc.lara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.audio.SessionPrograms
import io.github.domicc.lara.core.input.InputSource

private val buttonPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

/**
 * Starting a measurement by hand, and the free-text marker. The buttons go through the same
 * router as the hearing-aid tap and the remote, so the screen tests the real path.
 *
 * The marker is for whatever the journal cannot know: "ab jetzt 2 h Ruhe", "Telefon in der
 * Hosentasche", "50 Drücke, Taste 1". It becomes a line of its own between the events.
 */
@Composable
fun MeasureSection() {
    val app = LaraApp.instance
    var marker by rememberSaveable { mutableStateOf("") }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { app.router.request(InputSource.SCREEN, SessionPrograms.ECHO) },
            contentPadding = buttonPadding,
            modifier = Modifier.weight(1f),
        ) { Text("Echo-Test jetzt", maxLines = 1) }
        Button(
            onClick = { app.router.request(InputSource.SCREEN, SessionPrograms.COUNT) },
            contentPadding = buttonPadding,
            modifier = Modifier.weight(1f),
        ) { Text("Zählsitzung starten", maxLines = 1) }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
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
