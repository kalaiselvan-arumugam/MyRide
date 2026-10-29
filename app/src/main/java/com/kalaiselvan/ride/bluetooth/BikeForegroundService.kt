package com.kalaiselvan.ride.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.kalaiselvan.ride.MainActivity
import com.kalaiselvan.ride.R
import com.kalaiselvan.ride.logs.LogStore

class BikeForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var batteryReceiver: BroadcastReceiver? = null

    companion object {
        private const val CHANNEL_ID = "myride_ble_channel"
        private const val CHANNEL_ID_ALERTS = "myride_alert_channel"
        const val NOTIFICATION_ID = 900
        const val NOTIFICATION_ID_ALERT = 901

        const val ACTION_START = "com.kalaiselvan.ride.action.START"
        const val ACTION_STOP = "com.kalaiselvan.ride.action.STOP"
        const val ACTION_UPDATE_NOTIFICATION = "com.kalaiselvan.ride.action.UPDATE_NOTIFICATION"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val bm = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            if (bm?.adapter?.isEnabled != true) {
                LogStore.logEvent("BikeForegroundService.start skipped: Bluetooth is OFF")
                return
            }
            val intent = Intent(context, BikeForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                LogStore.logEvent("BikeForegroundService.start blocked by OS: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BikeForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.stopService(intent)
            } catch (_: Exception) {}
        }

        fun updateNotification(context: Context, statusText: String) {
            if (!isRunning) return
            val intent = Intent(context, BikeForegroundService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        // 1. Immediately call startForeground in onCreate to satisfy Android requirements
        val initialNotif = buildNotification("Waiting for bike ignition...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotif,
                    getForegroundServiceTypes()
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotif)
            }
        } catch (e: Exception) {
            LogStore.logEvent("BikeForegroundService startForeground in onCreate error: ${e.message}")
        }

        // In standby mode, WakeLock is NOT held so the device CPU sleeps normally
        registerBatteryReceiver()
        LogStore.logEvent("BikeForegroundService started in standby mode (zero CPU wake lock)")

        // 2. Start engine and initiate autoConnect standby connection
        val engine = com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(this)
        engine.start()
        if (!engine.state.value.isBtConnected && !engine.state.value.isBtConnecting) {
            engine.connect(isAutoConnect = true)
        }

        BackgroundBleScanner.launchApp(this)
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(this, com.kalaiselvan.ride.notifications.BikeNotificationListenerService::class.java)
            )
        } catch (_: Exception) {}
        BikeTileService.requestRefresh(this)
    }

    private fun getForegroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            return type
        }
        return 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(this).stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_NOTIFICATION -> {
                val statusText = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "Waiting for bike ignition..."
                val isConnected = statusText.contains("Connected", ignoreCase = true) ||
                        statusText.contains("Live", ignoreCase = true) ||
                        statusText.contains("Ready", ignoreCase = true) ||
                        com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value

                if (isConnected) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }

                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
                return START_STICKY
            }
            else -> {
                val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT) ?: "Waiting for bike ignition..."
                val notif = buildNotification(statusText)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notif,
                            getForegroundServiceTypes()
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notif)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BikeFGS", "Failed to start foreground: ${e.message}", e)
                }
                val isConnected = statusText.contains("Connected", ignoreCase = true) ||
                        statusText.contains("Live", ignoreCase = true) ||
                        statusText.contains("Ready", ignoreCase = true) ||
                        com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value
                if (isConnected) {
                    acquireWakeLock()
                } else {
                    releaseWakeLock()
                }
                val engine = com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(this)
                if (!engine.state.value.isBtConnected && !engine.state.value.isBtConnecting) {
                    engine.connect(isAutoConnect = true)
                }
                return START_STICKY
            }
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isConnected = statusText.contains("Connected", ignoreCase = true) ||
                statusText.contains("Live", ignoreCase = true) ||
                statusText.contains("Ready", ignoreCase = true)

        val channel = if (isConnected) CHANNEL_ID_ALERTS else CHANNEL_ID

        val builder = NotificationCompat.Builder(this, channel)
            .setContentTitle("MyRide Telemetry Engine")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_qs_bike)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (isConnected) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val channelLow = NotificationChannel(
                CHANNEL_ID,
                "MyRide Bluetooth Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps motorcycle TFT and ECU connection active in background"
            }
            manager.createNotificationChannel(channelLow)

            val channelHigh = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Bike Connection Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when motorcycle ignition is turned ON"
                enableVibration(true)
            }
            manager.createNotificationChannel(channelHigh)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyRide:BleWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
                LogStore.logEvent("WakeLock acquired for active ride/navigation")
            }
        } catch (e: Exception) {
            LogStore.logEvent("WakeLock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                LogStore.logEvent("WakeLock released: entering ultra-low-power standby")
            }
        } catch (_: Exception) {}
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    BikeGattManager.instance?.sendPhoneStatus()
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        LogStore.logEvent("BikeForegroundService stopped")
        BackgroundBleScanner.arm(applicationContext)
        BikeTileService.requestRefresh(this)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        LogStore.logEvent("App swiped away from Recents: arming background BLE hardware scanner and persisting.")
        BackgroundBleScanner.arm(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
