package io.github.domicc.lara.shelly

import android.app.PendingIntent
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.domicc.lara.LaraApp

/**
 * Receives the results of the rescue scan. That scan is registered with a PendingIntent, lives
 * inside the Bluetooth stack and so survives the death of this process: Android starts the
 * process again to deliver a result here. The press that woke the app is handled at once, and
 * the listener service is started again.
 *
 * The scan asks for every advertisement, like the callback scan, so that the two can be
 * compared packet by packet; Android sends one broadcast per advertisement. What Android still
 * lets through to a dead process is one of the things to be measured.
 */
class RescueScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = LaraApp.instance
        if (intent.hasExtra(BluetoothLeScanner.EXTRA_ERROR_CODE)) {
            val code = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
            app.journal.log(
                "scan_failed",
                mapOf("scan" to ScanSource.RESCUE.word, "code" to code, "name" to BleScans.failureName(code)),
            )
        }
        val results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
        results?.forEach { app.shelly.onScanResult(it, ScanSource.RESCUE) }

        // The listener also clears this scan again if it has been switched off in the meantime.
        // Not for every packet of a burst: a refused start would fill the journal.
        val now = SystemClock.elapsedRealtime()
        if (!ListenerService.running && now - lastStartMs > START_PAUSE_MS) {
            lastStartMs = now
            ListenerService.start(context, "rescue_scan")
        }
    }

    companion object {
        private const val START_PAUSE_MS = 10_000L
        private var lastStartMs = -START_PAUSE_MS

        /**
         * The one PendingIntent of the rescue scan. Android tells scans apart by it, so it must
         * be the same every time. Mutable, because the Bluetooth stack adds the results to it.
         */
        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RescueScanReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
