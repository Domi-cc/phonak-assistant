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
    private fun add(type: String, vararg fields: Pair<String, Any?>) = addAt(2, type, *fields)

    /** A line written when the clock that counts on while the phone sleeps stood at [elapsedMs]. */
    private fun addAt(elapsedMs: Long, type: String, vararg fields: Pair<String, Any?>) {
        val line = JournalCodec.encode(JournalRecord(1, elapsedMs, 3, type, mapOf(*fields)))
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

    private fun media(state: String, vararg more: Pair<String, Any?>) = add("aid_media_stream", "state" to state, *more)

    private fun voice(state: String, vararg more: Pair<String, Any?>) = add("hfp_audio_state", "state" to state, *more)

    @Test
    fun `the media stream is counted when it closes, with the time it was open`() {
        media("playing", "announcing" to true, "announceReason" to "hourly")
        media("stopped", "onMs" to 6_400, "announcing" to true, "announceReason" to "hourly")
        media("playing", "announcing" to false)
        media("stopped", "onMs" to 3_100, "announcing" to false)
        assertEquals(DayStats(mediaOpenings = 2, mediaAnnouncingMs = 6_400, mediaOtherMs = 3_100), counter.stats())
        assertEquals(9_500, counter.stats().mediaMs)
    }

    @Test
    fun `a media stream that is still open is not counted yet`() {
        media("playing", "announcing" to true)
        assertEquals(DayStats(), counter.stats())
    }

    @Test
    fun `whether a media stream is the app's is what its closing line says`() {
        // a sound of the phone opened the stream and an announcement followed in it
        media("playing", "announcing" to false)
        media("stopped", "onMs" to 8_000, "announcing" to true, "announceReason" to "calendar")
        // an announcement opened the stream and something else kept it open long after
        media("playing", "announcing" to true, "announceReason" to "screen")
        media("stopped", "onMs" to 60_000, "announcing" to false)
        assertEquals(DayStats(mediaOpenings = 2, mediaAnnouncingMs = 8_000, mediaOtherMs = 60_000), counter.stats())
    }

    @Test
    fun `a report that nothing plays is no opening`() {
        // Android sends it every time the hearing aid's media profile connects: no stream was open
        media("not_playing", "announcing" to false)
        media("not_playing", "announcing" to false)
        assertEquals(DayStats(), counter.stats())
        // nor does it close a stream whose start an earlier process journaled: the stream may
        // have ended hours before the hearing aid connected again
        media("playing", "announcing" to true, "announceReason" to "hourly")
        media("not_playing", "announcing" to false)
        assertEquals(DayStats(), counter.stats())
    }

    @Test
    fun `two reports that the stream plays and one that it stopped are one opening`() {
        // the report of the stop in between was lost; the closing line has the length since the newer start
        media("playing", "announcing" to false)
        media("playing", "announcing" to false)
        media("stopped", "onMs" to 4_000, "announcing" to false)
        assertEquals(DayStats(mediaOpenings = 1, mediaOtherMs = 4_000), counter.stats())
    }

    @Test
    fun `a closing line without its length is an opening of unknown length`() {
        // this build always writes the length of a stream it calls stopped; the voice link was up
        // when the process started, and no line of today says since when
        media("stopped", "announcing" to false)
        voice("disconnected", "session" to false, "mode" to "normal")
        assertEquals(DayStats(mediaOpenings = 1, mediaUnknown = 1, voiceOpenings = 1, voiceUnknown = 1), counter.stats())
    }

    @Test
    fun `a day that begins with a closing line books that opening on this day`() {
        // the stream was opened before midnight; the line that closes it is the first of the new file
        media("stopped", "onMs" to 5_200, "announcing" to true, "announceReason" to "hourly")
        voice("disconnected", "session" to false, "mode" to "in_call", "onMs" to 1_260_000)
        assertEquals(
            DayStats(mediaOpenings = 1, mediaAnnouncingMs = 5_200, voiceOpenings = 1, voiceOtherMs = 1_260_000),
            counter.stats(),
        )
    }

    @Test
    fun `the voice link is counted when it closes, sessions apart from everything else`() {
        voice("connecting", "session" to true, "mode" to "normal")
        voice("connected", "session" to true, "mode" to "normal")
        voice("disconnected", "session" to true, "mode" to "normal", "onMs" to 34_800)
        // a phone call
        voice("connected", "session" to false, "mode" to "in_call")
        voice("disconnected", "session" to false, "mode" to "normal", "onMs" to 90_000)
        assertEquals(DayStats(voiceOpenings = 2, voiceSessionMs = 34_800, voiceOtherMs = 90_000), counter.stats())
        assertEquals(124_800, counter.stats().voiceMs)
    }

    @Test
    fun `a second report that the voice link is connected is no second opening`() {
        // Android repeats the report when closing the audio failed: the link was up all the time
        voice("connecting", "session" to true, "mode" to "normal")
        voice("connected", "session" to true, "mode" to "normal")
        voice("connected", "session" to false, "mode" to "normal", "repeated" to true)
        voice("disconnected", "session" to true, "mode" to "normal", "onMs" to 75_000)
        assertEquals(DayStats(voiceOpenings = 1, voiceSessionMs = 75_000), counter.stats())
    }

    @Test
    fun `the part of a voice link that a phone call took over is not the session's`() {
        // a call came in during a session and Android kept the audio up for it
        voice("connected", "session" to true, "mode" to "normal")
        voice("disconnected", "session" to true, "mode" to "normal", "onMs" to 320_000, "callMs" to 300_000)
        assertEquals(DayStats(voiceOpenings = 1, voiceSessionMs = 20_000, voiceOtherMs = 300_000), counter.stats())
    }

    @Test
    fun `the time of a call never exceeds the opening and never counts twice`() {
        // longer than the opening: all of it is the call's
        voice("disconnected", "session" to true, "onMs" to 10_000, "callMs" to 12_000)
        // on a link that was no session's the whole time is "other" anyway
        voice("disconnected", "session" to false, "onMs" to 90_000, "callMs" to 90_000)
        // not a length: no part is taken away from the session
        voice("disconnected", "session" to true, "onMs" to 30_000, "callMs" to -4)
        voice("disconnected", "session" to true, "onMs" to 5_000, "callMs" to "long")
        assertEquals(DayStats(voiceOpenings = 4, voiceSessionMs = 35_000, voiceOtherMs = 100_000), counter.stats())
    }

    @Test
    fun `a voice link that outlived its process gets its length from the clocks of the two lines`() {
        // The process died in a session. The new one did not see the link come up, so the line
        // that closes it has no length and does not know of the session.
        addAt(100_000, "hfp_audio_state", "state" to "connecting", "session" to true, "mode" to "normal")
        addAt(100_700, "hfp_audio_state", "state" to "connected", "session" to true, "mode" to "normal")
        addAt(113_700, "hfp_audio_state", "state" to "disconnected", "session" to false, "mode" to "normal")
        assertEquals(DayStats(voiceOpenings = 1, voiceSessionMs = 13_000), counter.stats())
        // the next link is counted by its own line again
        addAt(200_000, "hfp_audio_state", "state" to "connected", "session" to false, "mode" to "in_call")
        addAt(260_000, "hfp_audio_state", "state" to "disconnected", "session" to false, "mode" to "normal", "onMs" to 60_000)
        assertEquals(DayStats(voiceOpenings = 2, voiceSessionMs = 13_000, voiceOtherMs = 60_000), counter.stats())
    }

    @Test
    fun `a link that outlived its process counts from the first report that it was connected`() {
        addAt(100_000, "hfp_audio_state", "state" to "connected", "session" to false, "mode" to "in_call")
        addAt(130_000, "hfp_audio_state", "state" to "connected", "session" to false, "mode" to "in_call", "repeated" to true)
        addAt(150_000, "hfp_audio_state", "state" to "disconnected", "session" to false, "mode" to "normal")
        assertEquals(DayStats(voiceOpenings = 1, voiceOtherMs = 50_000), counter.stats())
    }

    @Test
    fun `a closing line without its length after a restart of the phone stays of unknown length`() {
        // the clock began anew, so the two lines cannot be set against each other
        addAt(500_000, "hfp_audio_state", "state" to "connected", "session" to true, "mode" to "normal")
        addAt(20_000, "hfp_audio_state", "state" to "disconnected", "session" to false, "mode" to "normal")
        assertEquals(DayStats(voiceOpenings = 1, voiceUnknown = 1), counter.stats())
    }

    @Test
    fun `a report that the voice link is not up is no opening`() {
        // Android reports "disconnected" after "disconnected" when an attempt was refused at once
        voice("not_connected", "session" to true, "mode" to "normal")
        voice("not_connected", "session" to false, "mode" to "normal")
        assertEquals(DayStats(), counter.stats())
    }

    @Test
    fun `a report that the voice link is not up ends what an earlier process saw come up`() {
        // the link of the dead process went down unseen; the line without a length that
        // follows much later is not its end
        addAt(100_000, "hfp_audio_state", "state" to "connected", "session" to true, "mode" to "normal")
        addAt(900_000, "hfp_audio_state", "state" to "not_connected", "session" to false, "mode" to "normal")
        addAt(5_000_000, "hfp_audio_state", "state" to "disconnected", "session" to false, "mode" to "normal")
        assertEquals(DayStats(voiceOpenings = 1, voiceUnknown = 1), counter.stats())
    }

    @Test
    fun `a voice link that was asked for and never came up is no opening`() {
        voice("connecting", "session" to true, "mode" to "normal")
        voice("disconnected", "session" to true, "mode" to "normal")
        assertEquals(DayStats(), counter.stats())
        // the next one comes up and counts
        voice("connecting", "session" to true, "mode" to "normal")
        voice("connected", "session" to true, "mode" to "normal")
        voice("disconnected", "session" to true, "mode" to "normal", "onMs" to 12_000)
        assertEquals(DayStats(voiceOpenings = 1, voiceSessionMs = 12_000), counter.stats())
    }

    @Test
    fun `lines of the voice link written by earlier builds count as openings of unknown length`() {
        // Before the channels were watched all day the line had the state and nothing else.
        // Whose the link was these lines do not say, so their clocks are not used either.
        addAt(1_000, "hfp_audio_state", "state" to "connecting")
        addAt(1_600, "hfp_audio_state", "state" to "connected")
        addAt(36_000, "hfp_audio_state", "state" to "disconnected")
        assertEquals(DayStats(voiceOpenings = 1, voiceUnknown = 1), counter.stats())
    }

    @Test
    fun `a length that is no number or below zero adds no time`() {
        media("stopped", "onMs" to "soon", "announcing" to false)
        voice("disconnected", "session" to true, "onMs" to -5)
        assertEquals(DayStats(mediaOpenings = 1, mediaUnknown = 1, voiceOpenings = 1, voiceUnknown = 1), counter.stats())
    }

    @Test
    fun `states this build does not know are not counted`() {
        media("12", "announcing" to false)
        voice("13", "session" to false)
        assertEquals(DayStats(), counter.stats())
    }

    @Test
    fun `every type that counts is named for readers that skip lines`() {
        val counted = listOf(
            "shelly_press", "shelly_beacon", "tap_intent", "session_end", "session_refused", "aid_media_stream", "hfp_audio_state",
        )
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
                JournalNames.AID_MEDIA_STREAM,
                JournalNames.HFP_AUDIO_STATE,
            ),
        )
        assertEquals(
            listOf(
                "id", "tsMs", "missedBefore", "fg", "result", "ok", "cancelled",
                "state", "stopped", "connecting", "connected", "disconnected", "not_connected",
                "onMs", "callMs", "announcing", "session",
            ),
            listOf(
                JournalNames.ID,
                JournalNames.TS_MS,
                JournalNames.MISSED_BEFORE,
                JournalNames.CONTROL_SCAN,
                JournalNames.RESULT,
                JournalNames.RESULT_OK,
                JournalNames.RESULT_CANCELLED,
                JournalNames.STATE,
                JournalNames.STATE_STOPPED,
                JournalNames.STATE_CONNECTING,
                JournalNames.STATE_CONNECTED,
                JournalNames.STATE_DISCONNECTED,
                JournalNames.STATE_NOT_CONNECTED,
                JournalNames.ON_MS,
                JournalNames.CALL_MS,
                JournalNames.ANNOUNCING,
                JournalNames.SESSION,
            ),
        )
        // the two channels are counted at the line that closes them
        val closing = mapOf("aid_media_stream" to "stopped", "hfp_audio_state" to "disconnected")
        for (type in counted) {
            val before = counter.stats()
            add(type, "id" to type.hashCode(), "tsMs" to 1L, "missedBefore" to 1, "state" to closing[type])
            assertTrue(counter.stats() != before, type)
        }
    }
}
