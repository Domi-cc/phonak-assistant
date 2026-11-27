package io.github.domicc.lara.shelly

import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.audio.AudioLink
import io.github.domicc.lara.audio.SessionProgram
import io.github.domicc.lara.core.input.Gesture

/**
 * "Wake by tap, navigate by remote", in its simplest form: while the voice link is open, every
 * press on the Shelly remote is announced ("Taste 1 kurz"). If a phrase has not been
 * synthesised yet, the tone for that button and gesture is played instead.
 *
 * The session ends after [IDLE_LIMIT_MS] without a press, on a long press of button 4, or when
 * the wearer taps the hearing aid again.
 */
class RemoteProgram : SessionProgram {

    override suspend fun run(link: AudioLink): String {
        val app = LaraApp.instance
        val remote = app.shelly.remote.value ?: return "no_remote_enrolled"
        val settings = app.shelly.scan.value
        val scan = SessionScan(app, app.journal, remote.address, settings.session, settings.passive)
        if (!scan.start()) return "scan_refused"
        var presses = 0
        try {
            var idleMs = 0
            while (idleMs < IDLE_LIMIT_MS) {
                val event = scan.events.tryReceive().getOrNull()
                if (event == null) {
                    // Silence keeps the voice link alive and paces this loop.
                    link.silence(SLICE_MS)
                    idleMs += SLICE_MS
                    scan.tick()
                    continue
                }
                idleMs = 0
                if (event.missedBefore > 0) {
                    val phrase = if (event.missedBefore == 1) RemotePhrases.MISSED_ONE else RemotePhrases.MISSED_SEVERAL
                    link.play(app.prompts.get(phrase) ?: ButtonSounds.missed)
                }
                for (press in event.presses) {
                    presses++
                    // Spoken if the phrase is ready, otherwise the tone for that button and gesture.
                    val spoken = app.prompts.get(RemotePhrases.forPress(press.button, press.gesture))
                    link.play(spoken ?: ButtonSounds.forPress(press.button, press.gesture))
                    if (spoken != null) link.silence(120)
                    if (press.button == END_BUTTON && press.gesture == Gesture.LONG) {
                        link.step("remote_end_requested", mapOf("presses" to presses))
                        return "ok"
                    }
                }
            }
            link.step("remote_idle_timeout", mapOf("presses" to presses))
            return "ok"
        } finally {
            scan.stop()
        }
    }

    private companion object {
        const val IDLE_LIMIT_MS = 30_000
        const val SLICE_MS = 80
        const val END_BUTTON = 4
    }
}
