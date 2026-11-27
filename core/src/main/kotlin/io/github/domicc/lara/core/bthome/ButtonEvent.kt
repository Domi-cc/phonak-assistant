package io.github.domicc.lara.core.bthome

/**
 * What one button object (id 0x3A) of a BTHome packet says. A remote with several buttons sends
 * one object per button in every packet, so most of them are [NONE].
 *
 * The codes are data on purpose: the BTHome table still grows (hold release was added in
 * October 2026), and Shelly firmware before 1.0.22 sent 0xFE for a held button.
 */
enum class ButtonEvent(private vararg val codes: Int) {
    NONE(0x00),
    PRESS(0x01),
    DOUBLE(0x02),
    TRIPLE(0x03),
    LONG(0x04),
    LONG_DOUBLE(0x05),
    LONG_TRIPLE(0x06),
    HOLD(0x80, 0xFE),
    HOLD_RELEASE(0x81),

    /** A code this table does not know. Kept instead of dropped, so the journal shows it. */
    UNKNOWN;

    companion object {
        private val byCode: Map<Int, ButtonEvent> =
            entries.flatMap { event -> event.codes.map { it to event } }.toMap()

        fun fromCode(code: Int): ButtonEvent = byCode[code and 0xFF] ?: UNKNOWN
    }
}
