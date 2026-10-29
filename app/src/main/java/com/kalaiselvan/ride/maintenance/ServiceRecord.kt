package com.kalaiselvan.ride.maintenance

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Service Record data model.
 *
 * Captures historical maintenance logs, attached receipt images,
 * and sets future service reminder targets (by date or odometer reading).
 */
data class ServiceRecord(
    val id: String = UUID.randomUUID().toString(),
    val serviceDateMs: Long = System.currentTimeMillis(),
    val odoKm: Double = 0.0,
    val totalCost: Double = 0.0,
    val currency: String = "₹",
    val notes: String = "",
    val receiptImagePath: String? = null,
    // Reminder Target Configuration
    val nextServiceDateMs: Long? = null,
    val nextServiceOdoKm: Double? = null,
    val reminderEnabled: Boolean = true
) {
    val formattedServiceDate: String
        get() = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(serviceDateMs))

    val formattedNextServiceDate: String?
        get() = nextServiceDateMs?.let {
            SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(it))
        }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("serviceDateMs", serviceDateMs)
            put("odoKm", odoKm)
            put("totalCost", totalCost)
            put("currency", currency)
            put("notes", notes)
            put("receiptImagePath", receiptImagePath ?: "")
            put("nextServiceDateMs", nextServiceDateMs ?: -1L)
            put("nextServiceOdoKm", nextServiceOdoKm ?: -1.0)
            put("reminderEnabled", reminderEnabled)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): ServiceRecord {
            val nextDate = json.optLong("nextServiceDateMs", -1L)
            val nextOdo = json.optDouble("nextServiceOdoKm", -1.0)
            val receipt = json.optString("receiptImagePath", "")

            return ServiceRecord(
                id = json.optString("id", UUID.randomUUID().toString()),
                serviceDateMs = json.optLong("serviceDateMs", System.currentTimeMillis()),
                odoKm = json.optDouble("odoKm", 0.0),
                totalCost = json.optDouble("totalCost", 0.0),
                currency = json.optString("currency", "₹"),
                notes = json.optString("notes", ""),
                receiptImagePath = if (receipt.isNotBlank()) receipt else null,
                nextServiceDateMs = if (nextDate > 0) nextDate else null,
                nextServiceOdoKm = if (nextOdo > 0) nextOdo else null,
                reminderEnabled = json.optBoolean("reminderEnabled", true)
            )
        }
    }
}
