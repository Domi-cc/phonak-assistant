package io.github.domicc.lara.core.bthome

/**
 * Reads the service data of a BTHome v2 advertisement (service UUID 0xFCD2) the way Android
 * hands it over: without the two UUID bytes, starting with the device-information byte.
 *
 * The rules follow the BTHome format and its reference parser, with two deliberate choices:
 * - Parsing stops at the first unknown object id and keeps what came before. A new object type
 *   behind the buttons therefore cannot hide a press; one in front of them would, which is why
 *   [BthomePacket.stop] exists.
 * - A device-information byte with a reserved bit set is rejected. The reference parser reads
 *   bit 1 as "six address bytes follow", which the format does not define; reading such a
 *   packet as plain objects could turn address bytes into a button press.
 *
 * Object ids are not required to be in ascending order (the reference parser only warns).
 * The parser never throws, whatever the bytes are.
 */
object BthomeParser {

    /** For the scan filter. */
    const val SERVICE_UUID = "0000fcd2-0000-1000-8000-00805f9b34fb"

    private const val FLAG_ENCRYPTED = 0x01
    private const val FLAG_TRIGGER_BASED = 0x04
    private const val RESERVED_BITS = 0x1A
    private const val VERSION = 2

    /**
     * @return null if [serviceData] is not BTHome version 2: empty, another version, or a
     * reserved bit set. Callers journal the raw bytes then; the first byte tells which it was.
     */
    fun parse(serviceData: ByteArray): BthomePacket? {
        if (serviceData.isEmpty()) return null
        val deviceInfo = serviceData[0].toInt() and 0xFF
        if (deviceInfo shr 5 != VERSION || deviceInfo and RESERVED_BITS != 0) return null
        val triggerBased = deviceInfo and FLAG_TRIGGER_BASED != 0
        if (deviceInfo and FLAG_ENCRYPTED != 0) {
            return BthomePacket(deviceInfo, encrypted = true, triggerBased = triggerBased)
        }

        var packetId: Int? = null
        var battery: Int? = null
        val buttons = ArrayList<ButtonEvent>(4)
        var stop: BthomeStop? = null
        var pos = 1
        while (pos < serviceData.size) {
            val id = serviceData[pos].toInt() and 0xFF
            var valueStart = pos + 1
            var length = BthomeObjects.lengthOf(id)
            if (length == BthomeObjects.UNKNOWN) {
                stop = BthomeStop.UNKNOWN_OBJECT
                break
            }
            if (length == BthomeObjects.LENGTH_PREFIXED || length == BthomeObjects.COMMAND) {
                if (valueStart >= serviceData.size) {
                    stop = BthomeStop.TRUNCATED
                    break
                }
                val lengthByte = serviceData[valueStart].toInt() and 0xFF
                length = if (length == BthomeObjects.COMMAND) 1 + (lengthByte and 0x1F) else lengthByte
                valueStart++
            }
            if (valueStart + length > serviceData.size) {
                stop = BthomeStop.TRUNCATED
                break
            }
            // The three objects kept are one byte long; a variable-length one may be empty.
            when (id) {
                // a repeated object would be a sender bug; the first one counts
                BthomeObjects.PACKET_ID -> if (packetId == null) packetId = serviceData[valueStart].toInt() and 0xFF
                BthomeObjects.BATTERY -> if (battery == null) battery = serviceData[valueStart].toInt() and 0xFF
                BthomeObjects.BUTTON -> buttons += ButtonEvent.fromCode(serviceData[valueStart].toInt())
            }
            pos = valueStart + length
        }
        return BthomePacket(deviceInfo, false, triggerBased, packetId, battery, buttons.toList(), stop)
    }
}
