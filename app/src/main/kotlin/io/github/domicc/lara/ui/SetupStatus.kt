package io.github.domicc.lara.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.assistant.LaraVoiceInteractionService
import io.github.domicc.lara.journal.DeviceState
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

    fun read(context: Context): List<SetupItem> = listOf(
        permissions(context),
        role(context),
        voiceButton(context),
        hearingAid(),
        battery(context),
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
        val app = LaraApp.instance
        val name = app.hearingAid.name ?: "?"
        return when {
            app.hearingAid.address == null ->
                SetupItem(HEARING_AID, "Hörgerät", false, "nicht festgelegt: ohne Auswahl bleibt die App stumm")
            app.headset.hearingAidConnected() -> SetupItem(HEARING_AID, "Hörgerät", true, "$name · verbunden")
            else -> SetupItem(HEARING_AID, "Hörgerät", null, "$name · nicht verbunden")
        }
    }

    private fun battery(context: Context): SetupItem {
        val exempt = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        return SetupItem(BATTERY, "Akku-Ausnahme", exempt, if (exempt) "erteilt" else "fehlt")
    }
}
