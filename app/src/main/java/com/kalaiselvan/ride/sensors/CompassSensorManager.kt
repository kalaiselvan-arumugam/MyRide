package com.kalaiselvan.ride.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import com.kalaiselvan.ride.logs.LogStore
import kotlin.math.abs

/**
 * High-precision phone hardware compass manager.
 * Uses Sensor.TYPE_ROTATION_VECTOR / Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR for
 * gyroscopic-stabilized magnetic heading, with automatic fallback to Magnetometer +
 * Accelerometer, display rotation remapping (landscape/portrait), and tilt compensation
 * for motorcycle handlebar mounts.
 */
class CompassSensorManager(
    private val context: Context,
    private val onHeadingChanged: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var geoRotationVectorSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null

    private val rotationMatrix = FloatArray(9)
    private val displayMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val lastAccel = FloatArray(3)
    private val lastMag = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false
    private var lastRotationVectorTime = 0L

    private var currentHeading: Float = 0f
    private var hasInitialHeading = false
    private var isListening = false
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    init {
        rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        geoRotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    fun start() {
        if (isListening || sensorManager == null) return
        isListening = true
        hasInitialHeading = false

        var registeredAny = false

        // 1. Try Gyro/Fused Rotation Vector
        val rotSensor = rotationVectorSensor ?: geoRotationVectorSensor
        if (rotSensor != null) {
            val ok = sensorManager.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_UI)
            if (ok) {
                registeredAny = true
                LogStore.logEvent("Compass: Registered ${rotSensor.name}")
            }
        }

        // 2. Register Accelerometer + Magnetometer as continuous reliable hardware fallback
        val acc = accelSensor
        val mag = magSensor
        if (acc != null && mag != null) {
            val accOk = sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
            val magOk = sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
            if (accOk && magOk) {
                registeredAny = true
                LogStore.logEvent("Compass: Registered Accel (${acc.name}) + Mag (${mag.name}) fallback")
            }
        }

        if (!registeredAny) {
            LogStore.logEvent("Compass: No orientation sensors could be registered on this device")
        }
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        isListening = false
        sensorManager.unregisterListener(this)
        hasAccel = false
        hasMag = false
        lastRotationVectorTime = 0L
        LogStore.logEvent("Compass: Unregistered sensor listeners")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                lastRotationVectorTime = SystemClock.elapsedRealtime()
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                computeHeading(rotationMatrix)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccel, 0, 3)
                hasAccel = true
                processFallbackIfActive()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMag, 0, 3)
                hasMag = true
                processFallbackIfActive()
            }
        }
    }

    private fun processFallbackIfActive() {
        // Only use Accel + Mag fallback if Rotation Vector is not present or has stopped streaming (> 600ms)
        val now = SystemClock.elapsedRealtime()
        if (lastRotationVectorTime != 0L && (now - lastRotationVectorTime) < 600L) {
            return
        }

        if (hasAccel && hasMag) {
            if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)) {
                computeHeading(rotationMatrix)
            }
        }
    }

    private fun computeHeading(matrix: FloatArray) {
        val displayRotation = try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.let { it.rotation } ?: wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } catch (_: Exception) {
            Surface.ROTATION_0
        }

        // 1. Remap for screen display orientation (Portrait, Landscape-Left, Landscape-Right, Reverse Portrait)
        var axisX = SensorManager.AXIS_X
        var axisY = SensorManager.AXIS_Y
        when (displayRotation) {
            Surface.ROTATION_90 -> {
                axisX = SensorManager.AXIS_Y
                axisY = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_180 -> {
                axisX = SensorManager.AXIS_MINUS_X
                axisY = SensorManager.AXIS_MINUS_Y
            }
            Surface.ROTATION_270 -> {
                axisX = SensorManager.AXIS_MINUS_Y
                axisY = SensorManager.AXIS_X
            }
        }

        val displayRemapOk = SensorManager.remapCoordinateSystem(matrix, axisX, axisY, displayMatrix)
        val finalMatrix = if (displayRemapOk) displayMatrix else matrix

        SensorManager.getOrientation(finalMatrix, orientation)
        val azimuthRad = orientation[0]
        var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
        if (azimuthDeg < 0) azimuthDeg += 360f

        if (azimuthDeg.isNaN()) return

        // Smooth heading with shortest-distance low-pass filter
        if (!hasInitialHeading) {
            currentHeading = azimuthDeg
            hasInitialHeading = true
        } else {
            val diff = ((azimuthDeg - currentHeading + 540f) % 360f) - 180f
            currentHeading = (currentHeading + diff * 0.25f + 360f) % 360f
        }

        onHeadingChanged(currentHeading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorAccuracy = accuracy
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            LogStore.logEvent("Compass: Sensor accuracy reported UNRELIABLE for ${sensor?.name} (calibration needed)")
        }
    }
}
