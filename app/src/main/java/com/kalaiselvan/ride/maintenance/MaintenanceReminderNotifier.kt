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
     * Distance Trigger: Alerts on every vehicle/app connection starting 500 km
     * before reaching the target odometer reading.
     */
    fun checkDistanceTriggerOnConnection(context: Context, currentOdo: Double) {
        if (currentOdo <= 0) return
        val store = MaintenanceStore.getInstance(context)
        val active = store.getActiveReminder() ?: return
        val targetOdo = active.nextServiceOdoKm ?: return
        if (targetOdo <= 0) return

        val remainingKm = targetOdo - currentOdo
        if (remainingKm <= 500.0) {
            val (title, text) = if (remainingKm <= 0.0) {
                "⚠️ Bike Service Overdue!" to "Odometer is ${String.format(Locale.US, "%.0f", currentOdo)} KM (Target was ${String.format(Locale.US, "%.0f", targetOdo)} KM). Schedule your service!"
            } else {
                "🔧 Maintenance Due Soon" to "Next service due in ${String.format(Locale.US, "%.0f", remainingKm)} KM (Target: ${String.format(Locale.US, "%.0f", targetOdo)} KM)."
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
        val active = store.getActiveReminder() ?: return
        val targetDate = active.nextServiceDateMs ?: return
        if (targetDate <= 0) return

        val now = System.currentTimeMillis()
        val diffMs = targetDate - now
        val daysRemaining = (diffMs / (1000L * 60 * 60 * 24)).toInt()

        // Alerts daily beginning 15 days before the set date
        if (daysRemaining <= 15) {
            val formattedDate = active.formattedNextServiceDate ?: ""
            val (title, text) = when {
                daysRemaining < 0 -> {
                    "⚠️ Service Date Overdue!" to "Maintenance was scheduled for $formattedDate (${abs(daysRemaining)} days ago). Please book service!"
                }
                daysRemaining == 0 -> {
                    "🔧 Maintenance Due Today!" to "Your motorcycle service is scheduled for today ($formattedDate)."
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
