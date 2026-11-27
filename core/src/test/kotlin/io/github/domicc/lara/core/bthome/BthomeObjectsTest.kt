package io.github.domicc.lara.core.bthome

import kotlin.test.Test
import kotlin.test.assertEquals

class BthomeObjectsTest {

    @Test
    fun `the table has the 97 object ids of the format page`() {
        val byLength = (0..255).groupingBy { BthomeObjects.lengthOf(it) }.eachCount()
        assertEquals(
            mapOf(
                1 to 42,
                2 to 33,
                3 to 7,
                4 to 12,
                BthomeObjects.LENGTH_PREFIXED to 2,
                BthomeObjects.COMMAND to 1,
                BthomeObjects.UNKNOWN to 159,
            ),
            byLength,
        )
    }

    @Test
    fun `the ids are in three blocks and everything else is unknown`() {
        val known = (0..255).filter { BthomeObjects.lengthOf(it) != BthomeObjects.UNKNOWN }
        assertEquals((0x00..0x2F) + (0x3A..0x67) + (0xF0..0xF2), known)
    }

    @Test
    fun `the objects this app reads and the ones around them have the documented lengths`() {
        val expected = mapOf(
            0x00 to 1, // packet id
            0x01 to 1, // battery
            0x02 to 2, // temperature
            0x04 to 3, // pressure
            0x05 to 3, // illuminance
            0x15 to 1, // first binary sensor
            0x2D to 1, // last binary sensor
            0x3A to 1, // button
            0x3C to 2, // dimmer
            0x3E to 4, // count, 32 bit
            0x3F to 2, // rotation
            0x45 to 2, // temperature
            0x50 to 4, // timestamp
            0x5A to 2, // count, signed 16 bit
            0x64 to 1,
            0x66 to 2, // frequency, added 24 November 2026
            0x67 to 1, // progress, added 25 November 2026
            0xF0 to 2, // device type id
            0xF1 to 4, // firmware version, four parts
            0xF2 to 3, // firmware version, three parts
            0x3B to BthomeObjects.COMMAND,
            0x53 to BthomeObjects.LENGTH_PREFIXED,
            0x54 to BthomeObjects.LENGTH_PREFIXED,
            0x30 to BthomeObjects.UNKNOWN,
            0x39 to BthomeObjects.UNKNOWN,
            0x68 to BthomeObjects.UNKNOWN,
            0xA9 to BthomeObjects.UNKNOWN,
            0xFF to BthomeObjects.UNKNOWN,
        )
        for ((id, length) in expected) assertEquals(length, BthomeObjects.lengthOf(id), "object id $id")
    }
}
