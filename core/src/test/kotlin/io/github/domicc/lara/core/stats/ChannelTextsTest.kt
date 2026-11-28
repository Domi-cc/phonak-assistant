package io.github.domicc.lara.core.stats

import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelTextsTest {

    @Test
    fun `a length under a minute is seconds`() {
        assertEquals("0 s", ChannelTexts.duration(0))
        assertEquals("0 s", ChannelTexts.duration(499))
        assertEquals("1 s", ChannelTexts.duration(500))
        assertEquals("41 s", ChannelTexts.duration(41_000))
        assertEquals("59 s", ChannelTexts.duration(59_499))
    }

    @Test
    fun `a length under an hour is minutes and seconds`() {
        assertEquals("1 min 0 s", ChannelTexts.duration(59_500))
        assertEquals("1 min 32 s", ChannelTexts.duration(92_000))
        assertEquals("59 min 59 s", ChannelTexts.duration(3_599_000))
    }

    @Test
    fun `from an hour on the seconds are left out`() {
        assertEquals("1 h 0 min", ChannelTexts.duration(3_600_000))
        assertEquals("2 h 5 min", ChannelTexts.duration(7_529_000))
    }

    @Test
    fun `a length below zero is shown as none`() {
        assertEquals("0 s", ChannelTexts.duration(-3_000))
    }

    @Test
    fun `the media channel shows how often, how long and whose`() {
        val stats = DayStats(mediaOpenings = 14, mediaAnnouncingMs = 41_000, mediaOtherMs = 51_000)
        assertEquals("14 × · 1 min 32 s (App 41 s, anderes 51 s)", ChannelTexts.media(stats))
    }

    @Test
    fun `the voice channel shows how often, how long and whose`() {
        val stats = DayStats(voiceOpenings = 3, voiceSessionMs = 108_000, voiceOtherMs = 0)
        assertEquals("3 × · 1 min 48 s (Sitzungen 1 min 48 s, anderes 0 s)", ChannelTexts.voice(stats))
    }

    @Test
    fun `the total is the sum of the two parts as they are shown`() {
        // each part rounds up; rounded for itself the total would read 1 min 31 s
        val media = DayStats(mediaOpenings = 2, mediaAnnouncingMs = 40_600, mediaOtherMs = 50_600)
        assertEquals("2 × · 1 min 32 s (App 41 s, anderes 51 s)", ChannelTexts.media(media))
        // each part rounds down; rounded for itself the total would read 3 s
        val voice = DayStats(voiceOpenings = 2, voiceSessionMs = 1_400, voiceOtherMs = 1_400)
        assertEquals("2 × · 2 s (Sitzungen 1 s, anderes 1 s)", ChannelTexts.voice(voice))
        // and the folded section adds up the same four numbers
        assertEquals("Kanäle 1 min 34 s", ChannelTexts.summary(media.copy(voiceSessionMs = 1_400, voiceOtherMs = 1_400)))
    }

    @Test
    fun `a day without a channel says so in numbers`() {
        assertEquals("0 × · 0 s (App 0 s, anderes 0 s)", ChannelTexts.media(DayStats()))
        assertEquals("0 × · 0 s (Sitzungen 0 s, anderes 0 s)", ChannelTexts.voice(DayStats()))
    }

    @Test
    fun `openings of unknown length are named, because the sum is too small by them`() {
        val media = DayStats(mediaOpenings = 3, mediaUnknown = 1, mediaAnnouncingMs = 6_000, mediaOtherMs = 3_000)
        assertEquals("3 × · 9 s (App 6 s, anderes 3 s) · 1 × ohne Dauer", ChannelTexts.media(media))
        val voice = DayStats(voiceOpenings = 5, voiceUnknown = 4, voiceSessionMs = 35_000)
        assertEquals("5 × · 35 s (Sitzungen 35 s, anderes 0 s) · 4 × ohne Dauer", ChannelTexts.voice(voice))
    }

    @Test
    fun `the folded section shows the time of both channels together`() {
        val stats = DayStats(mediaAnnouncingMs = 41_000, mediaOtherMs = 51_000, voiceSessionMs = 108_000)
        assertEquals("Kanäle 3 min 20 s", ChannelTexts.summary(stats))
        assertEquals("Kanäle 0 s", ChannelTexts.summary(DayStats()))
    }
}
