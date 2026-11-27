package io.github.domicc.lara.journal

import io.github.domicc.lara.core.journal.JournalCodec
import io.github.domicc.lara.core.journal.JournalTail
import io.github.domicc.lara.core.stats.DayStats
import io.github.domicc.lara.core.stats.DayStatsCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * The numbers of today for the screen. They are counted from today's journal file, not kept
 * next to it: so a process that was killed and restarted in the middle of a measurement shows
 * the same numbers as before, and they start at zero with the new file at midnight. The
 * losses in them are the ones the app wrote into its lines; that they are also right across a
 * restart is the job of the packet id the app keeps in its preferences (see
 * [io.github.domicc.lara.shelly.ShellyStore.lastPacketId]).
 *
 * Nothing happens until somebody calls [refresh], which the screen does while it is visible.
 * The first call reads the whole file, every later one only what has been appended since.
 */
class DayCounters(private val journal: Journal) {

    private val stats = MutableStateFlow(DayStats())
    private val mutex = Mutex()
    private var file: File? = null
    private var offset = 0L
    private var counter = DayStatsCounter()

    /** The last read failed; keeps a file that stays unreadable from filling the journal. */
    private var unreadable = false

    val today: StateFlow<DayStats> = stats.asStateFlow()

    /** Brings [today] up to date with the journal. Safe to call from any thread. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = journal.fileOf(System.currentTimeMillis())
            if (current != file || current.length() < offset) {
                // a new day, or the file was replaced: count from its beginning
                file = current
                offset = 0
                counter = DayStatsCounter()
            }
            try {
                if (current.length() > offset) {
                    FileInputStream(current).use { stream ->
                        stream.channel.position(offset)
                        offset += JournalTail.read(stream) { line ->
                            // most lines are packets of the remote; decode only those that count
                            if (DayStatsCounter.TYPES.any { it in line }) JournalCodec.decode(line)?.let(counter::add)
                        }
                    }
                }
                unreadable = false
            } catch (e: IOException) {
                if (!unreadable) journal.log("counters_unreadable", mapOf("exception" to e.toString()))
                unreadable = true
            }
            stats.value = counter.stats()
        }
    }
}
