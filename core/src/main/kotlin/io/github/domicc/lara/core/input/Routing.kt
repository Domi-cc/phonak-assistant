package io.github.domicc.lara.core.input

/** What the app is doing with the hearing aid at the moment an input arrives. */
enum class SessionState {
    /** No session runs and none is on its way. */
    NONE,

    /** A count session was asked for a moment ago; its voice link is not up yet. */
    OPENING,

    /** A session runs and its program takes inputs. */
    OPEN,
}

/**
 * What happens to an input.
 *
 * @param word the name used in the journal
 */
enum class Route(val word: String) {
    /** Open a count session. The input only opens it and is not counted. */
    START_COUNT("start_count"),

    /** Open a count session and keep the input for it: it becomes number one. */
    WAKE_COUNT("wake_count"),

    /** Keep the input for the session that is being opened, without asking for another one. */
    HOLD("hold"),

    /** Hand the input to the running session. */
    TO_SESSION("to_session"),

    /** Nothing: the input is journaled and counted on the screen only. */
    NONE("none"),
}

/**
 * The one place that decides what an input does. Version 0.1 measures "wake by tap, navigate
 * by Shelly":
 * - A hearing-aid tap opens a count session. A tap that arrives while one runs ends it, like
 *   the "stop" the hearing aid itself sends for a second tap.
 * - A Shelly press belongs to the running session. Without a session it opens one, if the
 *   owner allows the remote to wake the app; the press that did so is the first one counted.
 *   With that setting off, presses outside a session do nothing. That is the silent mode for
 *   measuring over hours how many presses Android's scan throttling lets through.
 * - The screen, adb and the assist gesture name the program they want themselves and do not
 *   pass through here.
 */
object Routing {

    /**
     * @param session what the app is doing right now
     * @param shellyWakes the owner's setting "Shelly weckt auf"
     */
    fun route(input: Input, session: SessionState, shellyWakes: Boolean): Route = when (input.source) {
        InputSource.HEARING_AID_TAP ->
            if (session == SessionState.OPEN) Route.TO_SESSION else Route.START_COUNT
        InputSource.SHELLY -> when {
            session == SessionState.OPEN -> Route.TO_SESSION
            // Also while a session is being opened: if that attempt has failed in the
            // meantime, this press starts the next one; if not, the request is turned down as
            // "busy" and the press waits for the session like the one before it.
            shellyWakes -> Route.WAKE_COUNT
            session == SessionState.OPENING -> Route.HOLD
            else -> Route.NONE
        }
        else -> Route.NONE
    }
}
