package io.github.domicc.lara.trigger

import android.app.Activity
import android.bluetooth.BluetoothDevice
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
        // The router decides what a tap does: it starts what the owner chose for it (the echo
        // test unless set otherwise), or ends a running count session.
        LaraApp.instance.router.tap(device)
        finish()
    }

    private companion object {
        const val EXTRA_PROFILE = "android.bluetooth.profile.extra.PROFILE"
    }
}
