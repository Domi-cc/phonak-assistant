package io.github.domicc.lara.shelly

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.core.content.edit
import io.github.domicc.lara.core.scan.ScanConfig
import io.github.domicc.lara.core.scan.ScanMode
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The remote the owner picked.
 *
 * @param address upper case, the only form Android's scan filter accepts
 */
data class EnrolledRemote(val address: String)

/**
 * What the app remembers about the remote between starts: which one it listens to, how it
 * scans, whether a press may open a session, and the packet id it received last. Kept in
 * app-private preferences.
 *
 * The settings are flows on purpose. The listener service registers its scans anew as soon as
 * one of them changes, so whoever changes a setting (screen, adb) needs to know nothing about
 * the service. Every change is journaled: a measurement is only worth something together with
 * the settings it ran under.
 */
class ShellyStore(context: Context, private val journal: Journal) {

    private val prefs = context.getSharedPreferences("shelly", Context.MODE_PRIVATE)
    private val remoteState = MutableStateFlow(loadRemote())
    private val scanState = MutableStateFlow(loadScan())
    private val wakesState = MutableStateFlow(prefs.getBoolean(KEY_WAKES, true))

    /** Null until a remote is enrolled. */
    val remote: StateFlow<EnrolledRemote?> = remoteState.asStateFlow()

    val scan: StateFlow<ScanConfig> = scanState.asStateFlow()

    /**
     * "Shelly weckt auf": a press outside a session opens a count session. Off is the silent
     * mode for measuring over hours what Android's scan throttling lets through: presses are
     * then only journaled and counted, and nothing is played into the hearing aid.
     */
    val wakes: StateFlow<Boolean> = wakesState.asStateFlow()

    /**
     * Makes the sender [address] the one remote the app listens to; a remote enrolled before
     * is replaced.
     *
     * @return false if [address] is not a Bluetooth address; nothing is changed then
     */
    @Synchronized
    fun enrol(address: String): Boolean {
        val upper = address.trim().uppercase(Locale.ROOT)
        if (!BluetoothAdapter.checkBluetoothAddress(upper)) {
            journal.log("shelly_enrol_rejected", mapOf("address" to address))
            return false
        }
        val remote = EnrolledRemote(upper)
        prefs.edit {
            putString(KEY_ADDRESS, remote.address)
            // another remote counts for itself
            remove(KEY_LAST_PACKET_ID)
        }
        journal.log("shelly_enrolled", mapOf("address" to remote.address, "replaced" to remoteState.value?.address))
        remoteState.value = remote
        return true
    }

    /** Forgets the remote; the listener then stops scanning. */
    @Synchronized
    fun forget() {
        val old = remoteState.value ?: return
        prefs.edit {
            remove(KEY_ADDRESS)
            remove(KEY_LAST_PACKET_ID)
        }
        journal.log("shelly_forgotten", mapOf("address" to old.address))
        remoteState.value = null
    }

    /**
     * The packet id of the newest event received from the enrolled remote; null if there was
     * none since it was enrolled. It outlives the process on purpose: losses are counted from
     * one id to the next, and without the id from before a restart whatever was lost while the
     * process was gone (the very case the rescue scan exists for) would count as nothing.
     */
    val lastPacketId: Int?
        get() = prefs.getInt(KEY_LAST_PACKET_ID, NO_PACKET_ID).takeIf { it != NO_PACKET_ID }

    /** Remembers [id] as [lastPacketId]. Cheap enough to call for every event. */
    fun rememberPacketId(id: Int) {
        if (id != lastPacketId) prefs.edit { putInt(KEY_LAST_PACKET_ID, id) }
    }

    /** Stores new scan settings; a running listener applies them at once. */
    @Synchronized
    fun setScan(config: ScanConfig) {
        val clean = config.copy(refreshMinutes = config.refreshMinutes.coerceIn(0, ScanConfig.MAX_REFRESH_MINUTES))
        if (clean == scanState.value) return
        prefs.edit {
            putString(KEY_IDLE, clean.idle.word)
            putString(KEY_SESSION, clean.session.word)
            putBoolean(KEY_PASSIVE, clean.passive)
            putBoolean(KEY_RESCUE, clean.rescue)
            putInt(KEY_REFRESH_MINUTES, clean.refreshMinutes)
        }
        journal.log(
            "scan_settings",
            mapOf(
                "idle" to clean.idle.word,
                "session" to clean.session.word,
                "passive" to clean.passive,
                "rescue" to clean.rescue,
                "refreshMinutes" to clean.refreshMinutes,
            ),
        )
        scanState.value = clean
    }

    @Synchronized
    fun setWakes(on: Boolean) {
        if (on == wakesState.value) return
        prefs.edit { putBoolean(KEY_WAKES, on) }
        journal.log("shelly_wakes", mapOf("on" to on))
        wakesState.value = on
    }

    private fun loadRemote(): EnrolledRemote? =
        prefs.getString(KEY_ADDRESS, null)?.let(::EnrolledRemote)

    /** A value that is missing or unreadable falls back to its default. */
    private fun loadScan(): ScanConfig {
        val defaults = ScanConfig()
        return ScanConfig(
            idle = ScanMode.fromWord(prefs.getString(KEY_IDLE, null)) ?: defaults.idle,
            session = ScanMode.fromWord(prefs.getString(KEY_SESSION, null)) ?: defaults.session,
            passive = prefs.getBoolean(KEY_PASSIVE, defaults.passive),
            rescue = prefs.getBoolean(KEY_RESCUE, defaults.rescue),
            refreshMinutes = prefs.getInt(KEY_REFRESH_MINUTES, defaults.refreshMinutes),
        )
    }

    private companion object {
        const val KEY_ADDRESS = "remote_address"
        const val KEY_LAST_PACKET_ID = "remote_last_packet_id"
        const val NO_PACKET_ID = -1
        const val KEY_IDLE = "scan_idle"
        const val KEY_SESSION = "scan_session"
        const val KEY_PASSIVE = "scan_passive"
        const val KEY_RESCUE = "scan_rescue"
        const val KEY_REFRESH_MINUTES = "scan_refresh_minutes"
        const val KEY_WAKES = "wakes"
    }
}
