package com.kalaiselvan.ride.maintenance

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kalaiselvan.ride.MainActivity
import com.kalaiselvan.ride.R
import com.kalaiselvan.ride.logs.LogStore
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

object MaintenanceReminderNotifier {

    private const val CHANNEL_ID = "myride_maintenance_channel"
    const val NOTIF_ID_DISTANCE = 905
    const val NOTIF_ID_DATE = 906
    private const val ALARM_REQ_CODE = 907

    @Volatile
    private var lastDistanceNotifiedOdo: Double = 0.0
    @Volatile
    private var lastDistanceNotifiedTimeMs: Long = 0L

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bike Service & Maintenance Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Proactive alerts for upcoming motorcycle service by date and odometer"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(context: Context, id: Int, title: String, text: String) {
        createNotificationChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_qs_bike)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(id, notif)
    }

    /**
     * Clears any active maintenance notifications (e.g. when a service record is logged or updated).
     */
    fun cancelReminderNotifications(context: Context) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.cancel(NOTIF_ID_DISTANCE)
            manager?.cancel(NOTIF_ID_DATE)
        } catch (_: Exception) {}
    }

    /**
     * Distance Trigger: Alerts on every vehicle/app connection starting 500 km
     * before reaching the target odometer reading.
     */
    fun checkDistanceTriggerOnConnection(context: Context, currentOdo: Double) {
        if (currentOdo <= 0) return
        val store = MaintenanceStore.getInstance(context)
        val status = store.getStatus(currentOdo)
        if (!status.hasActiveReminder || status.targetOdoKm == null) return
        val targetOdo = status.targetOdoKm
        val remainingKm = status.remainingKm ?: return

        if (status.isOverdue || (remainingKm <= 500.0)) {
            val now = System.currentTimeMillis()
            // Deduplicate: notify at most once per 12 hours unless rider traveled > 50 km or crossed into overdue
            val odoDelta = abs(currentOdo - lastDistanceNotifiedOdo)
            val timeDelta = now - lastDistanceNotifiedTimeMs
            val justCrossedOverdue = status.isOverdue && lastDistanceNotifiedOdo < targetOdo

            if (lastDistanceNotifiedTimeMs > 0L && timeDelta < 12 * 3600 * 1000L && odoDelta < 50.0 && !justCrossedOverdue) {
                return
            }
            lastDistanceNotifiedOdo = currentOdo
            lastDistanceNotifiedTimeMs = now

            val (title, text) = if (status.isOverdue) {
                "⚠️ Bike Service Overdue!" to "Odometer is ${String.format(Locale.US, "%.0f", currentOdo)} km (Target was ${String.format(Locale.US, "%.0f", targetOdo)} km). Schedule your service!"
            } else {
                "🔧 Maintenance Due Soon" to "Next service due in ${String.format(Locale.US, "%.0f", remainingKm)} km (Target: ${String.format(Locale.US, "%.0f", targetOdo)} km)."
            }
            sendNotification(context, NOTIF_ID_DISTANCE, title, text)
            LogStore.logEvent("Maintenance Alert (Distance): $title - $text")
        }
    }

    /**
     * Date Trigger: Alerts daily beginning 15 days before the set date.
     */
    fun checkDailyDateTrigger(context: Context) {
        val store = MaintenanceStore.getInstance(context)
        val status = store.getStatus(0.0)
        if (!status.hasActiveReminder || status.remainingDays == null) return
        val daysRemaining = status.remainingDays

        // Alerts daily beginning 15 days before the set date
        if (daysRemaining <= 15) {
            val formattedDate = status.targetDateFormatted ?: ""
            val (title, text) = when {
                daysRemaining < 0 -> {
                    "⚠️ Service Date Overdue!" to "Maintenance was scheduled for $formattedDate (${abs(daysRemaining)} days ago). Please book service!"
                }
                daysRemaining == 0 -> {
                    "🔧 Maintenance Due Today!" to "Your bike service is scheduled for today ($formattedDate)."
                }
                else -> {
                    "🔧 Upcoming Service Reminder" to "Service due in $daysRemaining days ($formattedDate). Schedule your appointment."
                }
            }
            sendNotification(context, NOTIF_ID_DATE, title, text)
            LogStore.logEvent("Maintenance Alert (Date): $title - $text")
        }
    }

    /**
     * Schedules the next daily alarm at 9:00 AM.
     */
    fun scheduleNextDateAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, DailyMaintenanceAlarmReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent, flags)

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                // If 9 AM today has already passed, schedule for tomorrow 9 AM
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            LogStore.logEvent("Maintenance: Scheduled next daily date reminder check for 09:00 AM.")
        } catch (e: Exception) {
            LogStore.logEvent("Failed to schedule daily maintenance alarm: ${e.message}")
        }
    }
}
