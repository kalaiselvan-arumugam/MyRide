package com.kalaiselvan.ride.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.kalaiselvan.ride.logs.LogStore

object AppIconManager {
    private const val TAG = "AppIconManager"

    fun updateIcon(context: Context, isConnected: Boolean) {
        try {
            val pm = context.packageManager
            val pkg = context.packageName

            val connectedComponent = ComponentName(pkg, "$pkg.MainActivityConnected")
            val disconnectedComponent = ComponentName(pkg, "$pkg.MainActivityDisconnected")

            val currentConnectedState = pm.getComponentEnabledSetting(connectedComponent)
            val targetConnectedState = if (isConnected) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            if (currentConnectedState != targetConnectedState) {
                if (isConnected) {
                    pm.setComponentEnabledSetting(
                        connectedComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    pm.setComponentEnabledSetting(
                        disconnectedComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    LogStore.logEvent("Home screen launcher icon switched to Connected (Blue)")
                } else {
                    pm.setComponentEnabledSetting(
                        disconnectedComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    pm.setComponentEnabledSetting(
                        connectedComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    LogStore.logEvent("Home screen launcher icon switched to Disconnected (Gray)")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update dynamic launcher icon", e)
        }
    }
}
