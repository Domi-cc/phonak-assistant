package io.github.domicc.lara.core.control

import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.TapProgram
import io.github.domicc.lara.core.scan.ScanConfig
import io.github.domicc.lara.core.scan.ScanMode

/** The command cannot be carried out as given; the message goes back to the shell. */
class Rejected(message: String) : Exception(message)

/**
 * What `lara.SET` asks for.
 *
 * @param scan the scan settings with the named ones changed
 * @param wake the new value of "Shelly weckt auf"; null to leave it
 * @param tap the new value of "Hörgeräte-Tap startet"; null to leave it
 */
data class SettingsChange(val scan: ScanConfig, val wake: Boolean?, val tap: TapProgram?)

/** What `lara.PRESS` asks for. */
data class SimulatedPress(val button: Int, val gesture: Gesture, val missed: Int)

/**
 * Reads the parameters of the adb commands. They arrive as the extras of a broadcast, each
 * with whatever type the `am` command line gave it, and one mistyped flag is enough to get a
 * string where a number was meant. Everything is checked before anything is returned, so half
 * a command never applies; whatever cannot be carried out exactly as given is [Rejected] with a
 * message that says what was expected.
 */
object AdbArguments {

    private val SETTINGS = setOf("idle", "session", "passive", "rescue", "refresh", "wake", "tap")

    /**
     * @param extras name to value, as sent
     * @param current the scan settings in force; settings that are not named stay as they are
     */
    fun settings(extras: Map<String, Any?>, current: ScanConfig): SettingsChange {
        val unknown = extras.keys - SETTINGS
        if (unknown.isNotEmpty()) {
            throw Rejected("unknown setting ${unknown.sorted().joinToString(" ")}; known: ${SETTINGS.joinToString(" ")}")
        }
        var scan = current
        mode(extras, "idle")?.let { scan = scan.copy(idle = it) }
        mode(extras, "session")?.let { scan = scan.copy(session = it) }
        flag(extras, "passive")?.let { scan = scan.copy(passive = it) }
        flag(extras, "rescue")?.let { scan = scan.copy(rescue = it) }
        number(extras, "refresh")?.let {
            if (it !in 0..ScanConfig.MAX_REFRESH_MINUTES) {
                throw Rejected("refresh must be 0 to ${ScanConfig.MAX_REFRESH_MINUTES} (minutes)")
            }
            scan = scan.copy(refreshMinutes = it)
        }
        val tap = text(extras, "tap")?.let { word ->
            TapProgram.fromWord(word) ?: throw Rejected("tap must be one of " + TapProgram.entries.joinToString(" ") { it.word })
        }
        return SettingsChange(scan, flag(extras, "wake"), tap)
    }

    fun press(extras: Map<String, Any?>): SimulatedPress {
        val button = number(extras, "button") ?: 1
        if (button !in 1..4) throw Rejected("button must be 1 to 4")
        val word = text(extras, "gesture") ?: "press"
        val gesture = Gesture.entries.firstOrNull { it.name.lowercase() == word }
            ?: throw Rejected("gesture must be one of " + Gesture.entries.joinToString(" ") { it.name.lowercase() })
        val missed = number(extras, "missed") ?: 0
        if (missed !in 0..99) throw Rejected("missed must be 0 to 99")
        return SimulatedPress(button, gesture, missed)
    }

    fun text(extras: Map<String, Any?>, key: String): String? = extras[key]?.toString()

    private fun mode(extras: Map<String, Any?>, key: String): ScanMode? {
        val word = text(extras, key) ?: return null
        return ScanMode.fromWord(word)
            ?: throw Rejected("$key must be one of " + ScanMode.entries.joinToString(" ") { it.word })
    }

    /** Takes `--ez key true` and, because it is easily mistyped, `--es key true` as well. */
    private fun flag(extras: Map<String, Any?>, key: String): Boolean? = when (val value = extras[key]) {
        null -> null
        is Boolean -> value
        "true", "on", "1" -> true
        "false", "off", "0" -> false
        else -> throw Rejected("$key must be true or false")
    }

    /** Takes `--ei key 3` and `--es key 3`. */
    private fun number(extras: Map<String, Any?>, key: String): Int? = when (val value = extras[key]) {
        null -> null
        is Int -> value
        else -> value.toString().toIntOrNull() ?: throw Rejected("$key must be a whole number")
    }
}
