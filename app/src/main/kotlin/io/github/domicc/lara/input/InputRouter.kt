package io.github.domicc.lara.input

import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.audio.SessionPrograms
import io.github.domicc.lara.audio.SessionService
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputSource
import io.github.domicc.lara.core.input.Route
import io.github.domicc.lara.core.input.Routing
import io.github.domicc.lara.core.input.WaitingInputs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns everything the wearer does into an action. Every input path ends here: the hearing-aid
 * tap ([tap]), the Shelly remote and simulated presses (through the app's input bus), and the
 * screen, adb and the assist gesture ([request]). What an input does is decided by [Routing];
 * which presses wait for a session and for how long, by [WaitingInputs]. This class carries the
 * decisions out and journals them: `input_routed` for every input, `input_dropped` for a press
 * that is given up.
 *
 * It owns the inbox of the running program and asks the session service for sessions. That
 * service cannot be asked whether a session runs; a service that is busy says so through
 * [SessionService.busyHandler], and nothing is lost by having asked.
 *
 * Inputs arrive on the main thread; a program opens and closes its inbox from its own thread.
 */
class InputRouter(private val app: LaraApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()

    /** What waits for a session and what is known about sessions. Guarded by [lock]. */
    private val inputs = WaitingInputs()

    /** The inbox of the running program; null while no program takes inputs. Guarded by [lock]. */
    private var session: Channel<Input>? = null

    /** Call once, before the first input can arrive. */
    fun start() {
        SessionService.busyHandler = ::onBusy
        // Main.immediate: a press is routed before the scan callback that brought it returns,
        // because the phone may fall asleep again right after that.
        scope.launch { app.inputs.collect { onInput(it, null) } }
    }

    /**
     * Input path A: Android started the app for a hearing-aid tap.
     *
     * @param device the hearing aid Android named, if it named one
     */
    fun tap(device: BluetoothDevice?) =
        onInput(Input(InputSource.HEARING_AID_TAP, SystemClock.elapsedRealtime()), device)

    /**
     * Somebody asks for [program] by name: a button on the screen, an adb command, the assist
     * gesture. During a count session a request for the echo test is run inside that session,
     * like button 3 of the remote. Any other request that meets a running session is turned
     * down by the session service and journaled as `session_busy`.
     *
     * @param program a name from [SessionPrograms]
     */
    fun request(source: InputSource, program: String) {
        val input = Input(source, SystemClock.elapsedRealtime())
        val handedOver = program == SessionPrograms.ECHO &&
            synchronized(lock) { session?.trySend(input)?.isSuccess == true }
        app.journal.log(
            "input_routed",
            fields(input) + mapOf("route" to if (handedOver) Route.TO_SESSION.word else "start_$program", "program" to program),
        )
        if (!handedOver) startSession(input, null, program)
    }

    /**
     * For a session program that takes inputs: from now until [closeSession] every input that
     * belongs to a session arrives in the returned inbox, beginning with those that waited for
     * the session to open.
     */
    fun openSession(): ReceiveChannel<Input> {
        val inbox = Channel<Input>(Channel.UNLIMITED)
        val opened = synchronized(lock) {
            inputs.open(SystemClock.elapsedRealtime()).also { opened ->
                opened.handOver.forEach { inbox.trySend(it) }
                session = inbox
            }
        }
        opened.stale.forEach { dropped(it, "session_did_not_open") }
        return inbox
    }

    /** The program is done with [inbox]. Safe on every exit path, also after a cancellation. */
    fun closeSession(inbox: ReceiveChannel<Input>) {
        synchronized(lock) {
            if (session === inbox) {
                session = null
                inputs.close(SystemClock.elapsedRealtime())
            }
        }
        // an input that came in after the program had looked for the last time
        while (true) dropped(inbox.tryReceive().getOrNull() ?: break, "session_closed")
    }

    private fun onInput(input: Input, device: BluetoothDevice?) {
        val routed = synchronized(lock) {
            inputs.route(input, SystemClock.elapsedRealtime(), app.shellyStore.wakes.value, app.tapStore.program.value)
                .also { if (it.route == Route.TO_SESSION) session?.trySend(input) }
        }
        routed.stale.forEach { dropped(it, "session_did_not_open") }
        app.journal.log(
            "input_routed",
            fields(input) + mapOf("route" to routed.route.word, "session" to routed.state.name.lowercase()),
        )
        when (routed.route) {
            Route.START_ECHO -> startSession(input, device, SessionPrograms.ECHO)
            Route.START_COUNT, Route.WAKE_COUNT -> startSession(input, device, SessionPrograms.COUNT)
            Route.HOLD, Route.TO_SESSION, Route.NONE -> Unit
        }
        // Without this the loss of a waiting press would only be journaled with the next input.
        if (routed.route == Route.WAKE_COUNT || routed.route == Route.HOLD) {
            scope.launch {
                delay(WaitingInputs.WAIT_LIMIT_MS + 100)
                synchronized(lock) { inputs.takeStale(SystemClock.elapsedRealtime()) }.forEach { dropped(it, "session_did_not_open") }
            }
        }
    }

    private fun startSession(input: Input, device: BluetoothDevice?, program: String) {
        if (program == SessionPrograms.COUNT) synchronized(lock) { inputs.asked(SystemClock.elapsedRealtime()) }
        // The press or command that leads here keeps the phone awake for a moment at best; the
        // service takes its own wake lock only once Android has started it.
        SessionService.holdAwake(app)
        SessionService.start(app, input.origin, device, program)
    }

    /**
     * The session service did not start a session because one runs already. Journaled, because
     * from the service's `session_busy` line alone nobody can tell whether a press is still
     * waiting. If the busy session is one that is just closing, the request is repeated a
     * moment later (see [WaitingInputs.busy]); the wake lock of the request that was turned
     * down keeps the phone awake until then.
     */
    private fun onBusy(trigger: String) {
        val busy = synchronized(lock) { inputs.busy(SystemClock.elapsedRealtime()) }
        app.journal.log(
            "input_busy",
            buildMap {
                put("origin", trigger)
                put("sessionTakesInput", busy.sessionTakesInput)
                put("waiting", busy.waiting)
                busy.askAgainInMs?.let { put("askAgainMs", it) }
            },
        )
        val waitMs = busy.askAgainInMs ?: return
        scope.launch {
            delay(waitMs)
            askAgain()
        }
    }

    /** Asks once more for the session the oldest waiting press wants, if it still wants one. */
    private fun askAgain() {
        val now = SystemClock.elapsedRealtime()
        val (stale, input) = synchronized(lock) { inputs.takeStale(now) to inputs.askAgain() }
        stale.forEach { dropped(it, "session_did_not_open") }
        if (input == null) return
        app.journal.log("input_retry", fields(input) + ("ageMs" to now - input.elapsedMs))
        startSession(input, null, SessionPrograms.COUNT)
    }

    private fun dropped(input: Input, reason: String) {
        app.journal.log(
            "input_dropped",
            fields(input) + mapOf("reason" to reason, "ageMs" to SystemClock.elapsedRealtime() - input.elapsedMs),
        )
    }

    companion object {
        /** What every journal line about an input says about it; `origin` is where it came from. */
        fun fields(input: Input): Map<String, Any?> = buildMap {
            put("origin", input.origin)
            put("source", input.source.word)
            input.button?.let { put("button", it) }
            input.gesture?.let { put("gesture", it.name.lowercase()) }
            if (input.missedBefore > 0) put("missedBefore", input.missedBefore)
        }
    }
}
