package io.github.domicc.lara.audio

import android.bluetooth.BluetoothDevice
import android.content.Context

/**
 * Which Bluetooth device is THE hearing aid. The owner picks it once; its address is stored.
 *
 * Everything audible depends on this choice: sessions and announcements only ever go to this
 * device. A car kit, another headset or the phone speaker must never be used, so "the first
 * connected headset" is not good enough, and a name is not either (names can be changed).
 */
class HearingAidStore(context: Context) {
    private val preferences = context.getSharedPreferences("hearing_aid", Context.MODE_PRIVATE)

    val address: String? get() = preferences.getString(KEY_ADDRESS, null)
    val name: String? get() = preferences.getString(KEY_NAME, null)

    fun select(address: String, name: String?) {
        preferences.edit().putString(KEY_ADDRESS, address.uppercase()).putString(KEY_NAME, name).apply()
    }

    /** True only for the selected hearing aid; false for null and while nothing is selected. */
    fun isSelected(device: BluetoothDevice?): Boolean {
        val selected = address ?: return false
        return device != null && device.address.equals(selected, ignoreCase = true)
    }

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
    }
}
