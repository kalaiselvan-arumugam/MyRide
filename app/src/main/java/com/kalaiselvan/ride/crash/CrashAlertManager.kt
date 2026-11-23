package com.kalaiselvan.ride.crash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.kalaiselvan.ride.MainActivity
import com.kalaiselvan.ride.R
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.sensors.CrashDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CrashEvent(
    val lat: Double,
    val lon: Double,
    val timestamp: Long = System.currentTimeMillis()
)

object CrashAlertManager {
    private const val NOTIFICATION_ID_CRASH = 911
    internal const val CHANNEL_ID_CRASH = "myride_crash_channel"
    const val ACTION_CRASH_ALERT = "com.kalaiselvan.ride.ACTION_CRASH_ALERT"
    const val EXTRA_CRASH_ALERT = "EXTRA_CRASH_ALERT"

    private val _activeCrash = MutableStateFlow<CrashEvent?>(null)
    val activeCrash: StateFlow<CrashEvent?> = _activeCrash.asStateFlow()

    private var countdownJob: Job? = null
    private val _countdownSecondsLeft = MutableStateFlow(30)
    val countdownSecondsLeft: StateFlow<Int> = _countdownSecondsLeft.asStateFlow()

    private var detector: CrashDetector? = null
    private var ringtone: Ringtone? = null

    // Screen off/on (lock button) listener to cancel crash with 2+ quick clicks
    private var screenEventReceiver: BroadcastReceiver? = null
    private var lockButtonPressCount = 0
    private var lastLockPressTime = 0L

    fun startMonitoring(context: Context) {
        val appPrefs = AppPreferences.getInstance(context)
        if (!appPrefs.userPreferences.value.crashDetectionEnabled) return

        if (detector == null) {
            detector = CrashDetector(
                context = context,
                sensitivity = appPrefs.userPreferences.value.crashSensitivity,
                onCrashDetected = { lat, lon ->
                    triggerCrash(context, lat, lon)
                }
            )
        }
        detector?.start()
    }

    fun stopMonitoring() {
        detector?.stop()
        detector = null
    }

    fun updateBikeSpeed(speedKmh: Float) {
        detector?.currentBikeSpeedKmh = speedKmh
    }

    fun triggerFromBikeImu() {
        detector?.triggerFromBikeImu()
    }

    fun triggerCrash(context: Context, lat: Double, lon: Double) {
        LogStore.logEvent("CrashAlertManager: CRASH DETECTED at lat=$lat, lon=$lon")
        _activeCrash.value = CrashEvent(lat, lon)

        val appPrefs = AppPreferences.getInstance(context)
        val totalSecs = appPrefs.userPreferences.value.crashCountdownSeconds.coerceAtLeast(10)
        _countdownSecondsLeft.value = totalSecs

        // 1. Show high-priority heads-up notification with "I'M OK — CANCEL" button
        showCrashNotification(context, totalSecs)

        // 2. Start monitoring lock/power button presses
        startScreenEventListener(context)

        // 3. Wake screen and keep awake
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm?.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "myride:crash_alert_wake"
            )
            wakeLock?.acquire(totalSecs * 1000L + 10000L)
        } catch (_: Exception) {}

        // 4. Launch MainActivity over lockscreen
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_CRASH_ALERT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(EXTRA_CRASH_ALERT, true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            LogStore.logEvent("CrashAlertManager: Failed to launch MainActivity: ${e.message}")
        }

        // 5. Autonomous background countdown ticker & buzzer controller
        countdownJob?.cancel()
        countdownJob = CoroutineScope(Dispatchers.Main).launch {
            vibrateWarning(context)

            var remaining = totalSecs
            while (remaining > 0 && _activeCrash.value != null) {
                _countdownSecondsLeft.value = remaining
                if (remaining <= 10) {
                    playWarningAlarm(context)
                }
                updateCrashNotificationCountdown(context, remaining)
                delay(1000L)
                remaining--
            }

            if (_activeCrash.value != null && remaining <= 0) {
                _countdownSecondsLeft.value = 0
                executeCrashSms(context)
            }
        }
    }

    fun cancelCrash(context: Context? = null) {
        LogStore.logEvent("CrashAlertManager: Crash cancelled by user (I'm OK / Lock button / Notification)")
        countdownJob?.cancel()
        countdownJob = null
        _activeCrash.value = null
        stopWarningAlarm()
        context?.let {
            dismissCrashNotification(it)
            stopScreenEventListener(it)
            vibrateFeedback(it)
        }
    }

    fun executeCrashSms(context: Context) {
        countdownJob?.cancel()
        countdownJob = null
        val event = _activeCrash.value
        _activeCrash.value = null
        stopWarningAlarm()
        dismissCrashNotification(context)
        stopScreenEventListener(context)

        if (event == null) return

        LogStore.logEvent("CrashAlertManager: Countdown completed, sending emergency SOS SMS...")
        val appPrefs = AppPreferences.getInstance(context)
        val prefs = appPrefs.userPreferences.value
        val contacts = appPrefs.getCrashEmergencyContacts()
        if (contacts.isNotEmpty()) {
            val sims = EmergencySmsManager.getAvailableSims(context)
            val subId = prefs.crashSmsSubscriptionId.let { id ->
                if (id == -1 && sims.isNotEmpty()) sims[0].subscriptionId else id
            }
            EmergencySmsManager.sendCrashSms(
                context = context,
                lat = event.lat,
                lon = event.lon,
                riderName = prefs.riderName,
                contacts = contacts,
                subscriptionId = subId
            )
        }
    }

    // ── Loud Emergency Alarm Buzzer (Last 10 Seconds) ────────────────

    fun playWarningAlarm(context: Context) {
        EmergencyBuzzer.play()
    }

    fun stopWarningAlarm() {
        EmergencyBuzzer.stop()
    }

    // ── Notification with Cancel Action ───────────────────────────────

    private fun showCrashNotification(context: Context, secondsLeft: Int = 30) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_CRASH,
                    "Crash Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent motorcycle crash detection alerts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
                nm.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_CRASH_ALERT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(EXTRA_CRASH_ALERT, true)
            }
            val openPendingIntent = PendingIntent.getActivity(
                context, 1001, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val cancelIntent = Intent(context, CrashCancelReceiver::class.java)
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context, 1002, cancelIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val msg = if (secondsLeft > 0) {
                "Emergency SOS sending in ${secondsLeft}s. Tap 'I'M OK — CANCEL' if safe."
            } else {
                "Emergency SOS sending now..."
            }

            val notif = NotificationCompat.Builder(context, CHANNEL_ID_CRASH)
                .setSmallIcon(R.drawable.ic_qs_bike)
                .setContentTitle("CRASH DETECTED — MyRide")
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPendingIntent)
                .setFullScreenIntent(openPendingIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(
                    0,
                    "I'M OK — CANCEL",
                    cancelPendingIntent
                )
                .build()

            nm.notify(NOTIFICATION_ID_CRASH, notif)
        } catch (e: Exception) {
            LogStore.logEvent("CrashAlertManager: Failed to post crash notification: ${e.message}")
        }
    }

    private fun updateCrashNotificationCountdown(context: Context, secondsLeft: Int) {
        showCrashNotification(context, secondsLeft)
    }

    private fun vibrateWarning(context: Context) {
        try {
            val pattern = longArrayOf(0, 600, 200, 600, 200, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun dismissCrashNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_CRASH)
        } catch (_: Exception) {}
    }

    // ── Power/Lock Button Listener (2+ quick clicks cancels) ─────────

    private fun startScreenEventListener(context: Context) {
        if (screenEventReceiver != null) return
        lockButtonPressCount = 0
        lastLockPressTime = 0L

        screenEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                val action = intent?.action ?: return
                if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
                    val now = System.currentTimeMillis()
                    if (now - lastLockPressTime > 3_500L) {
                        lockButtonPressCount = 1
                    } else {
                        lockButtonPressCount++
                    }
                    lastLockPressTime = now

                    if (lockButtonPressCount >= 2) {
                        LogStore.logEvent("CrashAlertManager: Lock button pressed $lockButtonPressCount times — cancelling crash!")
                        cancelCrash(ctx)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.applicationContext.registerReceiver(screenEventReceiver, filter)
    }

    private fun stopScreenEventListener(context: Context) {
        screenEventReceiver?.let {
            try {
                context.applicationContext.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        screenEventReceiver = null
        lockButtonPressCount = 0
    }

    private fun vibrateFeedback(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(100L)
            }
        } catch (_: Exception) {}
    }
}
