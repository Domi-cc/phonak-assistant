package io.github.domicc.lara.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps a connection to the phone's hands-free (HFP) profile service ready. Getting that
 * connection is asynchronous, and after a hearing-aid tap the app has only 5 seconds to
 * answer, so it is requested as early as possible and kept for the life of the process.
 */
class HeadsetLink(private val context: Context, private val journal: Journal) {

    private val proxy = MutableStateFlow<BluetoothHeadset?>(null)
    private var requested = false

    /** Safe to call repeatedly; does nothing without the Bluetooth permission. */
    @Synchronized
    fun connect() {
        if (requested || !hasConnectPermission()) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        requested = adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
                    proxy.value = service as BluetoothHeadset
                    journal.log("hfp_proxy_ready")
                }

                override fun onServiceDisconnected(profile: Int) {
                    proxy.value = null
                    journal.log("hfp_proxy_lost")
                }
            },
            BluetoothProfile.HEADSET,
        )
    }

    fun current(): BluetoothHeadset? = proxy.value

    suspend fun await(timeoutMs: Long): BluetoothHeadset? {
        connect()
        return withTimeoutOrNull(timeoutMs) { proxy.filterNotNull().first() }
    }

    /**
     * The device to talk to: the one named by the trigger if it is connected, otherwise the
     * first connected hands-free device.
     */
    @SuppressLint("MissingPermission")
    fun pick(headset: BluetoothHeadset, preferred: BluetoothDevice?): BluetoothDevice? {
        val connected = headset.connectedDevices
        return connected.firstOrNull { it.address == preferred?.address } ?: connected.firstOrNull()
    }

    /** Names of the connected hands-free devices, for the setup screen. */
    @SuppressLint("MissingPermission")
    fun connectedNames(): List<String> =
        if (!hasConnectPermission()) emptyList()
        else proxy.value?.connectedDevices?.map { it.name ?: "?" } ?: emptyList()

    private fun hasConnectPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
