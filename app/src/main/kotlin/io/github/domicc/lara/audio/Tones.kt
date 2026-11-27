package io.github.domicc.lara.audio

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Short signal tones as 16 kHz mono PCM, the format of the wideband voice link to the hearing
 * aid. All tones stay below 1 kHz so they remain audible with high-frequency hearing loss.
 */
object Tones {
    const val SAMPLE_RATE = 16_000

    fun silence(ms: Int): ShortArray = ShortArray(SAMPLE_RATE * ms / 1000)

    fun tone(frequencyHz: Double, ms: Int, volume: Double = 0.35): ShortArray {
        val samples = SAMPLE_RATE * ms / 1000
        val fade = min(samples / 2, SAMPLE_RATE * 8 / 1000) // 8 ms fade avoids clicks
        return ShortArray(samples) { i ->
            val envelope = when {
                fade == 0 -> 1.0
                i < fade -> i.toDouble() / fade
                i >= samples - fade -> (samples - 1 - i).toDouble() / fade
                else -> 1.0
            }
            (sin(2.0 * PI * frequencyHz * i / SAMPLE_RATE) * volume * envelope * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Rising pair: the session is open. */
    val sessionStart: ShortArray by lazy { tone(523.0, 90) + silence(40) + tone(784.0, 130) }

    /** Single higher tone: speak now. */
    val speakNow: ShortArray by lazy { tone(880.0, 160) }

    /** Single lower tone: recording finished. */
    val recordingDone: ShortArray by lazy { tone(659.0, 120) }

    /** Falling pair: the session closes normally. */
    val sessionEnd: ShortArray by lazy { tone(784.0, 90) + silence(40) + tone(523.0, 130) }

    /** Two low buzzes: something went wrong. */
    val error: ShortArray by lazy { tone(220.0, 220) + silence(90) + tone(220.0, 220) }
}
