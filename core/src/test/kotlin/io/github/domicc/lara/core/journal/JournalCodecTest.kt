package io.github.domicc.lara.core.journal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalCodecTest {

    @Test
    fun `a record survives encode and decode`() {
        val record = JournalRecord(
            wallMs = 1_795_000_000_000,
            elapsedMs = 123_456,
            uptimeMs = 100_000,
            type = "tap_intent",
            fields = mapOf("hasDevice" to true, "profile" to 1L, "note" to "a \"quoted\" text", "nothing" to null),
            ctx = mapOf("screenOn" to false, "battery" to 87L),
        )
        assertEquals(record, JournalCodec.decode(JournalCodec.encode(record)))
    }

    @Test
    fun `one record is one line`() {
        val line = JournalCodec.encode(
            JournalRecord(1, 2, 3, "x", fields = mapOf("text" to "line one\nline two")),
        )
        assertTrue('\n' !in line)
    }

    @Test
    fun `nested values are kept`() {
        val record = JournalRecord(1, 2, 3, "x", fields = mapOf("list" to listOf(1L, "a"), "map" to mapOf("k" to false)))
        assertEquals(record, JournalCodec.decode(JournalCodec.encode(record)))
    }

    @Test
    fun `broken lines decode to null instead of throwing`() {
        assertNull(JournalCodec.decode(""))
        assertNull(JournalCodec.decode("{\"t\":1,\"el\":2"))
        assertNull(JournalCodec.decode("not json at all"))
        assertNull(JournalCodec.decode("{\"type\":\"x\"}"))
    }
}
