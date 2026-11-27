package io.github.domicc.lara.speech

/** Phrases that belong to every session, whatever its program. */
object SessionPhrases {
    /** Spoken whenever a session ends normally, so the wearer knows the microphone link is closed. */
    const val END = "Beende"

    fun all(): List<String> = listOf(END)
}
