package com.kalaiselvan.ride.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.MainActivity
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.preferences.AppPreferences

/**
 * Background BLE hardware scanner using Android OS BluetoothLeScanner PendingIntent.
 *
 * This provides zero-battery continuous standby scanning:
 * The Android Bluetooth controller chip / HAL handles advertising packet matching in hardware.
 * When the motorcycle ignition is turned on, the bike broadcasts its BLE advertisements.
 * The Bluetooth chip detects the match and wakes up the app via BluetoothStateReceiver,
 * even when the app is completely closed or the phone is in deep sleep.
 */
@SuppressLint("MissingPermission")
object BackgroundBleScanner {

    const val ACTION_BLE_FOUND = "com.kalaiselvan.ride.ACTION_BLE_FOUND"
    private const val REQUEST_CODE_BG_BLE_SCAN = 9002

    fun isSupportedBikeName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.lowercase()
        // Generic check: any device name containing "bike" or starting with "le_"
        return n.contains("bike") || n.startsWith("le_")
    }

    fun getBondedBikeDevice(context: Context): BluetoothDevice? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
        }
        return try {
            val bm = context.getSystemService(BluetoothManager::class.java)
            val adapter = bm?.adapter ?: return null
            if (!adapter.isEnabled) return null

            val bonded = adapter.bondedDevices ?: return null
            val savedMac = AppPreferences.getInstance(context).userPreferences.value.lastBikeMac

            // 1. Check if bonded devices contain our saved bike MAC
            if (savedMac.isNotBlank()) {
                val byMac = bonded.firstOrNull { it.address.equals(savedMac, ignoreCase = true) }
                if (byMac != null) return byMac
            }

            // 2. Otherwise find by known bike naming signatures
            val byName = bonded.firstOrNull { isSupportedBikeName(it.name) }
            if (byName != null) return byName

            // 3. Fallback: bonded device that is LE/Dual and NOT an audio headset/intercom
            bonded.firstOrNull { d ->
                val devClass = d.bluetoothClass
                val isAudio = devClass?.hasService(android.bluetooth.BluetoothClass.Service.AUDIO) == true ||
                        devClass?.deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                        devClass?.deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                        devClass?.deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES
                !isAudio && (d.type == BluetoothDevice.DEVICE_TYPE_LE || d.type == BluetoothDevice.DEVICE_TYPE_DUAL)
            }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BluetoothStateReceiver::class.java).apply {
            action = ACTION_BLE_FOUND
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE_BG_BLE_SCAN, intent, flags)
    }

    fun arm(context: Context) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            LogStore.logEvent("BackgroundBleScanner: Permissions not granted yet. Skipping arm.")
            return
        }

        val bm = context.getSystemService(BluetoothManager::class.java)
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            LogStore.logEvent("BackgroundBleScanner: Bluetooth is disabled. Cannot arm hardware scanner.")
            return
        }

        val scanner: BluetoothLeScanner = adapter.bluetoothLeScanner ?: return

        try {
            val filters = mutableListOf<ScanFilter>()

            // 1. Bonded Bike MAC or Last Saved MAC
            val bonded = getBondedBikeDevice(context)
            val savedMac = AppPreferences.getInstance(context).userPreferences.value.lastBikeMac
            val targetMac = bonded?.address ?: savedMac.takeIf { it.isNotBlank() }

            if (!targetMac.isNullOrBlank() && BluetoothAdapter.checkBluetoothAddress(targetMac)) {
                filters.add(
                    ScanFilter.Builder()
                        .setDeviceAddress(targetMac)
                        .build()
                )
                LogStore.logEvent("BackgroundBleScanner: Added target MAC filter for $targetMac")
            }

            if (savedMac.isNotBlank() && savedMac != targetMac && BluetoothAdapter.checkBluetoothAddress(savedMac)) {
                filters.add(
                    ScanFilter.Builder()
                        .setDeviceAddress(savedMac)
                        .build()
                )
                LogStore.logEvent("BackgroundBleScanner: Added saved MAC filter for $savedMac")
            }

            // 2. Bike Telemetry Service UUID
            filters.add(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(BikeGattManager.SERVICE_UUID))
                    .build()
            )

            // 3. Official Bike Advertisement UUIDs
            BluetoothScanEngine.OFFICIAL_AD_UUIDS.forEach { uuid ->
                filters.add(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(uuid))
                        .build()
                )
            }

            // Standard low-power ScanSettings compatible with all Android OS versions (no illegal match flags)
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()

            val pendingIntent = getPendingIntent(context)
            scanner.startScan(filters, settings, pendingIntent)
            LogStore.logEvent("BackgroundBleScanner: Hardware scanner armed with ${filters.size} filters.")
        } catch (e: Exception) {
            LogStore.logEvent("BackgroundBleScanner: Failed to arm hardware scanner: ${e.message}")
        }
    }

    fun disarm(context: Context) {
        try {
            val bm = context.getSystemService(BluetoothManager::class.java)
            val adapter = bm?.adapter ?: return
            if (!adapter.isEnabled) return
            val scanner = adapter.bluetoothLeScanner ?: return
            val pendingIntent = getPendingIntent(context)
            scanner.stopScan(pendingIntent)
            LogStore.logEvent("BackgroundBleScanner: Hardware scanner disarmed.")
        } catch (e: Exception) {
            LogStore.logEvent("BackgroundBleScanner: Disarm error: ${e.message}")
        }
    }

    fun launchApp(context: Context) {
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
            LogStore.logEvent("BackgroundBleScanner: Auto-launched MainActivity UI")
        } catch (e: Exception) {
            LogStore.logEvent("BackgroundBleScanner: Direct startActivity blocked by OS: ${e.message}")
        }
    }
}
