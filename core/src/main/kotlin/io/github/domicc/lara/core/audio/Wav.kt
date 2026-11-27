package io.github.domicc.lara.core.audio

/** What [Wav.read] made of a file. */
sealed interface WavResult {
    class Audio(val audio: PcmAudio) : WavResult

    /** @param reason a short word for the journal, such as `bits_24` or `data_truncated` */
    class Rejected(val reason: String) : WavResult
}

/**
 * Reads RIFF/WAVE files with 16-bit PCM, mono or stereo. That is what Android's text-to-speech
 * engine writes when it synthesises into a file, which is how spoken prompts are prepared.
 * Everything else (8-bit, 24-bit, float, compressed, more channels) is rejected with a reason
 * instead of being played as noise.
 */
object Wav {

    /**
     * Sample rates outside this range are taken for a damaged header. The lower bound also
     * limits how much memory the resampler can be made to allocate for a given file.
     */
    private val SAMPLE_RATES = 1_000L..384_000L

    private const val FORMAT_PCM = 1
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    /** The fields of the format chunk that decide whether and how the data can be read. */
    private class Format(val tag: Int, val channels: Int, val sampleRate: Long, val blockAlign: Int, val bits: Int) {
        val problem: String?
            get() = when {
                tag != FORMAT_PCM -> "format_$tag"
                bits != 16 -> "bits_$bits"
                channels != 1 && channels != 2 -> "channels_$channels"
                sampleRate !in SAMPLE_RATES -> "sample_rate_$sampleRate"
                blockAlign != 2 * channels -> "block_align_$blockAlign"
                else -> null
            }
    }

    /**
     * Never throws. A file whose data chunk is shorter than its header says is rejected: for a
     * prompt that means the synthesis was cut off, and half an announcement is worse than none.
     */
    fun read(bytes: ByteArray): WavResult {
        if (bytes.size < 12 || !hasTag(bytes, 0, "RIFF") || !hasTag(bytes, 8, "WAVE")) {
            return WavResult.Rejected("not_riff_wave")
        }
        var format: Format? = null
        // Chunk sizes are unsigned 32-bit values taken from the file, hence Long arithmetic.
        var pos = 12L
        while (pos + 8 <= bytes.size) {
            val start = pos.toInt()
            val size = u32(bytes, start + 4)
            val body = pos + 8
            if (hasTag(bytes, start, "fmt ")) {
                if (size < 16 || body + 16 > bytes.size) return WavResult.Rejected("fmt_chunk_too_short")
                val found = readFormat(bytes, body.toInt(), extended = size >= 40 && body + 40 <= bytes.size)
                found.problem?.let { return WavResult.Rejected(it) }
                format = found
            } else if (hasTag(bytes, start, "data")) {
                val known = format ?: return WavResult.Rejected("data_before_fmt")
                if (body + size > bytes.size) return WavResult.Rejected("data_truncated")
                // a trailing partial frame is dropped
                val count = (size / known.blockAlign).toInt() * known.channels
                val offset = body.toInt()
                val samples = ShortArray(count) { i -> u16(bytes, offset + 2 * i).toShort() }
                return WavResult.Audio(PcmAudio(samples, known.sampleRate.toInt(), known.channels))
            }
            pos = body + size + (size and 1) // chunks are padded to an even length
        }
        return WavResult.Rejected(if (format == null) "no_fmt_chunk" else "no_data_chunk")
    }

    /** @param extended the chunk has the 40 bytes of the extensible layout */
    private fun readFormat(bytes: ByteArray, body: Int, extended: Boolean): Format {
        var tag = u16(bytes, body)
        // The extensible layout keeps the real format in the first two bytes of a sub-format id.
        if (tag == FORMAT_EXTENSIBLE && extended) tag = u16(bytes, body + 24)
        return Format(
            tag = tag,
            channels = u16(bytes, body + 2),
            sampleRate = u32(bytes, body + 4),
            blockAlign = u16(bytes, body + 12),
            bits = u16(bytes, body + 14),
        )
    }

    private fun hasTag(bytes: ByteArray, offset: Int, tag: String): Boolean =
        tag.indices.all { bytes[offset + it] == tag[it].code.toByte() }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)
}
