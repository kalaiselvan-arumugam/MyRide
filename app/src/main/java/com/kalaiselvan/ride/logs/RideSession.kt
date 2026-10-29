package com.kalaiselvan.ride.logs

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RideSession(
    val id: String = System.currentTimeMillis().toString(),
    val startTime: Long,
    val endTime: Long = System.currentTimeMillis(),
    val durationSeconds: Long,
    val distanceKm: Double,
    val topSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val maxLeanLeft: Float,
    val maxLeanRight: Float,
    val avgFuelMileage: Float
) {
    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(startTime))

    val formattedDuration: String
        get() {
            val hrs = durationSeconds / 3600
            val mins = (durationSeconds % 3600) / 60
            val secs = durationSeconds % 60
            return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m ${secs}s"
        }
}
