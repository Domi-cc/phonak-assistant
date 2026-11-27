package io.github.domicc.lara.journal

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.domicc.lara.BuildInfo
import java.io.File

/**
 * Hands journal files to Android's share sheet. This is the only way the journal leaves the
 * phone, and it takes a deliberate tap: the lines contain Bluetooth addresses and device names.
 *
 * The files stay where they are, in app-private storage. The app chosen in the share sheet
 * gets read access to exactly these files through the app's file provider.
 */
object JournalShare {

    /** Must match the provider in the manifest. */
    private const val AUTHORITY_SUFFIX = ".files"

    /** @return false if there was nothing to share or no share sheet; both are journaled */
    fun share(context: Context, journal: Journal, files: List<File>): Boolean {
        val existing = files.filter { it.isFile && it.length() > 0 }
        if (existing.isEmpty()) {
            journal.log("journal_share_failed", mapOf("why" to "no_files"))
            return false
        }
        return try {
            val uris = ArrayList(existing.map { FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, it) })
            val send = Intent(Intent.ACTION_SEND_MULTIPLE)
                // JSON Lines has no registered type that share targets know; they all take text
                .setType("text/plain")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // the read permission is granted for what the clip data lists
            send.clipData = ClipData.newRawUri("Protokoll", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            context.startActivity(Intent.createChooser(send, "Protokoll teilen"))
            journal.log(
                "journal_shared",
                // with the build, so that a shared file names it close to its end as well
                mapOf("files" to existing.map { it.name }, "bytes" to existing.sumOf { it.length() }, "origin" to "screen") +
                    BuildInfo.fields,
            )
            true
        } catch (e: Exception) {
            // no share sheet on this phone, or a file outside the provider's paths
            journal.log("journal_share_failed", mapOf("why" to "exception", "exception" to e.toString()))
            false
        }
    }
}
