package io.github.domicc.lara.core.audio

/**
 * 16-bit PCM sound as it comes out of a file.
 *
 * @param samples one value per channel and frame; for stereo: left, right, left, right ...
 * @param channels 1 or 2
 */
class PcmAudio(val samples: ShortArray, val sampleRate: Int, val channels: Int = 1) {

    init {
        require(sampleRate > 0) { "sampleRate $sampleRate" }
        require(channels == 1 || channels == 2) { "channels $channels" }
        require(samples.size % channels == 0) { "half a frame" }
    }

    /** Number of points in time; for stereo half the number of samples. */
    val frames: Int get() = samples.size / channels

    val durationMs: Long get() = frames * 1000L / sampleRate
}
