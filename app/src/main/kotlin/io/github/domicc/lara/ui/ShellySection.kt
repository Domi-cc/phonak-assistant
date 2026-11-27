package io.github.domicc.lara.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.core.bthome.ButtonEvent
import io.github.domicc.lara.shelly.BleScans
import io.github.domicc.lara.shelly.ForegroundScan
import io.github.domicc.lara.shelly.HeardEvent
import io.github.domicc.lara.shelly.RawPacket
import io.github.domicc.lara.shelly.RemotePhrases
import io.github.domicc.lara.shelly.SeenDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SHOWN_EVENTS = 20
private const val SHOWN_PACKETS = 12
private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

/** Shows only the end of an address: enough to tell two remotes apart, not enough to track one. */
fun shortAddress(address: String): String = "…" + address.takeLast(5)

/**
 * Searching and picking the remote, and the live test, on one scan: the scan of the screen
 * hears every BTHome sender in range in the fastest mode. With the app on the screen Android
 * throttles nothing, so this view is the positive control for the background measurements: a
 * press that shows up here but not in the background was lost to Android, not to the remote.
 *
 * The scan runs only between "Scan starten" and "Scan stoppen". It ends when this section is
 * folded away, and by itself when the app leaves the screen; nothing here starts it again,
 * because a scan that runs unnoticed spoils the measurements that follow.
 *
 * Addresses are shown shortened; the journal has them in full.
 */
@Composable
fun ShellySection() {
    val app = LaraApp.instance
    val store = app.shellyStore
    val context = LocalContext.current
    val scan = remember { ForegroundScan(context.applicationContext as Application, app.journal, store) }
    DisposableEffect(scan) { onDispose { scan.stop("view_closed") } }

    val state by scan.state.collectAsState()
    val devices by scan.devices.collectAsState()
    val events by scan.events.collectAsState()
    val packets by scan.packets.collectAsState()
    val remote by store.remote.collectAsState()
    val config by store.scan.collectAsState()
    val running = state == ForegroundScan.State.RUNNING
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            remote?.let { "Angelernt: ${shortAddress(it.address)}" } ?: "Kein Shelly angelernt",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (remote != null) TextButton(onClick = store::forget) { Text("Vergessen") }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { if (running) scan.stop() else scan.start() }) { Text(if (running) "Scan stoppen" else "Scan starten") }
        Text(stateText(state), style = small, color = if (state.isProblem) MaterialTheme.colorScheme.error else muted)
    }
    SwitchRow(
        "Passive Scan-Art",
        if (BleScans.scanTypeSelectable) "aus = aktiv, Androids Standard · gilt auch für den Lausch-Dienst"
        else "auf diesem Android nicht wählbar: es wird immer aktiv gescannt",
        config.passive,
    ) { passive ->
        store.setScan(config.copy(passive = passive))
        // a running scan keeps the type it was started with
        if (running) {
            scan.stop("scan_type")
            scan.start()
        }
    }

    // With a remote enrolled the test is about that remote only.
    val watched = remote?.let { enrolled -> devices.filter { it.address.equals(enrolled.address, ignoreCase = true) } } ?: devices
    Text(
        "Ereignisse: ${watched.sumOf { it.events }} · Lücken: ${watched.sumOf { it.missed }} · Pakete: ${watched.sumOf { it.packets }}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        if (remote == null) "Zum Anlernen: Scan starten, eine Taste am Shelly drücken, das Gerät mit diesem Tastendruck wählen."
        else "Gezählt wird der angelernte Shelly, seit der Scan läuft. Lücken zählt die App selbst: Der Shelly nummeriert seine Ereignisse durch.",
        style = small,
        color = muted,
    )

    devices.forEach { device ->
        DeviceRow(
            device,
            enrolled = device.address.equals(remote?.address, ignoreCase = true),
            onEnrol = { store.enrol(device.address) },
        )
    }

    val heard = events.filter { event -> watched.any { it.address == event.address } }
    if (heard.isNotEmpty()) {
        Text("Ereignisse (neueste oben)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        heard.asReversed().take(SHOWN_EVENTS).forEach { EventLine(it) }
    }
    val shown = packets.filter { packet -> watched.any { it.address == packet.address } }
    if (shown.isNotEmpty()) {
        Text("Rohpakete (neueste oben)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        shown.asReversed().take(SHOWN_PACKETS).forEach { PacketLine(it) }
    }
}

private val ForegroundScan.State.isProblem: Boolean
    get() = this != ForegroundScan.State.RUNNING && this != ForegroundScan.State.STOPPED

private fun stateText(state: ForegroundScan.State): String = when (state) {
    ForegroundScan.State.STOPPED -> "Scan läuft nicht"
    ForegroundScan.State.RUNNING -> "Scan läuft (schnellster Modus)"
    ForegroundScan.State.NO_PERMISSION -> "Berechtigung „Geräte in der Nähe“ fehlt"
    ForegroundScan.State.BLUETOOTH_OFF -> "Bluetooth ist aus"
    ForegroundScan.State.TOO_MANY_STARTS -> "zu viele Scan-Starts, in einer halben Minute noch einmal"
    ForegroundScan.State.FAILED -> "Start fehlgeschlagen, siehe Protokoll"
}

@Composable
private fun DeviceRow(device: SeenDevice, enrolled: Boolean, onEnrol: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
            Text(shortAddress(device.address), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(
                buildString {
                    append("${device.rssi} dBm · ${device.packets} Pakete")
                    device.battery?.let { append(" · Akku $it %") }
                    val button = device.lastButton
                    val at = device.lastEventMs
                    if (button != null && at != null) {
                        append(" · Taste $button ${device.lastEvent?.let(::gestureLabel)} um ${clock.format(Date(wallTime(at)))}")
                    }
                    if (device.encrypted) append(" · verschlüsselt, nicht lesbar")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enrolled) {
            Text("angelernt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else if (!device.encrypted) {
            TextButton(onClick = onEnrol) { Text("Anlernen") }
        }
    }
}

/** One event in the words the hearing aid would say, and what arrived of its burst. */
@Composable
private fun EventLine(event: HeardEvent) {
    val what = if (event.presses.isEmpty()) "Bake (kein Tastendruck)"
    else event.presses.joinToString(" + ") { RemotePhrases.forPress(it.button, it.gesture) }
    val burst = event.packets?.let { " · $it Pakete in ${event.spanMs} ms" } ?: ""
    val missed = if (event.missedBefore > 0) "  ⚠ davor ${event.missedBefore} verpasst" else ""
    Text(
        "${clock.format(Date(wallTime(event.elapsedMs)))}  #${event.packetId ?: "?"}  $what  ${event.rssi} dBm$burst$missed",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        color = if (event.missedBefore > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Composable
private fun PacketLine(packet: RawPacket) {
    val pressed = packet.buttons
        .mapIndexedNotNull { index, event -> if (event == ButtonEvent.NONE) null else "${index + 1}:${gestureLabel(event)}" }
        .joinToString(",")
        .ifEmpty { "–" }
    Text(
        "${clock.format(Date(wallTime(packet.elapsedMs)))}  #${packet.packetId ?: "?"}  $pressed  ${packet.rssi} dBm" +
            (packet.problem?.let { "  $it" } ?: "") + "\n${packet.hex}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        color = if (packet.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

/** How a button was pressed, in the words the app also speaks. */
private fun gestureLabel(event: ButtonEvent): String = when (event) {
    ButtonEvent.NONE -> "–"
    ButtonEvent.PRESS -> "kurz"
    ButtonEvent.DOUBLE -> "doppelt"
    ButtonEvent.TRIPLE -> "dreifach"
    ButtonEvent.LONG -> "lang"
    ButtonEvent.LONG_DOUBLE -> "lang doppelt"
    ButtonEvent.LONG_TRIPLE -> "lang dreifach"
    ButtonEvent.HOLD -> "gehalten"
    ButtonEvent.HOLD_RELEASE -> "losgelassen"
    ButtonEvent.UNKNOWN -> "unbekannt"
}

/** The wall-clock time of a moment given on the clock the Bluetooth stack stamps packets with. */
private fun wallTime(elapsedMs: Long): Long = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsedMs)
