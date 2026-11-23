package com.kalaiselvan.ride.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import com.kalaiselvan.ride.preferences.CrashSensitivity
import kotlin.math.sqrt

class CrashDetector(
    private val context: Context,
    private val sensitivity: CrashSensitivity = CrashSensitivity.MEDIUM,
    private val onCrashDetected: (lat: Double, lon: Double) -> Unit
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private val handler = Handler(Looper.getMainLooper())

    // Thresholds per sensitivity
    private val accelThresholdG: Float get() = when (sensitivity) {
        CrashSensitivity.LOW    -> 7.0f
        CrashSensitivity.MEDIUM -> 5.5f
        CrashSensitivity.HIGH   -> 4.0f
    }
    private val gyroThresholdRad: Float get() = when (sensitivity) {
        CrashSensitivity.LOW    -> 7.0f   // ~400°/s
        CrashSensitivity.MEDIUM -> 5.5f   // ~315°/s
        CrashSensitivity.HIGH   -> 4.0f   // ~230°/s
    }

    private var highImpactDetectedAt: Long = 0L
    private var isMonitoring = false
    private var postImpactCheckRunnable: Runnable? = null

    // Latest gyro magnitude
    private var latestGyroMag = 0f

    // Bike speed from BLE telemetry (set externally)
    var currentBikeSpeedKmh: Float = 0f

    // Was speed > threshold before impact?
    private var hadSpeedBeforeImpact = false

    fun start() {
        if (isMonitoring) return
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            gyroscope?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            isMonitoring = true
        } catch (e: Exception) {
            com.kalaiselvan.ride.logs.LogStore.logEvent("CrashDetector: Failed to register sensor listeners: ${e.message}")
            isMonitoring = false
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        postImpactCheckRunnable?.let { handler.removeCallbacks(it) }
        isMonitoring = false
        highImpactDetectedAt = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                latestGyroMag = sqrt(gx * gx + gy * gy + gz * gz)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val resultantMs2 = sqrt(ax * ax + ay * ay + az * az)
                val resultantG = resultantMs2 / SensorManager.GRAVITY_EARTH

                // High impact detected
                if (resultantG >= accelThresholdG && latestGyroMag >= gyroThresholdRad) {
                    val now = System.currentTimeMillis()
                    if (highImpactDetectedAt == 0L) {
                        highImpactDetectedAt = now
                        hadSpeedBeforeImpact = currentBikeSpeedKmh > 10f
                        schedulePostImpactCheck()
                    }
                }
            }
        }
    }

    private fun schedulePostImpactCheck() {
        postImpactCheckRunnable?.let { handler.removeCallbacks(it) }
        postImpactCheckRunnable = Runnable {
            // After 3 seconds, if speed is still 0 (or BLE gave us data), trigger
            val speedDropped = currentBikeSpeedKmh < 2f
            if (hadSpeedBeforeImpact && speedDropped) {
                fetchLocationAndTrigger()
            } else if (!hadSpeedBeforeImpact) {
                // No BLE speed data — trust the sensor impact alone with high threshold
                if (sensitivity == CrashSensitivity.HIGH) {
                    fetchLocationAndTrigger()
                }
            }
            highImpactDetectedAt = 0L
        }
        handler.postDelayed(postImpactCheckRunnable!!, 3_000L)
    }

    fun triggerFromBikeImu() {
        // Called externally when bike IMU accelG exceeds threshold
        hadSpeedBeforeImpact = currentBikeSpeedKmh > 10f
        if (highImpactDetectedAt == 0L) {
            highImpactDetectedAt = System.currentTimeMillis()
            schedulePostImpactCheck()
        }
    }

    private fun fetchLocationAndTrigger() {
        var lat = 0.0
        var lon = 0.0
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        handler.post { onCrashDetected(lat, lon) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
