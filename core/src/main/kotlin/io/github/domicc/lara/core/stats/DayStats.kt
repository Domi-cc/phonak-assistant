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
 */
data class DayStats(
    val shellyEvents: Int = 0,
    val shellyWithControlScan: Int = 0,
    val shellyMissed: Int = 0,
    val taps: Int = 0,
    val sessionsOk: Int = 0,
    val sessionsCancelled: Int = 0,
    val sessionsFailed: Int = 0,
)

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
 */
class DayStatsCounter {

    private var stats = DayStats()

    /** Packet id and time of the remote's event counted last; null if that line had neither. */
    private var lastShellyEvent: Pair<Any, Any>? = null

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
        }
    }

    companion object {
        /** The record types that change the numbers; a reader may skip every other line. */
        val TYPES = setOf(
            JournalNames.SHELLY_PRESS,
            JournalNames.SHELLY_BEACON,
            JournalNames.TAP,
            JournalNames.SESSION_END,
            JournalNames.SESSION_REFUSED,
        )
    }
}
