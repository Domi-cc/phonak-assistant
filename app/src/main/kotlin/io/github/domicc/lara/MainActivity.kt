package io.github.domicc.lara

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.domicc.lara.announce.HourlyAnnouncement
import io.github.domicc.lara.audio.SessionPrograms
import io.github.domicc.lara.audio.SessionService
import io.github.domicc.lara.core.journal.JournalRecord
import io.github.domicc.lara.ui.SetupItem
import io.github.domicc.lara.ui.ShellyDialog
import io.github.domicc.lara.ui.shortAddress
import io.github.domicc.lara.ui.SetupStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The only screen: setup checklist, a button for the echo test, and the live journal. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val app = LaraApp.instance
    val records by app.journal.recent.collectAsState()
    var items by remember { mutableStateOf(SetupStatus.read(context)) }
    var hourly by remember { mutableStateOf(HourlyAnnouncement.isEnabled(context)) }
    var choosingHearingAid by remember { mutableStateOf(false) }
    var showingShelly by remember { mutableStateOf(false) }
    val shellyRemote by app.shelly.remote.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        app.headset.connect()
        items = SetupStatus.read(context)
    }
    LaunchedEffect(Unit) {
        while (true) {
            items = SetupStatus.read(context)
            delay(1_000)
        }
    }

    fun fix(item: SetupItem) {
        when (item.id) {
            SetupStatus.PERMISSIONS -> permissionLauncher.launch(SetupStatus.runtimePermissions)
            SetupStatus.ROLE -> context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            SetupStatus.HEARING_AID -> choosingHearingAid = true
            SetupStatus.BATTERY -> context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)),
            )
        }
    }

    if (showingShelly) ShellyDialog(onClose = { showingShelly = false })

    if (choosingHearingAid) {
        val devices = app.headset.connectedDevices()
        AlertDialog(
            onDismissRequest = { choosingHearingAid = false },
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
                                items = SetupStatus.read(context)
                                choosingHearingAid = false
                            },
                        ) { Text(name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingHearingAid = false }) { Text("Abbrechen") } },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            "Lara im Ohr · Messmodus   v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text("Einrichtung", style = MaterialTheme.typography.labelLarge)
        items.forEach { item ->
            val fixable = item.ok != true && item.id in setOf(SetupStatus.PERMISSIONS, SetupStatus.ROLE, SetupStatus.BATTERY)
            when {
                // the hearing aid can always be chosen again
                item.id == SetupStatus.HEARING_AID -> SetupRow(item, "Wählen") { fix(item) }
                fixable -> SetupRow(item, "Beheben") { fix(item) }
                else -> SetupRow(item, null, null)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { SessionService.start(context, "screen", null) }) { Text("Echo-Test jetzt") }
            OutlinedButton(
                onClick = { app.announcer.announce(HourlyAnnouncement.timeText(System.currentTimeMillis()), "screen") },
            ) { Text("Ansage jetzt") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showingShelly = true }) {
                Text(
                    shellyRemote?.let { "Shelly ${shortAddress(it.address)}: Live-Test" } ?: "Shelly-Fernbedienung anlernen",
                )
            }
            if (shellyRemote != null) {
                Button(onClick = { SessionService.start(context, "screen", null, SessionPrograms.REMOTE) }) {
                    Text("Shelly-Sitzung")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Stundenansage", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "zur vollen Stunde die Uhrzeit ins Hörgerät (Musik-Kanal)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = hourly,
                onCheckedChange = {
                    hourly = it
                    HourlyAnnouncement.setEnabled(context, it)
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Text("Protokoll (neueste oben)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(records.asReversed()) { JournalLine(it) }
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

private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

@Composable
private fun JournalLine(record: JournalRecord) {
    val bad = listOf("error", "refused", "timeout", "lost", "unbound", "skipped").any { it in record.type } ||
        (record.type == "session_end" && record.fields["result"] != "ok")
    val state = buildString {
        if (record.ctx["screenOn"] == false) append(" ·dunkel")
        if (record.ctx["locked"] == true) append(" ·gesperrt")
        if (record.ctx["doze"] == true) append(" ·Doze")
    }
    val details = record.fields.entries.joinToString("  ") { (key, value) -> "$key=$value" }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            clock.format(Date(record.wallMs)) + "  " + record.type + state,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (details.isNotEmpty()) {
            Text(details, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
