package io.github.domicc.lara.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.assistant.LaraVoiceInteractionService
import io.github.domicc.lara.core.scan.ScanStatus
import io.github.domicc.lara.journal.DeviceState
import io.github.domicc.lara.shelly.ListenerService
import io.github.domicc.lara.speech.PromptCache
import io.github.domicc.lara.trigger.VoiceCommandActivity

/** One line of the setup checklist. [ok] is null when the state is neither good nor bad. */
data class SetupItem(val id: String, val label: String, val ok: Boolean?, val detail: String)

/** Reads the state of everything the app needs. Cheap enough to call every second. */
object SetupStatus {

    val runtimePermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    const val PERMISSIONS = "permissions"
    const val ROLE = "role"
    const val VOICE_BUTTON = "voice_button"
    const val HEARING_AID = "hearing_aid"
    const val BATTERY = "battery"
    const val SHELLY = "shelly"
    const val LISTENER = "listener"
    const val PROMPTS = "prompts"

    fun read(context: Context): List<SetupItem> = listOf(
        permissions(context),
        role(context),
        voiceButton(context),
        hearingAid(),
        battery(context),
        shelly(),
        listener(),
        prompts(),
    )

    private fun permissions(context: Context): SetupItem {
        val missing = runtimePermissions.filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        return SetupItem(
            PERMISSIONS, "Berechtigungen", missing.isEmpty(),
            if (missing.isEmpty()) "Mikrofon, Geräte in der Nähe, Mitteilungen"
            else "fehlt: " + missing.joinToString { it.substringAfterLast('.') },
        )
    }

    private fun role(context: Context): SetupItem {
        val held = DeviceState.holdsAssistantRole(context)
        val bound = LaraVoiceInteractionService.ready
        return SetupItem(
            ROLE, "Assistentenrolle", held && bound,
            when {
                held && bound -> "Lara · Dienst gebunden"
                held -> "Lara gewählt, Dienst noch nicht gebunden"
                else -> "nicht gewählt"
            },
        )
    }

    /** Which app Android would start for the hearing-aid tap right now. */
    private fun voiceButton(context: Context): SetupItem {
        val resolved = context.packageManager
            .resolveActivity(Intent(Intent.ACTION_VOICE_COMMAND), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
        val ours = ComponentName(context, VoiceCommandActivity::class.java)
        return when {
            resolved == null -> SetupItem(VOICE_BUTTON, "Hörgeräte-Sprachtaste", false, "keine App zuständig")
            resolved.packageName == ours.packageName && resolved.name == ours.className ->
                SetupItem(VOICE_BUTTON, "Hörgeräte-Sprachtaste", true, "→ Lara („Immer“)")
            resolved.packageName == "android" ->
                SetupItem(VOICE_BUTTON, "Hörgeräte-Sprachtaste", false, "kein Standard: entsperrt tippen, Lara › „Immer“ wählen")
            else -> SetupItem(VOICE_BUTTON, "Hörgeräte-Sprachtaste", false, "→ " + resolved.packageName)
        }
    }

    private fun hearingAid(): SetupItem {
        val names = LaraApp.instance.headset.connectedNames()
        return SetupItem(
            HEARING_AID, "Hörgerät (Freisprechen)", names.isNotEmpty(),
            if (names.isEmpty()) "nichts verbunden" else names.joinToString(),
        )
    }

    private fun battery(context: Context): SetupItem {
        val exempt = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        return SetupItem(BATTERY, "Akku-Ausnahme", exempt, if (exempt) "erteilt" else "fehlt")
    }

    private fun shelly(): SetupItem {
        val app = LaraApp.instance
        if (app.shellyStore.remote.value == null) return SetupItem(SHELLY, "Shelly-Fernbedienung", false, "nicht angelernt")
        val lastPress = app.shelly.lastPressElapsedMs
        return SetupItem(
            SHELLY, "Shelly-Fernbedienung", true,
            if (lastPress == null) "angelernt · seit App-Start kein Druck empfangen"
            else "angelernt · letzter Druck vor " + ago(SystemClock.elapsedRealtime() - lastPress),
        )
    }

    /** The service counts as fine only while it really scans; without a remote it is neither good nor bad. */
    private fun listener(): SetupItem {
        if (!ListenerService.running) return SetupItem(LISTENER, "Lausch-Dienst", false, "läuft nicht")
        val status = ListenerService.status
        val mode = status.mode
        if (mode == null) {
            val why = when (status.off) {
                ScanStatus.NOT_ENROLLED -> "Shelly nicht angelernt"
                ScanStatus.BLUETOOTH_OFF -> "Bluetooth ist aus"
                ScanStatus.NO_SCAN_PERMISSION -> "Berechtigung fehlt"
                ScanStatus.WAITING -> "Start folgt in bis zu 30 Sekunden"
                else -> "Start fehlgeschlagen, siehe Protokoll"
            }
            return SetupItem(LISTENER, "Lausch-Dienst", if (status.off == ScanStatus.NOT_ENROLLED) null else false, "läuft · kein Scan: $why")
        }
        val detail = buildString {
            append("läuft · Scan ")
            append(modeLabel(mode))
            append(if (status.passive) ", passiv" else ", aktiv")
            if (ListenerService.sessionOpen) append(" · Sitzung offen")
            if (status.rescue) append(" · Rettungs-Scan an")
        }
        return SetupItem(LISTENER, "Lausch-Dienst", true, detail)
    }

    /** Missing prompts are no obstacle: sessions then use tones. So this is never plain "bad" while it is being worked on. */
    private fun prompts(): SetupItem {
        val status = LaraApp.instance.prompts.status.value
        val count = "${status.ready} von ${status.total}"
        return when {
            status.complete -> SetupItem(PROMPTS, "Ansagen", true, "$count bereit")
            status.state == PromptCache.State.WARMING -> SetupItem(PROMPTS, "Ansagen", null, "werden erzeugt: $count")
            status.state == PromptCache.State.FAILED -> {
                val problem = status.problem.orEmpty()
                val why = when {
                    problem.startsWith("engine_init") || problem.startsWith("engine_timeout") ->
                        "keine Sprachausgabe auf dem Telefon erreichbar"
                    problem.startsWith("no_german_voice") -> "keine deutsche Stimme installiert"
                    else -> "Sprachausgabe scheitert, siehe Protokoll"
                }
                SetupItem(PROMPTS, "Ansagen", false, "$count bereit, sonst Töne · $why")
            }
            else -> SetupItem(PROMPTS, "Ansagen", null, "$count bereit, sonst Töne")
        }
    }

    private fun ago(ms: Long): String = when {
        ms < 60_000 -> "${ms / 1000} s"
        ms < 3_600_000 -> "${ms / 60_000} min"
        else -> "${ms / 3_600_000} h ${ms % 3_600_000 / 60_000} min"
    }
}
