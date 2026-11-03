package com.kalaiselvan.ride.bluetooth

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kalaiselvan.ride.MainActivity
import com.kalaiselvan.ride.logs.LogStore

class BikeTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        val context = applicationContext
        val isRunning = BikeForegroundService.isRunning

        if (isRunning) {
            LogStore.logEvent("QS Tile: Stop telemetry requested")
            BikeForegroundService.stop(context)
            updateTileState(isRunning = false, secondaryLabel = "Disconnected")
        } else {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btEnabled = bm?.adapter?.isEnabled == true

            if (!btEnabled) {
                LogStore.logEvent("QS Tile: Bluetooth is off, opening app to prompt")
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("ACTION_REQUEST_BT", true)
                }
                launchActivityCompat(intent)
            } else {
                LogStore.logEvent("QS Tile: Start telemetry requested")
                BikeForegroundService.start(context)
                updateTileState(isRunning = true, secondaryLabel = "Searching...")
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchActivityCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile() {
        val running = BikeForegroundService.isRunning
        updateTileState(
            isRunning = running,
            secondaryLabel = if (running) "Active" else "Off"
        )
    }

    private fun updateTileState(isRunning: Boolean, secondaryLabel: String) {
        val tile = qsTile ?: return
        val label = "Smart Bike"
        tile.label = label
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = secondaryLabel
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = secondaryLabel
        }
        tile.contentDescription = "$label, $secondaryLabel"
        tile.updateTile()
    }

    companion object {
        fun requestRefresh(context: Context) {
            val component = ComponentName(context, BikeTileService::class.java)
            runCatching {
                requestListeningState(context, component)
            }
        }
    }
}
