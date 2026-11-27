package io.github.domicc.lara.core.input

/**
 * What the router knows about sessions, and the presses that wait for one. It is the state
 * behind every routing decision, kept free of Android so that it can be tested on its own.
 *
 * Whether a session runs, only the session service knows for certain, and it cannot be asked.
 * So this class does not keep a copy of that state. It knows three things at first hand:
 * - a program that takes inputs has opened its inbox ([open]) and not closed it yet ([close]);
 * - the router has asked for a count session a moment ago ([asked]);
 * - the program closed its inbox a moment ago, so the session service is still busy playing
 *   the closing tone.
 *
 * A press that opens a session, or arrives while one is being opened, waits here until the
 * program fetches it; that is how the press that woke the app becomes the first one of the
 * session. A press that waits longer than [waitLimitMs] is handed back as stale: the session
 * did not come, and a press must never be acted on long after it was made.
 *
 * Times are milliseconds on any clock that never runs backwards and counts while the phone
 * sleeps. Not thread-safe: the router guards every call with its lock.
 *
 * @param waitLimitMs how long a press waits for its session. Opening the voice link takes
 * about a second; in the worst case the link gives up after 15 seconds.
 * @param closingMs how long after [close] the session service may still be busy with the
 * session that has just ended (closing tone, tail, closing the link)
 * @param retryMs how long to wait before asking a busy session service again
 */
class WaitingInputs(
    private val waitLimitMs: Long = WAIT_LIMIT_MS,
    private val closingMs: Long = CLOSING_MS,
    private val retryMs: Long = RETRY_MS,
) {
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
     * @param askAgainInMs non-null if the router should call [askAgain] after that time
     */
    data class Busy(val sessionTakesInput: Boolean, val waiting: Int, val askAgainInMs: Long?)

    /** True while a program has its inbox open. */
    var isOpen = false
        private set

    /** Presses kept for the session that is being opened, oldest first. */
    private val waiting = ArrayDeque<Input>()

    /** When the router last asked for a count session; null once that session has opened. */
    private var askedAtMs: Long? = null

    /** When the last program closed its inbox. */
    private var closedAtMs: Long? = null

    /** A call of [askAgain] is on its way; keeps several busy answers from starting several. */
    private var askingAgain = false

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
    fun route(input: Input, nowMs: Long, shellyWakes: Boolean, tap: TapProgram): Routed {
        val stale = takeStale(nowMs)
        val state = state(nowMs)
        val route = Routing.route(input, state, shellyWakes, tap)
        if (route == Route.WAKE_COUNT || route == Route.HOLD) waiting.addLast(input)
        return Routed(route, state, stale)
    }

    /** The router asks the session service for a count session now. */
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
        return Opened(handOver, stale)
    }

    /** The program has closed its inbox; its session ends within the next moments. */
    fun close(nowMs: Long) {
        isOpen = false
        closedAtMs = nowMs
    }

    /** Removes and returns the presses that have waited too long. */
    fun takeStale(nowMs: Long): List<Input> {
        val stale = ArrayList<Input>()
        while (waiting.isNotEmpty() && nowMs - waiting.first().elapsedMs > waitLimitMs) stale += waiting.removeFirst()
        return stale
    }

    /**
     * The session service turned a request down because a session runs already. Presses that
     * wait stay where they are: if the running session is the count session that is being
     * opened, its program fetches them; if it is another one, they run out.
     *
     * The one case in which nobody would come for them is a session that is just closing: its
     * program takes no inputs any more, and the service is busy for another second. The wearer
     * who presses right after the closing tone would hear nothing. Then, and only then, the
     * router is told to ask again.
     */
    fun busy(nowMs: Long): Busy {
        val closing = closedAtMs?.let { nowMs - it < closingMs } == true
        val again = !isOpen && waiting.isNotEmpty() && closing && !askingAgain
        if (again) askingAgain = true
        return Busy(isOpen, waiting.size, if (again) retryMs else null)
    }

    /**
     * The time [Busy.askAgainInMs] is over. Call [takeStale] first.
     *
     * @return the press to ask a count session for; null if none waits any more or a session
     * has opened in the meantime
     */
    fun askAgain(): Input? {
        askingAgain = false
        return if (isOpen) null else waiting.firstOrNull()
    }

    companion object {
        const val WAIT_LIMIT_MS = 20_000L
        const val CLOSING_MS = 5_000L
        const val RETRY_MS = 400L
    }
}
