package io.github.domicc.lara.core.audio

/**
 * What the app does about a voice link that was still up when its session gave up waiting for
 * it to go down. While the link is up the hearing aid stays in a streaming program (probably
 * its phone program; nobody has shown that for a voice-recognition link) instead of its
 * hearing program. So the app looks again a few times, says in the journal and to the wearer
 * what it finds, and stops voice recognition once more in the one case where that can still
 * close the link.
 *
 * The rule that orders everything here: never touch a link that this app's session did not
 * open. After the session, hands-free audio on the hearing aid may just as well be a phone
 * call, the next session, or the audio of another app that was there before the session
 * began. So a look ends the follow-up, without a stop, as soon as anything says that the link
 * of the old session is gone or that somebody else may hold one.
 *
 * What a second stop can do, read from the Android 17 source (`HeadsetService`) and not tried:
 * a stop closes the audio only while Android has a voice recognition marked as started, and
 * every stop that reaches that mark clears it. After a stop that Android took, a second one
 * closes nothing and makes Android send the hearing aid an error result. So a follow-up stops
 * at most once, and only if the session's own stop was not taken or never made. In every
 * other case it reports: an ordinary app has no stronger call.
 *
 * The decisions are here; reading the state and calling Android is the app's part.
 */
object StuckVoiceLink {

    /** When the looks happen, counted from the moment the session gave up waiting. */
    val LOOKS_AFTER_MS: List<Long> = listOf(3_000L, 10_000L, 30_000L)

    /**
     * What one look finds.
     *
     * @param word the name used in the journal
     */
    enum class Look(val word: String) {
        /** A new session is busy: the link that is up now is its own. The follow-up ends. */
        NEW_SESSION("new_session"),

        /** The phone is in a call or ringing: the audio on the hearing aid is the call's. The follow-up ends. */
        PHONE_CALL("phone_call"),

        /**
         * The link was reported down at some moment since the session began to close.
         * Whatever is up now was opened by somebody else. The follow-up ends.
         */
        WENT_DOWN("went_down"),

        /** No report of it, but the link is down, or the hearing aid is gone. The follow-up ends. */
        IS_DOWN("is_down"),

        /**
         * Up, but by the reports it did not come up while a session of this app was busy:
         * another app had the hearing aid's audio before the session and has it still. The
         * follow-up ends.
         */
        NOT_THE_SESSIONS("not_the_sessions"),

        /** Still up, still the session's, and a stop may help: stop once more, and look again later. */
        STOP_AGAIN("stop_again"),

        /** Still up and still the session's, and a stop cannot help: say so, and look again later. */
        REPORT("report_only"),
        ;

        /** The link is up and the session's. Every other look ends the follow-up. */
        val stuck: Boolean get() = this == STOP_AGAIN || this == REPORT
    }

    /**
     * Decides one look.
     *
     * @param sessionBusy a session of this app runs or is still closing
     * @param phoneCall the phone's audio mode is one of a call, ringing included
     * @param disconnectSeen the hearing aid's hands-free audio was reported disconnected at
     * any moment since the session began to close
     * @param audioConnected whether that audio is connected right now; null if nobody can
     * say, because the hearing aid is not connected or the phone's hands-free service is gone
     * @param cameUpInSession the link that is up now was reported coming up while a session
     * of this app was busy and the phone was not in a call
     * @param mayStop a stop can still help and this follow-up has not made one yet
     */
    fun look(
        sessionBusy: Boolean,
        phoneCall: Boolean,
        disconnectSeen: Boolean,
        audioConnected: Boolean?,
        cameUpInSession: Boolean,
        mayStop: Boolean,
    ): Look = when {
        sessionBusy -> Look.NEW_SESSION
        phoneCall -> Look.PHONE_CALL
        disconnectSeen -> Look.WENT_DOWN
        audioConnected != true -> Look.IS_DOWN
        !cameUpInSession -> Look.NOT_THE_SESSIONS
        mayStop -> Look.STOP_AGAIN
        else -> Look.REPORT
    }

    /**
     * One look of a follow-up and what follows from it.
     *
     * @param number which look of the follow-up this was, from 1
     * @param signal tell the wearer now, by vibration: true for the first look of a
     * follow-up that finds the link stuck and for no other
     * @param gaveUp this was the last look and the link is still up: the follow-up ends
     */
    data class Step(val number: Int, val look: Look, val signal: Boolean, val gaveUp: Boolean)

    /**
     * One follow-up, from the moment its session gave up waiting. It remembers what its looks
     * have done so far, so that the rules which span several looks are decided here: one stop
     * at most, one signal to the wearer at most, and an end after the last look.
     *
     * @param sessionStopTaken what the session got when it stopped voice recognition at its
     * end: true if Android took the stop, false if not, null if the session made no stop
     * because its start had not been accepted
     */
    class FollowUp(private val sessionStopTaken: Boolean?) {
        private var looks = 0
        private var stopped = false
        private var signalled = false

        /** The next look; the parameters are those of [StuckVoiceLink.look]. */
        fun look(
            sessionBusy: Boolean,
            phoneCall: Boolean,
            disconnectSeen: Boolean,
            audioConnected: Boolean?,
            cameUpInSession: Boolean,
        ): Step {
            looks++
            val look = StuckVoiceLink.look(
                sessionBusy,
                phoneCall,
                disconnectSeen,
                audioConnected,
                cameUpInSession,
                mayStop = sessionStopTaken != true && !stopped,
            )
            if (look == Look.STOP_AGAIN) stopped = true
            val signal = look.stuck && !signalled
            if (signal) signalled = true
            return Step(looks, look, signal, gaveUp = look.stuck && looks >= LOOKS_AFTER_MS.size)
        }
    }
}
