package io.github.domicc.lara.shelly

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.core.scan.ScanMode
import io.github.domicc.lara.core.scan.ScanStatus

/**
 * Owns the background scans for the enrolled remote and keeps them equal to what the settings
 * ask for. Created by the listener service, and only once that service is in the foreground:
 * Android decides at the start of a scan whether the app counts as foreground, and does not
 * reliably notice when that changes later.
 *
 * Two scans with the same filter (the remote's address and the BTHome service data):
 * - the callback scan, which delivers the presses. Its mode follows the session: one mode
 *   while a session listens to the remote and for a few seconds after it
 *   ([ListenerService.scanForSession]), another one the rest of the day. A scan cannot be
 *   changed, so a new mode means a new scan; the new one is started before the old one is
 *   stopped. There is never a second scan for the remote next to it for a session: both would
 *   draw on the same hourly allowance.
 * - optionally the rescue scan, which delivers through a broadcast to [RescueScanReceiver] and
 *   stays registered inside the Bluetooth stack when this process dies. It always asks for the
 *   idle mode. It is deliberately not stopped together with the service.
 *
 * Both are registered anew ([sync] with force) whenever something may have dropped them:
 * Bluetooth coming back, a new process, and the periodic alarm if the owner has switched it on.
 *
 * A start that has to wait (start budget, retry after a failure) is posted to the main
 * handler under a timed wake lock for the length of the wait. Without the lock the phone
 * would sleep through it: a scan left in session mode, or no scan at all, until something
 * else wakes the phone.
 *
 * Main thread only.
 *
 * @param onStatus called whenever what is registered has changed
 */
@SuppressLint("MissingPermission") // every path checks BleScans.hasPermission first
internal class RemoteScan(
    private val context: Context,
    private val app: LaraApp,
    private val onStatus: (ScanStatus) -> Unit,
) {
    private data class Target(val address: String, val mode: ScanMode, val passive: Boolean)

    /**
     * One registered callback scan. A new object for every start, because Android refuses a
     * callback object that it still has registered.
     */
    private inner class Registration(val number: Int, val target: Target, val startedAtMs: Long) : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) =
            app.shelly.onScanResult(result, ScanSource.CALLBACK)

        // not expected with report delay 0, but nothing that arrives may be lost
        override fun onBatchScanResults(results: List<ScanResult>) =
            results.forEach { app.shelly.onScanResult(it, ScanSource.CALLBACK) }

        override fun onScanFailed(errorCode: Int) = failed(this, errorCode)
    }

    private val journal = app.journal
    private val handler = Handler(Looper.getMainLooper())
    private var current: Registration? = null
    private var started = 0

    /** What this process registered as rescue scan; meaningless until [rescueKnown]. */
    private var rescue: Target? = null

    /** False while a rescue scan of an earlier process may still be registered. */
    private var rescueKnown = false

    private var failuresInRow = 0

    /** A forced start had to wait for the start budget and is still owed. */
    private var forceOwed = false

    private val retry = Runnable { run("retry", force = false) }

    /**
     * Brings the scans in line with the settings, the enrolled remote and the open session.
     *
     * @param reason why this is called, for the journal
     * @param force register anew even if nothing has changed
     */
    fun sync(reason: String, force: Boolean) {
        failuresInRow = 0
        run(reason, force)
    }

    /** Stops the callback scan because the service ends. The rescue scan stays. */
    fun stop(reason: String) {
        handler.removeCallbacks(retry)
        val old = current
        if (old != null && BleScans.scanner(context) != null && BleScans.hasPermission(context)) stopCallback(old, reason)
        current = null
    }

    private fun run(reason: String, force: Boolean) {
        handler.removeCallbacks(retry)
        val scanner = BleScans.scanner(context)
        val remote = app.shellyStore.remote.value
        if (!BleScans.hasPermission(context) || scanner == null) {
            // Android has dropped the registrations itself, or will not let this app touch them
            current = null
            rescue = null
            rescueKnown = false
            val why = if (scanner == null) ScanStatus.BLUETOOTH_OFF else ScanStatus.NO_SCAN_PERMISSION
            journal.log("scan_skipped", mapOf("reason" to reason, "why" to why))
            report(why)
            return
        }
        if (remote == null) {
            // nothing to filter on, and a scan without a filter would hear every device around
            current?.let { stopCallback(it, reason) }
            current = null
            syncRescue(scanner, null, reason, force = false)
            journal.log("scan_skipped", mapOf("reason" to reason, "why" to ScanStatus.NOT_ENROLLED))
            report(ScanStatus.NOT_ENROLLED)
            return
        }
        val config = app.shellyStore.scan.value
        val waiting = !syncCallback(
            scanner,
            Target(remote.address, config.modeFor(ListenerService.scanForSession), config.passive),
            reason,
            force,
        )
        syncRescue(
            scanner,
            if (config.rescue) Target(remote.address, config.idle, config.passive) else null,
            reason,
            force,
        )
        report(if (waiting) ScanStatus.WAITING else ScanStatus.SCAN_FAILED)
    }

    /** @return false if the start has to wait for the start budget */
    private fun syncCallback(scanner: BluetoothLeScanner, target: Target, reason: String, force: Boolean): Boolean {
        val old = current
        val restart = force || forceOwed
        if (!restart && old != null && old.target == target) return true

        val now = SystemClock.elapsedRealtime()
        val waitMs = BleScans.starts.waitMs(now)
        if (waitMs > 0) {
            forceOwed = restart
            journal.log("scan_deferred", mapOf("reason" to reason, "waitMs" to waitMs))
            later(waitMs + 100)
            return false
        }
        forceOwed = false
        val next = Registration(++started, target, now)
        try {
            scanner.startScan(listOf(BleScans.remoteFilter(target.address)), BleScans.settings(target.mode, target.passive), next)
        } catch (e: Exception) {
            // the old scan, if any, stays as it is
            journal.log("scan_failed", mapOf("scan" to ScanSource.CALLBACK.word, "reason" to reason, "exception" to e.toString()))
            return true
        }
        BleScans.starts.record(now)
        current = next
        journal.log("scan_started", startFields(ScanSource.CALLBACK, target, reason) + ("number" to next.number))
        if (old != null) stopCallback(old, "replaced")
        return true
    }

    private fun syncRescue(scanner: BluetoothLeScanner, target: Target?, reason: String, force: Boolean) {
        val unchanged = rescueKnown && target == rescue
        if (unchanged && (target == null || !force)) return
        val intent = RescueScanReceiver.pendingIntent(context)
        try {
            if (!unchanged) {
                // Android ignores a second registration of the same PendingIntent, so new
                // settings need a stop first. This also clears what an earlier process left.
                scanner.stopScan(intent)
                if (rescue != null) journal.log("scan_stopped", mapOf("scan" to ScanSource.RESCUE.word, "reason" to reason))
                rescue = null
                rescueKnown = true
            }
            if (target == null) return
            // With nothing changed this is the keep-alive: ignored while the registration
            // lives, a fresh registration if the Bluetooth stack has lost it.
            val code = scanner.startScan(
                listOf(BleScans.remoteFilter(target.address)),
                BleScans.settings(target.mode, target.passive),
                intent,
            )
            if (!unchanged) BleScans.starts.record(SystemClock.elapsedRealtime())
            if (code == START_ACCEPTED) {
                rescue = target
                journal.log("scan_started", startFields(ScanSource.RESCUE, target, reason) + ("keepAlive" to unchanged))
            } else {
                rescue = null
                journal.log(
                    "scan_failed",
                    mapOf("scan" to ScanSource.RESCUE.word, "reason" to reason, "code" to code, "name" to BleScans.failureName(code)),
                )
            }
        } catch (e: Exception) {
            journal.log("scan_failed", mapOf("scan" to ScanSource.RESCUE.word, "reason" to reason, "exception" to e.toString()))
        }
    }

    /**
     * Stops [registration]; a scan that was started less than a moment ago is stopped a
     * moment later (see [BleScans.stopWhenRegistered]). `scan_stopped` is journaled when the
     * stop has really been issued, not when it was decided.
     */
    private fun stopCallback(registration: Registration, reason: String) {
        BleScans.stopWhenRegistered(context, registration, registration.startedAtMs) { failure ->
            val about = mapOf("scan" to ScanSource.CALLBACK.word, "reason" to reason, "number" to registration.number)
            if (failure == null) {
                journal.log("scan_stopped", about + ("ageMs" to SystemClock.elapsedRealtime() - registration.startedAtMs))
            } else {
                journal.log("scan_failed", about + mapOf("where" to "stop", "exception" to failure.toString()))
            }
        }
    }

    /** Runs [retry] after [delayMs], also if the display is off and nothing else is going on. */
    private fun later(delayMs: Long) {
        BleScans.holdAwake(context, delayMs)
        handler.postDelayed(retry, delayMs)
    }

    /** Android reports a failed start a moment after the call, on the callback of that start. */
    private fun failed(registration: Registration, code: Int) {
        journal.log(
            "scan_failed",
            mapOf(
                "scan" to ScanSource.CALLBACK.word,
                "code" to code,
                "name" to BleScans.failureName(code),
                "number" to registration.number,
                "mode" to registration.target.mode.word,
            ),
        )
        if (current !== registration) return
        current = null
        report(ScanStatus.SCAN_FAILED)
        failuresInRow++
        if (failuresInRow > MAX_RETRIES) {
            // the periodic alarm and every later change still try again
            journal.log("scan_gave_up", mapOf("failures" to failuresInRow))
            return
        }
        later(RETRY_MS * failuresInRow)
    }

    private fun startFields(source: ScanSource, target: Target, reason: String): Map<String, Any?> = mapOf(
        "scan" to source.word,
        "reason" to reason,
        "mode" to target.mode.word,
        "scanType" to BleScans.scanTypeWord(target.passive),
        "filter" to "address+service_data",
        "session" to ListenerService.sessionOpen,
        "importance" to BleScans.importance(),
        // replacing a scan on a timer changes how Android accounts for it
        "refreshMinutes" to app.shellyStore.scan.value.refreshMinutes,
    )

    /** @param off why there is no callback scan, used only if there is none */
    private fun report(off: String) {
        val target = current?.target
        onStatus(
            ScanStatus(
                mode = target?.mode,
                passive = target != null && target.passive && BleScans.scanTypeSelectable,
                rescue = rescue != null,
                off = if (target == null) off else null,
            ),
        )
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val RETRY_MS = 2_000L

        /** What startScan with a PendingIntent returns when the request was sent. */
        const val START_ACCEPTED = 0
    }
}
