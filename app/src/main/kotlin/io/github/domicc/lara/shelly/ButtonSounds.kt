package io.github.domicc.lara.shelly

import io.github.domicc.lara.audio.Tones
import io.github.domicc.lara.core.input.Gesture

/**
 * One sound per button and gesture, so that the wearer hears which press arrived. The button is
 * the pitch (four rising notes), the gesture is the rhythm. All pitches stay below 1 kHz.
 */
object ButtonSounds {
    private val pitches = mapOf(1 to 440.0, 2 to 554.4, 3 to 659.3, 4 to 880.0)
    private const val OTHER_BUTTON = 330.0

    fun forPress(button: Int, gesture: Gesture): ShortArray {
        val pitch = pitches[button] ?: OTHER_BUTTON
        val beep = Tones.tone(pitch, 90)
        val gap = Tones.silence(70)
        val sound = when (gesture) {
            Gesture.PRESS -> beep
            Gesture.DOUBLE -> beep + gap + beep
            Gesture.TRIPLE -> beep + gap + beep + gap + beep
            Gesture.LONG -> Tones.tone(pitch, 380)
            Gesture.LONG_DOUBLE -> Tones.tone(pitch, 300) + gap + Tones.tone(pitch, 300)
            Gesture.LONG_TRIPLE -> Tones.tone(pitch, 300) + gap + Tones.tone(pitch, 300) + gap + Tones.tone(pitch, 300)
            // held: the note falls
            Gesture.HOLD -> Tones.tone(pitch, 200) + Tones.tone(pitch * 0.75, 220)
            Gesture.HOLD_RELEASE -> Tones.tone(pitch * 0.75, 90)
            Gesture.UNKNOWN -> Tones.tone(220.0, 120)
        }
        return sound + Tones.silence(60)
    }

    /** A short low blip: the remote's packet counter shows that a press was lost before this one. */
    val missed: ShortArray by lazy { Tones.tone(196.0, 70) + Tones.silence(50) }
}
