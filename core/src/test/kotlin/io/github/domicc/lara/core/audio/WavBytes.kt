package io.github.domicc.lara.core.audio

/**
 * Builds RIFF/WAVE files for the tests byte by byte, so that broken ones can be built as well.
 * Numbers are written little endian, as the format wants them.
 */
class WavBytes {
    private val bytes = ArrayList<Byte>()

    fun tag(text: String) = apply { text.forEach { bytes += it.code.toByte() } }

    fun u16(value: Int) = apply {
        bytes += value.toByte()
        bytes += (value shr 8).toByte()
    }

    fun u32(value: Long) = apply {
        u16((value and 0xFFFF).toInt())
        u16((value shr 16).toInt())
    }

    fun raw(data: ByteArray) = apply { data.forEach { bytes += it } }

    /** A chunk with its size and, for an odd size, the pad byte. */
    fun chunk(id: String, body: ByteArray) = apply {
        tag(id)
        u32(body.size.toLong())
        raw(body)
        if (body.size % 2 == 1) bytes += 0
    }

    /** The 16-byte format chunk. The block size is derived unless given. */
    fun format(
        channels: Int,
        sampleRate: Long,
        bits: Int = 16,
        formatTag: Int = 1,
        blockAlign: Int = channels * bits / 8,
    ) = apply {
        tag("fmt ")
        u32(16)
        u16(formatTag)
        u16(channels)
        u32(sampleRate)
        u32(sampleRate * blockAlign)
        u16(blockAlign)
        u16(bits)
    }

    fun data(samples: ShortArray) = apply {
        tag("data")
        u32(samples.size * 2L)
        samples.forEach { u16(it.toInt()) }
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    companion object {
        /** The start of a file. The total size is left at 0: readers must not depend on it. */
        fun riff(): WavBytes = WavBytes().tag("RIFF").u32(0).tag("WAVE")

        /** A plain file as a speech engine writes it: 44 bytes of header, then the samples. */
        fun of(samples: ShortArray, sampleRate: Int, channels: Int = 1): ByteArray =
            riff().format(channels, sampleRate.toLong()).data(samples).toByteArray()
    }
}
