package com.kalaiselvan.ride.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RideSessionRecorder {
    private var sessionStartTime: Long = 0L
    private var initialOdo: Double = 0.0
    private var peakSpeed: Float = 0f
    private var peakLeanLeft: Float = 0f
    private var peakLeanRight: Float = 0f
    private var totalSpeedSamples: Long = 0
    private var sumSpeedSamples: Double = 0.0

    var isRecording: Boolean = false
        private set

    private val _completedSessions = MutableStateFlow<List<RideSession>>(emptyList())
    val completedSessions: StateFlow<List<RideSession>> = _completedSessions.asStateFlow()

    fun startSession(currentOdo: Double) {
        sessionStartTime = System.currentTimeMillis()
        initialOdo = currentOdo
        peakSpeed = 0f
        peakLeanLeft = 0f
        peakLeanRight = 0f
        totalSpeedSamples = 0
        sumSpeedSamples = 0.0
        isRecording = true
    }

    fun updateTelemetry(speedKmh: Float, leanL: Float, leanR: Float) {
        if (!isRecording) return
        if (speedKmh > peakSpeed) peakSpeed = speedKmh
        if (leanL > peakLeanLeft) peakLeanLeft = leanL
        if (leanR > peakLeanRight) peakLeanRight = leanR
        if (speedKmh > 0) {
            totalSpeedSamples++
            sumSpeedSamples += speedKmh
        }
    }

    fun stopSession(finalOdo: Double, explicitDistKm: Double? = null, avgFuel: Float = 17.5f): RideSession? {
        if (!isRecording) return null
        isRecording = false
        val now = System.currentTimeMillis()
        val durationSec = ((now - sessionStartTime) / 1000).coerceAtLeast(1)
        val calculatedDist = if (finalOdo >= initialOdo && initialOdo > 0) finalOdo - initialOdo else 0.0
        val dist = if (explicitDistKm != null && explicitDistKm > 0) explicitDistKm else calculatedDist
        val avgSpeed = if (totalSpeedSamples > 0) (sumSpeedSamples / totalSpeedSamples).toFloat() else 0f

        val session = RideSession(
            startTime = sessionStartTime,
            endTime = now,
            durationSeconds = durationSec,
            distanceKm = dist,
            topSpeedKmh = peakSpeed,
            avgSpeedKmh = avgSpeed,
            maxLeanLeft = peakLeanLeft,
            maxLeanRight = peakLeanRight,
            avgFuelMileage = avgFuel
        )

        val updated = _completedSessions.value.toMutableList()
        updated.add(0, session)
        _completedSessions.value = updated
        return session
    }

    fun clearCompletedSessions() {
        _completedSessions.value = emptyList()
    }
}
