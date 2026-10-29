package com.kalaiselvan.ride.telemetry

import androidx.compose.ui.graphics.Color
import com.kalaiselvan.ride.preferences.SpeedUnit
import com.kalaiselvan.ride.theme.RpmGreen
import com.kalaiselvan.ride.theme.RpmOrange
import com.kalaiselvan.ride.theme.RpmRed
import com.kalaiselvan.ride.theme.RpmYellow

enum class CruiseState { OFF, ON, ACTIVE }

/**
 * Baseline telemetry model restoring motorcycle cluster baseline values.
 * Real BLE packets from the ECU update these values live in real-time.
 */
data class TelemetryState(
    val rpm: Float = 0f,
    val targetRpm: Float = 0f,
    val speed: Float = 0f, // Always stored natively in km/h from ECU
    val targetSpeed: Float = 0f,
    val gear: Int? = null, // null = No data received / disconnected, 0 = N, 1..6
    val isShifting: Boolean = false,
    val throttle: Float = 0f, // 0..100%
    val gForce: Float = 0.0f,
    val connectedDeviceName: String? = null,
    val cruiseState: CruiseState = CruiseState.OFF,
    val cruiseSetSpeed: Int = 0,
    val ktrcActive: Boolean = false,
    val ktrcLevel: String = "1",
    val kqsActive: Boolean = false,
    val kqsStatus: String = "OFF",
    val ridingMode: String = "", // empty = No data received / disconnected, SPORT, ROAD, RAIN, RIDER
    val powerMode: String = "FULL",
    val currentOdo: Double = 0.0,
    val tripA: Float = 0.0f,
    val tripB: Float = 0.0f,
    val leanLeft: Float = 0f,
    val leanRight: Float = 0f,
    val currentLean: Float = 0f,
    val leanDirection: String = "CENTER", // "LEFT", "RIGHT", "CENTER"
    val coolantTemp: Int = 0,
    val fuelPercent: Int = 0, // 0..100%
    val airTemp: Int = 0,
    val batteryVolt: Float = 0.0f,
    val rangeKm: Int = 0,
    val avgMil: Float = 0.0f,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    // Session stats: start at 00:00 / 0.0 until ride begins
    val sessionTimeMs: Long = 0L,
    val sessionDistanceKm: Double = 0.0,
    val sessionMaxLeanL: Float = 0f,
    val sessionMaxLeanR: Float = 0f,
    val sessionMaxWheelie: Float = 0f,
    val sessionMaxStoppie: Float = 0f,
    val sessionMaxG: Float = 0.0f,
    // Bluetooth connection status
    val isBtConnected: Boolean = false,
    val isBtConnecting: Boolean = false,
    // Phone built-in compass heading (0..360°)
    val headingDegrees: Float = 0f,
    // Parked bike location memory ("Walk to the bike")
    val parkedLat: Double = 0.0,
    val parkedLon: Double = 0.0,
    val parkedAccuracy: Float = 0f,
    val parkedTimeMs: Long = 0L,
    val parkedTimeFormatted: String = "",
    // Bike Model identification (from BLE Frame 0x03 or BLE advertised device name)
    val bikeModelName: String? = null,
    val bikeVin: String? = null,
    // Shift-Up Indicator Lamp threshold (read from TFT Frame 0x1B, 0 = not read yet)
    val shiftLampRpm: Int = 0,
    // Turn-by-Turn Navigation Live Status
    val navInstruction: String = "",
    val navDistanceMeters: Int = 0,
    val navIconId: Int = 0,
    val isNavActive: Boolean = false
) {
    /**
     * Authentic TFT Shift-Up Indicator trigger.
     * Fires when engine RPM meets or exceeds the bike's shift lamp threshold.
     */
    val isShiftIndicatorActive: Boolean
        get() = shiftLampRpm > 0 && (displayRpm >= shiftLampRpm || targetRpm.toInt() >= shiftLampRpm || rpm.toInt() >= shiftLampRpm)

    /**
     * Calculated Display Speed respecting the KM/H vs MPH user setting.
     * Formula: mph = km/h * 0.621371
     */
    val displaySpeed: Int
        get() = when (speedUnit) {
            SpeedUnit.KMH -> speed.toInt().coerceAtLeast(0)
            SpeedUnit.MPH -> (speed * 0.621371f).toInt().coerceAtLeast(0)
        }

    val displayRpm: Int
        get() = rpm.toInt().coerceAtLeast(0)

    val speedUnitLabel: String
        get() = if (speedUnit == SpeedUnit.MPH) "MPH" else "KM/H"

    val displayTripA: Float
        get() = when (speedUnit) {
            SpeedUnit.KMH -> tripA
            SpeedUnit.MPH -> tripA * 0.621371f
        }

    val displayTripB: Float
        get() = when (speedUnit) {
            SpeedUnit.KMH -> tripB
            SpeedUnit.MPH -> tripB * 0.621371f
        }

    val displayOdo: Int
        get() = when (speedUnit) {
            SpeedUnit.KMH -> currentOdo.toInt().coerceAtLeast(0)
            SpeedUnit.MPH -> (currentOdo * 0.621371).toInt().coerceAtLeast(0)
        }

    val displayRange: Int
        get() = when (speedUnit) {
            SpeedUnit.KMH -> rangeKm
            SpeedUnit.MPH -> (rangeKm * 0.621371f).toInt().coerceAtLeast(0)
        }

    val displayAvgMil: Float
        get() = when (speedUnit) {
            SpeedUnit.KMH -> avgMil
            SpeedUnit.MPH -> avgMil * 2.35215f
        }

    val rpmRatio: Float
        get() = (rpm / 12000f).coerceIn(0f, 1f)

    val leanColor: Color
        get() = when {
            currentLean > 45f -> RpmRed
            currentLean > 35f -> RpmYellow
            currentLean > 20f -> RpmOrange
            else -> RpmGreen
        }
}
