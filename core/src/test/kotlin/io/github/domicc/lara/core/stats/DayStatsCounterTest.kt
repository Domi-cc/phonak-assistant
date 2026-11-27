package io.github.domicc.lara.core.stats

import io.github.domicc.lara.core.journal.JournalCodec
import io.github.domicc.lara.core.journal.JournalNames
import io.github.domicc.lara.core.journal.JournalRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DayStatsCounterTest {

    private val counter = DayStatsCounter()

    /** Through the codec, because that is where the counter's records come from: numbers arrive as Long. */
    private fun add(type: String, vararg fields: Pair<String, Any?>) {
        val line = JournalCodec.encode(JournalRecord(1, 2, 3, type, mapOf(*fields)))
        counter.add(assertNotNull(JournalCodec.decode(line)))
    }

    private fun press(id: Int, tsMs: Long, button: Int = 1, missed: Int = 0) =
        add("shelly_press", "button" to button, "gesture" to "press", "id" to id, "missedBefore" to missed, "tsMs" to tsMs)

    @Test
    fun `nothing counted is all zeros`() {
        assertEquals(DayStats(), counter.stats())
    }

    @Test
    fun `presses and their losses are counted`() {
        press(id = 10, tsMs = 1_000)
        press(id = 11, tsMs = 5_000)
        press(id = 14, tsMs = 9_000, missed = 2)
        assertEquals(DayStats(shellyEvents = 3, shellyMissed = 2), counter.stats())
    }

    @Test
    fun `two buttons pressed together are one event and one loss`() {
        press(id = 10, tsMs = 1_000)
        press(id = 13, tsMs = 5_000, button = 1, missed = 2)
        press(id = 13, tsMs = 5_000, button = 3, missed = 2)
        assertEquals(DayStats(shellyEvents = 2, shellyMissed = 2), counter.stats())
    }

    @Test
    fun `the same packet id at another time is another event`() {
        // after 256 events the remote's counter is back where it was
        press(id = 10, tsMs = 1_000)
        press(id = 10, tsMs = 900_000)
        assertEquals(2, counter.stats().shellyEvents)
    }

    @Test
    fun `a beacon is no press, but a loss seen at a beacon is a loss`() {
        press(id = 10, tsMs = 1_000)
        add("shelly_beacon", "id" to 12, "missedBefore" to 1, "tsMs" to 60_000L)
        assertEquals(DayStats(shellyEvents = 1, shellyMissed = 1), counter.stats())
    }

    @Test
    fun `lines without packet id and time are each an event of their own`() {
        // written by a build that named these fields differently; they must not swallow each other
        add("shelly_press", "packetId" to 93, "missedBefore" to 0)
        add("shelly_press", "packetId" to 94, "missedBefore" to 1)
        add("shelly_press", "packetId" to 95, "missedBefore" to 0)
        add("shelly_press", "id" to 96, "missedBefore" to 0)
        add("shelly_press", "id" to 96, "missedBefore" to 0)
        assertEquals(DayStats(shellyEvents = 5, shellyMissed = 1), counter.stats())
    }

    @Test
    fun `presses received while the scan of the screen ran are counted apart as well`() {
        press(id = 10, tsMs = 1_000)
        add("shelly_press", "button" to 1, "id" to 11, "missedBefore" to 0, "tsMs" to 5_000L, "fg" to true)
        add("shelly_press", "button" to 1, "id" to 12, "missedBefore" to 0, "tsMs" to 9_000L, "fg" to false)
        add("shelly_beacon", "id" to 13, "missedBefore" to 0, "tsMs" to 12_000L, "fg" to true)
        assertEquals(DayStats(shellyEvents = 3, shellyWithControlScan = 1), counter.stats())
    }

    @Test
    fun `repeats, packets and simulated presses are not counted`() {
        add("shelly_repeat", "button" to 1, "id" to 10, "missedBefore" to 0, "tsMs" to 7_000L)
        add("shelly_packet", "id" to 10, "tsMs" to 7_000L, "new" to false)
        add("sim_press", "button" to 1, "gesture" to "press", "origin" to "simulated")
        add("input_routed", "origin" to "simulated", "source" to "shelly", "button" to 1, "route" to "to_session")
        assertEquals(DayStats(), counter.stats())
    }

    @Test
    fun `taps are counted`() {
        add("tap_intent", "action" to "android.intent.action.VOICE_COMMAND", "hasDevice" to true)
        add("tap_intent", "action" to "android.intent.action.VOICE_COMMAND", "hasDevice" to true)
        add("tap_stop")
        assertEquals(DayStats(taps = 2), counter.stats())
    }

    @Test
    fun `sessions are sorted by their result`() {
        add("session_end", "result" to "ok", "trigger" to "tap")
        add("session_end", "result" to "ok", "trigger" to "ble_callback")
        add("session_end", "result" to "cancelled", "trigger" to "tap")
        add("session_end", "result" to "link_timeout", "trigger" to "tap")
        add("session_end", "result" to "recording_not_from_hearing_aid", "trigger" to "screen")
        add("session_refused", "trigger" to "adb", "where" to "startForegroundService")
        add("session_start", "trigger" to "tap")
        add("session_busy", "trigger" to "tap")
        assertEquals(DayStats(sessionsOk = 2, sessionsCancelled = 1, sessionsFailed = 3), counter.stats())
    }

    @Test
    fun `every type that counts is named for readers that skip lines`() {
        val counted = listOf("shelly_press", "shelly_beacon", "tap_intent", "session_end", "session_refused")
        assertEquals(counted.toSet(), DayStatsCounter.TYPES)
        // journals written by earlier builds use these words; changing one breaks their counting
        assertEquals(
            counted,
            listOf(
                JournalNames.SHELLY_PRESS,
                JournalNames.SHELLY_BEACON,
                JournalNames.TAP,
                JournalNames.SESSION_END,
                JournalNames.SESSION_REFUSED,
            ),
        )
        assertEquals(
            listOf("id", "tsMs", "missedBefore", "fg", "result", "ok", "cancelled"),
            listOf(
                JournalNames.ID,
                JournalNames.TS_MS,
                JournalNames.MISSED_BEFORE,
                JournalNames.CONTROL_SCAN,
                JournalNames.RESULT,
                JournalNames.RESULT_OK,
                JournalNames.RESULT_CANCELLED,
            ),
        )
        for (type in counted) {
            val before = counter.stats()
            add(type, "id" to type.hashCode(), "tsMs" to 1L, "missedBefore" to 1)
            assertTrue(counter.stats() != before, type)
        }
    }
}
