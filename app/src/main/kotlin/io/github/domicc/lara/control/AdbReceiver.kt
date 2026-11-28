package io.github.domicc.lara.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.assistant.LaraVoiceInteractionService
import io.github.domicc.lara.audio.SessionPrograms
import io.github.domicc.lara.audio.SessionService
import io.github.domicc.lara.calendar.CalendarStatus
import io.github.domicc.lara.core.control.AdbArguments
import io.github.domicc.lara.core.control.Rejected
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputPath
import io.github.domicc.lara.core.input.InputSource
import io.github.domicc.lara.input.InputRouter
import io.github.domicc.lara.shelly.ListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Control from a computer, so that a series of measurements can be run and repeated without
 * touching the phone: `adb shell am broadcast -n io.github.domicc.lara/.control.AdbReceiver
 * -a lara.REMOTE`. The commands are listed in docs/adb-control.md.
 *
 * The manifest demands android.permission.DUMP from the sender. The adb shell holds it; an
 * ordinary app cannot get it, unless the owner has granted it to that app over adb himself.
 *
 * Every command is journaled as `adb_command` before it does anything, so that what follows
 * in the journal can be traced back to it. The answer goes back to the shell as the
 * broadcast's result data: `ok ...` or `error: ...`. What the parameters may be is decided by
 * [AdbArguments]; a command that cannot be carried out exactly as given changes nothing.
 *
 * A simulated press is no press: it is journaled as `sim_press`, carries the origin
 * `simulated` through everything it causes, and does not touch the counters of the remote.
 *
 * Nothing here plays anything itself. A session asked for from here goes through the router
 * and the session service like every other one, to the selected hearing aid or nowhere.
 *
 * What is said here about the calendar is numbers: no title, no calendar name and no account
 * ever goes into an answer.
 */
class AdbReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = LaraApp.instance
        val extras = extras(intent)
        app.journal.log(
            "adb_command",
            mapOf("action" to intent.action, "extras" to extras.mapValues { it.value.toString() }, "origin" to InputSource.ADB.word),
        )
        if (intent.action == ACTION_CALENDAR) {
            // answers later, when the calendar has been read
            calendar(app)
            return
        }
        val answer = try {
            "ok " + run(app, intent.action, extras)
        } catch (e: Rejected) {
            app.journal.log("adb_rejected", mapOf("action" to intent.action, "why" to e.message, "origin" to InputSource.ADB.word))
            "error: " + e.message
        }
        if (isOrderedBroadcast) resultData = answer.trim()
    }

    private fun run(app: LaraApp, action: String?, extras: Map<String, Any?>): String = when (action) {
        ACTION_REMOTE -> {
            app.router.request(InputSource.ADB, SessionPrograms.REMOTE)
            "remote session asked for"
        }
        ACTION_ECHO -> {
            app.router.request(InputSource.ADB, SessionPrograms.ECHO)
            "echo test asked for"
        }
        ACTION_COUNT -> {
            app.router.request(InputSource.ADB, SessionPrograms.COUNT)
            "count session asked for"
        }
        ACTION_SET -> set(app, extras)
        ACTION_MARK -> {
            val text = AdbArguments.text(extras, "text")?.takeIf { it.isNotBlank() } ?: throw Rejected("--es text \"...\" is missing")
            app.journal.log("marker", mapOf("text" to text, "origin" to InputSource.ADB.word))
            "marker written"
        }
        ACTION_PRESS -> press(app, extras)
        ACTION_STATUS -> status(app)
        else -> throw Rejected("unknown action $action; see docs/adb-control.md")
    }

    /** Changes the settings that are named and leaves the others as they are. */
    private fun set(app: LaraApp, extras: Map<String, Any?>): String {
        // everything is checked before anything is changed: half a command must not apply
        val change = AdbArguments.settings(extras, app.shellyStore.scan.value)
        app.shellyStore.setScan(change.scan)
        change.wake?.let(app.shellyStore::setWakes)
        change.tap?.let(app.tapStore::setProgram)
        return settings(app)
    }

    private fun press(app: LaraApp, extras: Map<String, Any?>): String {
        val press = AdbArguments.press(extras)
        val input = Input(
            InputSource.SHELLY, SystemClock.elapsedRealtime(), press.button, press.gesture, InputPath.SIMULATED, press.missed,
        )
        // Deliberately not `shelly_press`: that type is what the counters of the remote count.
        app.journal.log("sim_press", InputRouter.fields(input))
        val consumers = app.publish(input)
        return "simulated press button=${press.button} gesture=${press.gesture.name.lowercase()} missed=${press.missed} consumers=$consumers"
    }

    /**
     * Asks for a new plan of the calendar reminders and answers with its numbers. The plan
     * reads the calendar on another thread, so the broadcast is kept open until it is through.
     */
    private fun calendar(app: LaraApp) {
        val ordered = isOrderedBroadcast
        val pending = goAsync()
        val answered = AtomicBoolean()
        /** @return false if the answer was given before */
        fun answer(text: String): Boolean {
            if (!answered.compareAndSet(false, true)) return false
            if (ordered) pending.resultData = text
            pending.finish()
            return true
        }
        // Android allows a receiver a limited time; a plan that hangs must not use it up.
        Handler(Looper.getMainLooper()).postDelayed(
            {
                val why = "no plan within ${CALENDAR_ANSWER_MS / 1000} s"
                if (answer("error: $why; see calendar_* in the journal")) {
                    app.journal.log("adb_rejected", mapOf("action" to ACTION_CALENDAR, "why" to why, "origin" to InputSource.ADB.word))
                }
            },
            CALENDAR_ANSWER_MS,
        )
        app.calendar.requestPlan(InputSource.ADB.word) { answer("ok " + calendarNumbers(it)) }
    }

    private fun calendarNumbers(status: CalendarStatus): String {
        val wake = status.wakeAtMs?.let { SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(it)) } ?: "none"
        return "enabled=${status.enabled} permission=${status.permission} readable=${status.readable}" +
            " calendars=${status.selected} events=${status.events} wake=$wake kind=${status.wakeKind?.word ?: "none"}"
    }

    /** The one word of `lara.STATUS` about the calendar reminders. */
    private fun calendarWord(app: LaraApp): String = when {
        !app.calendarStore.enabled.value -> "off"
        !app.calendar.hasPermission() -> "no_permission"
        else -> "on"
    }

    private fun status(app: LaraApp): String {
        val prompts = app.prompts.status.value
        val hearingAid = when {
            app.hearingAid.address == null -> "not_selected"
            app.headset.hearingAidConnected() -> "connected"
            else -> "not_connected"
        }
        return settings(app) +
            " enrolled=${app.shellyStore.remote.value != null}" +
            " scan=${ListenerService.status.word}" +
            " listener=${ListenerService.running}" +
            " sessionBusy=${SessionService.busy}" +
            " sessionOpen=${ListenerService.sessionOpen}" +
            " assistantBound=${LaraVoiceInteractionService.ready}" +
            " hearingAid=$hearingAid" +
            " prompts=${prompts.ready}/${prompts.total}" +
            " calendar=${calendarWord(app)}"
    }

    private fun settings(app: LaraApp): String {
        val scan = app.shellyStore.scan.value
        return "idle=${scan.idle.word} session=${scan.session.word} passive=${scan.passive} rescue=${scan.rescue}" +
            " refresh=${scan.refreshMinutes} wake=${app.shellyStore.wakes.value} tap=${app.tapStore.program.value.word}"
    }

    /** The extras with whatever type `am` gave them; the typed getters would hide a mistyped flag. */
    @Suppress("DEPRECATION")
    private fun extras(intent: Intent): Map<String, Any?> {
        val bundle = intent.extras ?: return emptyMap()
        return bundle.keySet().sorted().associateWith { bundle.get(it) }
    }

    private companion object {
        const val ACTION_REMOTE = "lara.REMOTE"
        const val ACTION_ECHO = "lara.ECHO"
        const val ACTION_COUNT = "lara.COUNT"
        const val ACTION_SET = "lara.SET"
        const val ACTION_MARK = "lara.MARK"
        const val ACTION_PRESS = "lara.PRESS"
        const val ACTION_STATUS = "lara.STATUS"
        const val ACTION_CALENDAR = "lara.CALENDAR"

        /** How long `lara.CALENDAR` waits for the plan before it answers with an error. */
        const val CALENDAR_ANSWER_MS = 8_000L
    }
}
