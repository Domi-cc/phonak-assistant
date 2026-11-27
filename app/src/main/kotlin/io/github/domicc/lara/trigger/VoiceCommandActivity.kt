package io.github.domicc.lara.trigger

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.audio.SessionService
import io.github.domicc.lara.core.input.InputSource
import io.github.domicc.lara.core.journal.JournalNames

/**
 * Input path A. Started by Android's Bluetooth stack when the hearing aid (or any hands-free
 * headset) asks for voice recognition. Invisible: it logs, hands over and finishes in onCreate.
 * It must never ask for the screen to be turned on.
 *
 * The app has 5 seconds from here to answer with BluetoothHeadset.startVoiceRecognition,
 * otherwise the hearing aid gets an error.
 */
class VoiceCommandActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionService.holdAwake(this)
        val device = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        LaraApp.instance.journal.log(
            JournalNames.TAP,
            mapOf(
                "action" to intent?.action,
                "hasDevice" to (device != null),
                "profile" to intent?.getIntExtra(EXTRA_PROFILE, -1),
                "extras" to intent?.extras?.keySet()?.sorted()?.joinToString(","),
                "flags" to intent?.flags?.let { "0x" + Integer.toHexString(it) },
                "origin" to InputSource.HEARING_AID_TAP.word,
            ),
        )
        val app = LaraApp.instance
        if (device != null && app.hearingAid.address != null && !app.hearingAid.isSelected(device)) {
            // The voice button of a car kit or another headset: not ours to answer.
            handOver()
        } else {
            // The router decides what a tap does. Unless the owner set it otherwise: with a
            // remote enrolled it opens the session in which the remote is heard, without one
            // it runs the echo test. A tap during a running session ends that session.
            app.router.tap(device)
        }
        finish()
    }

    /**
     * Passes the request on to another app that handles the voice button (the phone's own
     * assistant), so that selecting this app as "Always" does not take the voice button away
     * from the car and from other headsets.
     */
    private fun handOver() {
        val journal = LaraApp.instance.journal
        val others = packageManager
            .queryIntentActivities(Intent(Intent.ACTION_VOICE_COMMAND), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .filter { it.packageName != packageName }
        val target = others.firstOrNull { it.packageName == GOOGLE_APP } ?: others.firstOrNull()
        if (target == null) {
            journal.log("tap_other_device", mapOf("handedOverTo" to null))
            return
        }
        try {
            startActivity(Intent(intent).setComponent(ComponentName(target.packageName, target.name)))
            journal.log("tap_other_device", mapOf("handedOverTo" to target.packageName))
        } catch (e: Exception) {
            journal.log("tap_other_device", mapOf("handedOverTo" to null, "exception" to e.toString()))
        }
    }

    private companion object {
        const val EXTRA_PROFILE = "android.bluetooth.profile.extra.PROFILE"
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    }
}
