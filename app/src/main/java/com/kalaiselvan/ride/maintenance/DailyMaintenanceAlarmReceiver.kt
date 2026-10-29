package com.kalaiselvan.ride.maintenance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kalaiselvan.ride.logs.LogStore

/**
 * BroadcastReceiver triggered daily (e.g. at 9:00 AM) to evaluate date-based maintenance reminders.
 * If an active service reminder is within 15 days of its target date, it alerts the rider.
 */
class DailyMaintenanceAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        LogStore.logEvent("DailyMaintenanceAlarmReceiver: Triggered daily date reminder check.")
        MaintenanceReminderNotifier.checkDailyDateTrigger(context)
        MaintenanceReminderNotifier.scheduleNextDateAlarm(context)
    }
}
