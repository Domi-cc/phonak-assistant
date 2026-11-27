package io.github.domicc.lara.core.input

/** What the app is doing with the hearing aid at the moment an input arrives. */
enum class SessionState {
    /** No session runs and none is on its way. */
    NONE,

    /** A session that takes inputs was asked for a moment ago; its program does not listen yet. */
    OPENING,

    /** A session runs and its program takes inputs. */
    OPEN,
}

/**
 * What a hearing-aid tap starts, the owner's setting "Hörgeräte-Tap startet". The tap is the
 * only trigger that is known to work with the phone locked and dark, so it has to be able to
 * start every program.
 *
 * @param word the name used in the preferences, in the journal and in commands; the same as
 * the name of the session program
 */
enum class TapProgram(val word: String) {
    /**
     * The remote session, "wake by tap, navigate by remote": every press is announced in
     * words. The default. While no remote is enrolled the tap runs the echo test instead.
     */
    REMOTE("remote"),

    /** The echo test: what the hearing-aid microphone recorded is played back. */
    ECHO("echo"),

    /** A count session, the measurement tool for series: every press gets its running number. */
    COUNT("count");

    companion object {
        fun fromWord(word: String?): TapProgram? = entries.firstOrNull { it.word == word }
    }
}

/**
 * What happens to an input.
 *
 * @param word the name used in the journal
 */
enum class Route(val word: String) {
    /** Run the echo test in a session of its own. */
    START_ECHO("start_echo"),

    /** Open a remote session. The input only opens it and is not announced. */
    START_REMOTE("start_remote"),

    /** Open a count session. The input only opens it and is not counted. */
    START_COUNT("start_count"),

    /** Open a remote session and keep the input for it: it is the first press the session announces. */
    WAKE_REMOTE("wake_remote"),

    /** Keep the input for the session that is being opened, without asking for another one. */
    HOLD("hold"),

    /** Hand the input to the running session. */
    TO_SESSION("to_session"),

    /** Nothing: the input is journaled and counted on the screen only. */
    NONE("none"),

    /**
     * Nothing, and the press is given up: it reached the app so long after it was made that
     * the session it asks for could no longer announce it. [WaitingInputs] decides this,
     * because it knows the time; [Routing] itself never returns it.
     */
    TOO_LATE("too_late"),
}

/**
 * The one place that decides what an input does:
 * - A hearing-aid tap starts what the owner chose for it: the remote session (the echo test
 *   while no remote is enrolled), the echo test or a count session. A tap that arrives while
 *   a session takes inputs ends that session, like the "stop" the hearing aid itself sends
 *   for a second tap.
 * - A press on the remote belongs to the running session, or to the one that is being opened.
 *   Without a session it opens a remote session, if the owner allows the remote to wake the
 *   app; the press that did so is the first one the session announces. With that setting off,
 *   presses outside a session do nothing. That is the silent mode for measuring over hours how
 *   many presses Android's scan throttling lets through.
 * - A press with a gesture code this app does not know does nothing at all. It is journaled
 *   where it arrives, but it never opens the voice link: a damaged or foreign packet must not
 *   be able to start a session.
 * - The screen, adb and the assist gesture name the program they want themselves and do not
 *   pass through here.
 */
object Routing {

    /**
     * @param session what the app is doing right now
     * @param shellyWakes the owner's setting "Shelly weckt auf"
     * @param tap the owner's setting "Hörgeräte-Tap startet"
     * @param remoteEnrolled a remote has been picked; without one a remote session would hear nothing
     */
    fun route(
        input: Input,
        session: SessionState,
        shellyWakes: Boolean,
        tap: TapProgram,
        remoteEnrolled: Boolean,
    ): Route = when (input.source) {
        InputSource.HEARING_AID_TAP -> when {
            session == SessionState.OPEN -> Route.TO_SESSION
            tap == TapProgram.COUNT -> Route.START_COUNT
            tap == TapProgram.REMOTE && remoteEnrolled -> Route.START_REMOTE
            else -> Route.START_ECHO
        }
        InputSource.SHELLY -> when {
            input.gesture == Gesture.UNKNOWN -> Route.NONE
            session == SessionState.OPEN -> Route.TO_SESSION
            // Whatever the setting says: a session is on its way and will announce this press.
            // If it does not come, the session service says so and the press is given up.
            session == SessionState.OPENING -> Route.HOLD
            shellyWakes -> Route.WAKE_REMOTE
            else -> Route.NONE
        }
        else -> Route.NONE
    }
}
