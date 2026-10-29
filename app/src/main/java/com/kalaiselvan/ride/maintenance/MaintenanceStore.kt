package com.kalaiselvan.ride.maintenance

import android.content.Context
import com.kalaiselvan.ride.logs.LogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class MaintenanceStatus(
    val hasActiveReminder: Boolean,
    val isOverdue: Boolean,
    val isWarning: Boolean,
    val remainingKm: Double?,
    val remainingDays: Int?,
    val targetOdoKm: Double?,
    val targetDateFormatted: String?,
    val statusText: String
)

class MaintenanceStore private constructor(private val context: Context) {

    private val file = File(context.filesDir, "maintenance_records.json")
    private val receiptsDir = File(context.filesDir, "receipts").apply { mkdirs() }

    private val _records = MutableStateFlow<List<ServiceRecord>>(emptyList())
    val records: StateFlow<List<ServiceRecord>> = _records.asStateFlow()

    init {
        loadRecords()
    }

    private fun loadRecords() {
        if (!file.exists()) {
            _records.value = emptyList()
            return
        }
        try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ServiceRecord>()
            for (i in 0 until array.length()) {
                list.add(ServiceRecord.fromJsonObject(array.getJSONObject(i)))
            }
            list.sortByDescending { it.serviceDateMs }
            _records.value = list
        } catch (e: Exception) {
            LogStore.logEvent("Failed to load maintenance records: ${e.message}")
            _records.value = emptyList()
        }
    }

    private fun saveRecords() {
        try {
            val array = JSONArray()
            _records.value.forEach { array.put(it.toJsonObject()) }
            file.writeText(array.toString(2))
        } catch (e: Exception) {
            LogStore.logEvent("Failed to save maintenance records: ${e.message}")
        }
    }

    fun addRecord(record: ServiceRecord) {
        val updated = (_records.value.filter { it.id != record.id } + record).sortedByDescending { it.serviceDateMs }
        _records.value = updated
        saveRecords()
        LogStore.logEvent("Maintenance: Added service record for ${record.odoKm} KM")
        MaintenanceReminderNotifier.scheduleNextDateAlarm(context)
    }

    fun updateRecord(record: ServiceRecord) {
        val updated = _records.value.map { if (it.id == record.id) record else it }.sortedByDescending { it.serviceDateMs }
        _records.value = updated
        saveRecords()
        LogStore.logEvent("Maintenance: Updated service record ${record.id}")
        MaintenanceReminderNotifier.scheduleNextDateAlarm(context)
    }

    fun deleteRecord(id: String) {
        val rec = _records.value.firstOrNull { it.id == id }
        rec?.receiptImagePath?.let { path ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        _records.value = _records.value.filter { it.id != id }
        saveRecords()
        LogStore.logEvent("Maintenance: Deleted service record $id")
        MaintenanceReminderNotifier.scheduleNextDateAlarm(context)
    }

    fun saveReceiptImage(inputStream: InputStream, extension: String = "jpg"): String {
        val fileName = "receipt_${System.currentTimeMillis()}.$extension"
        val destFile = File(receiptsDir, fileName)
        FileOutputStream(destFile).use { out ->
            inputStream.copyTo(out)
        }
        return destFile.absolutePath
    }

    fun createReceiptTempFile(): File {
        return File(receiptsDir, "camera_receipt_${System.currentTimeMillis()}.jpg")
    }

    fun getActiveReminder(): ServiceRecord? {
        return _records.value.firstOrNull {
            it.reminderEnabled && (it.nextServiceDateMs != null || it.nextServiceOdoKm != null)
        }
    }

    fun getStatus(currentOdo: Double): MaintenanceStatus {
        val active = getActiveReminder()
        if (active == null) {
            return MaintenanceStatus(
                hasActiveReminder = false,
                isOverdue = false,
                isWarning = false,
                remainingKm = null,
                remainingDays = null,
                targetOdoKm = null,
                targetDateFormatted = null,
                statusText = "No upcoming service scheduled"
            )
        }

        val targetOdo = active.nextServiceOdoKm
        val targetDate = active.nextServiceDateMs
        val targetDateStr = active.formattedNextServiceDate

        val remainingKm = if (targetOdo != null && targetOdo > 0 && currentOdo > 0) targetOdo - currentOdo else null
        val remainingDays = if (targetDate != null && targetDate > 0) {
            val diffMs = targetDate - System.currentTimeMillis()
            (diffMs / (1000L * 60 * 60 * 24)).toInt()
        } else null

        val isDistOverdue = remainingKm != null && remainingKm <= 0.0
        val isDateOverdue = remainingDays != null && remainingDays < 0
        val isOverdue = isDistOverdue || isDateOverdue

        val isDistWarning = remainingKm != null && remainingKm in 0.0..500.0
        val isDateWarning = remainingDays != null && remainingDays in 0..15
        val isWarning = isDistWarning || isDateWarning

        val statusText = when {
            isOverdue -> {
                if (isDistOverdue && isDateOverdue) "Service Overdue by ${String.format(java.util.Locale.US, "%.0f", -remainingKm!!)} KM & ${kotlin.math.abs(remainingDays!!)} Days"
                else if (isDistOverdue) "Service Overdue by ${String.format(java.util.Locale.US, "%.0f", -remainingKm!!)} KM"
                else "Service Overdue by ${kotlin.math.abs(remainingDays!!)} Days"
            }
            isWarning -> {
                if (isDistWarning && isDateWarning) "Due Soon in ${String.format(java.util.Locale.US, "%.0f", remainingKm!!)} KM or $remainingDays Days"
                else if (isDistWarning) "Due Soon in ${String.format(java.util.Locale.US, "%.0f", remainingKm!!)} KM"
                else "Due Soon in $remainingDays Days"
            }
            else -> {
                if (remainingKm != null && remainingDays != null) "Next Service in ${String.format(java.util.Locale.US, "%.0f", remainingKm)} KM or $remainingDays Days"
                else if (remainingKm != null) "Next Service in ${String.format(java.util.Locale.US, "%.0f", remainingKm)} KM"
                else if (remainingDays != null) "Next Service in $remainingDays Days"
                else "Service scheduled"
            }
        }

        return MaintenanceStatus(
            hasActiveReminder = true,
            isOverdue = isOverdue,
            isWarning = isWarning,
            remainingKm = remainingKm,
            remainingDays = remainingDays,
            targetOdoKm = targetOdo,
            targetDateFormatted = targetDateStr,
            statusText = statusText
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: MaintenanceStore? = null

        fun getInstance(context: Context): MaintenanceStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MaintenanceStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
