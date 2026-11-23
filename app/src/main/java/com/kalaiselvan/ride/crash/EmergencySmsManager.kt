package com.kalaiselvan.ride.crash

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.R
import com.kalaiselvan.ride.preferences.EmergencyContact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SimInfo(
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val number: String,
    val slotIndex: Int
)

object EmergencySmsManager {

    fun getAvailableSims(context: Context): List<SimInfo> {
        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPhoneState && !hasPhoneNumbers) return emptyList()

        return try {
            val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            val subs: List<SubscriptionInfo> = sm.activeSubscriptionInfoList ?: return emptyList()
            subs.map { info ->
                var num = ""
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        num = sm.getPhoneNumber(info.subscriptionId)
                    } catch (_: Exception) {}
                }
                if (num.isBlank()) {
                    @Suppress("DEPRECATION")
                    num = info.number ?: ""
                }
                if (num.isBlank()) {
                    try {
                        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
                        val subTm = tm?.createForSubscriptionId(info.subscriptionId)
                        num = subTm?.line1Number ?: ""
                    } catch (_: Exception) {}
                }
                if (num.isBlank()) {
                    num = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context).getSimPhoneOverride(info.subscriptionId)
                }

                val carrier = info.carrierName?.toString()?.trim() ?: ""
                val display = info.displayName?.toString()?.trim() ?: ""
                val label = when {
                    display.isNotBlank() && !display.startsWith("SIM", ignoreCase = true) -> display
                    carrier.isNotBlank() -> carrier
                    else -> "SIM ${info.simSlotIndex + 1}"
                }

                val maskedNum = if (num.length > 4) "***-***-" + num.takeLast(4) else if (num.isNotBlank()) "***" else "None"
                com.kalaiselvan.ride.logs.LogStore.logEvent("EmergencySmsManager: SIM ${info.simSlotIndex + 1} ($label) resolved number: '$maskedNum'")

                SimInfo(
                    subscriptionId = info.subscriptionId,
                    displayName = label,
                    carrierName = carrier,
                    number = num.trim(),
                    slotIndex = info.simSlotIndex
                )
            }.sortedBy { it.slotIndex }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun sendCrashSms(
        context: Context,
        lat: Double,
        lon: Double,
        riderName: String,
        contacts: List<EmergencyContact>,
        subscriptionId: Int
    ) {
        if (contacts.isEmpty()) return
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            com.kalaiselvan.ride.logs.LogStore.logEvent("EmergencySmsManager: SEND_SMS permission missing during emergency dispatch!")
            showSosOutcomeNotification(context, 0, contacts.size)
            return
        }

        val name = riderName.ifBlank { "A MyRide user" }
        val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val message = buildString {
            appendLine("🚨 CRASH ALERT - MyRide")
            appendLine()
            appendLine("$name may have been in a motorcycle crash.")
            appendLine()
            if (lat != 0.0 && lon != 0.0) {
                appendLine("📍 Location:")
                appendLine("https://maps.google.com/?q=$lat,$lon")
                appendLine()
            }
            appendLine("🕒 Time: $timeStr")
            appendLine()
            appendLine("Please check on them immediately.")
            append("Sent automatically by MyRide.")
        }

        val smsManager = getSmsManager(context, subscriptionId)
        var successCount = 0
        var failedCount = 0
        contacts.forEach { contact ->
            val ok = sendTextMessageInternal(smsManager, contact.number, message)
            if (ok) successCount++ else failedCount++
        }

        showSosOutcomeNotification(context, successCount, failedCount)
    }

    fun sendNotificationSms(
        context: Context,
        riderName: String,
        riderPhone: String,
        contact: EmergencyContact,
        subscriptionId: Int
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) return false

        val name = riderName.ifBlank { "A MyRide user" }
        val message = buildString {
            appendLine("Hi, $name has added your number as an emergency contact on MyRide — a motorcycle safety app.")
            appendLine()
            appendLine("If they are in a crash, you will automatically receive an SOS SMS from this number with their GPS location.")
            appendLine()
            if (riderPhone.isNotBlank()) {
                appendLine("No action is needed. To reach $name directly: $riderPhone")
                appendLine()
            }
            append("- MyRide Safety")
        }

        val smsManager = getSmsManager(context, subscriptionId)
        return sendTextMessageInternal(smsManager, contact.number, message)
    }

    private fun sendTextMessageInternal(
        smsManager: SmsManager,
        destinationAddress: String,
        message: String
    ): Boolean {
        return try {
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(destinationAddress, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(destinationAddress, null, parts, null, null)
            }
            true
        } catch (e: Exception) {
            com.kalaiselvan.ride.logs.LogStore.logEvent("EmergencySmsManager: Failed to dispatch SMS to $destinationAddress: ${e.message}")
            false
        }
    }

    private fun showSosOutcomeNotification(context: Context, successCount: Int, failedCount: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val title = if (failedCount == 0) "SOS Alerts Dispatched" else "⚠️ SOS Alert Notice"
            val text = when {
                successCount > 0 && failedCount == 0 -> "Emergency SOS SMS sent successfully to $successCount contact(s)."
                successCount > 0 && failedCount > 0 -> "Sent to $successCount contact(s), but failed for $failedCount contact(s)!"
                else -> "Failed to send emergency SMS! Please check network and call emergency services directly."
            }

            val notif = NotificationCompat.Builder(context, CrashAlertManager.CHANNEL_ID_CRASH)
                .setSmallIcon(R.drawable.ic_qs_bike)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            nm.notify(1003, notif)
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(context: Context, subscriptionId: Int): SmsManager {
        return if (subscriptionId != -1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?.createForSubscriptionId(subscriptionId)
                    ?: SmsManager.getDefault()
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        } else {
            SmsManager.getDefault()
        }
    }
}
