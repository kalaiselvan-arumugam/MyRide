package com.kalaiselvan.ride.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * High-precision phone hardware compass manager.
 * Uses Sensor.TYPE_ROTATION_VECTOR for gyroscopic-stabilized magnetic heading,
 * with automatic fallback to Magnetometer + Accelerometer and tilt compensation
 * for motorcycle handlebar mounts.
 */
class CompassSensorManager(
    private val context: Context,
    private val onHeadingChanged: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val lastAccel = FloatArray(3)
    private val lastMag = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private var currentHeading: Float = 0f
    private var isListening = false

    init {
        rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor == null) {
            accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    fun start() {
        if (isListening || sensorManager == null) return
        isListening = true

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        isListening = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                computeHeading(rotationMatrix)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccel, 0, 3)
                hasAccel = true
                if (hasMag) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)) {
                        computeHeading(rotationMatrix)
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMag, 0, 3)
                hasMag = true
                if (hasAccel) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)) {
                        computeHeading(rotationMatrix)
                    }
                }
            }
        }
    }

    private fun computeHeading(matrix: FloatArray) {
        // Remap coordinates to handle phone mounted upright on bike handlebar (pitch around vertical)
        val pitch = matrix[7] // sin(pitch)
        val targetMatrix = if (abs(pitch) > 0.7f) {
            // Phone mounted nearly vertical on handlebars
            SensorManager.remapCoordinateSystem(
                matrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            remappedMatrix
        } else {
            matrix
        }

        SensorManager.getOrientation(targetMatrix, orientation)
        var azimuthRad = orientation[0]
        var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
        if (azimuthDeg < 0) azimuthDeg += 360f

        // Smooth heading with shortest-distance low-pass filter
        val diff = ((azimuthDeg - currentHeading + 540f) % 360f) - 180f
        currentHeading = (currentHeading + diff * 0.25f + 360f) % 360f

        onHeadingChanged(currentHeading)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
