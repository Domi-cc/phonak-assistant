package io.github.domicc.lara.shelly

import io.github.domicc.lara.core.input.Gesture

/** What the app says for a press on the remote, for example "Taste 1 kurz". */
object RemotePhrases {

    fun forPress(button: Int, gesture: Gesture): String = "Taste $button ${word(gesture)}"

    /** Every phrase a four-button remote can cause; synthesised ahead of time. */
    fun all(): List<String> =
        (1..4).flatMap { button -> Gesture.entries.map { gesture -> forPress(button, gesture) } } +
            listOf(MISSED_ONE, MISSED_SEVERAL)

    const val MISSED_ONE = "einer fehlt"
    const val MISSED_SEVERAL = "mehrere fehlen"

    private fun word(gesture: Gesture): String = when (gesture) {
        Gesture.PRESS -> "kurz"
        Gesture.DOUBLE -> "doppelt"
        Gesture.TRIPLE -> "dreifach"
        Gesture.LONG -> "lang"
        Gesture.LONG_DOUBLE -> "lang doppelt"
        Gesture.LONG_TRIPLE -> "lang dreifach"
        Gesture.HOLD -> "gehalten"
        Gesture.HOLD_RELEASE -> "losgelassen"
        Gesture.UNKNOWN -> "unbekannt"
    }
}
