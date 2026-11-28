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

    /**
     * The media stream to the hearing aid started or stopped, whoever plays: this app, another
     * app or the phone's own sounds. Fields: [STATE] and [ANNOUNCING] on every line, and
     * [ON_MS] on the line that closes the stream.
     */
    const val AID_MEDIA_STREAM = "aid_media_stream"

    /**
     * The voice link (hands-free audio) to the hearing aid changed. Fields: [STATE] and
     * [SESSION] on every line, and on the line that closes the link [ON_MS] and, if a phone
     * call took the link over, [CALL_MS]. Builds before the channels were watched all day
     * wrote the state alone, and only while a session ran.
     */
    const val HFP_AUDIO_STATE = "hfp_audio_state"

    const val STATE = "state"

    /**
     * The [STATE] that closes a media stream. Only written for a stream whose start was
     * seen: a report that nothing plays with no start before it has a word of its own,
     * which no counter reads.
     */
    const val STATE_STOPPED = "stopped"

    /** The [STATE] of a voice link that was asked for and is not up yet. */
    const val STATE_CONNECTING = "connecting"

    /** The [STATE] of a voice link that is up. */
    const val STATE_CONNECTED = "connected"

    /** The [STATE] that closes a voice link. */
    const val STATE_DISCONNECTED = "disconnected"

    /**
     * The [STATE] of a voice link that was not up and still is not: what the app writes when
     * Android reports "disconnected" with "disconnected" as the state before, after an
     * attempt that was refused or failed at once. No opening.
     */
    const val STATE_NOT_CONNECTED = "not_connected"

    /** How long the channel was open, in milliseconds; absent if the app did not see it open. */
    const val ON_MS = "onMs"

    /**
     * How much of [ON_MS] of a voice link came after the phone went into a call, in
     * milliseconds; absent if it did not.
     */
    const val CALL_MS = "callMs"

    /** True if an announcement of this app was playing or had just ended. */
    const val ANNOUNCING = "announcing"

    /**
     * On the line that closes a voice link: true if a session of this app held it. On the
     * lines before: true if a session was busy at that report and the phone was not in a call.
     */
    const val SESSION = "session"
}
