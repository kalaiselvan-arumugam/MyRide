package com.kalaiselvan.ride.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.logs.LogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.UUID

enum class ScannerState {
    STOPPED, RUNNING, FAILED
}

@SuppressLint("MissingPermission")
class BluetoothScanEngine(
    private val context: Context,
    private val onBikeFound: (macAddress: String, deviceName: String) -> Unit,
    private val onStateChanged: (ScannerState) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null

    @Volatile
    var running = false
        private set

    companion object {
        val OFFICIAL_AD_UUIDS = listOf(
            UUID.fromString("C5664E44-D60B-4342-8B7B-35BD6AE28E3F"),
            UUID.fromString("4fbb7609-f50a-40c6-89a9-9be7ea880e95"),
            UUID.fromString("99eb13ac-42c9-4745-8b76-c30141401ce5")
        )
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            running = false
            onStateChanged(ScannerState.FAILED)
            LogStore.logEvent("BLE scan failed with code $errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val mac = result.device?.address ?: return
        val name = result.scanRecord?.deviceName ?: result.device?.name ?: "Unknown Device"
        val rssi = result.rssi
        val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()

        val isBike = BackgroundBleScanner.isSupportedBikeName(name) ||
                serviceUuids.contains(BikeGattManager.SERVICE_UUID) ||
                serviceUuids.any { it in OFFICIAL_AD_UUIDS }

        if (isBike) {
            LogStore.logEvent("Bike Match: $name ($mac) RSSI: $rssi")
            stop()
            onBikeFound(mac, name)
        }
    }

    fun start() {
        if (running) return

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            LogStore.logEvent("Missing Bluetooth Scan/Connect permissions")
            onStateChanged(ScannerState.FAILED)
            return
        }

        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            LogStore.logEvent("Bluetooth is DISABLED. Please enable Bluetooth.")
            onStateChanged(ScannerState.FAILED)
            return
        }

        // 1. First check bonded/paired devices for instant single-tap connect
        val bondedBike = BackgroundBleScanner.getBondedBikeDevice(context)
        if (bondedBike != null) {
            val name = bondedBike.name ?: "Smart Bike"
            LogStore.logEvent("Found Bonded Bike: $name (${bondedBike.address}). Direct connecting...")
            onBikeFound(bondedBike.address, name)
            return
        }

        // 2. Otherwise start BLE discovery
        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            LogStore.logEvent("Bluetooth LE scanner not available")
            onStateChanged(ScannerState.FAILED)
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, callback)
        running = true
        onStateChanged(ScannerState.RUNNING)
        LogStore.logEvent("Bike Auto-Discovery scanner started")
    }

    fun stop() {
        if (!running) return
        try {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (hasPermission) {
                scanner?.stopScan(callback)
            }
        } catch (_: Exception) {}
        running = false
        onStateChanged(ScannerState.STOPPED)
        LogStore.logEvent("Bike Auto-Discovery scanner stopped")
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }
}
