package io.github.domicc.lara.speech

import io.github.domicc.lara.audio.Tones

/**
 * What the count session plays into the hearing aid: speech where the phrase has been prepared,
 * tone patterns where it has not (first start, no speech engine, no German voice). A session
 * must work without speech, because the wearer cannot look at the phone to find out why it is
 * quiet.
 */
class CountSounds(private val prompts: PromptCache) {

    /**
     * @param pcm 16 kHz mono, ready for the voice link
     * @param spoken false if tones stand in for the words
     */
    class Sound(val pcm: ShortArray, val spoken: Boolean) {
        /** For the journal. */
        val kind: String get() = if (spoken) "speech" else "tones"
    }

    /**
     * The running number of the count. Without speech: as many short ticks as the number up to
     * [MAX_TICKS], and one fixed three-note figure for everything above, where counting ticks
     * would take longer than the next press.
     */
    fun number(number: Int): Sound {
        CountPhrases.number(number)?.let(prompts::get)?.let { return Sound(it, spoken = true) }
        if (number in 1..MAX_TICKS) return Sound(ticks(number), spoken = false)
        return Sound(manyTone, spoken = false)
    }

    /**
     * The hint that [count] events of the remote were lost: "einer fehlt", "drei fehlen".
     * Without speech: one long low tone, whatever the count.
     */
    fun missed(count: Int): Sound {
        if (count == 1) {
            prompts.get(CountPhrases.MISSED_ONE)?.let { return Sound(it, spoken = true) }
        } else {
            val number = CountPhrases.number(count)?.let(prompts::get)
            val word = prompts.get(CountPhrases.MISSED_MANY)
            if (number != null && word != null) return Sound(number + Tones.silence(WORD_GAP_MS) + word, spoken = true)
        }
        return Sound(missedTone, spoken = false)
    }

    private fun ticks(count: Int): ShortArray {
        var pcm = ShortArray(0)
        repeat(count) { index ->
            if (index > 0) pcm += Tones.silence(TICK_GAP_MS)
            pcm += tick
        }
        return pcm
    }

    // Like all tones of the app below 1 kHz, and none of them like the tones of a session:
    // rising or falling pair (open, close), single 880 Hz and 659 Hz (echo test), two low
    // buzzes (error).
    private val tick: ShortArray by lazy { Tones.tone(700.0, 60) }
    private val manyTone: ShortArray by lazy {
        Tones.tone(440.0, 70) + Tones.silence(30) + Tones.tone(554.0, 70) + Tones.silence(30) + Tones.tone(659.0, 110)
    }
    private val missedTone: ShortArray by lazy { Tones.tone(330.0, 380) }

    private companion object {
        const val MAX_TICKS = 5
        const val TICK_GAP_MS = 90
        const val WORD_GAP_MS = 60
    }
}
