package com.kalaiselvan.ride

import kotlinx.coroutines.launch

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
        applyLockScreenWindowFlags()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val action = intent?.action ?: return

        if (action == com.kalaiselvan.ride.crash.CrashAlertManager.ACTION_CRASH_ALERT ||
            intent.getBooleanExtra(com.kalaiselvan.ride.crash.CrashAlertManager.EXTRA_CRASH_ALERT, false)
        ) {
            applyLockScreenWindowFlags()
            intent.removeExtra(com.kalaiselvan.ride.crash.CrashAlertManager.EXTRA_CRASH_ALERT)
            return
        }

        if (action == Intent.ACTION_SEND || (action == Intent.ACTION_VIEW && intent.scheme == "geo")) {
            val sharedText = if (action == Intent.ACTION_SEND) {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            } else {
                intent.dataString
            }
            if (!sharedText.isNullOrBlank()) {
                intent.removeExtra(Intent.EXTRA_TEXT)
                intent.data = null
                val appPrefs = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(this)
                if (!appPrefs.userPreferences.value.turnByTurnNav || appPrefs.userPreferences.value.navSource != com.kalaiselvan.ride.preferences.NavSource.OLA_MAPS) {
                    appPrefs.setTurnByTurnNav(true)
                    appPrefs.setNavSource(com.kalaiselvan.ride.preferences.NavSource.OLA_MAPS)
                }
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val resolved = com.kalaiselvan.ride.navigation.SharedLocationResolver.resolve(sharedText)
                    if (resolved != null) {
                        com.kalaiselvan.ride.navigation.OlaNavEngine.setPendingSharedLocation(resolved)
                    }
                }
            }
        }
    }

    private fun applyLockScreenWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        val km = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km?.requestDismissKeyguard(this, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLockScreenWindowFlags()

        val appPrefs = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(this)
        if (appPrefs.userPreferences.value.hasAcceptedDisclaimer) {
            startInitialPermissionSequence()
        }

        handleIncomingIntent(intent)

        com.kalaiselvan.ride.backup.BackupScheduler.checkAndRunScheduledBackup(this)
        com.kalaiselvan.ride.backup.BackupScheduler.scheduleNextBackup(this)

        com.kalaiselvan.ride.maintenance.MaintenanceReminderNotifier.createNotificationChannel(this)
        com.kalaiselvan.ride.maintenance.MaintenanceReminderNotifier.scheduleNextDateAlarm(this)
        com.kalaiselvan.ride.maintenance.MaintenanceReminderNotifier.checkDailyDateTrigger(this)

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
        // Ensure telemetry and compass sensors are active on resume
        val engine = com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(applicationContext)
        val appPrefs = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(this)
        if (appPrefs.userPreferences.value.hasAcceptedDisclaimer) {
            val bm = getSystemService(android.bluetooth.BluetoothManager::class.java)
            if (bm?.adapter?.isEnabled == true && engine.state.value.isBtConnected) {
                com.kalaiselvan.ride.bluetooth.BikeForegroundService.start(applicationContext)
            }
            engine.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val engine = com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(applicationContext)
        val isConnected = engine.state.value.isBtConnected
        val isNavigating = engine.state.value.isNavActive
        if (!isConnected && !isNavigating) {
            com.kalaiselvan.ride.bluetooth.BikeForegroundService.stop(applicationContext)
        }
    }

    override fun onStop() {
        super.onStop()
        // Ensure hardware scanner remains armed when app is minimized
        com.kalaiselvan.ride.bluetooth.BackgroundBleScanner.arm(applicationContext)
        // Pause UI sensors (compass) and UI render interpolation loop to save battery
        com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(applicationContext).stopUi()
    }

    private fun isNotificationAccessGranted(): Boolean {
        return com.kalaiselvan.ride.utils.DeviceUtils.isNotificationAccessGranted(this)
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS)
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
