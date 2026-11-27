package io.github.domicc.lara.core.bthome

/**
 * The BTHome v2 object table as data: how many value bytes follow each object id.
 *
 * Objects carry no length of their own, so a parser can only step over an object it knows.
 * The table is copied from https://bthome.io/format/ as of 25 November 2026 (97 ids, identical
 * to the reference parser bthome-ble 3.27.0). It grows by a few ids every year; an id missing
 * here makes the parser stop at that object, it never makes it misread.
 */
internal object BthomeObjects {
    const val PACKET_ID = 0x00
    const val BATTERY = 0x01
    const val BUTTON = 0x3A

    /** Not in the table. */
    const val UNKNOWN = -1

    /** Text (0x53) and raw (0x54): one length byte follows the id, then that many bytes. */
    const val LENGTH_PREFIXED = -2

    /**
     * Command (0x3B): one length byte follows the id; its low five bits count the argument
     * bytes, which come after one opcode byte.
     */
    const val COMMAND = -3

    /** Value length in bytes to the ids that have it; `15-2D` is a range including both ends. */
    private val fixedLengths = mapOf(
        1 to "00 01 09 0F-11 15-2D 2E 2F 3A 46 57 58 59 60 64 65 67",
        2 to "02 03 06 07 08 0C 0D 0E 12 13 14 3C 3D 3F 40 41 43 44 45 47 48 49 4A 51 52 56 5A 5D 5E 5F 61 66 F0",
        3 to "04 05 0A 0B 42 4B F2",
        4 to "3E 4C 4D 4E 4F 50 55 5B 5C 62 63 F1",
    )

    private val table = IntArray(256) { UNKNOWN }.also { table ->
        for ((length, ids) in fixedLengths) {
            for (entry in ids.split(' ')) {
                val first = entry.substringBefore('-').toInt(16)
                val last = entry.substringAfter('-').toInt(16)
                for (id in first..last) table[id] = length
            }
        }
        table[0x53] = LENGTH_PREFIXED
        table[0x54] = LENGTH_PREFIXED
        table[0x3B] = COMMAND
    }

    /** The value length of [id] in bytes, or [UNKNOWN], [LENGTH_PREFIXED] or [COMMAND]. */
    fun lengthOf(id: Int): Int = table[id and 0xFF]
}
