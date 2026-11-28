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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps a connection to the phone's hands-free (HFP) profile service ready. Getting that
 * connection is asynchronous, and after a hearing-aid tap the app has only 5 seconds to
 * answer, so it is requested as early as possible and kept for the life of the process.
 */
class HeadsetLink(
    private val context: Context,
    private val journal: Journal,
    val store: HearingAidStore,
) {

    private val proxy = MutableStateFlow<BluetoothHeadset?>(null)
    private var requested = false

    /**
     * The connection to the hands-free service; null until it is there and while it is lost.
     * For whoever has to act the moment it comes, however late that is.
     */
    val ready: StateFlow<BluetoothHeadset?> = proxy.asStateFlow()

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
     * The selected hearing aid, if it is connected right now; otherwise null. Never another
     * headset or a car kit, whatever else is connected.
     */
    @SuppressLint("MissingPermission")
    fun hearingAid(headset: BluetoothHeadset): BluetoothDevice? =
        headset.connectedDevices.firstOrNull { store.isSelected(it) }

    /** True if the selected hearing aid is connected for hands-free audio right now. */
    fun hearingAidConnected(): Boolean = proxy.value?.let { hearingAid(it) != null } ?: false

    /** Connected hands-free devices as (address, name), for choosing the hearing aid. */
    @SuppressLint("MissingPermission")
    fun connectedDevices(): List<Pair<String, String>> =
        if (!hasConnectPermission()) emptyList()
        else proxy.value?.connectedDevices?.map { it.address to (it.name ?: "?") } ?: emptyList()

    private fun hasConnectPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
