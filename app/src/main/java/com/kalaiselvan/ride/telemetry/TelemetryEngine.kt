package com.kalaiselvan.ride.telemetry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.bluetooth.BackgroundBleScanner
import com.kalaiselvan.ride.bluetooth.BluetoothScanEngine
import com.kalaiselvan.ride.bluetooth.ConnectionStatus
import com.kalaiselvan.ride.bluetooth.BikeForegroundService
import com.kalaiselvan.ride.bluetooth.BikeGattManager
import com.kalaiselvan.ride.bluetooth.RawTelemetryPacket
import com.kalaiselvan.ride.bluetooth.ScannerState
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.logs.RideSessionRecorder
import com.kalaiselvan.ride.maintenance.MaintenanceReminderNotifier
import com.kalaiselvan.ride.preferences.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

class TelemetryEngine private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        @Volatile
        private var INSTANCE: TelemetryEngine? = null

        fun getInstance(context: Context): TelemetryEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelemetryEngine(
                    context.applicationContext,
                    CoroutineScope(Dispatchers.Default + SupervisorJob())
                ).also { INSTANCE = it }
            }
        }
    }
    private val appPrefs = AppPreferences.getInstance(context)

    private val _state = MutableStateFlow(
        TelemetryState(
            speedUnit = appPrefs.userPreferences.value.speedUnit,
            currentOdo = appPrefs.userPreferences.value.lastOdo,
            tripA = appPrefs.userPreferences.value.lastTripA,
            tripB = appPrefs.userPreferences.value.lastTripB,
            parkedLat = appPrefs.userPreferences.value.parkedLat,
            parkedLon = appPrefs.userPreferences.value.parkedLon,
            parkedAccuracy = appPrefs.userPreferences.value.parkedAccuracy,
            parkedTimeMs = appPrefs.userPreferences.value.parkedTimeMs,
            parkedTimeFormatted = appPrefs.userPreferences.value.parkedTimeFormatted,
            bikeModelName = appPrefs.userPreferences.value.bikeModelName.ifBlank { null },
            bikeVin = appPrefs.userPreferences.value.bikeVin.ifBlank { null },
            ktrcLevel = appPrefs.userPreferences.value.ktrcLevel,
            shiftLampRpm = appPrefs.userPreferences.value.shiftLampRpm
        )
    )
    val state: StateFlow<TelemetryState> = _state.asStateFlow()

    private var renderInterpolationJob: Job? = null

    // Native BLE Engine components
    private var gattManager: BikeGattManager? = null
    private var scanEngine: BluetoothScanEngine? = null
    private var compassManager: com.kalaiselvan.ride.sensors.CompassSensorManager? = null

    // Active Ride Session metrics
    private var sessionActive = false
    private var sessionStartTime = 0L
    private var sessionStartOdo = 0.0
    private var sessionDistanceAccumulated = 0.0
    private var sessionLastSpeedTime = 0L
    private var sessionMaxLeanLeft = 0f
    private var sessionMaxLeanRight = 0f
    private var sessionMaxWheelieAngle = 0f
    private var sessionMaxStoppieAngle = 0f
    private var sessionMaxGForce = 0f
    private var sessionTickerJob: Job? = null

    // 0-100 km/h (and 0-60 mph) Sprint Tracking
    private data class SpeedSample(val timeMs: Long, val speedKmh: Float)
    private val speedHistory = ArrayDeque<SpeedSample>()
    private var lastSpeedSample: Float = 0f
    private var sprintArmed = false
    private var isSprintRunning = false
    private var sprintStartTime: Long = 0L

    // Real-time GPS ride tracking for walk-to-bike parked location
    private var locationListener: LocationListener? = null
    private var lastKnownRideLocation: Location? = null

    private var lastSyncSavedTime: Long = 0L

    init {
        // Observe preferences for speed unit, shift lamp, and parked changes
        scope.launch {
            appPrefs.userPreferences.collect { prefs ->
                _state.value = _state.value.copy(
                    speedUnit = prefs.speedUnit,
                    shiftLampRpm = prefs.shiftLampRpm,
                    parkedLat = prefs.parkedLat,
                    parkedLon = prefs.parkedLon,
                    parkedAccuracy = prefs.parkedAccuracy,
                    parkedTimeMs = prefs.parkedTimeMs,
                    parkedTimeFormatted = prefs.parkedTimeFormatted
                )
            }
        }
    }

    fun startLocationTracking() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            LogStore.logEvent("Location permission not granted for ride tracking")
            return
        }

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            if (locationListener != null) return // Already active

            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lastKnownRideLocation = loc
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationListener = listener

            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(
                        provider,
                        2000L,
                        3.0f,
                        listener,
                        android.os.Looper.getMainLooper()
                    )
                }
            }
            LogStore.logEvent("GPS ride location tracking active")
        } catch (e: Exception) {
            LogStore.logEvent("Failed to start GPS tracking: ${e.message}")
        }
    }

    fun stopLocationTracking() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationListener?.let { lm?.removeUpdates(it) }
            locationListener = null
        } catch (_: Exception) {}
    }

    fun start() {
        startRenderLoop()
        startLocationTracking()

        // Load saved parked bike location from preferences
        val initialPrefs = appPrefs.userPreferences.value
        _state.value = _state.value.copy(
            parkedLat = initialPrefs.parkedLat,
            parkedLon = initialPrefs.parkedLon,
            parkedAccuracy = initialPrefs.parkedAccuracy,
            parkedTimeFormatted = initialPrefs.parkedTimeFormatted,
            shiftLampRpm = initialPrefs.shiftLampRpm
        )

        // Initialize BLE components
        if (gattManager == null) {
            gattManager = BikeGattManager(
                context = context,
                onConnectionChanged = { status ->
                    handleConnectionChange(status)
                },
                onTelemetryReceived = { packet ->
                    handleRawTelemetry(packet)
                }
            )
        }

        if (scanEngine == null) {
            scanEngine = BluetoothScanEngine(
                context = context,
                onBikeFound = { mac, name ->
                    val cleanName = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(name)
                    LogStore.logEvent("Connecting to $cleanName ($mac)")
                    appPrefs.setBikeModelAndVin(cleanName, null)
                    appPrefs.setLastBikeMac(mac)
                    _state.value = _state.value.copy(
                        isBtConnecting = true,
                        connectedDeviceName = cleanName,
                        bikeModelName = cleanName
                    )
                    gattManager?.connect(mac)
                },
                onStateChanged = { scannerState ->
                    if (scannerState == ScannerState.FAILED) {
                        _state.value = _state.value.copy(isBtConnecting = false, isBtConnected = false)
                    }
                }
            )
        }

        // Initialize Phone Compass Sensor
        if (compassManager == null) {
            compassManager = com.kalaiselvan.ride.sensors.CompassSensorManager(context) { heading ->
                _state.value = _state.value.copy(headingDegrees = heading)
            }
        }
        compassManager?.start()

        // Auto-connect to bike if Bluetooth is enabled and not already connected
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        if (bm?.adapter?.isEnabled == true && !_state.value.isBtConnected && !_state.value.isBtConnecting) {
            LogStore.logEvent("Auto-connecting to bike...")
            connect()
        }
    }

    fun stopUi() {
        renderInterpolationJob?.cancel()
        compassManager?.stop()
        // Do NOT disconnect gattManager or stop scanEngine here so background navigation and alerts stay active!
    }

    fun stop() {
        stopSessionTracking()
        stopLocationTracking()
        renderInterpolationJob?.cancel()
        compassManager?.stop()
        scanEngine?.stop()
        gattManager?.disconnect()
    }

    fun connect(targetMac: String? = null, isAutoConnect: Boolean = true, isDirect: Boolean = false) {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val isEnabled = bm?.adapter?.isEnabled == true
        if (!isEnabled) {
            LogStore.logEvent("Cannot connect: Bluetooth is turned off")
            return
        }

        val bondedDevice = BackgroundBleScanner.getBondedBikeDevice(context)
        val macToConnect = targetMac?.takeIf { it.isNotBlank() }
            ?: appPrefs.userPreferences.value.lastBikeMac.takeIf { it.isNotBlank() }
            ?: bondedDevice?.address

        if (!macToConnect.isNullOrBlank()) {
            appPrefs.setLastBikeMac(macToConnect)
            val name = bondedDevice?.name ?: "Smart Bike"
            val cleanName = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(name)
            _state.value = _state.value.copy(
                connectedDeviceName = cleanName,
                bikeModelName = cleanName
            )
        }

        val effectiveAutoConnect = if (isDirect) false else isAutoConnect
        LogStore.logEvent("Connecting to bike (target MAC: $macToConnect, autoConnect=$effectiveAutoConnect, isDirect=$isDirect)...")
        _state.value = _state.value.copy(
            isBtConnecting = true,
            isBtConnected = false
        )
        startLocationTracking()
        try {
            BikeForegroundService.start(context)
        } catch (e: Exception) {
            LogStore.logEvent("BikeForegroundService.start background notice: ${e.message}")
        }

        if (!macToConnect.isNullOrBlank()) {
            if (gattManager == null) {
                gattManager = BikeGattManager(
                    context = context,
                    onConnectionChanged = { status -> handleConnectionChange(status) },
                    onTelemetryReceived = { packet -> handleRawTelemetry(packet) }
                )
            }
            gattManager?.connect(macToConnect, isAutoConnect = effectiveAutoConnect, isDirect = isDirect)
        } else {
            scanEngine?.start()
        }
    }

    fun onBluetoothDisabled() {
        LogStore.logEvent("TelemetryEngine: Bluetooth disabled. Resetting connection state cleanly.")
        if (_state.value.isBtConnected || lastKnownRideLocation != null) {
            recordParkedLocation()
        }
        stopSessionTracking()
        scanEngine?.stop()
        gattManager?.onBluetoothDisabled()
        BikeForegroundService.stop(context)
        _state.value = _state.value.copy(
            isBtConnected = false,
            isBtConnecting = false,
            cruiseState = CruiseState.OFF,
            cruiseSetSpeed = 0,
            gear = null,
            ridingMode = ""
        )
    }

    fun updateNavigationState(isActive: Boolean, instruction: String, distanceMeters: Int, iconId: Int) {
        _state.value = _state.value.copy(
            isNavActive = isActive,
            navInstruction = instruction,
            navDistanceMeters = distanceMeters,
            navIconId = iconId
        )
    }

    fun disconnect() {
        LogStore.logEvent("User tapped Disconnect Bluetooth")
        if (_state.value.isBtConnected || lastKnownRideLocation != null) {
            recordParkedLocation()
        }
        stopSessionTracking()
        scanEngine?.stop()
        gattManager?.disconnect()
        BikeForegroundService.stop(context)
        BackgroundBleScanner.arm(context)
        _state.value = _state.value.copy(
            isBtConnected = false,
            isBtConnecting = false,
            cruiseState = CruiseState.OFF,
            cruiseSetSpeed = 0,
            gear = null,
            ridingMode = ""
        )
    }

    fun toggleBluetooth() {
        val currentState = _state.value
        if (currentState.isBtConnected || currentState.isBtConnecting) {
            disconnect()
        } else {
            connect()
        }
    }

    private fun startSessionTracking() {
        sessionActive = true
        sessionStartTime = System.currentTimeMillis()
        sessionStartOdo = _state.value.currentOdo
        sessionDistanceAccumulated = 0.0
        sessionLastSpeedTime = System.currentTimeMillis()
        sessionMaxLeanLeft = 0f
        sessionMaxLeanRight = 0f
        sessionMaxWheelieAngle = 0f
        sessionMaxStoppieAngle = 0f
        sessionMaxGForce = 0f

        RideSessionRecorder.startSession(_state.value.currentOdo)

        sessionTickerJob?.cancel()
        sessionTickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                if (sessionActive) {
                    val now = System.currentTimeMillis()
                    val elapsedMs = now - sessionStartTime
                    val current = _state.value
                    val distKm = if (current.currentOdo > 0 && sessionStartOdo > 0 && current.currentOdo >= sessionStartOdo) {
                        current.currentOdo - sessionStartOdo
                    } else {
                        sessionDistanceAccumulated
                    }

                    _state.value = current.copy(
                        sessionTimeMs = elapsedMs,
                        sessionDistanceKm = distKm,
                        sessionMaxLeanL = sessionMaxLeanLeft,
                        sessionMaxLeanR = sessionMaxLeanRight,
                        sessionMaxWheelie = sessionMaxWheelieAngle,
                        sessionMaxStoppie = sessionMaxStoppieAngle,
                        sessionMaxG = sessionMaxGForce
                    )
                }
            }
        }
    }

    private fun stopSessionTracking() {
        if (!sessionActive) return
        sessionActive = false
        sessionTickerJob?.cancel()
        sessionTickerJob = null

        val current = _state.value
        val finalDist = if (current.currentOdo > 0 && sessionStartOdo > 0 && current.currentOdo >= sessionStartOdo) {
            current.currentOdo - sessionStartOdo
        } else {
            sessionDistanceAccumulated
        }
        val durationSec = ((System.currentTimeMillis() - sessionStartTime) / 1000L).coerceAtLeast(0L)
        RideSessionRecorder.stopSession(current.currentOdo, explicitDistKm = finalDist)
        if (finalDist > 0.05 || durationSec > 20L) {
            appPrefs.updateLongestRide(finalDist, durationSec)
        }
    }

    private fun handleConnectionChange(status: ConnectionStatus) {
        when (status) {
            ConnectionStatus.CONNECTED -> {
                _state.value = _state.value.copy(
                    isBtConnected = true,
                    isBtConnecting = false
                )
                startLocationTracking()
                BikeForegroundService.updateNotification(context, "Connected to Bike")
                startSessionTracking()
                BackgroundBleScanner.disarm(context)
                MaintenanceReminderNotifier.checkDistanceTriggerOnConnection(context, _state.value.currentOdo)
                MaintenanceReminderNotifier.checkDailyDateTrigger(context)
                com.kalaiselvan.ride.utils.AppIconManager.updateIcon(context, isConnected = true)
            }
            ConnectionStatus.CONNECTING -> {
                _state.value = _state.value.copy(
                    isBtConnecting = true,
                    isBtConnected = false
                )
                BikeForegroundService.updateNotification(context, "Searching for Bike...")
            }
            ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED -> {
                val wasConnected = _state.value.isBtConnected
                _state.value = _state.value.copy(
                    isBtConnected = false,
                    isBtConnecting = false,
                    cruiseState = CruiseState.OFF,
                    cruiseSetSpeed = 0,
                    gear = null,
                    ridingMode = ""
                )
                val isNavigating = com.kalaiselvan.ride.navigation.OlaNavEngine.isNavigating.value
                val statusMsg = if (isNavigating) "Navigating (Bike reconnecting...)" else "Waiting for bike ignition (Standby)..."
                BikeForegroundService.updateNotification(context, statusMsg)
                stopSessionTracking()
                if (wasConnected || lastKnownRideLocation != null) {
                    recordParkedLocation()
                }
                if (!isNavigating) {
                    stopLocationTracking()
                }
                BackgroundBleScanner.arm(context)
                com.kalaiselvan.ride.utils.AppIconManager.updateIcon(context, isConnected = false)
            }
        }
    }

    fun recordParkedLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            LogStore.logEvent("Location permission not granted: cannot record parked location")
            return
        }

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            
            // 1. Prioritize freshly tracked ride location from our active listener
            var bestLoc: Location? = lastKnownRideLocation

            // 2. Fall back to cached system providers if listener hasn't received a sample yet
            if (bestLoc == null) {
                for (provider in listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    try {
                        val l = lm.getLastKnownLocation(provider)
                        if (l != null && (bestLoc == null || l.time > bestLoc.time)) {
                            bestLoc = l
                        }
                    } catch (_: Exception) {}
                }
            }

            if (bestLoc != null) {
                val now = System.currentTimeMillis()
                appPrefs.setParkedLocation(
                    lat = bestLoc.latitude,
                    lon = bestLoc.longitude,
                    accuracy = bestLoc.accuracy,
                    timeMs = now
                )
                _state.value = _state.value.copy(
                    parkedLat = bestLoc.latitude,
                    parkedLon = bestLoc.longitude,
                    parkedAccuracy = bestLoc.accuracy,
                    parkedTimeMs = now,
                    parkedTimeFormatted = appPrefs.userPreferences.value.parkedTimeFormatted
                )
                LogStore.logEvent("📍 Bike Parked: ${bestLoc.latitude}, ${bestLoc.longitude} (±${bestLoc.accuracy}m)")
            } else {
                LogStore.logEvent("No GPS location available to record parked spot")
            }

            // 3. On Android 30+, refine asynchronously with high-accuracy satellite fix
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val provider = if (lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                    LocationManager.FUSED_PROVIDER
                } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    LocationManager.GPS_PROVIDER
                } else {
                    LocationManager.NETWORK_PROVIDER
                }
                lm.getCurrentLocation(provider, null, context.mainExecutor) { refinedLoc ->
                    if (refinedLoc != null) {
                        lastKnownRideLocation = refinedLoc
                        appPrefs.setParkedLocation(
                            lat = refinedLoc.latitude,
                            lon = refinedLoc.longitude,
                            accuracy = refinedLoc.accuracy,
                            timeMs = System.currentTimeMillis()
                        )
                        _state.value = _state.value.copy(
                            parkedLat = refinedLoc.latitude,
                            parkedLon = refinedLoc.longitude,
                            parkedAccuracy = refinedLoc.accuracy,
                            parkedTimeFormatted = appPrefs.userPreferences.value.parkedTimeFormatted
                        )
                        LogStore.logEvent("📍 Refined Parked Spot: ${refinedLoc.latitude}, ${refinedLoc.longitude}")
                    }
                }
            }
        } catch (e: Exception) {
            LogStore.logEvent("Failed to capture parked location: ${e.message}")
        }
    }

    private fun handleRawTelemetry(p: RawTelemetryPacket) {
        val current = _state.value

        var newTargetSpeed = current.targetSpeed
        if (p.speed != null) {
            val currentSpeed: Float = p.speed
            newTargetSpeed = currentSpeed
            appPrefs.updateTopSpeed(currentSpeed)

            val now = System.currentTimeMillis()
            speedHistory.addLast(SpeedSample(now, currentSpeed))
            // Keep 90 seconds of rolling speed samples
            val cutoff = now - 90_000L
            while (speedHistory.isNotEmpty() && speedHistory.first().timeMs < cutoff) {
                speedHistory.removeFirst()
            }

            // Target threshold: 100 km/h for Metric, 60 mph (96.56 km/h) for Imperial
            val isMph = appPrefs.userPreferences.value.speedUnit == com.kalaiselvan.ride.preferences.SpeedUnit.MPH
            val targetSpeed = if (isMph) 96.56f else 100.0f
            val sprintLabel = if (isMph) "0-60 MPH" else "0-100 KM/H"

            // ── Strict Dead-Stop Arming Gate (< 2.0 km/h) & 20-Seconds Hard Timeout ──
            // Must launch from a complete standstill (< 2.0 km/h). Rolling starts are strictly excluded.
            if (currentSpeed < 2.0f) {
                sprintArmed = true
                isSprintRunning = false
            } else if (sprintArmed && !isSprintRunning && currentSpeed >= 2.5f) {
                // Launch detected from standstill!
                isSprintRunning = true
                sprintArmed = false
                sprintStartTime = now
                LogStore.logEvent("⚡ $sprintLabel Sprint Timer Started: Standstill launch detected (< 2.0 km/h)")
            } else if (isSprintRunning) {
                val elapsed = (now - sprintStartTime) / 1000f
                if (currentSpeed >= targetSpeed) {
                    if (elapsed in 1.5f..20.0f) {
                        LogStore.logEvent("🏁 Fastest $sprintLabel Recorded: ${String.format(java.util.Locale.US, "%.2f", elapsed)}s (Valid standstill launch)")
                        appPrefs.updateZeroToHundred(elapsed)
                    }
                    isSprintRunning = false
                } else if (elapsed > 20.0f) {
                    // 20-Seconds Hard Timeout reached
                    LogStore.logEvent("⏱️ $sprintLabel Sprint Timed Out (> 20.0s). Run aborted.")
                    isSprintRunning = false
                } else if (currentSpeed < 1.0f) {
                    // Rider stopped before reaching target speed: reset & re-arm
                    isSprintRunning = false
                    sprintArmed = true
                }
            }

            lastSpeedSample = currentSpeed
        }

        var newTargetRpm = current.targetRpm
        if (p.rpm != null) {
            newTargetRpm = p.rpm
        }

        var newGear = current.gear
        if (p.gear != null) newGear = p.gear

        var newThrottle = current.throttle
        if (p.throttle != null) newThrottle = p.throttle

        var newGForce = current.gForce
        if (p.accelG != null) {
            val rawG = p.accelG
            // When bike is stationary (speed < 1.0 km/h) or within IMU mounting calibration noise threshold, snap to 0.00 G
            newGForce = if (newTargetSpeed < 1.0f || abs(rawG) < 0.05f) 0.0f else rawG
            if (abs(newGForce) > 0.08f) {
                appPrefs.updateMaxG(abs(newGForce))
            }
        } else if (newTargetSpeed < 1.0f) {
            newGForce = 0.0f
        }

        var newLeanLeft = current.leanLeft
        var newLeanRight = current.leanRight
        if (p.maxLeanLeft != null) {
            newLeanLeft = max(newLeanLeft, p.maxLeanLeft)
        }
        if (p.maxLeanRight != null) {
            newLeanRight = max(newLeanRight, p.maxLeanRight)
        }
        if (p.maxLeanLeft != null || p.maxLeanRight != null) {
            appPrefs.updateMaxLean(newLeanLeft, newLeanRight)
        }

        var newLeanDir = current.leanDirection
        if (p.leanDirection != null) newLeanDir = p.leanDirection

        val now = System.currentTimeMillis()
        if (p.odometer != null || p.tripA != null || p.tripB != null || (now - lastSyncSavedTime > 30000L)) {
            lastSyncSavedTime = now
            appPrefs.updateLastTelemetry(
                odo = p.odometer?.toDouble() ?: current.currentOdo,
                tripA = p.tripA ?: current.tripA,
                tripB = p.tripB ?: current.tripB
            )
        }

        var newCurrentLean = current.currentLean
        if (p.leanAngle != null) newCurrentLean = p.leanAngle

        var newOdo = current.currentOdo
        if (p.odometer != null) {
            newOdo = p.odometer.toDouble()
            MaintenanceReminderNotifier.checkDistanceTriggerOnConnection(context, newOdo)
        }

        var newTripA = current.tripA
        if (p.tripA != null) newTripA = p.tripA

        var newTripB = current.tripB
        if (p.tripB != null) newTripB = p.tripB

        var newCoolant = current.coolantTemp
        if (p.waterTemp != null) newCoolant = p.waterTemp

        var newAir = current.airTemp
        if (p.inletAirTemp != null) newAir = p.inletAirTemp

        var newBattery = current.batteryVolt
        if (p.ecuBattery != null) newBattery = p.ecuBattery

        var newFuelPct = current.fuelPercent
        if (p.fuelGaugeBars != null) {
            newFuelPct = when (p.fuelGaugeBars) {
                6 -> 100
                5 -> 83
                4 -> 67
                3 -> 50
                2 -> 33
                1 -> 17
                else -> 0
            }
        }

        var newRidingMode = current.ridingMode
        var newKtrcLevel = current.ktrcLevel
        var newPowerMode = current.powerMode

        if (p.ridingMode != null && p.ridingMode.isNotBlank()) {
            newRidingMode = p.ridingMode
        }
        if (p.powerMode != null && p.powerMode.isNotBlank()) {
            newPowerMode = p.powerMode
        }
        if (p.ktrcLevel != null && p.ktrcLevel.isNotBlank()) {
            newKtrcLevel = p.ktrcLevel
            appPrefs.setKtrcLevel(p.ktrcLevel)
        }

        var newKqsStatus = current.kqsStatus
        if (p.kqsStatus != null) newKqsStatus = p.kqsStatus

        var newCruiseState = current.cruiseState
        if (p.cruiseState != null) {
            newCruiseState = when (p.cruiseState) {
                "ACTIVE" -> CruiseState.ACTIVE
                "READY" -> CruiseState.ON
                else -> CruiseState.OFF
            }
        }

        var newCruiseSpeed = current.cruiseSetSpeed
        if (p.cruiseSetSpeed != null && p.cruiseSetSpeed > 0) {
            newCruiseSpeed = p.cruiseSetSpeed
        } else if (newCruiseState == CruiseState.OFF) {
            newCruiseSpeed = 0
        }

        var newBikeModel = current.bikeModelName
        var newVin = current.bikeVin
        if (!p.vin.isNullOrBlank()) {
            newVin = p.vin
        }
        if (!p.bikeModel.isNullOrBlank()) {
            newBikeModel = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(p.bikeModel, newVin).ifBlank { p.bikeModel }
        } else if (!newVin.isNullOrBlank() && (newBikeModel.isNullOrBlank() || newBikeModel.startsWith("ML5", ignoreCase = true))) {
            val decoded = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(newBikeModel, newVin)
            if (decoded.isNotBlank()) newBikeModel = decoded
        }
        appPrefs.setBikeModelAndVin(newBikeModel, newVin)

        var newKtrcActive = current.ktrcActive
        if (p.ktrcActive != null) {
            newKtrcActive = p.ktrcActive
        } else {
            val syntheticIntervention = (p.accelG != null && p.accelG > 1.2f) || (newThrottle > 85f && newTargetSpeed < 30f)
            if (syntheticIntervention) newKtrcActive = true
            else if (!current.isBtConnected) newKtrcActive = false
        }
        val newKqsActive = (newGear != current.gear && (newGear ?: 0) > 0 && (current.gear ?: 0) > 0)

        // Session Live Accumulation
        if (sessionActive) {
            if (p.speed != null && p.speed > 0f) {
                val dtSec = (now - sessionLastSpeedTime) / 1000.0
                if (dtSec in 0.05..3.0) {
                    sessionDistanceAccumulated += (p.speed / 3600.0) * dtSec
                }
                sessionLastSpeedTime = now
            }
            if (p.odometer != null && p.odometer > 0) {
                if (sessionStartOdo == 0.0) {
                    sessionStartOdo = p.odometer.toDouble()
                }
            }
            if (p.maxLeanLeft != null) sessionMaxLeanLeft = max(sessionMaxLeanLeft, p.maxLeanLeft)
            if (p.maxLeanRight != null) sessionMaxLeanRight = max(sessionMaxLeanRight, p.maxLeanRight)
            if (p.accelG != null) sessionMaxGForce = max(sessionMaxGForce, abs(p.accelG))
            if (p.wheelieAngle != null && p.wheelieAngle > 0f) {
                sessionMaxWheelieAngle = max(sessionMaxWheelieAngle, p.wheelieAngle)
                appPrefs.updateMaxWheelie(sessionMaxWheelieAngle)
            }
        }

        RideSessionRecorder.updateTelemetry(newTargetSpeed, newLeanLeft, newLeanRight)

        val currentDist = if (sessionActive) {
            if (newOdo > 0 && sessionStartOdo > 0 && newOdo >= sessionStartOdo) {
                newOdo - sessionStartOdo
            } else {
                sessionDistanceAccumulated
            }
        } else current.sessionDistanceKm

        val currentSessionTime = if (sessionActive) {
            System.currentTimeMillis() - sessionStartTime
        } else current.sessionTimeMs

        val newAvgMil = p.avgFuelMileage ?: current.avgMil
        val effectiveMil = if (newAvgMil > 5.0f) newAvgMil else 18.0f // Z900 real-world average ~18 km/L
        val calculatedRangeKm = if (newFuelPct > 0) {
            (17.0 * (newFuelPct / 100.0) * effectiveMil).toInt()
        } else {
            0
        }

        _state.value = current.copy(
            targetSpeed = newTargetSpeed,
            targetRpm = newTargetRpm,
            gear = newGear,
            throttle = newThrottle,
            gForce = newGForce,
            leanLeft = newLeanLeft,
            leanRight = newLeanRight,
            currentLean = newCurrentLean,
            leanDirection = newLeanDir,
            currentOdo = newOdo,
            tripA = newTripA,
            tripB = newTripB,
            coolantTemp = newCoolant,
            airTemp = newAir,
            batteryVolt = newBattery,
            fuelPercent = newFuelPct,
            rangeKm = calculatedRangeKm,
            ridingMode = newRidingMode,
            powerMode = newPowerMode,
            ktrcLevel = newKtrcLevel,
            ktrcActive = newKtrcActive,
            kqsStatus = newKqsStatus,
            kqsActive = newKqsActive,
            cruiseState = newCruiseState,
            cruiseSetSpeed = newCruiseSpeed,
            avgMil = newAvgMil,
            sessionTimeMs = currentSessionTime,
            sessionDistanceKm = currentDist,
            sessionMaxLeanL = sessionMaxLeanLeft,
            sessionMaxLeanR = sessionMaxLeanRight,
            sessionMaxWheelie = sessionMaxWheelieAngle,
            sessionMaxStoppie = sessionMaxStoppieAngle,
            sessionMaxG = sessionMaxGForce,
            bikeModelName = newBikeModel,
            bikeVin = newVin,
            shiftLampRpm = p.shiftLampRpm ?: if (current.shiftLampRpm > 0) current.shiftLampRpm else appPrefs.userPreferences.value.shiftLampRpm
        )
    }

    /**
     * 60 FPS Render Interpolation Loop (16ms per frame)
     * Provides liquid-smooth needle sweep and gauge animation.
     */
    private fun startRenderLoop() {
        renderInterpolationJob?.cancel()
        renderInterpolationJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val s = _state.value
                val lerpRpm = s.rpm + (s.targetRpm - s.rpm) * 0.22f
                val lerpSpeed = s.speed + (s.targetSpeed - s.speed) * 0.20f

                _state.value = s.copy(
                    rpm = lerpRpm,
                    speed = lerpSpeed
                )

                delay(16) // ~60fps
            }
        }
    }
}
