package io.github.domicc.lara.core.stats

import io.github.domicc.lara.core.journal.JournalRecord

/**
 * What the screen shows as "today".
 *
 * @param shellyEvents events of the remote with at least one press that arrived
 * @param shellyMissed events of the remote that never arrived, counted by its packet ids. Not
 * every one of them is a lost press: the remote's beacon uses the same counter.
 * @param taps hearing-aid taps for which Android started the app
 * @param sessionsOk sessions that ended with the result `ok`
 * @param sessionsCancelled sessions ended from outside: a second tap, or the link went away
 * @param sessionsFailed sessions with any other result, and sessions Android refused to start
 */
data class DayStats(
    val shellyEvents: Int = 0,
    val shellyMissed: Int = 0,
    val taps: Int = 0,
    val sessionsOk: Int = 0,
    val sessionsCancelled: Int = 0,
    val sessionsFailed: Int = 0,
)

/**
 * Builds [DayStats] from journal records, fed in the order they were written. The numbers on
 * the screen are computed from the journal and from nothing else, so they are what anybody
 * gets who counts the same lines in an exported file, and they survive the end of the process.
 *
 * A simulated press is journaled under a type of its own and therefore never counted here.
 */
class DayStatsCounter {

    private var stats = DayStats()

    /** Packet id and time of the remote's event counted last. */
    private var lastShellyEvent: Pair<Any?, Any?>? = null

    fun stats(): DayStats = stats

    fun add(record: JournalRecord) {
        val fields = record.fields
        when (record.type) {
            SHELLY_PRESS, SHELLY_BEACON -> {
                // Two buttons pressed together are two lines for one event of the remote.
                val event = fields["id"] to fields["tsMs"]
                if (event == lastShellyEvent) return
                lastShellyEvent = event
                stats = stats.copy(
                    shellyEvents = stats.shellyEvents + if (record.type == SHELLY_PRESS) 1 else 0,
                    shellyMissed = stats.shellyMissed + ((fields["missedBefore"] as? Number)?.toInt() ?: 0),
                )
            }
            TAP -> stats = stats.copy(taps = stats.taps + 1)
            SESSION_REFUSED -> stats = stats.copy(sessionsFailed = stats.sessionsFailed + 1)
            SESSION_END -> stats = when (fields["result"]) {
                "ok" -> stats.copy(sessionsOk = stats.sessionsOk + 1)
                "cancelled" -> stats.copy(sessionsCancelled = stats.sessionsCancelled + 1)
                else -> stats.copy(sessionsFailed = stats.sessionsFailed + 1)
            }
        }
    }

    companion object {
        private const val SHELLY_PRESS = "shelly_press"
        private const val SHELLY_BEACON = "shelly_beacon"
        private const val TAP = "tap_intent"
        private const val SESSION_END = "session_end"
        private const val SESSION_REFUSED = "session_refused"

        /** The record types that change the numbers; a reader may skip every other line. */
        val TYPES = setOf(SHELLY_PRESS, SHELLY_BEACON, TAP, SESSION_END, SESSION_REFUSED)
    }
}
