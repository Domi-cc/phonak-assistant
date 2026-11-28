package io.github.domicc.lara.core.announce

/**
 * Which of Android's volumes an announcement is played under. Android decides that by the
 * "usage" a sound is played with, and since Android 17 the usage of an assistant has a volume
 * of its own, apart from the media volume.
 *
 * @param word the name used in the preferences, in the journal and in commands
 */
enum class AnnounceChannel(val word: String) {
    /**
     * Played as media. An announcement then follows the phone's media volume, the one the
     * owner sets with the ordinary slider and with the volume keys, and the one the app can
     * set for the length of an announcement. It also follows the phone's choice of a media
     * output. The default.
     */
    MEDIA("media"),

    /**
     * Played as assistant speech, as every build before this setting did. The loudness is
     * then Android's assistant volume, which an ordinary app cannot set. Kept as a way
     * back, and to compare the two on the phone.
     */
    ASSISTANT("assistant"),
    ;

    companion object {
        val DEFAULT = MEDIA

        fun fromWord(word: String?): AnnounceChannel? = entries.firstOrNull { it.word == word }
    }
}

/**
 * The silence in front of the chime of an announcement. It opens the stream to the hearing
 * aid before the first sound. Whether the aid fades a stream in while it changes into its
 * streaming program is not known; the longer lead-ins are there to find out by ear.
 *
 * @param ms the length of the silence
 */
enum class LeadIn(val ms: Int) {
    /** What every build so far used. */
    SHORT(450),
    MEDIUM(1_500),
    LONG(3_000),
    ;

    companion object {
        val DEFAULT = SHORT

        fun fromMs(ms: Int?): LeadIn? = entries.firstOrNull { it.ms == ms }
    }
}

/**
 * A step of the media volume the owner chose for announcements, kept with the scale it was
 * chosen on. Android's scale is not a constant: it differs between phones and can change
 * with an update, and "18" means something only next to "of 25".
 *
 * @param step from 1 to [max]; never 0, which mutes
 * @param max the highest step of the scale the step was chosen on
 */
data class StoredStep(val step: Int, val max: Int) {
    /** This step on a scale that ends at [max]: the same share of it, and always a step that can be sent. */
    fun on(max: Int): StoredStep =
        if (max == this.max) copy(step = VolumeSteps.clamp(step, max)) else StoredStep(VolumeSteps.rescale(step, this.max, max), max)
}

/** The arithmetic of volume steps. A step is an index of Android's media volume from 1 to its maximum. */
object VolumeSteps {

    /** [step] brought into 1..[max]. Never 0: index 0 mutes the stream. */
    fun clamp(step: Int, max: Int): Int = step.coerceIn(1, maxOf(1, max))

    /**
     * A step chosen on a scale that ended at [fromMax], on one that ends at [toMax]: the same
     * share of the scale, rounded, and inside 1..[toMax] whatever was stored.
     */
    fun rescale(step: Int, fromMax: Int, toMax: Int): Int {
        if (fromMax <= 0 || fromMax == toMax) return clamp(step, toMax)
        val scaled = (2L * step * toMax + fromMax) / (2L * fromMax)
        return clamp(scaled.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(), toMax)
    }

    /**
     * The step the switch "Eigene Lautstärke nur für Ansagen" starts with: the phone's step
     * for the hearing aid if that can be read, so that switching on changes nothing by
     * itself; otherwise three fifths of the scale.
     *
     * @param phoneStep the media step for the hearing aid right now; null if the aid is not
     * the output or the stream is muted
     */
    fun initial(phoneStep: Int?, max: Int): Int =
        if (phoneStep != null && phoneStep in 1..max) phoneStep else clamp((3 * max + 2) / 5, max)
}

/**
 * How announcements are played, as the owner set it.
 *
 * @param ownLevel "Eigene Lautstärke nur für Ansagen": the app sets [step] for the length of
 * an announcement and puts the phone's value back afterwards. Off: announcements play at
 * the phone's media volume as it is, and the app writes nothing around them.
 * @param step the step for announcements; kept while [ownLevel] is off, null until one was chosen
 */
data class AnnounceSettings(
    val channel: AnnounceChannel = AnnounceChannel.DEFAULT,
    val ownLevel: Boolean = false,
    val step: StoredStep? = null,
    val leadIn: LeadIn = LeadIn.DEFAULT,
) {
    /** The step the app is to set for an announcement; null if it is to leave the phone's value alone. */
    val own: StoredStep? get() = step.takeIf { ownLevel }
}
