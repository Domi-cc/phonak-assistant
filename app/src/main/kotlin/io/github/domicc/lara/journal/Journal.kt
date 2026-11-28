package io.github.domicc.lara.journal

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.domicc.lara.BuildInfo
import io.github.domicc.lara.core.journal.JournalCodec
import io.github.domicc.lara.core.journal.JournalRecord
import io.github.domicc.lara.core.journal.JournalTail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Persistent event journal: one JSON line per event, one file per day, in app-private storage.
 * Every line is also written to logcat (tag LaraJournal), so `adb logcat -s LaraJournal` shows
 * the journal live. Lines contain Bluetooth device names; the journal never leaves the phone
 * on its own.
 */
class Journal(private val context: Context) {

    private val dir = File(context.filesDir, "journal").apply { mkdirs() }
    private val lock = Any()
    private val recentRecords = MutableStateFlow(loadTail())

    /** The name of the day file that is known to begin with its `journal_day` line. Guarded by [lock]. */
    private var begunFile: String? = null

    /**
     * What the first line of a day file says besides the build: state that is journaled once
     * per process and that the reader of a single day still has to know, because a process
     * lives for days. Set once at process start. Called from whatever thread writes the
     * first line of a day, inside the journal's lock: it must be quick and must not log.
     */
    @Volatile
    var dayFields: () -> Map<String, Any?> = { emptyMap() }

    /** The newest records, oldest first, for the screen; without the types in [NOT_ON_SCREEN]. */
    val recent: StateFlow<List<JournalRecord>> = recentRecords.asStateFlow()

    fun log(type: String, fields: Map<String, Any?> = emptyMap()) {
        val record = JournalRecord(
            wallMs = System.currentTimeMillis(),
            elapsedMs = SystemClock.elapsedRealtime(),
            uptimeMs = SystemClock.uptimeMillis(),
            type = type,
            fields = fields,
            ctx = DeviceState.snapshot(context),
        )
        val line = JournalCodec.encode(record)
        Log.i(TAG, line)
        synchronized(lock) {
            try {
                val file = fileOf(record.wallMs)
                // Every day file begins with a line that says which build writes it. The owner
                // shares single days, and the process that wrote `process_start` may have been
                // started on an earlier one. For the same reason it carries [dayFields].
                if (file.name != begunFile) {
                    if (file.length() == 0L) {
                        val first = record.copy(type = DAY_START, fields = BuildInfo.fields + dayFields())
                        val firstLine = JournalCodec.encode(first)
                        Log.i(TAG, firstLine)
                        file.appendText(firstLine + "\n")
                        recentRecords.value = (recentRecords.value + first).takeLast(KEEP_IN_MEMORY)
                    }
                    begunFile = file.name
                }
                file.appendText(line + "\n")
            } catch (e: IOException) {
                Log.e(TAG, "journal write failed", e)
            }
            if (type !in NOT_ON_SCREEN) recentRecords.value = (recentRecords.value + record).takeLast(KEEP_IN_MEMORY)
        }
    }

    /** All journal files, oldest first. */
    fun files(): List<File> = dir.listFiles()?.sortedBy { it.name } ?: emptyList()

    /** The file that takes the lines of the day [wallMs] lies in; it need not exist yet. */
    fun fileOf(wallMs: Long): File = File(dir, fileName(wallMs))

    /**
     * The newest records of the newest file. Only its end is read: this runs in every process
     * start, also the one for a hearing-aid tap, and the file of a day of measurements has
     * many megabytes. A journal that cannot be read must not stop the app, whatever the reason.
     */
    private fun loadTail(): List<JournalRecord> = try {
        val file = files().lastOrNull()
        if (file == null) {
            emptyList()
        } else {
            RandomAccessFile(file, "r").use { reader ->
                val start = max(0, reader.length() - TAIL_BYTES)
                reader.seek(start)
                val end = ByteArray((reader.length() - start).toInt())
                reader.readFully(end)
                // Sorted out as text, before decoding: of the lines of a day with the remote
                // most are its packets, and this runs while a tap waits for its answer.
                JournalTail.lastLines(end, fromStart = start == 0L, max = Int.MAX_VALUE)
                    .filterNot { line -> OFF_SCREEN_MARKS.any { it in line } }
                    .takeLast(KEEP_IN_MEMORY)
                    .mapNotNull(JournalCodec::decode)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "journal not readable", e)
        emptyList()
    }

    private fun fileName(wallMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(wallMs)) + ".jsonl"

    private companion object {
        const val TAG = "LaraJournal"
        const val KEEP_IN_MEMORY = 300

        /** The type of the first line of every day file: which build writes it, and [dayFields]. */
        const val DAY_START = "journal_day"

        /** Lines have a few hundred bytes each, so this holds [KEEP_IN_MEMORY] of them several times over. */
        const val TAIL_BYTES = 512 * 1024L

        /**
         * Types that are written to the file and to logcat like every other line, but kept out
         * of [recent]: they come in dozens per press (one line for every advertisement of the
         * remote), and a single press would push everything else off the screen.
         */
        val NOT_ON_SCREEN = setOf("shelly_packet")

        /** How a line of such a type looks in the file (see [JournalCodec]). */
        val OFF_SCREEN_MARKS = NOT_ON_SCREEN.map { "\"type\":\"$it\"" }
    }
}
