package com.kalaiselvan.ride.logs

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

object RideSessionRecorder {
    private var appContext: Context? = null
    private var sessionStartTime: Long = 0L
    private var initialOdo: Double = 0.0
    private var peakSpeed: Float = 0f
    private var peakLeanLeft: Float = 0f
    private var peakLeanRight: Float = 0f
    private var totalSpeedSamples: Long = 0
    private var sumSpeedSamples: Double = 0.0

    private val activeBreadcrumbs = mutableListOf<RideBreadcrumb>()
    private var lastBreadcrumbTime = 0L

    var isRecording: Boolean = false
        private set

    private val _completedSessions = MutableStateFlow<List<RideSession>>(emptyList())
    val completedSessions: StateFlow<List<RideSession>> = _completedSessions.asStateFlow()

    private const val FILE_NAME = "ride_sessions.json"

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        loadPersistedSessions()
    }

    fun reloadPersistedSessions(context: Context? = null) {
        if (context != null && appContext == null) {
            appContext = context.applicationContext
        }
        loadPersistedSessions()
    }

    fun loadPersistedSessions() {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, FILE_NAME)
            val atomicFile = androidx.core.util.AtomicFile(file)
            if (file.exists()) {
                val jsonStr = atomicFile.openRead().use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                val array = JSONArray(jsonStr)
                val list = mutableListOf<RideSession>()
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let {
                        list.add(RideSession.fromJsonObject(it))
                    }
                }
                _completedSessions.value = list
            } else {
                _completedSessions.value = emptyList()
            }
        } catch (e: Exception) {
            LogStore.logEvent("Failed to load persisted ride sessions: ${e.message}")
        }
    }

    private fun persistSessions() {
        val ctx = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(ctx.filesDir, FILE_NAME)
            val atomicFile = androidx.core.util.AtomicFile(file)
            var stream: java.io.FileOutputStream? = null
            try {
                val array = JSONArray()
                _completedSessions.value.forEach {
                    array.put(it.toJsonObject())
                }
                val bytes = array.toString().toByteArray(Charsets.UTF_8)
                stream = atomicFile.startWrite()
                stream.write(bytes)
                atomicFile.finishWrite(stream)
            } catch (e: Exception) {
                LogStore.logEvent("Failed to save ride sessions: ${e.message}")
                stream?.let { atomicFile.failWrite(it) }
            }
        }
    }

    fun startSession(currentOdo: Double) {
        sessionStartTime = System.currentTimeMillis()
        initialOdo = currentOdo
        peakSpeed = 0f
        peakLeanLeft = 0f
        peakLeanRight = 0f
        totalSpeedSamples = 0
        sumSpeedSamples = 0.0
        synchronized(activeBreadcrumbs) {
            activeBreadcrumbs.clear()
        }
        lastBreadcrumbTime = 0L
        isRecording = true
    }

    fun addBreadcrumb(lat: Double, lon: Double, speedKmh: Float, lean: Float) {
        if (!isRecording) return
        if (lat == 0.0 && lon == 0.0) return

        val now = System.currentTimeMillis()
        // Record at most once per 1.5 seconds to balance fidelity and memory
        if (now - lastBreadcrumbTime < 1500L && activeBreadcrumbs.isNotEmpty()) return

        lastBreadcrumbTime = now
        val bc = RideBreadcrumb(
            lat = lat,
            lon = lon,
            speedKmh = speedKmh,
            leanAngle = lean,
            timestamp = now
        )
        synchronized(activeBreadcrumbs) {
            activeBreadcrumbs.add(bc)
        }
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

    fun stopSession(
        finalOdo: Double,
        explicitDistKm: Double? = null,
        maxGForce: Float = 0f,
        maxWheelie: Float = 0f,
        maxStoppie: Float = 0f,
        avgFuel: Float = 17.5f
    ): RideSession? {
        if (!isRecording) return null
        isRecording = false
        val now = System.currentTimeMillis()
        val durationSec = ((now - sessionStartTime) / 1000).coerceAtLeast(1)
        val calculatedDist = if (finalOdo >= initialOdo && initialOdo > 0) finalOdo - initialOdo else 0.0
        val dist = if (explicitDistKm != null && explicitDistKm > 0) explicitDistKm else calculatedDist
        val avgSpeed = if (totalSpeedSamples > 0) (sumSpeedSamples / totalSpeedSamples).toFloat() else 0f

        val recordedBreadcrumbs = synchronized(activeBreadcrumbs) {
            val list = activeBreadcrumbs.toList()
            // Downsample to max 1,000 points per ride if ride was exceptionally long to prevent unbounded file growth
            if (list.size > 1000) {
                val step = list.size / 1000.0
                (0 until 1000).map { i -> list[(i * step).toInt().coerceAtMost(list.size - 1)] }
            } else {
                list
            }
        }

        val session = RideSession(
            startTime = sessionStartTime,
            endTime = now,
            durationSeconds = durationSec,
            distanceKm = dist,
            topSpeedKmh = peakSpeed,
            avgSpeedKmh = avgSpeed,
            maxLeanLeft = peakLeanLeft,
            maxLeanRight = peakLeanRight,
            maxGForce = maxGForce,
            maxWheelie = maxWheelie,
            maxStoppie = maxStoppie,
            avgFuelMileage = avgFuel,
            breadcrumbs = recordedBreadcrumbs
        )

        val updated = _completedSessions.value.toMutableList()
        updated.add(0, session)
        // Cap retained ride session history to 200 sessions to bound storage
        if (updated.size > 200) {
            _completedSessions.value = updated.take(200)
        } else {
            _completedSessions.value = updated
        }
        persistSessions()
        return session
    }

    fun deleteSession(sessionId: String) {
        val updated = _completedSessions.value.filter { it.id != sessionId }
        _completedSessions.value = updated
        persistSessions()
    }

    fun clearCompletedSessions() {
        _completedSessions.value = emptyList()
        val ctx = appContext
        if (ctx != null) {
            try {
                File(ctx.filesDir, FILE_NAME).delete()
            } catch (_: Exception) {}
        }
    }
}
