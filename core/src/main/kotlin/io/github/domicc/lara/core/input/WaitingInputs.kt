package io.github.domicc.lara.core.input

/**
 * What the router knows about sessions, and the presses that wait for one. It is the state
 * behind every routing decision, kept free of Android so that it can be tested on its own.
 *
 * It knows three things, each at first hand:
 * - the router has asked the session service for a session that takes inputs ([asked]);
 * - the program of that session has opened its inbox ([open]) and not closed it yet ([close]);
 * - the session service has reported that the session is over, or that it never started
 *   ([ended]).
 *
 * A press that opens a session, or arrives while one is being opened, waits here until the
 * program fetches it; that is how the press that woke the app becomes the first one the
 * session announces. What happens to the presses that still wait when a session is over
 * depends on that session:
 * - It never took inputs (it failed to open, or it was an echo test): the presses waited for
 *   nothing and are handed back as dropped. Asking again would only fail again, and a press
 *   must never be acted on long after it was made.
 * - It did take inputs: then these presses arrived after its program had stopped listening,
 *   while the closing phrase was still playing. The wearer pressed again right after "Beende"
 *   and is waiting, so the router is told to ask for the next session at once.
 *
 * As a safety net a press that waits longer than [waitLimitMs] is handed back as stale, in
 * case the report of the session service never comes. A press that is already older than
 * that when it arrives does not wait at all ([Route.TOO_LATE]).
 *
 * Times are milliseconds on any clock that never runs backwards and counts while the phone
 * sleeps. Not thread-safe: the router guards every call with its lock.
 *
 * @param waitLimitMs how long a press waits for its session at most. Opening the voice link
 * takes about a second; in the worst case the link gives up after 15 seconds.
 */
class WaitingInputs(private val waitLimitMs: Long = WAIT_LIMIT_MS) {
    /**
     * @param state what the app was doing when the input arrived
     * @param stale presses that have waited too long; they are no longer kept and must be
     * journaled as dropped
     */
    data class Routed(val route: Route, val state: SessionState, val stale: List<Input>)

    /**
     * @param handOver the presses that waited for this session, oldest first
     * @param stale as in [Routed]
     */
    data class Opened(val handOver: List<Input>, val stale: List<Input>)

    /**
     * @param sessionTakesInput a program has its inbox open
     * @param waiting how many presses wait
     */
    data class Busy(val sessionTakesInput: Boolean, val waiting: Int)

    /**
     * @param dropped presses that are given up: their session did not come
     * @param askFor non-null if the router should ask for a remote session now: the oldest
     * press that arrived while the session that is over was closing. It and the presses after
     * it keep waiting, for the session that request opens.
     */
    data class Ended(val dropped: List<Input>, val askFor: Input?)

    /** True while a program has its inbox open. */
    var isOpen = false
        private set

    /** Presses kept for the session that is being opened, oldest first. */
    private val waiting = ArrayDeque<Input>()

    /** When the router last asked for a session that takes inputs; null once it has opened or ended. */
    private var askedAtMs: Long? = null

    /** A program has had its inbox open since the session service last reported an end. */
    private var tookInputs = false

    val waitingCount: Int get() = waiting.size

    fun state(nowMs: Long): SessionState = when {
        isOpen -> SessionState.OPEN
        askedAtMs?.let { nowMs - it < waitLimitMs } == true -> SessionState.OPENING
        else -> SessionState.NONE
    }

    /**
     * Decides what [input] does and keeps it if it has to wait for a session. Handing an input
     * to the running session ([Route.TO_SESSION]) and asking for a session are the caller's job.
     */
    fun route(input: Input, nowMs: Long, shellyWakes: Boolean, tap: TapProgram, remoteEnrolled: Boolean): Routed {
        val stale = takeStale(nowMs)
        val state = state(nowMs)
        val route = Routing.route(input, state, shellyWakes, tap, remoteEnrolled)
        if (route == Route.WAKE_REMOTE || route == Route.HOLD) {
            // A scan can deliver long after the fact: out of Doze, or into a process that had
            // to be started first. Such a press would be thrown away as stale when the session
            // opens, so the wearer would get a session he did not ask for, with nothing in it.
            if (nowMs - input.elapsedMs > waitLimitMs) return Routed(Route.TOO_LATE, state, stale)
            waiting.addLast(input)
        }
        return Routed(route, state, stale)
    }

    /** The router asks the session service for a session that takes inputs now. */
    fun asked(nowMs: Long) {
        if (!isOpen) askedAtMs = nowMs
    }

    /** A program opens its inbox: from now on inputs belong to it. */
    fun open(nowMs: Long): Opened {
        val stale = takeStale(nowMs)
        val handOver = waiting.toList()
        waiting.clear()
        askedAtMs = null
        isOpen = true
        tookInputs = true
        return Opened(handOver, stale)
    }

    /** The program has closed its inbox; its session ends within the next moments. */
    fun close() {
        isOpen = false
    }

    /** Removes and returns the presses that have waited too long. */
    fun takeStale(nowMs: Long): List<Input> {
        val stale = ArrayList<Input>()
        while (waiting.isNotEmpty() && nowMs - waiting.first().elapsedMs > waitLimitMs) stale += waiting.removeFirst()
        return stale
    }

    /**
     * The router does not ask for the session the waiting presses need, because a phone call
     * is running. Hands back everything that waits, to be journaled as dropped. Only for a
     * moment in which no session is on its way: afterwards nothing counts as being opened.
     */
    fun giveUp(): List<Input> {
        val given = waiting.toList()
        waiting.clear()
        askedAtMs = null
        return given
    }

    /**
     * The session service turned a request down because a session runs already. Presses that
     * wait stay where they are: the program of the session that is being opened fetches them,
     * or [ended] decides about them when the running session is over.
     */
    fun busy(): Busy = Busy(isOpen, waiting.size)

    /**
     * The session service reports that a session is over: it ran and was closed, it failed to
     * open, or Android did not let it start. See the class comment for what becomes of the
     * presses that wait.
     */
    fun ended(nowMs: Long): Ended {
        val closing = tookInputs
        isOpen = false
        tookInputs = false
        askedAtMs = null
        if (!closing) {
            val dropped = waiting.toList()
            waiting.clear()
            return Ended(dropped, null)
        }
        val stale = takeStale(nowMs)
        val next = waiting.firstOrNull()
        if (next != null) askedAtMs = nowMs
        return Ended(stale, next)
    }

    companion object {
        const val WAIT_LIMIT_MS = 20_000L
    }
}
