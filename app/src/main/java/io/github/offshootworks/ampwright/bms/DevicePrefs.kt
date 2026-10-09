package io.github.offshootworks.ampwright.bms

import android.content.Context
import androidx.core.content.edit

/** Remembers the last battery so the app can reconnect to it on launch. */
class DevicePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)

    var lastDevice: BmsDevice?
        get() {
            val address = prefs.getString(KEY_ADDRESS, null) ?: return null
            return BmsDevice(address, prefs.getString(KEY_NAME, null) ?: address)
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_ADDRESS)
                remove(KEY_NAME)
            } else {
                putString(KEY_ADDRESS, value.address)
                putString(KEY_NAME, value.name)
            }
        }

    private companion object {
        const val KEY_ADDRESS = "last_address"
        const val KEY_NAME = "last_name"
    }
}
