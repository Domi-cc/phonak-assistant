package io.github.domicc.lara.core.bthome

/**
 * What the app needs from one BTHome v2 advertisement. All other objects (temperature, firmware
 * version ...) are skipped by their length and not kept.
 *
 * @param deviceInfo the first byte as sent, 0 to 255
 * @param encrypted the payload is AES-CCM encrypted. This app does not decrypt (version 0.1
 * measures plain packets only), so such a packet carries no objects at all.
 * @param triggerBased the device sends at irregular times, for example on a button press
 * @param packetId object 0x00. The sender raises it by one for every new event and repeats it
 * unchanged within one burst, which makes it the key for de-duplication and loss counting.
 * @param batteryPercent object 0x01
 * @param buttons one entry per button object (0x3A) in the order sent, including [ButtonEvent.NONE]:
 * the n-th entry is button n
 * @param stop why the parser gave up before the end of the data, null if it read everything.
 * Whatever was parsed up to that point is still in the packet.
 */
data class BthomePacket(
    val deviceInfo: Int,
    val encrypted: Boolean,
    val triggerBased: Boolean,
    val packetId: Int? = null,
    val batteryPercent: Int? = null,
    val buttons: List<ButtonEvent> = emptyList(),
    val stop: BthomeStop? = null,
) {
    /**
     * True if objects at the end were not read. A press behind that point is lost, so a caller
     * should journal the raw bytes of such a packet.
     */
    val stoppedEarly: Boolean get() = stop != null
}

/** Why parsing ended before the end of the data. */
enum class BthomeStop {
    /** An object id that is not in the table: its length is unknown, so nothing after it can be read. */
    UNKNOWN_OBJECT,

    /** The data ends in the middle of an object. */
    TRUNCATED,
}
