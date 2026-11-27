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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.domicc.lara.shelly.ListenerService
import io.github.domicc.lara.ui.CountersSection
import io.github.domicc.lara.ui.JournalPane
import io.github.domicc.lara.ui.MeasureSection
import io.github.domicc.lara.ui.Section
import io.github.domicc.lara.ui.SettingsSection
import io.github.domicc.lara.ui.SetupItem
import io.github.domicc.lara.ui.SetupSection
import io.github.domicc.lara.ui.SetupStatus
import io.github.domicc.lara.ui.ShellySection
import io.github.domicc.lara.ui.countersSummary
import io.github.domicc.lara.ui.setupSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The only screen. Upper part: sections that fold and scroll (setup checklist, starting a
 * measurement, today's counters, the remote, settings). Lower part: the live journal, which
 * stays visible whatever is open above it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = LaraApp.instance
        // The first run takes the speech engine some seconds; the owner is looking at the
        // screen now, not waiting for a number in the hearing aid.
        app.prompts.warm("main_activity")
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) { MainScreen(lifecycle) }
            }
        }
    }

    /**
     * The one place from which the listener can be started before the app is the assistant.
     * Every time the app comes onto the screen, not only when the activity is created: the
     * permission the listener needs may have been granted in Android's settings in the
     * meantime, and nothing tells the app about that. Costs one journal line if it runs.
     */
    override fun onStart() {
        super.onStart()
        ListenerService.start(this, "main_activity")
    }
}

/** @param lifecycle of the activity: what the screen reads every second, it reads only while it is visible */
@SuppressLint("BatteryLife")
@Composable
private fun MainScreen(lifecycle: Lifecycle) {
    val context = LocalContext.current
    val app = LaraApp.instance
    var items by remember { mutableStateOf(SetupStatus.read(context)) }
    val today by app.counters.today.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        app.headset.connect()
        // the listener needs the permission for nearby devices, which may just have been granted
        ListenerService.start(context, "permissions_answered")
        items = SetupStatus.read(context)
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                while (true) {
                    items = SetupStatus.read(context)
                    delay(1_000)
                }
            }
            // on its own, because the first pass reads the whole journal of the day
            launch {
                while (true) {
                    app.counters.refresh()
                    delay(1_000)
                }
            }
        }
    }

    fun fix(item: SetupItem) {
        when (item.id) {
            SetupStatus.PERMISSIONS -> permissionLauncher.launch(SetupStatus.runtimePermissions)
            SetupStatus.ROLE -> context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            SetupStatus.BATTERY -> context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)),
            )
        }
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
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // The sections get what they need, but never so much that the journal disappears.
            val sectionsMax = maxHeight * SECTIONS_SHARE
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().heightIn(max = sectionsMax).verticalScroll(rememberScrollState())) {
                    // open at the start only if something needs attention; then it stays as the owner leaves it
                    Section("Einrichtung", setupSummary(items), startOpen = items.any { it.ok == false }) {
                        SetupSection(items, ::fix)
                    }
                    Section("Messen") { MeasureSection() }
                    Section("Zähler heute", countersSummary(today)) { CountersSection() }
                    Section("Shelly: Anlernen und Funk-Test", startOpen = false) { ShellySection() }
                    Section("Einstellungen", startOpen = false) { SettingsSection() }
                }
                JournalPane(Modifier.weight(1f))
            }
        }
    }
}

/** The part of the screen's height the sections may take at most; the rest is the journal's. */
private const val SECTIONS_SHARE = 0.6f
