package io.github.domicc.lara.journal

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.domicc.lara.core.journal.JournalCodec
import io.github.domicc.lara.core.journal.JournalRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /** The newest records, oldest first, for the screen. */
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
                File(dir, fileName(record.wallMs)).appendText(line + "\n")
            } catch (e: IOException) {
                Log.e(TAG, "journal write failed", e)
            }
            recentRecords.value = (recentRecords.value + record).takeLast(KEEP_IN_MEMORY)
        }
    }

    /** All journal files, oldest first. */
    fun files(): List<File> = dir.listFiles()?.sortedBy { it.name } ?: emptyList()

    private fun loadTail(): List<JournalRecord> = try {
        files().lastOrNull()
            ?.readLines()
            ?.takeLast(KEEP_IN_MEMORY)
            ?.mapNotNull(JournalCodec::decode)
            ?: emptyList()
    } catch (e: IOException) {
        emptyList()
    }

    private fun fileName(wallMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(wallMs)) + ".jsonl"

    private companion object {
        const val TAG = "LaraJournal"
        const val KEEP_IN_MEMORY = 300
    }
}
