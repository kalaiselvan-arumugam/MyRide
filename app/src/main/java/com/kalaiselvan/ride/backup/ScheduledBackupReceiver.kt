package com.kalaiselvan.ride.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kalaiselvan.ride.logs.LogStore

/**
 * BroadcastReceiver triggered by AlarmManager to execute recurring automated backups.
 */
class ScheduledBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        LogStore.logEvent("ScheduledBackupReceiver: Alarm triggered for scheduled backup.")
        BackupScheduler.checkAndRunScheduledBackup(context)
        BackupScheduler.scheduleNextBackup(context)
    }
}

