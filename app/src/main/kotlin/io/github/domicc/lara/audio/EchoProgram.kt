package io.github.domicc.lara.audio

import io.github.domicc.lara.core.journal.JournalNames

/**
 * The echo test: a tone says "speak now", a few seconds are recorded from the hearing-aid
 * microphone and played back, so the wearer hears which microphone was used.
 */
class EchoProgram : SessionProgram {

    override suspend fun run(link: AudioLink): String {
        link.silence(250)
        link.play(Tones.speakNow)
        link.step("speak_now")

        val recording = link.record(RECORD_MS)
        link.step("recorded", recording.stats)

        link.play(Tones.recordingDone)
        link.silence(300)
        if (!recording.valid) {
            // the wearer still hears what was recorded, but after a warning
            link.play(Tones.error)
            link.silence(300)
        }
        link.play(recording.pcm)
        link.silence(200)
        link.step("echo_played")
        return if (recording.valid) JournalNames.RESULT_OK else "recording_not_from_hearing_aid"
    }

    private companion object {
        const val RECORD_MS = 4_000
    }
}
