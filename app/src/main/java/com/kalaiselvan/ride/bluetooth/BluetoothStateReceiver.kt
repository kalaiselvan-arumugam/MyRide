package com.kalaiselvan.ride.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.telemetry.TelemetryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for system Bluetooth events, background BLE scanner triggers, and device connections.
 *
 * Triggers automatic background reconnection when:
 * 1. Background BLE hardware scanner detects bike advertising packets (bike ignition ON).
 * 2. Mobile Bluetooth is turned ON in Quick Settings / System Settings.
 * 3. The bike connects via Bluetooth ACL (ACL_CONNECTED). Non-bike audio accessories silently arm the BLE scanner.
 * 4. The phone finishes booting (BOOT_COMPLETED) or app is updated (MY_PACKAGE_REPLACED).
 */
@SuppressLint("MissingPermission")
class BluetoothStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        LogStore.logEvent("BluetoothStateReceiver: Received action $action")

        when (action) {
            BackgroundBleScanner.ACTION_BLE_FOUND,
            "android.bluetooth.le.action.SCAN_RESULT" -> {
                val results = IntentCompat.getParcelableArrayListExtra(intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
                val singleResult = IntentCompat.getParcelableExtra(intent, "android.bluetooth.le.extra.SCAN_RESULT", ScanResult::class.java)
                val matchedDevice = results?.firstOrNull()?.device
                    ?: singleResult?.device
                    ?: BackgroundBleScanner.getBondedBikeDevice(context)

                val prefs = AppPreferences.getInstance(context)
                val mac = matchedDevice?.address?.takeIf { it.isNotBlank() }
                    ?: prefs.userPreferences.value.lastBikeMac

                val name = results?.firstOrNull()?.scanRecord?.deviceName
                    ?: singleResult?.scanRecord?.deviceName
                    ?: matchedDevice?.name
                    ?: "Smart Bike"

                LogStore.logEvent("Auto-Wakeup: Hardware BLE scan matched bike! ($name, $mac)")
                val pendingResult = goAsync()
                val scope = CoroutineScope(Dispatchers.Main)
                scope.launch {
                    try {
                        attemptAutoConnect(context, mac, name, isDirect = true)
                        val engine = TelemetryEngine.getInstance(context)
                        var waited = 0
                        while (waited < 10000 && engine.state.value.isBtConnecting && !engine.state.value.isBtConnected) {
                            delay(500)
                            waited += 500
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_TURNING_OFF,
                    BluetoothAdapter.STATE_OFF -> {
                        LogStore.logEvent("Bluetooth turned OFF on phone. Stopping standby service & resetting telemetry engine...")
                        val engine = TelemetryEngine.getInstance(context)
                        engine.onBluetoothDisabled()
                        BikeForegroundService.stop(context)
                        BikeTileService.requestRefresh(context)
                    }
                    BluetoothAdapter.STATE_ON -> {
                        LogStore.logEvent("Bluetooth turned ON on phone. Arming background BLE hardware scanner & Companion Presence...")
                        val pendingResult = goAsync()
                        val scope = CoroutineScope(Dispatchers.Main)
                        scope.launch {
                            try {
                                delay(1200) // Allow Android BT daemon & HAL to initialize GATT tables
                                BackgroundBleScanner.arm(context)
                                val association = BikePresenceService.getAssociation(context)
                                if (association != null) {
                                    BikePresenceService.observe(context, association)
                                }
                            } catch (e: Exception) {
                                LogStore.logEvent("Arming on BT_ON error: ${e.message}")
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                val name = device?.name ?: ""
                val devClass = device?.bluetoothClass
                LogStore.logEvent("Bluetooth ACL connected: $name (${device?.address})")

                val pendingResult = goAsync()
                val scope = CoroutineScope(Dispatchers.Main)
                scope.launch {
                    try {
                        val bondedBike = BackgroundBleScanner.getBondedBikeDevice(context)
                        val savedMac = AppPreferences.getInstance(context).userPreferences.value.lastBikeMac
                        val isBike = BackgroundBleScanner.isSupportedBikeName(name) ||
                            (savedMac.isNotBlank() && device?.address.equals(savedMac, ignoreCase = true)) ||
                            (bondedBike != null && device?.address.equals(bondedBike.address, ignoreCase = true))

                        if (isBike) {
                            LogStore.logEvent("Bike connection detected via ACL. Launching telemetry...")
                            attemptAutoConnect(context, device?.address, name, isDirect = true)
                            val engine = TelemetryEngine.getInstance(context)
                            var waited = 0
                            while (waited < 10000 && engine.state.value.isBtConnecting && !engine.state.value.isBtConnected) {
                                delay(500)
                                waited += 500
                            }
                        } else if (devClass?.hasService(BluetoothClass.Service.AUDIO) == true ||
                            devClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                            devClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                            devClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
                        ) {
                            // Non-bike audio accessory (TWS Earphones, Helmet Intercom, Headphones, Car BT).
                            // Silently arm the low-power background BLE scanner so the bike can be discovered
                            // when ignition turns ON. Never start the foreground service or show notifications for audio accessories.
                            LogStore.logEvent("Audio accessory connected ($name). Silently arming background BLE scanner...")
                            BackgroundBleScanner.arm(context)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                LogStore.logEvent("System boot/update complete. Arming background BLE hardware scanner & Companion Presence...")
                BackgroundBleScanner.arm(context)
                val association = BikePresenceService.getAssociation(context)
                if (association != null) {
                    BikePresenceService.observe(context, association)
                }
                com.kalaiselvan.ride.maintenance.MaintenanceReminderNotifier.scheduleNextDateAlarm(context)
                com.kalaiselvan.ride.maintenance.MaintenanceReminderNotifier.checkDailyDateTrigger(context)
            }
        }
    }

    private fun attemptAutoConnect(
        context: Context,
        targetMac: String? = null,
        targetName: String? = null,
        isDirect: Boolean = false
    ) {
        val engine = TelemetryEngine.getInstance(context)
        if (!engine.state.value.isBtConnected && !engine.state.value.isBtConnecting) {
            try {
                BikeForegroundService.start(context)
            } catch (e: Exception) {
                LogStore.logEvent("Failed to start BikeForegroundService: ${e.message}")
            }
            if (!targetMac.isNullOrBlank()) {
                if (!targetName.isNullOrBlank()) {
                    AppPreferences.getInstance(context).setBikeModelAndVin(targetName, null)
                }
                AppPreferences.getInstance(context).setLastBikeMac(targetMac)
                engine.connect(targetMac, isAutoConnect = !isDirect, isDirect = isDirect)
            } else {
                engine.connect(isAutoConnect = !isDirect, isDirect = isDirect)
            }
        }

        // Attempt to launch MainActivity UI on screen
        BackgroundBleScanner.launchApp(context)
    }
}
