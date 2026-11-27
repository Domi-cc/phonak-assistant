package io.github.domicc.lara.core.audio

import kotlin.math.roundToInt

/**
 * Brings sound to the format of the voice link to the hearing aid: mono at 16 kHz. Speech
 * engines deliver 22.05, 24 or 48 kHz, and a track opened at 16 kHz would play those too slowly.
 *
 * Linear interpolation without a low-pass filter: when the rate is lowered, sound above 8 kHz
 * folds back into the audible band. Synthesised speech has little energy up there.
 */
object Resampler {

    /** Sample rate of the wideband voice link. */
    const val VOICE_LINK_RATE = 16_000

    /** Mono at [VOICE_LINK_RATE], whatever [audio] is. */
    fun toVoiceLink(audio: PcmAudio): ShortArray = resample(toMono(audio), audio.sampleRate)

    /** Averages left and right. Mono comes back as a copy. */
    fun toMono(audio: PcmAudio): ShortArray =
        if (audio.channels == 1) audio.samples.copyOf()
        else ShortArray(audio.frames) { i -> ((audio.samples[2 * i] + audio.samples[2 * i + 1]) / 2).toShort() }

    /**
     * Converts mono samples from [fromRate] to [toRate]. The result always is a new array and
     * lasts as long as the input, to the nearest sample.
     */
    fun resample(mono: ShortArray, fromRate: Int, toRate: Int = VOICE_LINK_RATE): ShortArray {
        require(fromRate > 0 && toRate > 0) { "rates $fromRate $toRate" }
        if (fromRate == toRate) return mono.copyOf()
        val length = (mono.size.toLong() * toRate + fromRate / 2) / fromRate
        require(length <= Int.MAX_VALUE) { "result too long: $length samples" }
        val last = mono.size - 1
        return ShortArray(length.toInt()) { i ->
            // position in the input as index plus fraction, in whole numbers so nothing drifts
            val position = i.toLong() * fromRate
            val index = (position / toRate).toInt()
            if (index >= last) {
                mono[last] // past the last input sample: hold it
            } else {
                val fraction = (position % toRate).toDouble() / toRate
                (mono[index] + (mono[index + 1] - mono[index]) * fraction).roundToInt().toShort()
            }
        }
    }
}
