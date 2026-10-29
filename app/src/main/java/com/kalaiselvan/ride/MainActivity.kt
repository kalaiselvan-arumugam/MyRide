package com.kalaiselvan.ride

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.notifications.BikeNotificationListenerService
import com.kalaiselvan.ride.theme.MyRideTheme
import com.kalaiselvan.ride.ui.main.MainAppScreen

class MainActivity : ComponentActivity() {

    private val notifListenerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkNotificationListenerAccess()
    }

    private val batteryOptLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        requestNotificationListenerAccess()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled; arm background hardware scanner
        com.kalaiselvan.ride.bluetooth.BackgroundBleScanner.arm(applicationContext)
        // Proceed to Battery Optimization & Notification Listener access
        requestBatteryOptimization()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appPrefs = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(this)
        if (appPrefs.userPreferences.value.hasAcceptedDisclaimer) {
            startInitialPermissionSequence()
        }

        val isConnected = com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(this).state.value.isBtConnected
        com.kalaiselvan.ride.utils.AppIconManager.updateIcon(this, isConnected)

        setContent {
            MyRideTheme {
                MainAppScreen()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.kalaiselvan.ride.bluetooth.BikePresenceService.REQ_COMPANION_ASSOCIATE) {
            val association = com.kalaiselvan.ride.bluetooth.BikePresenceService.getAssociation(this)
            if (association != null) {
                com.kalaiselvan.ride.bluetooth.BikePresenceService.observe(this, association)
                LogStore.logEvent("Companion association confirmed onActivityResult: ${association.deviceMacAddress}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure background BLE scanner is armed in hardware
        com.kalaiselvan.ride.bluetooth.BackgroundBleScanner.arm(applicationContext)
        // Ensure the notification listener service is bound and alive
        try {
            val component1 = ComponentName(this, BikeNotificationListenerService::class.java)
            NotificationListenerService.requestRebind(component1)
            LogStore.logEvent("MainActivity: Requested rebind for BikeNotificationListenerService")
        } catch (e: Exception) {
            Log.e("MainActivity", "requestRebind failed: ${e.message}")
        }
        // Auto-connect on resume if Bluetooth is on and not yet connected
        val engine = com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(applicationContext)
        if (!engine.state.value.isBtConnected && !engine.state.value.isBtConnecting) {
            val bm = getSystemService(android.bluetooth.BluetoothManager::class.java)
            if (bm?.adapter?.isEnabled == true) {
                engine.connect(isAutoConnect = true)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Ensure hardware scanner remains armed when app is minimized
        com.kalaiselvan.ride.bluetooth.BackgroundBleScanner.arm(applicationContext)
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val comp1 = ComponentName(this, BikeNotificationListenerService::class.java).flattenToString()
        return enabledListeners.contains(comp1)
    }

    fun checkNotificationListenerAccess() {
        try {
            if (isNotificationAccessGranted()) {
                NotificationListenerService.requestRebind(ComponentName(this, BikeNotificationListenerService::class.java))
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed checking notification listener access: ${e.message}")
        }
    }

    fun requestNotificationListenerAccess() {
        try {
            if (!isNotificationAccessGranted()) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                notifListenerLauncher.launch(intent)
            } else {
                NotificationListenerService.requestRebind(ComponentName(this, BikeNotificationListenerService::class.java))
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed launching notification listener settings: ${e.message}")
        }
    }

    fun requestBatteryOptimization() {
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isIgnoringBatteryOptimizations(packageName) == false) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                batteryOptLauncher.launch(intent)
                return
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch battery opt request: ${e.message}")
            }
        }
        requestNotificationListenerAccess()
    }

    fun startInitialPermissionSequence() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            requestBatteryOptimization()
        }
    }

    fun requestRequiredPermissions() {
        startInitialPermissionSequence()
    }
}
