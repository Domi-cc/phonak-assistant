package io.github.domicc.lara.core.journal

/**
 * The journal types and field names that code reads back, in one place. The counters on the
 * screen are computed from journal lines; if the code that writes a line and the code that
 * counts it each spelled the name themselves, a renamed type would leave a counter at zero
 * while every test still passed.
 *
 * Only names with a reader are listed. Everything else in the journal is for people.
 */
object JournalNames {
    /** A new press of the remote. Fields: [ID], [TS_MS], [MISSED_BEFORE], [CONTROL_SCAN]. */
    const val SHELLY_PRESS = "shelly_press"

    /** A new event of the remote without a press. Same fields. */
    const val SHELLY_BEACON = "shelly_beacon"

    /** Android started the app for a hearing-aid tap. */
    const val TAP = "tap_intent"

    /** A session with the hearing aid is over. Field: [RESULT]. */
    const val SESSION_END = "session_end"

    /** Android did not let a session start. */
    const val SESSION_REFUSED = "session_refused"

    /** The remote's packet id. */
    const val ID = "id"

    /** When the first packet of an event was received, on the elapsed-realtime clock. */
    const val TS_MS = "tsMs"

    /** Events of the remote lost right before this one, counted by packet id. */
    const val MISSED_BEFORE = "missedBefore"

    /** True if the scan of the screen ran: the radio then listened all the time. */
    const val CONTROL_SCAN = "fg"

    const val RESULT = "result"
    const val RESULT_OK = "ok"
    const val RESULT_CANCELLED = "cancelled"
}
