package io.github.domicc.lara.journal

import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import io.github.domicc.lara.assistant.LaraVoiceInteractionService

/** The device state that is attached to every journal line. */
object DeviceState {

    fun snapshot(context: Context): Map<String, Any?> {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val battery = context.getSystemService(BatteryManager::class.java)
        return mapOf(
            "screenOn" to power.isInteractive,
            "locked" to keyguard.isKeyguardLocked,
            "doze" to power.isDeviceIdleMode,
            "dozeLight" to power.isDeviceLightIdleMode,
            "battery" to battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            "charging" to battery.isCharging,
            "role" to holdsAssistantRole(context),
            "bound" to LaraVoiceInteractionService.ready,
        )
    }

    fun holdsAssistantRole(context: Context): Boolean = try {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
    } catch (e: Exception) {
        false
    }
}
