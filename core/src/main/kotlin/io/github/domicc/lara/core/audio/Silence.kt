package io.github.domicc.lara.core.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Cuts the quiet stretches a speech engine puts before and after a word. In the count session
 * every tenth of a second between the press and its number is felt, and two prompts played one
 * after the other should not have a pause of unknown length between them.
 */
object Silence {

    /**
     * Quieter than this counts as silence: about 46 dB below full scale. Low on purpose, so
     * that the soft start of a word ("sechs", "fünf") is never taken for silence.
     */
    const val DEFAULT_THRESHOLD = 160

    /**
     * @param marginMs how much of the quiet part is kept at each end
     * @return a new array from the first to the last sample louder than [threshold], with the
     * margin around it; empty if no sample is louder, which for a prompt means there is
     * nothing to hear in it
     */
    fun trim(mono: ShortArray, sampleRate: Int, threshold: Int = DEFAULT_THRESHOLD, marginMs: Int = 20): ShortArray {
        val first = mono.indexOfFirst { abs(it.toInt()) > threshold }
        if (first < 0) return ShortArray(0)
        val last = mono.indexOfLast { abs(it.toInt()) > threshold }
        val margin = sampleRate * marginMs / 1000
        return mono.copyOfRange(max(0, first - margin), min(mono.size, last + 1 + margin))
    }
}
