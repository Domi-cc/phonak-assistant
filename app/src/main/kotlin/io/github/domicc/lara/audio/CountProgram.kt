package io.github.domicc.lara.audio

import android.content.Context
import android.os.SystemClock
import io.github.domicc.lara.core.count.CountSession
import io.github.domicc.lara.core.count.CountStep
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.journal.JournalNames
import io.github.domicc.lara.input.InputRouter
import io.github.domicc.lara.shelly.ListenerService
import io.github.domicc.lara.speech.Announcer
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The count session, measurement 1 of the design: while the voice link is open, every press
 * of the remote that arrives is answered with its running number, so the wearer, who counts
 * along, hears at once when a press is lost. Button 3 runs the echo test inside the session.
 * The session ends normally after [IDLE_MS] without input. A hearing-aid tap ends it at once:
 * Android usually delivers that tap as a "stop" to the session service, which cancels the
 * session (`reason=aborted`); only if it arrives as a new tap does the program end it itself
 * (`reason=tap`).
 *
 * While it runs, the scan for the remote is in its session mode. The switch back is in a
 * finally block: a scan left in session mode spends the hour's scan allowance within minutes.
 *
 * Waiting is done by playing silence in short slices, as [AudioLink] demands: Android closes
 * the link once this app has stopped playing. The price is that a number is heard up to a
 * quarter of a second after it was asked for, because it queues behind the silence that is
 * already on its way.
 *
 * If presses come faster than numbers can be said, only the newest number is said. Every press
 * still gets its `session_count` line.
 *
 * What a press does and what is said for it is decided by [CountSession]; this class plays
 * and journals.
 */
class CountProgram(
    private val context: Context,
    private val router: InputRouter,
    private val announcer: Announcer,
) : SessionProgram {

    override suspend fun run(link: AudioLink): String = coroutineScope {
        // The service's own wake lock ends after a minute, and this session lasts for as long
        // as buttons are pressed. Timed locks, renewed while the session runs: none of them
        // outlives it by more than a few seconds, whatever happens to this coroutine.
        val awake = launch {
            while (true) {
                SessionService.holdAwake(context)
                delay(AWAKE_EVERY_MS)
            }
        }
        ListenerService.setSessionOpen(true)
        try {
            val inbox = router.openSession()
            try {
                count(link, inbox)
            } finally {
                router.closeSession(inbox)
            }
        } finally {
            ListenerService.setSessionOpen(false)
            awake.cancel()
        }
    }

    private suspend fun count(link: AudioLink, inbox: ReceiveChannel<Input>): String {
        val counting = CountSession()
        var result = JournalNames.RESULT_OK
        var echoes = 0
        // an input taken from the inbox early, to see whether a newer number follows
        var ahead: Input? = null
        var quietSince = SystemClock.elapsedRealtime()
        // what stays if the loop is left by an exception: second tap, link lost, playback failed
        var reason = "aborted"
        link.step("count_open")
        try {
            while (true) {
                val input = ahead ?: inbox.tryReceive().getOrNull()
                ahead = null
                if (input == null) {
                    if (SystemClock.elapsedRealtime() - quietSince >= IDLE_MS) {
                        reason = "idle"
                        break
                    }
                    link.silence(SLICE_MS)
                    continue
                }
                // from the press (for the remote: its first packet) to this moment
                val about = InputRouter.fields(input) + ("waitMs" to SystemClock.elapsedRealtime() - input.elapsedMs)
                when (val step = counting.onInput(input)) {
                    is CountStep.Announce -> {
                        ahead = inbox.tryReceive().getOrNull()
                        // not said if a newer number is waiting already
                        val said = counting.say(step, newerWaits = ahead?.let(counting::counts) == true)
                        val number = said.number?.let(announcer::number)
                        link.step(
                            "count",
                            about + mapOf(
                                "number" to step.number,
                                "missed" to step.missed,
                                "said" to (number != null),
                                "saidMissed" to said.missed,
                                "voice" to number?.kind,
                            ),
                        )
                        if (said.missed > 0) {
                            link.play(announcer.missed(said.missed).pcm)
                            link.silence(HINT_GAP_MS)
                        }
                        if (number != null) link.play(number.pcm)
                    }
                    is CountStep.Echo -> {
                        link.step("count_echo", about + ("saidMissed" to step.missed))
                        if (step.missed > 0) link.play(announcer.missed(step.missed).pcm)
                        val echo = EchoProgram().run(link)
                        echoes++
                        // a session in which an echo test failed does not end as "ok"
                        if (echo != JournalNames.RESULT_OK && result == JournalNames.RESULT_OK) result = echo
                    }
                    // the number follows when the button is released
                    CountStep.Held -> link.step("count_held", about)
                    CountStep.End -> {
                        link.step("count_stop", about)
                        reason = "tap"
                        break
                    }
                    CountStep.Ignore -> link.step("count_ignored", about)
                }
                quietSince = SystemClock.elapsedRealtime()
            }
        } finally {
            // taken from the inbox already when the session was ended from outside
            ahead?.let { link.step("count_unhandled", InputRouter.fields(it)) }
            // also when the wearer ends the session with a second tap: the count reached then
            // is the result of the measurement
            link.step("count_end", mapOf("count" to counting.count, "echoes" to echoes, "reason" to reason))
        }
        return result
    }

    private companion object {
        const val IDLE_MS = 20_000L
        const val SLICE_MS = 50
        const val HINT_GAP_MS = 150
        const val AWAKE_EVERY_MS = 5_000L
    }
}
