package io.github.domicc.lara.core.stats

import io.github.domicc.lara.core.journal.JournalNames
import io.github.domicc.lara.core.journal.JournalRecord

/**
 * What the screen shows as "today".
 *
 * @param shellyEvents events of the remote with at least one press that arrived
 * @param shellyWithControlScan those of [shellyEvents] that arrived while the scan of the
 * screen ran. They say nothing about the background: the radio listened all the time then.
 * @param shellyMissed gaps in the packet ids of the remote: events it sent that never arrived.
 * Not every one of them is a lost press: the remote's beacon uses the same counter, and a
 * remote that starts counting anew looks like a loss in about one case out of eight.
 * @param taps hearing-aid taps for which Android started the app
 * @param sessionsOk sessions that ended with the result `ok`
 * @param sessionsCancelled sessions ended from outside: a second tap, or the link went away
 * @param sessionsFailed sessions with any other result, and sessions Android refused to start
 * @param mediaOpenings times the media stream to the hearing aid was open and closed again,
 * whoever played: this app, another app or the phone's own sounds
 * @param mediaUnknown those of [mediaOpenings] whose length is not known
 * @param mediaAnnouncingMs the time of the openings that closed while an announcement of this
 * app was playing or had just ended: the app's share
 * @param mediaOtherMs the time of all other openings
 * @param voiceOpenings times the voice link to the hearing aid was open and closed again
 * @param voiceUnknown those of [voiceOpenings] whose length is not known
 * @param voiceSessionMs the time of the openings a session of this app held, without the
 * part of them that a phone call took over
 * @param voiceOtherMs all other time of the voice link: phone calls
 */
data class DayStats(
    val shellyEvents: Int = 0,
    val shellyWithControlScan: Int = 0,
    val shellyMissed: Int = 0,
    val taps: Int = 0,
    val sessionsOk: Int = 0,
    val sessionsCancelled: Int = 0,
    val sessionsFailed: Int = 0,
    val mediaOpenings: Int = 0,
    val mediaUnknown: Int = 0,
    val mediaAnnouncingMs: Long = 0,
    val mediaOtherMs: Long = 0,
    val voiceOpenings: Int = 0,
    val voiceUnknown: Int = 0,
    val voiceSessionMs: Long = 0,
    val voiceOtherMs: Long = 0,
) {
    /** How long the media stream to the hearing aid was open, as far as its length is known. */
    val mediaMs: Long get() = mediaAnnouncingMs + mediaOtherMs

    /** How long the voice link to the hearing aid was open, as far as its length is known. */
    val voiceMs: Long get() = voiceSessionMs + voiceOtherMs
}

/**
 * Builds [DayStats] from journal records, fed in the order they were written. The numbers on
 * the screen are computed from the journal and from nothing else, so they are what anybody
 * gets who counts the same lines in an exported file.
 *
 * The losses are the `missedBefore` values the app wrote, not gaps worked out anew from the
 * ids: the app that wrote the line knew more (repeats, restarts of the remote's counter).
 *
 * A simulated press is journaled under a type of its own and therefore never counted here.
 * Sessions are counted whatever started them, a simulated press and adb included: each one is
 * a real session with the hearing aid.
 *
 * The two sound channels to the hearing aid are counted at the line that closes them, with
 * the length that line carries: a channel that is still open is not in the numbers yet, and
 * one that was opened before midnight is booked whole on the day it closed. A voice link
 * that was asked for and never came up is not counted at all.
 *
 * A media stream is closed by the state `stopped` and by no other. The app writes
 * `not_playing` when Android reports "not playing" and no start was seen: Android sends that
 * report every time the hearing aid's media profile connects, with no stream at all. So a
 * stream that was playing when the process started is not in the numbers.
 *
 * The voice link has such a word too, `not_connected`, for Android's report of an attempt
 * that was refused or failed at once.
 *
 * A closing line without a length is an opening of unknown length: it is counted and adds no
 * time, so the sums are too small by such openings. For the voice link there is one case in
 * which the length can still be told. The process died while the link was up, typically in
 * the middle of a session, and the new process closed the link or saw it close: its line has
 * no length, because it did not see the link come up. Then the length is taken from the
 * clocks of the two lines, the `connected` of the old process and the `disconnected` of the
 * new one, and whether a session held the link from the old process's lines. This is the
 * longest overhang the app can cause, so it must not be the one the numbers leave out.
 */
class DayStatsCounter {

    private var stats = DayStats()

    /** Packet id and time of the remote's event counted last; null if that line had neither. */
    private var lastShellyEvent: Pair<Any, Any>? = null

    /** The state in the line of the voice link counted last; null before the first one. */
    private var lastVoiceState: Any? = null

    /**
     * When the voice link that is up was first reported connected, on the clock of the record
     * that counts on while the phone sleeps; null while no link is known to be up. Only from
     * lines that say whether a session was busy: lines of earlier builds do not, and without
     * that nobody knows where to book the time.
     */
    private var voiceUpAtElapsedMs: Long? = null

    /** A line about the voice link that is coming up or up said that a session was busy. */
    private var voiceSessionSeen = false

    fun stats(): DayStats = stats

    fun add(record: JournalRecord) {
        val fields = record.fields
        when (record.type) {
            JournalNames.SHELLY_PRESS, JournalNames.SHELLY_BEACON -> {
                // Two buttons pressed together are two lines for one event of the remote. Only
                // lines that carry both values can be recognised as such; a line without them
                // (written by another build) counts on its own.
                val id = fields[JournalNames.ID]
                val time = fields[JournalNames.TS_MS]
                val event = if (id != null && time != null) id to time else null
                if (event != null && event == lastShellyEvent) return
                lastShellyEvent = event
                val press = record.type == JournalNames.SHELLY_PRESS
                stats = stats.copy(
                    shellyEvents = stats.shellyEvents + if (press) 1 else 0,
                    shellyWithControlScan = stats.shellyWithControlScan +
                        if (press && fields[JournalNames.CONTROL_SCAN] == true) 1 else 0,
                    shellyMissed = stats.shellyMissed + ((fields[JournalNames.MISSED_BEFORE] as? Number)?.toInt() ?: 0),
                )
            }
            JournalNames.TAP -> stats = stats.copy(taps = stats.taps + 1)
            JournalNames.SESSION_REFUSED -> stats = stats.copy(sessionsFailed = stats.sessionsFailed + 1)
            JournalNames.SESSION_END -> stats = when (fields[JournalNames.RESULT]) {
                JournalNames.RESULT_OK -> stats.copy(sessionsOk = stats.sessionsOk + 1)
                JournalNames.RESULT_CANCELLED -> stats.copy(sessionsCancelled = stats.sessionsCancelled + 1)
                else -> stats.copy(sessionsFailed = stats.sessionsFailed + 1)
            }
            JournalNames.AID_MEDIA_STREAM -> if (fields[JournalNames.STATE] == JournalNames.STATE_STOPPED) {
                val ms = length(fields[JournalNames.ON_MS])
                val announcing = fields[JournalNames.ANNOUNCING] == true
                stats = stats.copy(
                    mediaOpenings = stats.mediaOpenings + 1,
                    mediaUnknown = stats.mediaUnknown + if (ms == null) 1 else 0,
                    mediaAnnouncingMs = stats.mediaAnnouncingMs + if (announcing) ms ?: 0 else 0,
                    mediaOtherMs = stats.mediaOtherMs + if (announcing) 0 else ms ?: 0,
                )
            }
            JournalNames.HFP_AUDIO_STATE -> {
                // "connecting" and then "disconnected": the link was asked for and never came up
                val cameUp = lastVoiceState != JournalNames.STATE_CONNECTING
                lastVoiceState = fields[JournalNames.STATE]
                when (lastVoiceState) {
                    JournalNames.STATE_CONNECTING, JournalNames.STATE_CONNECTED -> {
                        if (fields[JournalNames.SESSION] == true) voiceSessionSeen = true
                        // The first report counts: Android repeats "connected" when closing
                        // the audio failed, and the link was up all the time.
                        if (lastVoiceState == JournalNames.STATE_CONNECTED && voiceUpAtElapsedMs == null &&
                            fields[JournalNames.SESSION] is Boolean
                        ) {
                            voiceUpAtElapsedMs = record.elapsedMs
                        }
                    }
                    JournalNames.STATE_DISCONNECTED -> {
                        if (cameUp) closeVoiceLink(record)
                        voiceUpAtElapsedMs = null
                        voiceSessionSeen = false
                    }
                    // No link is up, says Android: whatever an earlier line saw come up went
                    // down without a line, and nobody knows when.
                    JournalNames.STATE_NOT_CONNECTED -> {
                        voiceUpAtElapsedMs = null
                        voiceSessionSeen = false
                    }
                }
            }
        }
    }

    private fun closeVoiceLink(record: JournalRecord) {
        val fields = record.fields
        val own = length(fields[JournalNames.ON_MS])
        // No length on the line: another process saw the link come up. A clock that went
        // backwards means the phone was restarted in between, and then nothing can be said.
        val ms = own ?: voiceUpAtElapsedMs?.let { record.elapsedMs - it }?.takeIf { it >= 0 }
        val session = fields[JournalNames.SESSION] == true || (own == null && voiceSessionSeen)
        // what a phone call took over is not the session's, and never more than the opening
        val callMs = minOf(length(fields[JournalNames.CALL_MS]) ?: 0, ms ?: 0)
        val sessionMs = if (session) (ms ?: 0) - callMs else 0
        stats = stats.copy(
            voiceOpenings = stats.voiceOpenings + 1,
            voiceUnknown = stats.voiceUnknown + if (ms == null) 1 else 0,
            voiceSessionMs = stats.voiceSessionMs + sessionMs,
            voiceOtherMs = stats.voiceOtherMs + (ms ?: 0) - sessionMs,
        )
    }

    /** A length a closing line carries; null if it has none or one that cannot be a length. */
    private fun length(value: Any?): Long? = (value as? Number)?.toLong()?.takeIf { it >= 0 }

    companion object {
        /** The record types that change the numbers; a reader may skip every other line. */
        val TYPES = setOf(
            JournalNames.SHELLY_PRESS,
            JournalNames.SHELLY_BEACON,
            JournalNames.TAP,
            JournalNames.SESSION_END,
            JournalNames.SESSION_REFUSED,
            JournalNames.AID_MEDIA_STREAM,
            JournalNames.HFP_AUDIO_STATE,
        )
    }
}
