package com.kalaiselvan.ride.utils

/**
 * Standardized unit conversion constants and helpers across MyRide.
 */
object UnitConversions {
    const val KM_TO_MILES = 0.621371
    const val KM_TO_MILES_FLOAT = 0.621371f

    fun kmToMiles(km: Double): Double = km * KM_TO_MILES
    fun kmToMiles(km: Float): Float = km * KM_TO_MILES_FLOAT

    fun kmhToMph(kmh: Float): Float = kmh * KM_TO_MILES_FLOAT
    fun kmhToMph(kmh: Double): Double = kmh * KM_TO_MILES

    fun formatDistanceMeters(distanceMeters: Int): String {
        return if (distanceMeters >= 1000) {
            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000.0)
        } else {
            "${distanceMeters} m"
        }
    }

    fun formatDistanceValueAndUnit(distanceMeters: Int): Pair<String, String> {
        val value = when {
            distanceMeters >= 1000 -> String.format(java.util.Locale.US, "%.1f", distanceMeters / 1000.0)
            distanceMeters >= 10 -> "${(distanceMeters / 10) * 10}"
            else -> "$distanceMeters"
        }
        val unit = if (distanceMeters >= 1000) "km" else "m"
        return Pair(value, unit)
    }
}
