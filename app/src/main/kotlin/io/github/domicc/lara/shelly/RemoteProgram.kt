package io.github.domicc.lara.shelly

import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.audio.AudioLink
import io.github.domicc.lara.audio.EchoProgram
import io.github.domicc.lara.audio.SessionProgram
import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputSource
import io.github.domicc.lara.core.journal.JournalNames
import io.github.domicc.lara.input.InputRouter
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * "Wake, then navigate by remote", in its simplest form: while the voice link is open, every
 * press on the Shelly remote is announced in words ("Taste 1 kurz"). If a phrase has not been
 * synthesised yet, the tone for that button and gesture is played instead. This is what a tap
 * starts while a remote is enrolled, and what a press on the remote opens by itself; the press
 * that opened the session is the first one announced.
 *
 * The session ends after [IDLE_LIMIT_MS] without a press, on a long press of button 4, or when
 * the wearer taps the hearing aid again. Android usually delivers that tap as a "stop" to the
 * session service, which cancels the session (`reason=aborted`); only if it arrives as a new
 * tap does the program end it itself (`reason=tap`).
 *
 * No button of the remote starts a recording here: every press is announced and nothing else,
 * as the owner asked. A press can open this session by itself, also by accident in a pocket,
 * and the microphone must not follow from that. The echo test runs inside the session only
 * when the screen, adb or the assist gesture asks for it. An echo test that fails is journaled
 * (`session_remote_echo_failed`) and does not change how the session ends: an end by idle,
 * button or tap is a normal end and says "Beende".
 *
 * The presses come from the router's inbox, which the listener service's scan feeds through
 * the input bus. This program starts no scan of its own: the session service switches the
 * listener's scan to its session mode for as long as the session lasts.
 *
 * A gap in the remote's packet counter is announced ("einer fehlt") if it lies inside the
 * session. The gap before the first press is not the session's: it lies between the last
 * thing received, maybe hours ago, and now. It stays in the journal and is not said.
 */
class RemoteProgram(private val app: LaraApp) : SessionProgram {

    override suspend fun run(link: AudioLink): String {
        val inbox = app.router.openSession()
        return try {
            listen(link, inbox)
        } finally {
            app.router.closeSession(inbox)
        }
    }

    private suspend fun listen(link: AudioLink, inbox: ReceiveChannel<Input>): String {
        ListenerService.deafBecause(app.shellyStore.remote.value != null)?.let { why ->
            // thirty seconds of silence would look like a session in which nobody pressed
            link.step("remote_no_scan", mapOf("why" to why))
            return NO_SCAN
        }
        var presses = 0
        var echoes = 0
        var echoesFailed = 0
        // no event of the remote has arrived in this session yet
        var first = true
        var quietSince = SystemClock.elapsedRealtime()
        // what stays if the loop is left by an exception: second tap, link lost, playback failed
        var reason = "aborted"
        try {
            while (true) {
                val input = inbox.tryReceive().getOrNull()
                if (input == null) {
                    if (SystemClock.elapsedRealtime() - quietSince >= IDLE_LIMIT_MS) {
                        reason = "idle"
                        break
                    }
                    // Silence keeps the voice link alive and paces this loop.
                    link.silence(SLICE_MS)
                    continue
                }
                // from the press (for the remote: its first packet) to this moment
                val about = InputRouter.fields(input) + ("waitMs" to SystemClock.elapsedRealtime() - input.elapsedMs)
                val button = input.button
                val gesture = input.gesture
                if (input.source == InputSource.HEARING_AID_TAP) {
                    link.step("remote_stop", about)
                    reason = "tap"
                    break
                } else if (input.source != InputSource.SHELLY) {
                    // the screen, adb or the assist gesture: the router hands on nothing from
                    // them but a request for the echo test
                    link.step("remote_echo", about)
                    val echoResult = EchoProgram().run(link)
                    echoes++
                    if (echoResult != JournalNames.RESULT_OK) {
                        // the wearer heard the warning tone before the playback; the session goes on
                        echoesFailed++
                        link.step("remote_echo_failed", mapOf("result" to echoResult))
                    }
                } else if (button == null || gesture == null) {
                    link.step("remote_ignored", about)
                } else {
                    val missed = if (first) 0 else input.missedBefore
                    first = false
                    presses++
                    // Spoken if the phrase is ready, otherwise the tone for that button and gesture.
                    val spoken = app.prompts.get(RemotePhrases.forPress(button, gesture))
                    link.step("remote_press", about + mapOf("missed" to missed, "voice" to if (spoken != null) "speech" else "tones"))
                    if (missed > 0) {
                        val phrase = if (missed == 1) RemotePhrases.MISSED_ONE else RemotePhrases.MISSED_SEVERAL
                        link.play(app.prompts.get(phrase) ?: ButtonSounds.missed)
                    }
                    link.play(spoken ?: ButtonSounds.forPress(button, gesture))
                    if (spoken != null) link.silence(AFTER_PHRASE_MS)
                    if (button == END_BUTTON && gesture == Gesture.LONG) {
                        reason = "button"
                        break
                    }
                }
                quietSince = SystemClock.elapsedRealtime()
            }
        } finally {
            link.step(
                "remote_end",
                mapOf("presses" to presses, "echoes" to echoes, "echoesFailed" to echoesFailed, "reason" to reason),
            )
        }
        return JournalNames.RESULT_OK
    }

    private companion object {
        const val IDLE_LIMIT_MS = 30_000L
        const val SLICE_MS = 80
        const val AFTER_PHRASE_MS = 120
        const val END_BUTTON = 4
        const val NO_SCAN = "no_scan_for_remote"
    }
}
