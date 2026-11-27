package io.github.domicc.lara.shelly

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.core.scan.ScanConfig

/**
 * Brings the listener service back without the owner: after the phone has started (Android
 * sends that only after the first unlock, this app does nothing before it), after an update of
 * the app, and on the periodic alarm.
 *
 * The alarm exists because the app cannot ask Android whether a scan is still registered. A
 * restart of the Bluetooth stack that went unnoticed would leave the remote dead until the next
 * reboot, so the scans are registered anew every few minutes ([ScanConfig.refreshMinutes]).
 * The alarm is inexact, armed once and re-armed when it fires, and of the kind that also fires
 * in Doze; it wakes the phone for a moment and holds no wake lock.
 */
class ListenerStarter : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> ListenerService.start(context, "boot")
            Intent.ACTION_MY_PACKAGE_REPLACED -> ListenerService.start(context, "app_update")
            ACTION_REFRESH -> {
                scheduleRefresh(context)
                ListenerService.refresh(context, "alarm")
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "io.github.domicc.lara.action.REFRESH_SCAN"

        /** Arms the next refresh, replacing one that is already waiting; cancels it for 0 minutes. */
        fun scheduleRefresh(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val intent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ListenerStarter::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val minutes = LaraApp.instance.shellyStore.scan.value.refreshMinutes
            if (minutes <= 0) {
                alarms.cancel(intent)
                return
            }
            alarms.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + minutes * 60_000L,
                intent,
            )
        }
    }
}
