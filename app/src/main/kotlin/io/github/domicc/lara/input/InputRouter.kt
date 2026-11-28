package io.github.domicc.lara.input

import android.bluetooth.BluetoothDevice
import android.media.AudioManager
import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.audio.AudioLink
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
 * which presses wait for a session and what becomes of them, by [WaitingInputs]. This class
 * carries the decisions out and journals them: `input_routed` for every input, `input_dropped`
 * for a press that is given up.
 *
 * It owns the inbox of the running program and is the only caller of
 * [SessionService.start]. The session service answers in two ways: a request that meets a
 * running session is turned down through [SessionService.busyHandler], and every session that
 * is over, whether it ran, failed to open or was refused by Android, is reported through
 * [SessionService.endHandler].
 *
 * Two things only this class can know keep a press from opening a session by itself: a phone
 * call is running ([refusedForCall]), or the press reached the app too late to be announced
 * ([Route.TOO_LATE]).
 *
 * Inputs arrive on the main thread; a program opens and closes its inbox from its own thread.
 */
class InputRouter(private val app: LaraApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val lock = Any()

    /** What waits for a session and what is known about sessions. Guarded by [lock]. */
    private val inputs = WaitingInputs()

    /** The inbox of the running program; null while no program takes inputs. Guarded by [lock]. */
    private var session: Channel<Input>? = null

    /** Call once, before the first input can arrive. */
    fun start() {
        SessionService.busyHandler = ::onBusy
        SessionService.endHandler = ::onEnded
        // Main.immediate: a press is routed before the scan callback that brought it returns,
        // because the phone may fall asleep again right after that.
        scope.launch { app.inputs.collect { onInput(it, null) } }
    }

    /**
     * Input path A: Android started the app for a tap on the selected hearing aid.
     *
     * @param device the hearing aid Android named, if it named one
     */
    fun tap(device: BluetoothDevice?) =
        onInput(Input(InputSource.HEARING_AID_TAP, SystemClock.elapsedRealtime()), device)

    /**
     * Somebody asks for [program] by name: a button on the screen, an adb command, the assist
     * gesture. During a session that takes inputs a request for the echo test is run inside
     * that session. Any other request that meets a running session is turned down by the
     * session service and journaled as `session_busy`.
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
                inputs.close()
            }
        }
        // an input that came in after the program had looked for the last time
        while (true) dropped(inbox.tryReceive().getOrNull() ?: break, "session_closed")
    }

    private fun onInput(input: Input, device: BluetoothDevice?) {
        val routed = synchronized(lock) {
            inputs.route(
                input,
                SystemClock.elapsedRealtime(),
                app.shellyStore.wakes.value,
                app.tapStore.program.value,
                app.shellyStore.remote.value != null,
            ).also { if (it.route == Route.TO_SESSION) session?.trySend(input) }
        }
        routed.stale.forEach { dropped(it, "session_did_not_open") }
        app.journal.log(
            "input_routed",
            fields(input) + mapOf("route" to routed.route.word, "session" to routed.state.name.lowercase()),
        )
        when (routed.route) {
            Route.START_ECHO -> startSession(input, device, SessionPrograms.ECHO)
            Route.START_REMOTE -> startSession(input, device, SessionPrograms.REMOTE)
            Route.WAKE_REMOTE -> if (!refusedForCall()) startSession(input, device, SessionPrograms.REMOTE)
            Route.START_COUNT -> startSession(input, device, SessionPrograms.COUNT)
            Route.TOO_LATE -> dropped(input, "too_old")
            Route.HOLD, Route.TO_SESSION, Route.NONE -> Unit
        }
        // The safety net: normally the session service reports the end of a session that did
        // not come, and the press is given up then.
        if (routed.route == Route.WAKE_REMOTE || routed.route == Route.HOLD) {
            scope.launch {
                delay(WaitingInputs.WAIT_LIMIT_MS + 100)
                synchronized(lock) { inputs.takeStale(SystemClock.elapsedRealtime()) }.forEach { dropped(it, "session_did_not_open") }
            }
        }
    }

    private fun startSession(input: Input, device: BluetoothDevice?, program: String) {
        if (SessionPrograms.takesInputs(program)) synchronized(lock) { inputs.asked(SystemClock.elapsedRealtime()) }
        // The press or command that leads here keeps the phone awake for a moment at best; the
        // service takes its own wake lock only once Android has started it.
        SessionService.holdAwake(app)
        SessionService.start(app, input.origin, device, program)
    }

    /**
     * A press must not open a session into a phone call. The remote sits in a pocket and gets
     * pressed by accident, and during a call the Bluetooth stack refuses voice recognition:
     * [AudioLink] would fall back to the virtual call and play the start tones and "Taste 2
     * kurz" into the conversation. Asked only for sessions a press opens by itself. A tap on
     * the hearing aid during a call does not arrive here (the aid uses it for the call), and
     * the screen and adb are deliberate.
     *
     * If the phone is in a call or ringing, the presses that wait are given up, journaled,
     * and answered with the vibration of every failure before the link is up. Only the modes
     * that mean a call hold a press back: any other mode some app may have left behind (an
     * assistant conversation, one this build does not know) must not make the remote dead.
     *
     * @return true if nothing may be started
     */
    private fun refusedForCall(): Boolean {
        val mode = audio.mode
        if (mode !in CALL_MODES) return false
        synchronized(lock) { inputs.giveUp() }.forEach { dropped(it, "phone_call", mapOf("audioMode" to mode)) }
        AudioLink.vibrateFailure(app)
        return true
    }

    /**
     * The session service did not start a session because one runs already. Journaled, because
     * from the service's `session_busy` line alone nobody can tell whether a press is still
     * waiting. For presses nothing else happens here: [onEnded] decides about them when the
     * running session is over.
     *
     * A tap is different: nobody answers the hearing aid's request now, and Android rejects
     * further taps for five seconds. No tone is possible, the link belongs to the session
     * that runs, so the wearer is told by vibration.
     */
    private fun onBusy(trigger: String) {
        val busy = synchronized(lock) { inputs.busy() }
        app.journal.log(
            "input_busy",
            mapOf("origin" to trigger, "sessionTakesInput" to busy.sessionTakesInput, "waiting" to busy.waiting),
        )
        if (trigger == InputSource.HEARING_AID_TAP.word) AudioLink.vibrateFailure(app)
    }

    /**
     * The session service reports that a session is over or did not start. Presses that waited
     * for a session which never listened are given up, so that a later session does not
     * announce them. A press made while the session was saying its closing phrase opens the
     * next session at once.
     *
     * @param trigger what had started the session that is over
     */
    private fun onEnded(trigger: String, result: String) {
        val now = SystemClock.elapsedRealtime()
        val ended = synchronized(lock) { inputs.ended(now) }
        ended.dropped.forEach { dropped(it, "session_did_not_open") }
        val next = ended.askFor ?: return
        if (refusedForCall()) return
        app.journal.log(
            "input_retry",
            fields(next) + mapOf("ageMs" to now - next.elapsedMs, "after" to trigger, "afterResult" to result),
        )
        startSession(next, null, SessionPrograms.REMOTE)
    }

    private fun dropped(input: Input, reason: String, more: Map<String, Any?> = emptyMap()) {
        app.journal.log(
            "input_dropped",
            fields(input) + mapOf("reason" to reason, "ageMs" to SystemClock.elapsedRealtime() - input.elapsedMs) + more,
        )
    }

    companion object {
        /** The audio modes of a telephone or internet call, ringing included. */
        private val CALL_MODES = setOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
            AudioManager.MODE_CALL_REDIRECT,
            AudioManager.MODE_COMMUNICATION_REDIRECT,
        )

        /**
         * Whether [mode], what `AudioManager.mode` says, means a call, ringing included. Also
         * asked by the calendar reminders, which do not talk into one either.
         */
        fun isCall(mode: Int): Boolean = mode in CALL_MODES

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
