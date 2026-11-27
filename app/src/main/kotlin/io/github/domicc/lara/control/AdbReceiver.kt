package io.github.domicc.lara.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.assistant.LaraVoiceInteractionService
import io.github.domicc.lara.audio.SessionPrograms
import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputPath
import io.github.domicc.lara.core.input.InputSource
import io.github.domicc.lara.core.scan.ScanMode
import io.github.domicc.lara.input.InputRouter
import io.github.domicc.lara.shelly.ListenerService

/**
 * Control from a computer, so that a series of measurements can be run and repeated without
 * touching the phone: `adb shell am broadcast -n io.github.domicc.lara/.control.AdbReceiver
 * -a lara.ECHO`. The commands are listed in docs/adb-control.md.
 *
 * Only the adb shell gets through: the manifest demands android.permission.DUMP from the
 * sender, which the shell holds and an ordinary app cannot get.
 *
 * Every command is journaled as `adb_command` before it does anything, so that what follows
 * in the journal can be traced back to it. The answer goes back to the shell as the
 * broadcast's result data: `ok ...` or `error: ...`.
 *
 * A simulated press is no press: it is journaled as `sim_press`, carries the origin
 * `simulated` through everything it causes, and does not touch the counters of the remote.
 */
class AdbReceiver : BroadcastReceiver() {

    /** The command cannot be carried out as given; the message goes back to the shell. */
    private class Rejected(message: String) : Exception(message)

    override fun onReceive(context: Context, intent: Intent) {
        val app = LaraApp.instance
        val extras = intent.extras?.keySet()?.sorted()?.associateWith { extra(intent, it).toString() } ?: emptyMap()
        app.journal.log("adb_command", mapOf("action" to intent.action, "extras" to extras, "origin" to InputSource.ADB.word))
        val answer = try {
            "ok " + run(app, intent)
        } catch (e: Rejected) {
            app.journal.log("adb_rejected", mapOf("action" to intent.action, "why" to e.message, "origin" to InputSource.ADB.word))
            "error: " + e.message
        }
        if (isOrderedBroadcast) resultData = answer.trim()
    }

    private fun run(app: LaraApp, intent: Intent): String = when (intent.action) {
        ACTION_ECHO -> {
            app.router.request(InputSource.ADB, SessionPrograms.ECHO)
            "echo test asked for"
        }
        ACTION_COUNT -> {
            app.router.request(InputSource.ADB, SessionPrograms.COUNT)
            "count session asked for"
        }
        ACTION_SET -> set(app, intent)
        ACTION_MARK -> {
            val text = text(intent, "text")?.takeIf { it.isNotBlank() } ?: throw Rejected("--es text \"...\" is missing")
            app.journal.log("marker", mapOf("text" to text, "origin" to InputSource.ADB.word))
            "marker written"
        }
        ACTION_PRESS -> press(app, intent)
        ACTION_STATUS -> status(app)
        else -> throw Rejected("unknown action ${intent.action}; see docs/adb-control.md")
    }

    /** Changes the settings that are named and leaves the others as they are. */
    private fun set(app: LaraApp, intent: Intent): String {
        val store = app.shellyStore
        var config = store.scan.value
        // everything is checked before anything is changed: half a command must not apply
        mode(intent, "idle")?.let { config = config.copy(idle = it) }
        mode(intent, "session")?.let { config = config.copy(session = it) }
        flag(intent, "passive")?.let { config = config.copy(passive = it) }
        flag(intent, "rescue")?.let { config = config.copy(rescue = it) }
        number(intent, "refresh")?.let { config = config.copy(refreshMinutes = it) }
        val wake = flag(intent, "wake")
        store.setScan(config)
        wake?.let(store::setWakes)
        return settings(app)
    }

    private fun press(app: LaraApp, intent: Intent): String {
        val button = number(intent, "button") ?: 1
        if (button !in 1..4) throw Rejected("button must be 1 to 4")
        val word = text(intent, "gesture") ?: "press"
        val gesture = Gesture.entries.firstOrNull { it.name.lowercase() == word }
            ?: throw Rejected("gesture must be one of " + Gesture.entries.joinToString(" ") { it.name.lowercase() })
        val missed = number(intent, "missed") ?: 0
        if (missed !in 0..99) throw Rejected("missed must be 0 to 99")
        val input = Input(InputSource.SHELLY, SystemClock.elapsedRealtime(), button, gesture, InputPath.SIMULATED, missed)
        // Deliberately not `shelly_press`: that type is what the counters of the remote count.
        app.journal.log("sim_press", InputRouter.fields(input))
        val consumers = app.publish(input)
        return "simulated press button=$button gesture=$word missed=$missed consumers=$consumers"
    }

    private fun status(app: LaraApp): String {
        val prompts = app.prompts.status.value
        return settings(app) +
            " enrolled=${app.shellyStore.remote.value != null}" +
            " scan=${ListenerService.status.word}" +
            " listener=${ListenerService.running}" +
            " sessionOpen=${ListenerService.sessionOpen}" +
            " assistantBound=${LaraVoiceInteractionService.ready}" +
            " prompts=${prompts.ready}/${prompts.total}"
    }

    private fun settings(app: LaraApp): String {
        val scan = app.shellyStore.scan.value
        return "idle=${scan.idle.word} session=${scan.session.word} passive=${scan.passive} rescue=${scan.rescue}" +
            " refresh=${scan.refreshMinutes} wake=${app.shellyStore.wakes.value}"
    }

    private fun mode(intent: Intent, key: String): ScanMode? {
        val word = text(intent, key) ?: return null
        return ScanMode.fromWord(word)
            ?: throw Rejected("$key must be one of " + ScanMode.entries.joinToString(" ") { it.word })
    }

    private fun text(intent: Intent, key: String): String? = extra(intent, key)?.toString()

    /** Takes `--ez key true` and, because it is easily mistyped, `--es key true` as well. */
    private fun flag(intent: Intent, key: String): Boolean? = when (val value = extra(intent, key)) {
        null -> null
        is Boolean -> value
        "true", "on", "1" -> true
        "false", "off", "0" -> false
        else -> throw Rejected("$key must be true or false")
    }

    /** Takes `--ei key 3` and `--es key 3`. */
    private fun number(intent: Intent, key: String): Int? = when (val value = extra(intent, key)) {
        null -> null
        is Int -> value
        else -> value.toString().toIntOrNull() ?: throw Rejected("$key must be a whole number")
    }

    /** The extra whatever type `am` gave it; the typed getters would hide a mistyped flag. */
    @Suppress("DEPRECATION")
    private fun extra(intent: Intent, key: String): Any? = intent.extras?.get(key)

    private companion object {
        const val ACTION_ECHO = "lara.ECHO"
        const val ACTION_COUNT = "lara.COUNT"
        const val ACTION_SET = "lara.SET"
        const val ACTION_MARK = "lara.MARK"
        const val ACTION_PRESS = "lara.PRESS"
        const val ACTION_STATUS = "lara.STATUS"
    }
}
