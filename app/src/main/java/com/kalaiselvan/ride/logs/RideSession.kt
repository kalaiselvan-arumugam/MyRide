package com.kalaiselvan.ride.logs

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RideBreadcrumb(
    val lat: Double,
    val lon: Double,
    val speedKmh: Float,
    val leanAngle: Float,
    val timestamp: Long
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("lat", lat)
        put("lon", lon)
        put("speed", speedKmh.toDouble())
        put("lean", leanAngle.toDouble())
        put("ts", timestamp)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): RideBreadcrumb {
            return RideBreadcrumb(
                lat = json.optDouble("lat", 0.0),
                lon = json.optDouble("lon", 0.0),
                speedKmh = json.optDouble("speed", 0.0).toFloat(),
                leanAngle = json.optDouble("lean", 0.0).toFloat(),
                timestamp = json.optLong("ts", 0L)
            )
        }
    }
}

data class RideSession(
    val id: String = System.currentTimeMillis().toString(),
    val title: String = "",
    val startTime: Long,
    val endTime: Long = System.currentTimeMillis(),
    val durationSeconds: Long,
    val distanceKm: Double,
    val topSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val maxLeanLeft: Float,
    val maxLeanRight: Float,
    val maxGForce: Float = 0f,
    val maxWheelie: Float = 0f,
    val maxStoppie: Float = 0f,
    val avgFuelMileage: Float = 17.5f,
    val breadcrumbs: List<RideBreadcrumb> = emptyList()
) {
    val displayTitle: String
        get() = if (title.isNotBlank()) title else formattedDate

    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(startTime))

    val formattedDuration: String
        get() {
            val hrs = durationSeconds / 3600
            val mins = (durationSeconds % 3600) / 60
            val secs = durationSeconds % 60
            return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m ${secs}s"
        }

    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("startTime", startTime)
        put("endTime", endTime)
        put("durationSeconds", durationSeconds)
        put("distanceKm", distanceKm)
        put("topSpeedKmh", topSpeedKmh.toDouble())
        put("avgSpeedKmh", avgSpeedKmh.toDouble())
        put("maxLeanLeft", maxLeanLeft.toDouble())
        put("maxLeanRight", maxLeanRight.toDouble())
        put("maxGForce", maxGForce.toDouble())
        put("maxWheelie", maxWheelie.toDouble())
        put("maxStoppie", maxStoppie.toDouble())
        put("avgFuelMileage", avgFuelMileage.toDouble())

        val bcArray = JSONArray()
        breadcrumbs.forEach { bcArray.put(it.toJsonObject()) }
        put("breadcrumbs", bcArray)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): RideSession {
            val bcList = mutableListOf<RideBreadcrumb>()
            val bcArray = json.optJSONArray("breadcrumbs")
            if (bcArray != null) {
                for (i in 0 until bcArray.length()) {
                    bcArray.optJSONObject(i)?.let { bcList.add(RideBreadcrumb.fromJsonObject(it)) }
                }
            }
            return RideSession(
                id = json.optString("id", System.currentTimeMillis().toString()),
                title = json.optString("title", "").take(15),
                startTime = json.optLong("startTime", System.currentTimeMillis()),
                endTime = json.optLong("endTime", System.currentTimeMillis()),
                durationSeconds = json.optLong("durationSeconds", 0L),
                distanceKm = json.optDouble("distanceKm", 0.0),
                topSpeedKmh = json.optDouble("topSpeedKmh", 0.0).toFloat(),
                avgSpeedKmh = json.optDouble("avgSpeedKmh", 0.0).toFloat(),
                maxLeanLeft = json.optDouble("maxLeanLeft", 0.0).toFloat(),
                maxLeanRight = json.optDouble("maxLeanRight", 0.0).toFloat(),
                maxGForce = json.optDouble("maxGForce", 0.0).toFloat(),
                maxWheelie = json.optDouble("maxWheelie", 0.0).toFloat(),
                maxStoppie = json.optDouble("maxStoppie", 0.0).toFloat(),
                avgFuelMileage = json.optDouble("avgFuelMileage", 17.5).toFloat(),
                breadcrumbs = bcList
            )
        }
    }
}
