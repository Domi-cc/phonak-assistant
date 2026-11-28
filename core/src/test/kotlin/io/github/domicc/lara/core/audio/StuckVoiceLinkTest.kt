package io.github.domicc.lara.core.audio

import io.github.domicc.lara.core.audio.StuckVoiceLink.Look
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StuckVoiceLinkTest {

    private fun look(
        sessionBusy: Boolean = false,
        phoneCall: Boolean = false,
        disconnectSeen: Boolean = false,
        audioConnected: Boolean? = true,
        cameUpInSession: Boolean = true,
        mayStop: Boolean = true,
    ) = StuckVoiceLink.look(sessionBusy, phoneCall, disconnectSeen, audioConnected, cameUpInSession, mayStop)

    /** A look of [this] follow-up that finds the link up, the session's, and nothing else going on. */
    private fun StuckVoiceLink.FollowUp.stuckLook() = look(
        sessionBusy = false,
        phoneCall = false,
        disconnectSeen = false,
        audioConnected = true,
        cameUpInSession = true,
    )

    @Test
    fun `a link that is still up with nothing else going on is stopped once more if a stop can help`() {
        assertEquals(Look.STOP_AGAIN, look())
    }

    @Test
    fun `a link that is still up is only reported if a stop cannot help`() {
        assertEquals(Look.REPORT, look(mayStop = false))
    }

    @Test
    fun `a new session ends the follow-up whatever else is true`() {
        // the link that is up now is the new session's
        for (phoneCall in listOf(true, false)) {
            for (disconnectSeen in listOf(true, false)) {
                for (audioConnected in listOf(true, false, null)) {
                    for (cameUpInSession in listOf(true, false)) {
                        assertEquals(
                            Look.NEW_SESSION,
                            look(
                                sessionBusy = true,
                                phoneCall = phoneCall,
                                disconnectSeen = disconnectSeen,
                                audioConnected = audioConnected,
                                cameUpInSession = cameUpInSession,
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a phone call ends the follow-up, also while its audio is on the hearing aid`() {
        assertEquals(Look.PHONE_CALL, look(phoneCall = true))
        assertEquals(Look.PHONE_CALL, look(phoneCall = true, disconnectSeen = true))
        assertEquals(Look.PHONE_CALL, look(phoneCall = true, audioConnected = false))
    }

    @Test
    fun `a link that went down in between is not the session's any more, even if one is up now`() {
        assertEquals(Look.WENT_DOWN, look(disconnectSeen = true, audioConnected = true))
        assertEquals(Look.WENT_DOWN, look(disconnectSeen = true, audioConnected = false))
        assertEquals(Look.WENT_DOWN, look(disconnectSeen = true, audioConnected = null))
    }

    @Test
    fun `a link that is down without a report of it needs nothing`() {
        assertEquals(Look.IS_DOWN, look(audioConnected = false))
        // the hearing aid is not connected any more, or the phone's hands-free service is gone
        assertEquals(Look.IS_DOWN, look(audioConnected = null))
    }

    @Test
    fun `a link that did not come up in a session is somebody else's and is left alone`() {
        // another app held the hearing aid's audio before the session, and holds it after
        assertEquals(Look.NOT_THE_SESSIONS, look(cameUpInSession = false))
        assertEquals(Look.NOT_THE_SESSIONS, look(cameUpInSession = false, mayStop = false))
    }

    @Test
    fun `only a link that is up and the session's keeps the follow-up going`() {
        assertEquals(listOf(Look.STOP_AGAIN, Look.REPORT), Look.entries.filter { it.stuck })
    }

    @Test
    fun `after a stop that Android took, the follow-up never stops again and tells the wearer once`() {
        // by the Android source a second stop closes nothing then and sends the hearing aid an error
        val followUp = StuckVoiceLink.FollowUp(sessionStopTaken = true)
        assertEquals(StuckVoiceLink.Step(1, Look.REPORT, signal = true, gaveUp = false), followUp.stuckLook())
        assertEquals(StuckVoiceLink.Step(2, Look.REPORT, signal = false, gaveUp = false), followUp.stuckLook())
        assertEquals(StuckVoiceLink.Step(3, Look.REPORT, signal = false, gaveUp = true), followUp.stuckLook())
    }

    @Test
    fun `after a stop that Android did not take, the follow-up stops once, at its first look`() {
        val followUp = StuckVoiceLink.FollowUp(sessionStopTaken = false)
        assertEquals(StuckVoiceLink.Step(1, Look.STOP_AGAIN, signal = true, gaveUp = false), followUp.stuckLook())
        assertEquals(StuckVoiceLink.Step(2, Look.REPORT, signal = false, gaveUp = false), followUp.stuckLook())
        assertEquals(StuckVoiceLink.Step(3, Look.REPORT, signal = false, gaveUp = true), followUp.stuckLook())
    }

    @Test
    fun `a session that made no stop of its own is followed like one whose stop was not taken`() {
        // its start was not accepted, so its link was opened another way and closed without a stop
        val followUp = StuckVoiceLink.FollowUp(sessionStopTaken = null)
        assertEquals(Look.STOP_AGAIN, followUp.stuckLook().look)
        assertEquals(Look.REPORT, followUp.stuckLook().look)
    }

    @Test
    fun `a look that ends the follow-up is no signal and no giving up`() {
        val followUp = StuckVoiceLink.FollowUp(sessionStopTaken = true)
        assertEquals(StuckVoiceLink.Step(1, Look.REPORT, signal = true, gaveUp = false), followUp.stuckLook())
        val down = followUp.look(
            sessionBusy = false, phoneCall = false, disconnectSeen = true, audioConnected = false, cameUpInSession = false,
        )
        assertEquals(StuckVoiceLink.Step(2, Look.WENT_DOWN, signal = false, gaveUp = false), down)
    }

    @Test
    fun `a link that is gone at the last look is not given up`() {
        val followUp = StuckVoiceLink.FollowUp(sessionStopTaken = true)
        followUp.stuckLook()
        followUp.stuckLook()
        val last = followUp.look(
            sessionBusy = false, phoneCall = false, disconnectSeen = false, audioConnected = false, cameUpInSession = true,
        )
        assertEquals(Look.IS_DOWN, last.look)
        assertFalse(last.gaveUp)
    }

    @Test
    fun `a link found stuck only at a later look is signalled then, and stopped then if that can help`() {
        val followUp = StuckVoiceLink.FollowUp(sessionStopTaken = false)
        // the first look falls into a phone call that is over at the second: cannot happen in
        // the app, which ends at the first, but the rule must not depend on that
        val first = followUp.look(
            sessionBusy = false, phoneCall = true, disconnectSeen = false, audioConnected = true, cameUpInSession = true,
        )
        assertFalse(first.signal)
        val second = followUp.stuckLook()
        assertEquals(Look.STOP_AGAIN, second.look)
        assertTrue(second.signal)
    }

    @Test
    fun `the words for the journal stay as they are`() {
        assertEquals(
            listOf("new_session", "phone_call", "went_down", "is_down", "not_the_sessions", "stop_again", "report_only"),
            Look.entries.map { it.word },
        )
    }

    @Test
    fun `three looks, 3 s, 10 s and 30 s after the session gave up waiting`() {
        assertEquals(listOf(3_000L, 10_000L, 30_000L), StuckVoiceLink.LOOKS_AFTER_MS)
    }
}
