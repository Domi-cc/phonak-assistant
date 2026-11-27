package io.github.domicc.lara.shelly

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.PowerManager
import android.os.SystemClock
import io.github.domicc.lara.core.bthome.BthomePacket
import io.github.domicc.lara.core.bthome.BthomeParser
import io.github.domicc.lara.core.scan.ScanMode
import io.github.domicc.lara.core.scan.ScanStartBudget

/**
 * What all scans of this app have in common: filters, settings and the start budget.
 *
 * The rules behind them, read from the Android 17 source and still to be confirmed by measuring:
 * - A scan without a filter is suspended while the display is off, so every scan here has one.
 *   A filter on the service-data UUID must carry a data array, even an empty one: with null the
 *   scan still runs, but the filtering is not handed to the Bluetooth controller.
 * - With Android's default scan type an advertisement that can be asked for more reaches the
 *   app only when the answer to Android's scan request has arrived as well. The "passive" type
 *   hands over the advertisement itself, at once. It does not make the radio passive.
 * - Results are never batched (report delay 0): batching adds seconds and catches nothing more.
 */
internal object BleScans {

    val BTHOME: ParcelUuid = ParcelUuid.fromString(BthomeParser.SERVICE_UUID)

    /** One for the process, because Android counts the scan starts of the whole app. */
    val starts = ScanStartBudget()

    /** The scan type can be chosen from SDK 36.1 on; before that Android always scans "active". */
    val scanTypeSelectable: Boolean
        get() = Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    /** Null while Bluetooth is off: Android then refuses the scans of ordinary apps. */
    fun scanner(context: Context): BluetoothLeScanner? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
            ?.takeIf { it.isEnabled }
            ?.bluetoothLeScanner

    /** Every BTHome device in range; for the screen, before a remote is enrolled. */
    fun anyBthomeFilter(): ScanFilter =
        ScanFilter.Builder().setServiceData(BTHOME, byteArrayOf()).build()

    /** BTHome advertisements of one sender. Both conditions go to the Bluetooth controller. */
    fun remoteFilter(address: String): ScanFilter =
        ScanFilter.Builder().setDeviceAddress(address).setServiceData(BTHOME, byteArrayOf()).build()

    fun settings(mode: ScanMode, passive: Boolean): ScanSettings {
        val builder = ScanSettings.Builder()
            .setScanMode(
                when (mode) {
                    ScanMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
                    ScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
                    ScanMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
                },
            )
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
        if (scanTypeSelectable) {
            builder.setScanType(if (passive) ScanSettings.SCAN_TYPE_PASSIVE else ScanSettings.SCAN_TYPE_ACTIVE)
        }
        return builder.build()
    }

    /**
     * Stops the scan of [callback], but not before it is [MIN_STOP_AGE_MS] old.
     *
     * Why (read from the Android 17 source): a scan is registered asynchronously. Until the
     * registration has come back, stopScan takes the callback off Android's list and returns
     * without stopping anything. The scan then starts all the same, and the app can no longer
     * reach it: it runs on in its mode until the process dies or Bluetooth restarts, and the
     * journal would say it was stopped. That happens when a scan is replaced within a few
     * milliseconds of its start, for example by a session that fails at once.
     *
     * @param startedAtMs when the scan was started, on the elapsed-realtime clock
     * @param done called on the main thread once the stop has really been issued: with null,
     * or with the exception that made it fail
     */
    @SuppressLint("MissingPermission") // a missing permission ends up in [done] as SecurityException
    fun stopWhenRegistered(context: Context, callback: ScanCallback, startedAtMs: Long, done: (Exception?) -> Unit) {
        val stop = Runnable {
            done(
                try {
                    // without Bluetooth there is nothing left to stop
                    scanner(context)?.stopScan(callback)
                    null
                } catch (e: Exception) {
                    e
                },
            )
        }
        val waitMs = startedAtMs + MIN_STOP_AGE_MS - SystemClock.elapsedRealtime()
        if (waitMs <= 0) return stop.run()
        holdAwake(context, waitMs)
        main.postDelayed(stop, waitMs)
    }

    /**
     * Keeps the phone awake for [ms] and a moment more, for something that is posted to run
     * then: a handler's clock stands still while the phone sleeps, so without this a retry
     * "in two seconds" would run whenever something else wakes the phone. Timed; nobody
     * releases it.
     */
    fun holdAwake(context: Context, ms: Long) {
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:scan")
            .acquire(ms + AWAKE_MARGIN_MS)
    }

    /** The scan type a scan with [passive] really gets, for the journal. */
    fun scanTypeWord(passive: Boolean): String = if (passive && scanTypeSelectable) "passive" else "active"

    fun failureName(code: Int): String = when (code) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "already_started"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "registration_failed"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "internal_error"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "feature_unsupported"
        ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "out_of_hardware_resources"
        ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "scanning_too_frequently"
        else -> "code_$code"
    }

    /**
     * How Android rates this process right now: 100 on the screen, 125 with a foreground
     * service, higher numbers for less. The scan manager samples this when a scan starts and
     * throttles anything above 125 as background, so it is journaled with every scan start.
     */
    fun importance(): Int =
        ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance

    /**
     * Why a received advertisement is not a readable button packet; null if it is one.
     *
     * @param data the service data for the BTHome UUID, null if the advertisement has none
     * @param packet what the parser made of [data]
     */
    fun problemOf(data: ByteArray?, packet: BthomePacket?): String? = when {
        data == null -> "no_service_data"
        packet == null -> "not_bthome_v2"
        packet.encrypted -> "encrypted"
        packet.stoppedEarly -> packet.stop?.name?.lowercase()
        else -> null
    }

    /** By then the registration of a scan has come back; on the phone it takes a few milliseconds. */
    private const val MIN_STOP_AGE_MS = 1_000L
    private const val AWAKE_MARGIN_MS = 500L
    private val main = Handler(Looper.getMainLooper())
}
