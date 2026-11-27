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
import io.github.domicc.lara.core.input.SessionState
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
 * this class carries the decision out and journals it as `input_routed`.
 *
 * Whether a session runs, only the session service knows for certain, and it cannot be asked.
 * So the router does not keep a copy of that state. It knows two things at first hand:
 * - a program that takes inputs has opened its inbox ([openSession]) and not closed it yet;
 * - it has itself asked for a count session a moment ago.
 * In every other case it simply asks the service to start one. A service that is busy says so
 * through [SessionService.busyHandler], and nothing is lost by having asked.
 *
 * A press that opens a session, or arrives while one is being opened, waits here until the
 * program fetches it; that is how the press that woke the app becomes number one. A press that
 * waits longer than [WAIT_LIMIT_MS] is dropped and journaled: the session did not come, and a
 * press must never be counted long after it was made.
 *
 * Inputs arrive on the main thread; a program opens and closes its inbox from its own thread.
 */
class InputRouter(private val app: LaraApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()

    /** The inbox of the running program; null while no program takes inputs. */
    private var session: Channel<Input>? = null

    /** Inputs kept for the session that is being opened, oldest first. */
    private val waiting = ArrayDeque<Input>()

    /** When this router last asked for a count session; null once that session has opened. */
    private var askedAtMs: Long? = null

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
        val stale: List<Input>
        synchronized(lock) {
            stale = takeStale(SystemClock.elapsedRealtime())
            waiting.forEach { inbox.trySend(it) }
            waiting.clear()
            askedAtMs = null
            session = inbox
        }
        stale.forEach { dropped(it, "session_did_not_open") }
        return inbox
    }

    /** The program is done with [inbox]. Safe on every exit path, also after a cancellation. */
    fun closeSession(inbox: ReceiveChannel<Input>) {
        synchronized(lock) { if (session === inbox) session = null }
        // an input that came in after the program had looked for the last time
        while (true) dropped(inbox.tryReceive().getOrNull() ?: break, "session_closed")
    }

    private fun onInput(input: Input, device: BluetoothDevice?) {
        val now = SystemClock.elapsedRealtime()
        val stale: List<Input>
        val state: SessionState
        val route: Route
        synchronized(lock) {
            stale = takeStale(now)
            state = when {
                session != null -> SessionState.OPEN
                askedAtMs?.let { now - it < WAIT_LIMIT_MS } == true -> SessionState.OPENING
                else -> SessionState.NONE
            }
            route = Routing.route(input, state, app.shellyStore.wakes.value)
            when (route) {
                Route.TO_SESSION -> session?.trySend(input)
                Route.WAKE_COUNT, Route.HOLD -> waiting.addLast(input)
                Route.START_COUNT, Route.NONE -> Unit
            }
        }
        stale.forEach { dropped(it, "session_did_not_open") }
        app.journal.log("input_routed", fields(input) + mapOf("route" to route.word, "session" to state.name.lowercase()))
        when (route) {
            Route.START_COUNT, Route.WAKE_COUNT -> startSession(input, device, SessionPrograms.COUNT)
            else -> Unit
        }
        // Without this the loss of a waiting press would only be journaled with the next input.
        if (route == Route.WAKE_COUNT || route == Route.HOLD) {
            scope.launch {
                delay(WAIT_LIMIT_MS + 100)
                synchronized(lock) { takeStale(SystemClock.elapsedRealtime()) }.forEach { dropped(it, "session_did_not_open") }
            }
        }
    }

    private fun startSession(input: Input, device: BluetoothDevice?, program: String) {
        if (program == SessionPrograms.COUNT) {
            synchronized(lock) { if (session == null) askedAtMs = SystemClock.elapsedRealtime() }
        }
        // The press or command that leads here keeps the phone awake for a moment at best; the
        // service takes its own wake lock only once Android has started it.
        SessionService.holdAwake(app)
        SessionService.start(app, input.origin, device, program)
    }

    /**
     * The session service did not start a session because one runs already. Presses that wait
     * stay where they are: if the running session is the count session that is being opened,
     * its program fetches them; if not, they run out. Journaled, because from the service's
     * `session_busy` line alone nobody can tell whether a press is still waiting.
     */
    private fun onBusy(trigger: String) {
        val (open, kept) = synchronized(lock) { (session != null) to waiting.size }
        app.journal.log("input_busy", mapOf("origin" to trigger, "sessionTakesInput" to open, "waiting" to kept))
    }

    /** Removes the inputs that have waited too long. Call with [lock] held; journal the result outside. */
    private fun takeStale(nowMs: Long): List<Input> {
        val stale = ArrayList<Input>()
        while (waiting.isNotEmpty() && nowMs - waiting.first().elapsedMs > WAIT_LIMIT_MS) stale += waiting.removeFirst()
        return stale
    }

    private fun dropped(input: Input, reason: String) {
        app.journal.log(
            "input_dropped",
            fields(input) + mapOf("reason" to reason, "ageMs" to SystemClock.elapsedRealtime() - input.elapsedMs),
        )
    }

    companion object {
        /**
         * How long a press waits for its session. Opening the voice link takes about a second;
         * in the worst case the link gives up after 15 seconds.
         */
        const val WAIT_LIMIT_MS = 20_000L

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
