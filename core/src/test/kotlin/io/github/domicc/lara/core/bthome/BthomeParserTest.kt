package io.github.domicc.lara.core.bthome

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BthomeParserTest {

    private fun parse(hex: String): BthomePacket? = BthomeParser.parse(hex.hexToByteArray())

    @Test
    fun `every vector parses as expected`() {
        // R1 to R10, C1 to C5, S1 to S3, Y1 to Y32 without Y30 and Y31
        assertEquals(10 + 5 + 3 + 30, BthomeVectors.all.size)
        assertEquals(BthomeVectors.all.size, BthomeVectors.all.map { it.id }.toSet().size)
        // no vector stands in for another: each has bytes of its own
        assertEquals(BthomeVectors.all.size, BthomeVectors.all.map { it.hex }.toSet().size)
        val failures = BthomeVectors.all.mapNotNull { vector ->
            val actual = BthomeParser.parse(vector.bytes)
            if (actual == vector.expected) null else "${vector.id} ${vector.hex}: expected ${vector.expected} but got $actual"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `a packet received from the remote without Zigbee is a press of button 1`() {
        val packet = assertNotNull(parse(BthomeVectors.byId("C1").hex))
        assertEquals(0x44, packet.deviceInfo)
        assertFalse(packet.encrypted)
        assertTrue(packet.triggerBased)
        assertEquals(93, packet.packetId)
        assertEquals(100, packet.batteryPercent)
        assertEquals(listOf(ButtonEvent.PRESS, ButtonEvent.NONE, ButtonEvent.NONE, ButtonEvent.NONE), packet.buttons)
        assertFalse(packet.stoppedEarly)
    }

    @Test
    fun `two buttons pressed together arrive in one packet`() {
        val packet = assertNotNull(parse("44001101643a013a003a013a00"))
        assertEquals(17, packet.packetId)
        assertEquals(listOf(ButtonEvent.PRESS, ButtonEvent.NONE, ButtonEvent.PRESS, ButtonEvent.NONE), packet.buttons)
    }

    @Test
    fun `only version 2 without reserved bits is accepted`() {
        val accepted = (0..255).filter { BthomeParser.parse(byteArrayOf(it.toByte())) != null }
        assertEquals(listOf(0x40, 0x41, 0x44, 0x45), accepted)
        assertNull(BthomeParser.parse(ByteArray(0)))
    }

    @Test
    fun `an encrypted packet carries no objects even if its bytes look like some`() {
        val packet = assertNotNull(parse("4500073a01"))
        assertTrue(packet.encrypted)
        assertNull(packet.packetId)
        assertNull(packet.batteryPercent)
        assertTrue(packet.buttons.isEmpty())
        assertFalse(packet.stoppedEarly)
    }

    @Test
    fun `every button code maps to its event and anything else to unknown`() {
        val known = mapOf(
            0x00 to ButtonEvent.NONE,
            0x01 to ButtonEvent.PRESS,
            0x02 to ButtonEvent.DOUBLE,
            0x03 to ButtonEvent.TRIPLE,
            0x04 to ButtonEvent.LONG,
            0x05 to ButtonEvent.LONG_DOUBLE,
            0x06 to ButtonEvent.LONG_TRIPLE,
            0x80 to ButtonEvent.HOLD,
            0x81 to ButtonEvent.HOLD_RELEASE,
            0xFE to ButtonEvent.HOLD,
        )
        for (code in 0..255) {
            val packet = assertNotNull(BthomeParser.parse(byteArrayOf(0x44, 0x3A, code.toByte())))
            assertEquals(listOf(known[code] ?: ButtonEvent.UNKNOWN), packet.buttons, "code $code")
        }
    }

    @Test
    fun `objects in the wrong order are still read`() {
        val packet = assertNotNull(parse("403a010007015a"))
        assertEquals(listOf(ButtonEvent.PRESS), packet.buttons)
        assertEquals(7, packet.packetId)
        assertEquals(0x5A, packet.batteryPercent)
        assertFalse(packet.stoppedEarly)
    }

    @Test
    fun `of a repeated packet id or battery object the first one counts`() {
        val packet = assertNotNull(parse("40000100020163010a"))
        assertEquals(1, packet.packetId)
        assertEquals(0x63, packet.batteryPercent)
    }

    @Test
    fun `every object of the table is stepped over by its length`() {
        for (id in 0..255) {
            val length = BthomeObjects.lengthOf(id)
            if (length < 0) continue
            val data = byteArrayOf(0x40, id.toByte()) + ByteArray(length) + byteArrayOf(0x3A, 0x01)
            val packet = assertNotNull(BthomeParser.parse(data))
            val expected = if (id == BthomeObjects.BUTTON) listOf(ButtonEvent.NONE, ButtonEvent.PRESS) else listOf(ButtonEvent.PRESS)
            assertEquals(expected, packet.buttons, "object id $id")
            assertFalse(packet.stoppedEarly, "object id $id")
            // one byte short: the object is cut off and the button behind it is gone
            val cut = BthomeParser.parse(byteArrayOf(0x40, id.toByte()) + ByteArray(length - 1))
            assertEquals(BthomeStop.TRUNCATED, cut?.stop, "object id $id cut")
        }
    }

    @Test
    fun `an unknown object id stops parsing and hides what follows`() {
        for (id in 0..255) {
            if (BthomeObjects.lengthOf(id) != BthomeObjects.UNKNOWN) continue
            val packet = assertNotNull(BthomeParser.parse(byteArrayOf(0x40, 0x00, 0x09, id.toByte(), 0x3A, 0x01)))
            assertEquals(BthomeStop.UNKNOWN_OBJECT, packet.stop, "object id $id")
            assertEquals(9, packet.packetId)
            assertTrue(packet.buttons.isEmpty(), "object id $id")
        }
    }

    @Test
    fun `objects with a length byte are stepped over whatever their length`() {
        // empty text, then a button
        assertEquals(listOf(ButtonEvent.PRESS), parse("4053003a01")?.buttons)
        // raw object with three bytes that look like a button object
        assertEquals(listOf(ButtonEvent.DOUBLE), parse("4054033a013a3a02")?.buttons)
        // command: only the low five bits of the length byte count arguments
        assertEquals(listOf(ButtonEvent.PRESS), parse("403be103053a01")?.buttons)
        // command without arguments still has its opcode byte
        assertEquals(listOf(ButtonEvent.PRESS), parse("403b00073a01")?.buttons)
        // an empty text at the very end is complete
        assertFalse(assertNotNull(parse("405300")).stoppedEarly)
    }

    @Test
    fun `objects with a length byte that run past the end are reported as truncated`() {
        for (hex in listOf("4053", "40530561", "4054ff", "403b", "403b00", "403b1f00", "403a015302")) {
            assertEquals(BthomeStop.TRUNCATED, parse(hex)?.stop, hex)
        }
        assertEquals(listOf(ButtonEvent.PRESS), parse("403a015302")?.buttons)
    }

    @Test
    fun `cutting a vector short never throws and never invents data`() {
        for (vector in BthomeVectors.all) {
            val full = vector.expected
            val bytes = vector.bytes
            for (length in 0 until bytes.size) {
                val cut = BthomeParser.parse(bytes.copyOf(length))
                val where = "${vector.id} cut to $length"
                if (length == 0 || full == null) {
                    assertNull(cut, where)
                    continue
                }
                assertNotNull(cut, where)
                assertEquals(full.deviceInfo, cut.deviceInfo, where)
                assertEquals(full.buttons.take(cut.buttons.size), cut.buttons, where)
                assertTrue(cut.packetId == null || cut.packetId == full.packetId, where)
                assertTrue(cut.batteryPercent == null || cut.batteryPercent == full.batteryPercent, where)
            }
        }
    }

    @Test
    fun `every sequence of up to three bytes parses without throwing`() {
        for (deviceInfo in listOf(0x40, 0x44)) {
            for (first in 0..255) {
                for (second in 0..255) {
                    val data = byteArrayOf(deviceInfo.toByte(), first.toByte(), second.toByte())
                    checkInvariants(data, BthomeParser.parse(data))
                    checkInvariants(data.copyOf(2), BthomeParser.parse(data.copyOf(2)))
                }
            }
        }
    }

    @Test
    fun `random bytes never throw`() {
        val random = Random(20261127)
        repeat(300_000) { round ->
            val data = random.nextBytes(random.nextInt(0, 48))
            // most random first bytes are rejected at once; make three out of four packets get further
            if (data.isNotEmpty() && round % 4 != 0) data[0] = PLAUSIBLE_FIRST_BYTES.random(random)
            checkInvariants(data, BthomeParser.parse(data))
        }
    }

    @Test
    fun `random sequences of known objects never throw`() {
        val random = Random(4)
        val interesting = intArrayOf(0x00, 0x01, 0x3A, 0x3B, 0x53, 0x54, 0x3C, 0xF0, 0xF1, 0xF2, 0xA9)
        repeat(100_000) {
            val data = ArrayList<Byte>()
            data += 0x44
            repeat(random.nextInt(0, 9)) {
                data += interesting.random(random).toByte()
                repeat(random.nextInt(0, 4)) { data += LENGTH_LIKE_BYTES.random(random) }
            }
            val bytes = data.toByteArray()
            checkInvariants(bytes, BthomeParser.parse(bytes))
        }
    }

    /** What must hold for the result of any input. */
    private fun checkInvariants(data: ByteArray, packet: BthomePacket?) {
        if (packet == null) return
        assertEquals(data[0].toInt() and 0xFF, packet.deviceInfo)
        assertEquals(2, packet.deviceInfo shr 5)
        assertEquals(packet.deviceInfo and 1 == 1, packet.encrypted)
        assertEquals(packet.deviceInfo and 4 == 4, packet.triggerBased)
        assertTrue(packet.packetId == null || packet.packetId in 0..255)
        assertTrue(packet.batteryPercent == null || packet.batteryPercent in 0..255)
        // a button object is two bytes long
        assertTrue(packet.buttons.size <= (data.size - 1) / 2)
        if (packet.encrypted) {
            assertNull(packet.packetId)
            assertNull(packet.batteryPercent)
            assertTrue(packet.buttons.isEmpty())
            assertNull(packet.stop)
        }
    }

    private companion object {
        val PLAUSIBLE_FIRST_BYTES = byteArrayOf(0x40, 0x44, 0x44, 0x44, 0x41, 0x45)
        val LENGTH_LIKE_BYTES = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x1F, 0x3A, 0x7F, 0xE1.toByte(), 0xFF.toByte())
    }
}
