package com.kalaiselvan.ride.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.preferences.BackupSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Manages background scheduling of automated backups.
 * Supports Daily, Weekly, Monthly, and On Data Change triggers.
 */
object BackupScheduler {

    private const val ALARM_REQ_CODE = 918

    fun scheduleNextBackup(context: Context) {
        val appPrefs = AppPreferences.getInstance(context)
        val prefs = appPrefs.userPreferences.value
        if (!prefs.autoBackupEnabled || prefs.autoBackupFolderUri.isBlank() || prefs.autoBackupSchedule == BackupSchedule.NEVER) {
            cancelBackup(context)
            return
        }

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduledBackupReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent, flags)

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                when (prefs.autoBackupSchedule) {
                    BackupSchedule.DAILY -> {
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    BackupSchedule.WEEKLY -> {
                        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.WEEK_OF_YEAR, 1)
                        }
                    }
                    BackupSchedule.NEVER -> return
                }
            }

            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            LogStore.logEvent("BackupScheduler: Next ${prefs.autoBackupSchedule.displayName} backup scheduled for ${calendar.time}")
        } catch (e: Exception) {
            LogStore.logEvent("BackupScheduler: Failed to schedule backup: ${e.message}")
        }
    }

    fun cancelBackup(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduledBackupReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent, flags)
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {}
    }

    /**
     * Checks if a scheduled backup is overdue (e.g. device was asleep/off at 2:00 AM) and runs it.
     */
    fun checkAndRunScheduledBackup(context: Context) {
        val appPrefs = AppPreferences.getInstance(context)
        val prefs = appPrefs.userPreferences.value
        if (!prefs.autoBackupEnabled || prefs.autoBackupFolderUri.isBlank()) return

        val now = System.currentTimeMillis()
        val lastBackup = prefs.lastBackupTimestampMs
        val isDue = when (prefs.autoBackupSchedule) {
            BackupSchedule.NEVER -> false
            BackupSchedule.DAILY -> (now - lastBackup) >= (23L * 60 * 60 * 1000)
            BackupSchedule.WEEKLY -> (now - lastBackup) >= (6L * 24 * 60 * 60 * 1000)
        }

        if (isDue || lastBackup == 0L) {
            CoroutineScope(Dispatchers.IO).launch {
                val uri = Uri.parse(prefs.autoBackupFolderUri)
                val pass = if (prefs.autoBackupEncrypt) appPrefs.getAutoBackupPassword() else null
                val ok = BackupManager.performAutoBackupToDirectory(context, uri, pass)
                if (ok) {
                    appPrefs.setLastBackupTimestamp(System.currentTimeMillis())
                    LogStore.logEvent("BackupScheduler: Scheduled backup completed successfully to ${prefs.autoBackupFolderName}")
                } else {
                    LogStore.logEvent("BackupScheduler: Scheduled backup failed for folder ${prefs.autoBackupFolderName}")
                }
            }
        }
    }
}

