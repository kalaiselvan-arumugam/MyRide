package com.kalaiselvan.ride.utils

import android.content.Context

/**
 * AppIconManager.
 * Dynamic activity-alias switching at runtime is disabled because modifying
 * activity-alias component state via PackageManager causes Android OS to destroy the
 * active activity and sever active Bluetooth GATT connections, triggering infinite
 * connect/disconnect loops.
 */
object AppIconManager {
    fun updateIcon(context: Context, isConnected: Boolean) {
        // No-op to preserve activity lifecycle and maintain rock-solid BLE connectivity.
    }
}
