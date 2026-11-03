package com.kalaiselvan.ride.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HexFrameEntry(
    val timestamp: String,
    val direction: String, // "RX" or "TX"
    val frameId: String,
    val hex: String
)

object LogStore {
    private const val MAX_LOGS = 250
    private const val MAX_FRAMES = 500

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    private val _hexFrames = MutableStateFlow<List<HexFrameEntry>>(emptyList())
    val hexFrames: StateFlow<List<HexFrameEntry>> = _hexFrames.asStateFlow()

    private val _dtcCodes = MutableStateFlow<List<String>>(emptyList())
    val dtcCodes: StateFlow<List<String>> = _dtcCodes.asStateFlow()

    @Synchronized
    fun logEvent(message: String) {
        val time = timeFormat.format(Date())
        val entry = "[$time] $message"
        android.util.Log.d("MyRide", entry)
        val current = _systemLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > MAX_LOGS) {
            _systemLogs.value = current.take(MAX_LOGS)
        } else {
            _systemLogs.value = current
        }
    }

    @Synchronized
    fun logFrame(direction: String, frameId: Int, hexString: String) {
        val time = timeFormat.format(Date())
        val frameStr = "%02X".format(frameId)
        android.util.Log.d("MyRideFrame", "[$time] $direction 0x$frameStr: $hexString")
        val entry = HexFrameEntry(
            timestamp = time,
            direction = direction,
            frameId = frameStr,
            hex = hexString
        )
        val current = _hexFrames.value.toMutableList()
        current.add(0, entry)
        if (current.size > MAX_FRAMES) {
            _hexFrames.value = current.take(MAX_FRAMES)
        } else {
            _hexFrames.value = current
        }
    }

    @Synchronized
    fun setDtcCodes(codes: List<String>) {
        _dtcCodes.value = codes
    }

    fun clearHexFrames() {
        _hexFrames.value = emptyList()
    }

    fun clearSystemLogs() {
        _systemLogs.value = emptyList()
    }

    fun getFormattedExportText(): String {
        val sb = StringBuilder()
        sb.append("=== MyRide Diagnostic & Hex Stream Export ===\n")
        sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

        sb.append("--- Active DTC Trouble Codes ---\n")
        val codes = _dtcCodes.value
        if (codes.isEmpty()) {
            sb.append("None (ECU healthy)\n\n")
        } else {
            sb.append("DTCs: ${codes.joinToString(", ")}\n\n")
        }

        sb.append("--- Recent System Events ---\n")
        _systemLogs.value.forEach { sb.append(it).append("\n") }
        sb.append("\n")

        sb.append("--- Live Hex Frame Buffer ---\n")
        _hexFrames.value.forEach { f ->
            sb.append("[${f.timestamp}] ${f.direction} ${f.frameId}: ${f.hex}\n")
        }

        return sb.toString()
    }
}
