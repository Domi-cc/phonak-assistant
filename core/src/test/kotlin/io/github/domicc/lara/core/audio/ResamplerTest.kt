package io.github.domicc.lara.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ResamplerTest {

    private fun sine(frequencyHz: Double, sampleRate: Int, samples: Int, amplitude: Double = AMPLITUDE): ShortArray =
        ShortArray(samples) { i -> (sin(2 * PI * frequencyHz * i / sampleRate) * amplitude).roundToInt().toShort() }

    @Test
    fun `a sine keeps its pitch, level and length at every common rate`() {
        for (rate in listOf(8_000, 11_025, 22_050, 24_000, 32_000, 44_100, 48_000, 96_000)) {
            val input = sine(TONE_HZ, rate, samples = rate) // one second
            val output = Resampler.resample(input, rate)
            assertEquals(16_000, output.size, "$rate Hz: one second stays one second")

            // Largest error of a straight line between two samples of a sine: amplitude * (2 pi f / rate)^2 / 8,
            // plus one for rounding on the way in and out.
            val tolerance = AMPLITUDE * (2 * PI * TONE_HZ / rate).pow(2) / 8 + 1
            val ideal = sine(TONE_HZ, 16_000, samples = 16_000)
            // the last input sample is held for the rest of its period; compare up to there
            val covered = ((rate - 1).toLong() * 16_000 / rate).toInt()
            var worst = 0
            for (i in 0..covered) worst = maxOf(worst, abs(output[i] - ideal[i]))
            assertTrue(worst <= tolerance, "$rate Hz: off by $worst, allowed $tolerance")
            for (i in covered + 1 until output.size) assertEquals(input.last(), output[i], "$rate Hz: tail")

            // pitch: 440 upward zero crossings in one second
            val crossings = (1 until output.size).count { output[it - 1] < 0 && output[it] >= 0 }
            assertTrue(crossings in 439..440, "$rate Hz: $crossings periods")
            // level: the peak is still there
            assertTrue(output.max() > AMPLITUDE * 0.97 && output.max() <= AMPLITUDE, "$rate Hz: peak ${output.max()}")
        }
    }

    @Test
    fun `a sine sampled at 48 kHz gives exactly every third sample`() {
        val input = sine(TONE_HZ, 48_000, samples = 4_800)
        val output = Resampler.resample(input, 48_000)
        assertContentEquals(ShortArray(1_600) { input[3 * it] }, output)
    }

    @Test
    fun `doubling the rate puts the mean between two samples`() {
        val output = Resampler.resample(shortArrayOf(0, 100, 300, -100), 8_000)
        assertContentEquals(shortArrayOf(0, 50, 100, 200, 300, 100, -100, -100), output)
    }

    @Test
    fun `empty input gives empty output`() {
        for (rate in listOf(8_000, 16_000, 22_050, 48_000)) {
            assertEquals(0, Resampler.resample(ShortArray(0), rate).size)
        }
        assertEquals(0, Resampler.toVoiceLink(PcmAudio(ShortArray(0), 24_000, channels = 2)).size)
    }

    @Test
    fun `the same rate returns an equal copy`() {
        val input = sine(TONE_HZ, 16_000, samples = 1_000)
        val output = Resampler.resample(input, 16_000)
        assertContentEquals(input, output)
        assertNotSame(input, output)
        assertContentEquals(input, Resampler.resample(input, 44_100, toRate = 44_100))
    }

    @Test
    fun `very short input does not fail`() {
        assertContentEquals(shortArrayOf(7, 7), Resampler.resample(shortArrayOf(7), 8_000))
        assertContentEquals(shortArrayOf(), Resampler.resample(shortArrayOf(7), 48_000))
        assertContentEquals(shortArrayOf(7), Resampler.resample(shortArrayOf(7, 9), 32_000))
        assertContentEquals(shortArrayOf(7), Resampler.resample(shortArrayOf(7, 9, 11), 48_000))
        // five samples at 48 kHz are 1.67 samples at 16 kHz: rounded to two, not cut to one
        assertContentEquals(shortArrayOf(0, 90), Resampler.resample(shortArrayOf(0, 30, 60, 90, 120), 48_000))
    }

    @Test
    fun `a constant level stays constant and the extremes do not overflow`() {
        for (level in listOf(Short.MAX_VALUE, Short.MIN_VALUE, 0.toShort(), 1.toShort())) {
            for (rate in listOf(8_000, 22_050, 44_100)) {
                val output = Resampler.resample(ShortArray(999) { level }, rate)
                assertTrue(output.isNotEmpty() && output.all { it == level }, "$level at $rate Hz")
            }
        }
        // full-scale square wave: interpolated values stay between the extremes by construction
        val square = ShortArray(1_000) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertEquals(1_600, Resampler.resample(square, 10_000).size)
    }

    @Test
    fun `a ramp stays a ramp`() {
        // 22050 Hz to 16000 Hz: output sample i sits at input position i * 22050 / 16000
        val output = Resampler.resample(ShortArray(2_205) { (it * 10).toShort() }, 22_050)
        assertEquals(1_600, output.size)
        for (i in 0 until 1_599) assertEquals((i * 22_050 / 16_000.0 * 10).roundToInt(), output[i].toInt(), "sample $i")
    }

    @Test
    fun `another target rate can be asked for`() {
        assertEquals(8_000, Resampler.resample(ShortArray(16_000), 16_000, toRate = 8_000).size)
        assertEquals(44_100, Resampler.resample(ShortArray(48_000), 48_000, toRate = 44_100).size)
    }

    @Test
    fun `rates that make no sense are refused`() {
        assertFailsWith<IllegalArgumentException> { Resampler.resample(ShortArray(4), 0) }
        assertFailsWith<IllegalArgumentException> { Resampler.resample(ShortArray(4), -8_000) }
        assertFailsWith<IllegalArgumentException> { Resampler.resample(ShortArray(4), 16_000, toRate = 0) }
        assertFailsWith<IllegalArgumentException> { PcmAudio(ShortArray(4), 0) }
        assertFailsWith<IllegalArgumentException> { PcmAudio(ShortArray(4), 16_000, channels = 3) }
        assertFailsWith<IllegalArgumentException> { PcmAudio(ShortArray(3), 16_000, channels = 2) }
    }

    @Test
    fun `stereo is mixed down to the mean of left and right`() {
        val stereo = PcmAudio(
            shortArrayOf(1_000, 3_000, -1_000, 1_000, Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE, 5, -6),
            sampleRate = 16_000,
            channels = 2,
        )
        assertContentEquals(shortArrayOf(2_000, 0, Short.MAX_VALUE, Short.MIN_VALUE, 0), Resampler.toMono(stereo))
    }

    @Test
    fun `mono is passed through as a copy`() {
        val mono = PcmAudio(shortArrayOf(1, 2, 3), 16_000)
        assertContentEquals(mono.samples, Resampler.toMono(mono))
        assertNotSame(mono.samples, Resampler.toMono(mono))
    }

    @Test
    fun `a stereo file becomes mono at the rate of the voice link`() {
        // left channel a tone, right channel silence: the mix is the tone at half level
        val left = sine(TONE_HZ, 48_000, samples = 48_000)
        val stereo = ShortArray(96_000) { if (it % 2 == 0) left[it / 2] else 0 }
        val file = WavBytes.of(stereo, 48_000, channels = 2)
        val audio = assertIs<WavResult.Audio>(Wav.read(file)).audio
        val output = Resampler.toVoiceLink(audio)
        assertEquals(Resampler.VOICE_LINK_RATE, output.size)
        val ideal = sine(TONE_HZ, 16_000, samples = 16_000, amplitude = AMPLITUDE / 2)
        val worst = output.indices.maxOf { abs(output[it] - ideal[it]) }
        assertTrue(worst <= 2, "off by $worst")
    }

    private companion object {
        const val TONE_HZ = 440.0
        const val AMPLITUDE = 20_000.0
    }
}
