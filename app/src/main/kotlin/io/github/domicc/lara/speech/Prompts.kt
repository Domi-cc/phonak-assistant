package io.github.domicc.lara.speech

import io.github.domicc.lara.core.speech.GermanNumbers

/**
 * One thing the app can say.
 *
 * @param key a short name: part of the file name and the id of the synthesis job
 * @param text what the speech engine is asked to say, in German
 */
data class Prompt(val key: String, val text: String)

/** Everything the app says in version 0.1: the numbers of the count and two short phrases. */
object Prompts {

    /** Exactly one event of the remote is missing. */
    val MISSED_ONE = Prompt("missed_one", "einer fehlt")

    /** Said after a number: "drei fehlen". */
    val MISSED_MANY = Prompt("missed_many", "fehlen")

    /** Null outside 1 to [GermanNumbers.MAX]. */
    fun number(number: Int): Prompt? = GermanNumbers.word(number)?.let { Prompt("n$number", it) }

    val all: List<Prompt> = (1..GermanNumbers.MAX).mapNotNull(::number) + MISSED_ONE + MISSED_MANY
}
