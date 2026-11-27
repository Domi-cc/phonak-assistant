package io.github.domicc.lara.speech

import io.github.domicc.lara.core.speech.GermanNumbers
import io.github.domicc.lara.shelly.RemotePhrases

/** What the count session says: the running number and the hint that events of the remote were lost. */
object CountPhrases {
    /** Exactly one event of the remote is missing. The remote session says the same. */
    const val MISSED_ONE = RemotePhrases.MISSED_ONE

    /** Said after a number: "drei fehlen". */
    const val MISSED_MANY = "fehlen"

    /** The number as a word ("einundzwanzig"); null outside 1 to [GermanNumbers.MAX]. */
    fun number(number: Int): String? = GermanNumbers.word(number)

    fun all(): List<String> = (1..GermanNumbers.MAX).mapNotNull(::number) + MISSED_ONE + MISSED_MANY
}

/**
 * Everything the app says over the voice link, each phrase once. The order is the order in
 * which the phrases are synthesised: first what every session needs, then the presses of the
 * remote, last the numbers of the count session, which only a measurement series needs.
 */
object Prompts {
    val all: List<String> = (SessionPhrases.all() + RemotePhrases.all() + CountPhrases.all()).distinct()
}
