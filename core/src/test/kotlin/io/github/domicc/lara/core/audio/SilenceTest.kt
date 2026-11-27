package io.github.domicc.lara.core.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SilenceTest {

    /** [before] quiet samples, [sound] loud ones, [after] quiet ones, at 1000 samples per second. */
    private fun clip(before: Int, sound: Int, after: Int): ShortArray =
        ShortArray(before) { 3 } + ShortArray(sound) { 5_000 } + ShortArray(after) { -3 }

    @Test
    fun `the quiet ends are cut and a margin is kept`() {
        // at 1000 Hz one sample is one millisecond
        val trimmed = Silence.trim(clip(before = 300, sound = 100, after = 500), sampleRate = 1_000, marginMs = 20)
        assertEquals(20 + 100 + 20, trimmed.size)
        assertEquals(3, trimmed.first().toInt())
        assertEquals(5_000, trimmed[20].toInt())
        assertEquals(-3, trimmed.last().toInt())
    }

    @Test
    fun `a clip without quiet ends comes back whole, as a copy`() {
        val loud = ShortArray(50) { 5_000 }
        val trimmed = Silence.trim(loud, sampleRate = 1_000)
        assertContentEquals(loud, trimmed)
        trimmed[0] = 0
        assertEquals(5_000, loud[0].toInt())
    }

    @Test
    fun `ends shorter than the margin are kept as they are`() {
        val trimmed = Silence.trim(clip(before = 5, sound = 10, after = 7), sampleRate = 1_000, marginMs = 20)
        assertEquals(22, trimmed.size)
    }

    @Test
    fun `negative samples count as sound`() {
        val samples = shortArrayOf(0, 0, -4_000, 0, 0)
        assertContentEquals(shortArrayOf(-4_000), Silence.trim(samples, sampleRate = 1_000, marginMs = 0))
    }

    @Test
    fun `the loudest negative value does not overflow`() {
        val samples = shortArrayOf(0, Short.MIN_VALUE, 0)
        assertContentEquals(shortArrayOf(Short.MIN_VALUE), Silence.trim(samples, sampleRate = 1_000, marginMs = 0))
    }

    @Test
    fun `a clip with nothing to hear comes back empty`() {
        assertEquals(0, Silence.trim(ShortArray(400) { 10 }, sampleRate = 1_000).size)
        assertEquals(0, Silence.trim(ShortArray(0), sampleRate = 16_000).size)
    }

    @Test
    fun `quiet stretches inside the sound stay`() {
        val samples = clip(10, 10, 0) + clip(30, 10, 10)
        assertEquals(10 + 30 + 10, Silence.trim(samples, sampleRate = 1_000, marginMs = 0).size)
    }
}
