package com.mylockpilot.app

import android.content.Context

/**
 * The device_id + device_secret pair a shop staff member types in once,
 * read off the "Pair phone" reveal on the dashboard's Devices panel (see
 * lockpilot/src/app/components/DevicesPanel.tsx). This is the only
 * credential the phone ever holds — it authenticates exactly one row in
 * the devices table, nothing else.
 */
class PairingStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("lockpilot_pairing", Context.MODE_PRIVATE)

    val deviceId: String? get() = prefs.getString(KEY_DEVICE_ID, null)
    val deviceSecret: String? get() = prefs.getString(KEY_DEVICE_SECRET, null)
    val isPaired: Boolean get() = deviceId != null && deviceSecret != null

    fun save(deviceId: String, deviceSecret: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_SECRET, deviceSecret)
            .apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_SECRET = "device_secret"
    }
}
