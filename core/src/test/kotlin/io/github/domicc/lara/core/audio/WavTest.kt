package io.github.domicc.lara.core.audio

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WavTest {

    private val someSamples = shortArrayOf(0, 1, -1, 258, -259, Short.MAX_VALUE, Short.MIN_VALUE, 12_345)

    private fun audio(bytes: ByteArray): PcmAudio = assertIs<WavResult.Audio>(Wav.read(bytes)).audio

    private fun reason(bytes: ByteArray): String = assertIs<WavResult.Rejected>(Wav.read(bytes)).reason

    @Test
    fun `a mono file is read sample for sample`() {
        val bytes = WavBytes.of(someSamples, 24_000)
        assertEquals(44 + 16, bytes.size)
        val audio = audio(bytes)
        assertContentEquals(someSamples, audio.samples)
        assertEquals(24_000, audio.sampleRate)
        assertEquals(1, audio.channels)
        assertEquals(8, audio.frames)
    }

    @Test
    fun `the bytes of a known file are read as little endian`() {
        // RIFF size 40, format: PCM, mono, 16000 Hz, 32000 bytes per second, 2 bytes per frame, 16 bit
        val hex = "52494646" + "28000000" + "57415645" +
            "666d7420" + "10000000" + "0100" + "0100" + "803e0000" + "007d0000" + "0200" + "1000" +
            "64617461" + "04000000" + "0102" + "feff"
        val audio = audio(hex.hexToByteArray())
        assertContentEquals(shortArrayOf(0x0201, -2), audio.samples)
        assertEquals(16_000, audio.sampleRate)
    }

    @Test
    fun `a stereo file keeps left and right interleaved`() {
        val audio = audio(WavBytes.of(someSamples, 48_000, channels = 2))
        assertContentEquals(someSamples, audio.samples)
        assertEquals(2, audio.channels)
        assertEquals(4, audio.frames)
        assertEquals(48_000, audio.sampleRate)
    }

    @Test
    fun `any sample rate is taken as it is`() {
        for (rate in listOf(8_000, 11_025, 16_000, 22_050, 24_000, 32_000, 44_100, 48_000, 96_000, 192_000)) {
            assertEquals(rate, audio(WavBytes.of(someSamples, rate)).sampleRate)
        }
    }

    @Test
    fun `the duration follows from frames and rate`() {
        assertEquals(1_500, audio(WavBytes.of(ShortArray(36_000), 24_000)).durationMs)
        assertEquals(1_500, audio(WavBytes.of(ShortArray(144_000), 48_000, channels = 2)).durationMs)
    }

    @Test
    fun `a file without samples is a valid empty sound`() {
        val audio = audio(WavBytes.of(ShortArray(0), 22_050))
        assertEquals(0, audio.frames)
        assertEquals(0, audio.durationMs)
    }

    @Test
    fun `other chunks before and after the data are stepped over`() {
        val bytes = WavBytes.riff()
            .chunk("LIST", "INFOISFT".toByteArray())
            .format(1, 22_050)
            .chunk("fact", ByteArray(4))
            .chunk("odd ", ByteArray(3)) // odd length: a pad byte follows
            .data(someSamples)
            .chunk("LIST", ByteArray(10))
            .toByteArray()
        val audio = audio(bytes)
        assertContentEquals(someSamples, audio.samples)
        assertEquals(22_050, audio.sampleRate)
    }

    @Test
    fun `the extensible header with PCM inside is read`() {
        fun extensible(subFormat: Int): ByteArray = WavBytes()
            .u16(0xFFFE) // extensible
            .u16(2)
            .u32(48_000)
            .u32(48_000 * 4)
            .u16(4)
            .u16(16)
            .u16(22) // size of the extension
            .u16(16) // valid bits
            .u32(3) // channel mask: front left and right
            .u16(subFormat)
            .raw(ByteArray(14) { 0x11 }) // the rest of the sub-format id
            .toByteArray()

        val audio = audio(WavBytes.riff().chunk("fmt ", extensible(subFormat = 1)).data(someSamples).toByteArray())
        assertEquals(2, audio.channels)
        assertEquals(48_000, audio.sampleRate)
        assertContentEquals(someSamples, audio.samples)

        // float inside
        assertEquals("format_3", reason(WavBytes.riff().chunk("fmt ", extensible(subFormat = 3)).data(someSamples).toByteArray()))
        // the extensible tag in a chunk too short to hold the sub-format
        val short = extensible(subFormat = 1).copyOf(16)
        assertEquals("format_65534", reason(WavBytes.riff().chunk("fmt ", short).data(someSamples).toByteArray()))
    }

    @Test
    fun `a partial frame at the end is dropped`() {
        val mono = WavBytes.riff().format(1, 16_000).chunk("data", byteArrayOf(1, 0, 2, 0, 3)).toByteArray()
        assertContentEquals(shortArrayOf(1, 2), audio(mono).samples)
        val stereo = WavBytes.riff().format(2, 16_000).chunk("data", byteArrayOf(1, 0, 2, 0, 3, 0)).toByteArray()
        assertContentEquals(shortArrayOf(1, 2), audio(stereo).samples)
    }

    @Test
    fun `what is not a wave file is rejected`() {
        assertEquals("not_riff_wave", reason(ByteArray(0)))
        assertEquals("not_riff_wave", reason("RIFF".toByteArray()))
        assertEquals("not_riff_wave", reason(ByteArray(44))) // the placeholder a speech engine writes first
        assertEquals("not_riff_wave", reason("RIFF\u0000\u0000\u0000\u0000AVI LIST".toByteArray()))
        assertEquals("not_riff_wave", reason("RIFX\u0000\u0000\u0000\u0000WAVEfmt ".toByteArray()))
        assertEquals("not_riff_wave", reason("ID3\u0004 an mp3 file with some more bytes".toByteArray()))
    }

    @Test
    fun `other sample formats are rejected with the reason`() {
        fun file(build: WavBytes.() -> WavBytes) = WavBytes.riff().build().data(someSamples).toByteArray()
        assertEquals("bits_8", reason(file { format(1, 16_000, bits = 8) }))
        assertEquals("bits_24", reason(file { format(1, 48_000, bits = 24) }))
        assertEquals("bits_32", reason(file { format(2, 48_000, bits = 32) }))
        assertEquals("format_3", reason(file { format(1, 48_000, bits = 32, formatTag = 3) })) // float
        assertEquals("format_7", reason(file { format(1, 8_000, bits = 8, formatTag = 7) })) // mu-law
        assertEquals("format_85", reason(file { format(2, 44_100, formatTag = 85) })) // mp3 in a wave container
        assertEquals("channels_0", reason(file { format(0, 16_000) }))
        assertEquals("channels_3", reason(file { format(3, 16_000) }))
        assertEquals("channels_6", reason(file { format(6, 48_000) }))
        assertEquals("sample_rate_0", reason(file { format(1, 0) }))
        assertEquals("sample_rate_999", reason(file { format(1, 999) }))
        assertEquals("sample_rate_384001", reason(file { format(1, 384_001) }))
        assertEquals("sample_rate_4294967295", reason(file { format(1, 0xFFFFFFFFL) }))
        assertEquals("block_align_4", reason(file { format(1, 16_000, blockAlign = 4) }))
    }

    @Test
    fun `missing or misplaced chunks are rejected with the reason`() {
        assertEquals("no_fmt_chunk", reason(WavBytes.riff().toByteArray()))
        assertEquals("no_fmt_chunk", reason(WavBytes.riff().chunk("LIST", ByteArray(8)).toByteArray()))
        assertEquals("no_data_chunk", reason(WavBytes.riff().format(1, 16_000).toByteArray()))
        assertEquals("data_before_fmt", reason(WavBytes.riff().data(someSamples).format(1, 16_000).toByteArray()))
        assertEquals("fmt_chunk_too_short", reason(WavBytes.riff().chunk("fmt ", ByteArray(14)).data(someSamples).toByteArray()))
        // the format chunk announces 16 bytes but the file ends after 10
        val whole = WavBytes.riff().format(1, 16_000).toByteArray()
        assertEquals("fmt_chunk_too_short", reason(whole.copyOf(whole.size - 6)))
    }

    @Test
    fun `a file cut off inside the data is rejected`() {
        val whole = WavBytes.of(ShortArray(100) { it.toShort() }, 16_000)
        assertEquals("data_truncated", reason(whole.copyOf(whole.size - 1)))
        assertEquals("data_truncated", reason(whole.copyOf(45)))
        // header only: the data chunk is announced but nothing of it is there
        assertEquals("data_truncated", reason(whole.copyOf(44)))
        // a size field of "unknown length" as streaming writers leave it
        val streamed = WavBytes.riff().format(1, 16_000).tag("data").u32(0xFFFFFFFFL).raw(ByteArray(64)).toByteArray()
        assertEquals("data_truncated", reason(streamed))
    }

    @Test
    fun `chunk sizes near the 32-bit limit do not overflow`() {
        for (size in listOf(0x7FFFFFFFL, 0x80000000L, 0xFFFFFFF7L, 0xFFFFFFFEL, 0xFFFFFFFFL)) {
            val bytes = WavBytes.riff().tag("junk").u32(size).format(1, 16_000).data(someSamples).toByteArray()
            assertEquals("no_fmt_chunk", reason(bytes), "junk chunk of $size bytes")
            val format = WavBytes.riff().tag("fmt ").u32(size).raw(ByteArray(16)).data(someSamples).toByteArray()
            assertIs<WavResult.Rejected>(Wav.read(format), "format chunk of $size bytes")
        }
    }

    @Test
    fun `cutting a valid file anywhere never throws and never yields sound`() {
        val whole = WavBytes.riff()
            .chunk("LIST", ByteArray(7))
            .format(2, 44_100)
            .data(ShortArray(40) { (it * 500).toShort() })
            .toByteArray()
        assertIs<WavResult.Audio>(Wav.read(whole))
        for (length in 0 until whole.size) {
            assertIs<WavResult.Rejected>(Wav.read(whole.copyOf(length)), "cut to $length bytes")
        }
    }

    @Test
    fun `damaged files never throw`() {
        val random = Random(8)
        val whole = WavBytes.riff()
            .chunk("LIST", ByteArray(6))
            .format(2, 44_100)
            .data(ShortArray(64) { (it * 300).toShort() })
            .chunk("fact", ByteArray(4))
            .toByteArray()
        repeat(200_000) {
            val damaged = whole.copyOf(random.nextInt(whole.size + 1))
            // damage mostly the headers, where the lengths and formats live
            repeat(random.nextInt(1, 5)) {
                if (damaged.isNotEmpty()) damaged[random.nextInt(minOf(damaged.size, 72))] = random.nextInt().toByte()
            }
            check(Wav.read(damaged))
        }
    }

    @Test
    fun `random bytes never throw`() {
        val random = Random(9)
        repeat(100_000) {
            check(Wav.read(random.nextBytes(random.nextInt(0, 120))))
        }
    }

    @Test
    fun `random chunks with random sizes never throw`() {
        val random = Random(10)
        var sounds = 0
        repeat(200_000) {
            val result = Wav.read(randomChunks(random))
            check(result)
            if (result is WavResult.Audio) sounds++
        }
        // guards the generator: a test that never gets past the format chunk proves little
        assertTrue(sounds > 5_000, "only $sounds of the random files reached the sample reader")
    }

    /** A file start followed by up to four chunks whose names, sizes and contents may or may not fit. */
    private fun randomChunks(random: Random): ByteArray {
        val ids = listOf("fmt ", "fmt ", "data", "data", "LIST", "junk")
        val sizes = longArrayOf(0, 1, 2, 3, 4, 14, 15, 16, 17, 18, 39, 40, 41, 0x7FFFFFFF, 0x80000000, 0xFFFFFFFF)
        val file = WavBytes.riff()
        repeat(random.nextInt(0, 5)) {
            val id = ids.random(random)
            var body = random.nextBytes(random.nextInt(0, 48))
            if (id == "fmt " && random.nextInt(4) != 0) {
                // a format that passes, so that the data chunks behind it are read
                val channels = random.nextInt(1, 3)
                body = WavBytes().u16(1).u16(channels).u32(16_000).u32(0).u16(2 * channels).u16(16).toByteArray() +
                    body.copyOf(body.size % 8)
            }
            when (random.nextInt(6)) {
                0 -> file.tag(id).u32(sizes.random(random)).raw(body)
                1 -> file.tag(id).u32(random.nextLong(0, 0x100000000)).raw(body)
                else -> file.chunk(id, body)
            }
        }
        return file.toByteArray()
    }

    /** What must hold for any result. */
    private fun check(result: WavResult) {
        when (result) {
            is WavResult.Rejected -> assertTrue(result.reason.isNotEmpty())
            is WavResult.Audio -> {
                assertTrue(result.audio.channels in 1..2)
                assertTrue(result.audio.sampleRate in 1_000..384_000)
                assertEquals(0, result.audio.samples.size % result.audio.channels)
            }
        }
    }
}
