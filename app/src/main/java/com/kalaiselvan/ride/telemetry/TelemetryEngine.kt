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

    init {
        com.kalaiselvan.ride.logs.RideSessionRecorder.init(context)
    }

    private val _state = MutableStateFlow(
        TelemetryState(
            speedUnit = appPrefs.userPreferences.value.speedUnit,
            temperatureUnit = appPrefs.userPreferences.value.temperatureUnit,
            currentOdo = appPrefs.userPreferences.value.lastOdo,
            tripA = appPrefs.userPreferences.value.lastTripA,
            tripB = appPrefs.userPreferences.value.lastTripB,
            sessionTimeMs = appPrefs.userPreferences.value.lastSessionTimeMs,
            sessionDistanceKm = appPrefs.userPreferences.value.lastSessionDistanceKm,
            sessionMaxLeanL = appPrefs.userPreferences.value.lastSessionMaxLeanL,
            sessionMaxLeanR = appPrefs.userPreferences.value.lastSessionMaxLeanR,
            sessionMaxWheelie = appPrefs.userPreferences.value.lastSessionMaxWheelie,
            sessionMaxStoppie = appPrefs.userPreferences.value.lastSessionMaxStoppie,
            sessionMaxG = appPrefs.userPreferences.value.lastSessionMaxG,
            parkedLat = appPrefs.userPreferences.value.parkedLat,
            parkedLon = appPrefs.userPreferences.value.parkedLon,
            parkedAccuracy = appPrefs.userPreferences.value.parkedAccuracy,
            parkedTimeMs = appPrefs.userPreferences.value.parkedTimeMs,
            parkedTimeFormatted = appPrefs.userPreferences.value.parkedTimeFormatted,
            bikeModelName = appPrefs.userPreferences.value.bikeModelName.takeIf { com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(it, appPrefs.userPreferences.value.bikeVin).isNotBlank() },
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
    private var sessionMovingStarted = false
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
    private var lastRecordedBreadcrumbLat: Double = 0.0
    private var lastRecordedBreadcrumbLon: Double = 0.0
    private var lastRecordedBreadcrumbTime: Long = 0L

    private var lastSyncSavedTime: Long = 0L
    private var lastPeakSyncTime: Long = 0L
    private var sessionMaxSpeed: Float = 0f

    init {
        // Observe preferences for speed unit, shift lamp, and parked changes
        scope.launch {
            appPrefs.userPreferences.collect { prefs ->
                _state.value = _state.value.copy(
                    speedUnit = prefs.speedUnit,
                    temperatureUnit = prefs.temperatureUnit,
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
                    // Industry standard motorcycle telematics GPS filter:
                    // 1) Discard non-GPS providers if GPS is active
                    // 2) Discard poor accuracy fixes (> 45m). Pocketed phones / jackets typically exhibit 25m-40m accuracy.
                    if (loc.accuracy > 45.0f) {
                        return
                    }

                    if (_state.value.isBtConnected) {
                        lastKnownRideLocation = loc
                        if (sessionActive && sessionMovingStarted) {
                            // Jitter / outlier spike filter:
                            // Check distance and speed between consecutive raw fixes to reject teleports/cuts
                            val now = System.currentTimeMillis()
                            if (lastRecordedBreadcrumbTime > 0L) {
                                val dtSec = (now - lastRecordedBreadcrumbTime) / 1000.0
                                if (dtSec in 0.2..10.0) {
                                    val results = FloatArray(1)
                                    Location.distanceBetween(
                                        lastRecordedBreadcrumbLat,
                                        lastRecordedBreadcrumbLon,
                                        loc.latitude,
                                        loc.longitude,
                                        results
                                    )
                                    val distMeters = results[0]
                                    val impliedSpeedKmh = (distMeters / dtSec) * 3.6f
                                    // If GPS jumped at > 160 km/h between two successive 1-2s fixes, reject as physical multipath jump
                                    if (impliedSpeedKmh > 160f && distMeters > 40f) {
                                        LogStore.logEvent("Rejected GPS outlier jump: ${distMeters}m in ${dtSec}s (${impliedSpeedKmh.toInt()} km/h)")
                                        return
                                    }
                                }
                            }

                            lastRecordedBreadcrumbLat = loc.latitude
                            lastRecordedBreadcrumbLon = loc.longitude
                            lastRecordedBreadcrumbTime = now

                            val leanAngle = if (_state.value.leanRight > _state.value.leanLeft) {
                                _state.value.leanRight
                            } else {
                                -_state.value.leanLeft
                            }
                            // Read current live engine speed (or fallback to GPS speed if sensor lag)
                            val liveSpeed = if (_state.value.speed > 0f) {
                                _state.value.speed
                            } else if (loc.hasSpeed() && loc.speed > 0f) {
                                loc.speed * 3.6f // m/s to km/h
                            } else {
                                _state.value.targetSpeed
                            }

                            RideSessionRecorder.addBreadcrumb(
                                lat = loc.latitude,
                                lon = loc.longitude,
                                speedKmh = liveSpeed,
                                lean = leanAngle
                            )
                        }
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationListener = listener

            // Request GPS_PROVIDER as primary location service across the entire application
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    2.0f,
                    listener,
                    android.os.Looper.getMainLooper()
                )
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    5.0f,
                    listener,
                    android.os.Looper.getMainLooper()
                )
            }
            LogStore.logEvent("GPS ride location tracking active (GPS_PROVIDER primary)")
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
                    val cleanModel = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(name)
                    val logTarget = if (cleanModel.isNotBlank()) cleanModel else (name ?: mac)
                    LogStore.logEvent("Connecting to $logTarget ($mac)")
                    appPrefs.setLastBikeMac(mac)
                    _state.value = _state.value.copy(
                        isBtConnecting = true
                        // Never pre-populate connectedDeviceName or bikeModelName before connection is verified
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

    /**
     * Pauses UI-only rendering effects (60 Hz display interpolation loop and device compass sensor)
     * when the Activity is minimized or stopped.
     *
     * Invariant: BLE GATT connections, background ride session recording, and GPS location updates
     * MUST remain active during stopUi(). Do NOT call full teardown here.
     */
    fun stopUi() {
        renderInterpolationJob?.cancel()
        compassManager?.stop()
        LogStore.logEvent("TelemetryEngine: UI interpolation loop and compass paused for background state.")
    }

    /**
     * Full teardown called ONLY when the foreground service or app is completely shutting down.
     * Terminates active ride recording, shuts down GPS location tracking, stops BLE scanning,
     * and disconnects GATT.
     */
    fun stop() {
        LogStore.logEvent("TelemetryEngine: Full teardown requested.")
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

        if (!macToConnect.isNullOrBlank() && targetMac != null) {
            appPrefs.setLastBikeMac(macToConnect)
        }

        val effectiveAutoConnect = if (isDirect) false else isAutoConnect
        LogStore.logEvent("Connecting to bike (target MAC: $macToConnect, autoConnect=$effectiveAutoConnect, isDirect=$isDirect)...")
        _state.value = _state.value.copy(
            isBtConnecting = true,
            isBtConnected = false
        )
        startLocationTracking()

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
        scanEngine?.stop()
        gattManager?.onBluetoothDisabled()
        onBikeDisconnected()
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
        scanEngine?.stop()
        gattManager?.disconnect()
        onBikeDisconnected()
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
        sessionMovingStarted = false
        sessionStartTime = 0L
        sessionStartOdo = _state.value.currentOdo
        sessionDistanceAccumulated = 0.0
        sessionLastSpeedTime = System.currentTimeMillis()
        sessionMaxLeanLeft = 0f
        sessionMaxLeanRight = 0f
        sessionMaxWheelieAngle = 0f
        sessionMaxStoppieAngle = 0f
        sessionMaxGForce = 0f

        // Reset session metrics to zero for the connected state
        _state.value = _state.value.copy(
            sessionTimeMs = 0L,
            sessionDistanceKm = 0.0,
            sessionMaxLeanL = 0f,
            sessionMaxLeanR = 0f,
            sessionMaxWheelie = 0f,
            sessionMaxStoppie = 0f,
            sessionMaxG = 0f
        )

        sessionTickerJob?.cancel()
        sessionTickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                if (sessionActive && sessionMovingStarted && sessionStartTime > 0L) {
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
        val durationSec = if (sessionMovingStarted && sessionStartTime > 0L) {
            ((System.currentTimeMillis() - sessionStartTime) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
        val finalTimeMs = if (sessionMovingStarted) {
            current.sessionTimeMs.coerceAtLeast(durationSec * 1000L)
        } else {
            0L
        }

        // Qualification check: Only record/save session if the motorcycle actually moved (dist >= 0.05 km or top speed reached >= 5 km/h)
        val hasActuallyRidden = sessionMovingStarted && (finalDist >= 0.05 || (current.sessionDistanceKm >= 0.05) || durationSec >= 15L)

        if (hasActuallyRidden) {
            // Final flush of peak achievements
            if (sessionMaxSpeed > 0f) appPrefs.updateTopSpeed(sessionMaxSpeed)
            if (sessionMaxGForce > 0.08f) appPrefs.updateMaxG(sessionMaxGForce)
            if (sessionMaxLeanLeft > 0f || sessionMaxLeanRight > 0f) appPrefs.updateMaxLean(sessionMaxLeanLeft, sessionMaxLeanRight)

            RideSessionRecorder.stopSession(
                finalOdo = current.currentOdo,
                explicitDistKm = finalDist,
                maxGForce = sessionMaxGForce,
                maxWheelie = sessionMaxWheelieAngle,
                maxStoppie = sessionMaxStoppieAngle
            )

            // Retain session summary in preferences so it survives app closure while bike is disconnected
            appPrefs.saveLastSessionSummary(
                timeMs = finalTimeMs,
                distKm = finalDist,
                maxLeanL = sessionMaxLeanLeft,
                maxLeanR = sessionMaxLeanRight,
                maxWheelie = sessionMaxWheelieAngle,
                maxStoppie = sessionMaxStoppieAngle,
                maxG = sessionMaxGForce
            )

            if (finalDist > 0.05 || durationSec > 20L) {
                appPrefs.updateLongestRide(finalDist, durationSec)
            }
            LogStore.logEvent("🏍️ Ride session completed: ${String.format("%.2f", finalDist)} km in ${durationSec}s")
        } else {
            // Stationary idle connection (e.g. turned on key in garage without riding)
            LogStore.logEvent("Bike disconnected without ride movement (${String.format("%.2f", finalDist)} km) - discarded stationary session to preserve previous ride records")
            // Restore last saved session summary to state so dashboard displays previous real ride
            val savedPrefs = appPrefs.userPreferences.value
            _state.value = _state.value.copy(
                sessionTimeMs = savedPrefs.lastSessionTimeMs,
                sessionDistanceKm = savedPrefs.lastSessionDistanceKm,
                sessionMaxLeanL = savedPrefs.lastSessionMaxLeanL,
                sessionMaxLeanR = savedPrefs.lastSessionMaxLeanR,
                sessionMaxWheelie = savedPrefs.lastSessionMaxWheelie,
                sessionMaxStoppie = savedPrefs.lastSessionMaxStoppie,
                sessionMaxG = savedPrefs.lastSessionMaxG
            )
        }
        sessionMovingStarted = false
    }

    private fun handleConnectionChange(status: ConnectionStatus) {
        when (status) {
            ConnectionStatus.CONNECTED -> {
                val devName = gattManager?.connectedDeviceName
                val decodedModel = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(devName, appPrefs.userPreferences.value.bikeVin)
                val verifiedName = if (decodedModel.isNotBlank()) decodedModel else devName
                _state.value = _state.value.copy(
                    isBtConnected = true,
                    isBtConnecting = false,
                    connectedDeviceName = verifiedName,
                    bikeModelName = if (decodedModel.isNotBlank()) decodedModel else _state.value.bikeModelName
                )
                startLocationTracking()
                try {
                    BikeForegroundService.start(context)
                } catch (e: Exception) {
                    LogStore.logEvent("BikeForegroundService.start error: ${e.message}")
                }
                BikeForegroundService.updateNotification(context, "Connected to Bike")
                startSessionTracking()
                BackgroundBleScanner.disarm(context)
                MaintenanceReminderNotifier.checkDistanceTriggerOnConnection(context, _state.value.currentOdo)
                MaintenanceReminderNotifier.checkDailyDateTrigger(context)
            }
            ConnectionStatus.CONNECTING -> {
                _state.value = _state.value.copy(
                    isBtConnecting = true,
                    isBtConnected = false
                )
            }
            ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED -> {
                onBikeDisconnected()
            }
        }
    }

    /**
     * Generic, provider-agnostic bike disconnection handler.
     * Invoked automatically by GATT layer or on manual disconnect.
     * Completely decoupled from any specific navigation provider (Ola, Google Maps, OSM, Mapbox, etc.).
     */
    fun onBikeDisconnected() {
        val wasConnected = _state.value.isBtConnected
        _state.value = _state.value.copy(
            isBtConnected = false,
            isBtConnecting = false,
            connectedDeviceName = null,
            cruiseState = CruiseState.OFF,
            cruiseSetSpeed = 0,
            gear = null,
            ridingMode = ""
        )
        // Decoupled: uses generic TelemetryState navigation status
        val isNavActive = _state.value.isNavActive
        if (isNavActive) {
            BikeForegroundService.updateNotification(context, "Navigating (Bike Disconnected)")
        } else {
            BikeForegroundService.stop(context)
        }
        stopSessionTracking()
        if (wasConnected) {
            recordParkedLocation()
            lastKnownRideLocation = null
        }
        if (!isNavActive) {
            stopLocationTracking()
        }
        BackgroundBleScanner.arm(context)
        LogStore.logEvent("TelemetryEngine: Bike disconnected event processed (wasConnected=$wasConnected, isNavActive=$isNavActive)")
    }

    fun clearParkedLocation() {
        appPrefs.clearParkedLocation()
        _state.value = _state.value.copy(
            parkedLat = 0.0,
            parkedLon = 0.0,
            parkedAccuracy = 0f,
            parkedTimeMs = 0L,
            parkedTimeFormatted = ""
        )
    }

    fun recordParkedLocation() {
        // Strict requirement: Only record parked location if we have a valid GPS lock from while connected to the bike
        val bestLoc = lastKnownRideLocation
        if (bestLoc == null) {
            LogStore.logEvent("Skipping recordParkedLocation: bike was not connected or no ride GPS sample")
            return
        }

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
        } catch (e: Exception) {
            LogStore.logEvent("Failed to save parked location: ${e.message}")
        }
    }

    private fun handleRawTelemetry(p: RawTelemetryPacket) {
        val current = _state.value

        var newTargetSpeed = current.targetSpeed
        if (p.speed != null) {
            val currentSpeed: Float = p.speed
            newTargetSpeed = currentSpeed
            com.kalaiselvan.ride.crash.CrashAlertManager.updateBikeSpeed(currentSpeed)
            if (currentSpeed > sessionMaxSpeed) {
                sessionMaxSpeed = currentSpeed
            }

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

        var newLeanDir = current.leanDirection
        if (p.leanDirection != null) newLeanDir = p.leanDirection

        val now = System.currentTimeMillis()
        // Batch and throttle peak telemetry preference writes to every 15 seconds to prevent ~10Hz disk churn
        if (now - lastPeakSyncTime > 15000L) {
            lastPeakSyncTime = now
            if (sessionMaxSpeed > 0f) {
                appPrefs.updateTopSpeed(sessionMaxSpeed)
            }
            if (sessionMaxGForce > 0.08f) {
                appPrefs.updateMaxG(sessionMaxGForce)
            }
            if (newLeanLeft > 0f || newLeanRight > 0f) {
                appPrefs.updateMaxLean(newLeanLeft, newLeanRight)
            }
        }
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
            // Check if movement has started (speed >= 2.0 km/h)
            if (!sessionMovingStarted && (p.speed ?: 0f) >= 2.0f) {
                sessionMovingStarted = true
                sessionStartTime = now
                sessionLastSpeedTime = now
                if (p.odometer != null && p.odometer > 0) {
                    sessionStartOdo = p.odometer.toDouble()
                } else if (sessionStartOdo == 0.0) {
                    sessionStartOdo = current.currentOdo
                }
                RideSessionRecorder.startSession(sessionStartOdo)
                LogStore.logEvent("🏍️ Ride movement detected (${p.speed} km/h). Ride session started!")
            }

            if (sessionMovingStarted) {
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
                RideSessionRecorder.updateTelemetry(newTargetSpeed, newLeanLeft, newLeanRight)
            }
        }

        val currentDist = if (sessionActive) {
            if (sessionMovingStarted) {
                if (newOdo > 0 && sessionStartOdo > 0 && newOdo >= sessionStartOdo) {
                    newOdo - sessionStartOdo
                } else {
                    sessionDistanceAccumulated
                }
            } else {
                0.0
            }
        } else current.sessionDistanceKm

        val currentSessionTime = if (sessionActive) {
            if (sessionMovingStarted && sessionStartTime > 0L) {
                System.currentTimeMillis() - sessionStartTime
            } else {
                0L
            }
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

        evaluateAudioAlerts(
            speedKmh = newTargetSpeed,
            rpm = newTargetRpm,
            shiftLampRpm = _state.value.shiftLampRpm,
            leanAngle = newCurrentLean,
            isLeftLean = newLeanDir.equals("LEFT", ignoreCase = true)
        )
    }

    // ── Audio Alert Evaluation & Hysteresis State Machine ───────────
    private var lastRevAlertTime = 0L
    private var lastSpeedAlert80Fired = false
    private var lastSpeedAlert150Fired = false
    private var lastSpeedAlert200Fired = false
    private var lastLeanAlertTime = 0L
    private var isLeanAlertArmed = true

    private fun evaluateAudioAlerts(
        speedKmh: Float,
        rpm: Float,
        shiftLampRpm: Int,
        leanAngle: Float,
        isLeftLean: Boolean
    ) {
        val prefs = appPrefs.userPreferences.value
        if (!prefs.audioAlertsEnabled) return

        val now = System.currentTimeMillis()

        // 1. Rev Limit / Shift Lamp Alert (with 3.5s cooldown)
        if (prefs.audioRevLimitAlert) {
            val limitRpm = if (shiftLampRpm > 0) shiftLampRpm else 11000 // Z900 redline default
            if (rpm >= limitRpm && (now - lastRevAlertTime > 3500L)) {
                lastRevAlertTime = now
                com.kalaiselvan.ride.audio.AudioAlertManager.playRevLimitAlert()
            }
        }

        // 2. Speed Threshold Alerts (80, 150, 200 km/h or equivalent MPH)
        if (prefs.audioSpeedThresholdAlert) {
            // Speed crossing 80 km/h (reset hysteresis at 72 km/h)
            if (speedKmh >= 80f) {
                if (!lastSpeedAlert80Fired) {
                    lastSpeedAlert80Fired = true
                    com.kalaiselvan.ride.audio.AudioAlertManager.playSpeed80Alert()
                }
            } else if (speedKmh < 72f) {
                lastSpeedAlert80Fired = false
            }

            // Speed crossing 150 km/h (reset hysteresis at 140 km/h)
            if (speedKmh >= 150f) {
                if (!lastSpeedAlert150Fired) {
                    lastSpeedAlert150Fired = true
                    com.kalaiselvan.ride.audio.AudioAlertManager.playSpeed150Alert()
                }
            } else if (speedKmh < 140f) {
                lastSpeedAlert150Fired = false
            }

            // Speed crossing 200 km/h (reset hysteresis at 188 km/h)
            if (speedKmh >= 200f) {
                if (!lastSpeedAlert200Fired) {
                    lastSpeedAlert200Fired = true
                    com.kalaiselvan.ride.audio.AudioAlertManager.playSpeed200Alert()
                }
            } else if (speedKmh < 188f) {
                lastSpeedAlert200Fired = false
            }
        }

        // 3. Lean Angle Alert (with direction-aware ear panning and 8° reset hysteresis)
        if (prefs.audioLeanAngleAlert) {
            val threshold = prefs.audioLeanThresholdDeg.toFloat()
            if (leanAngle >= threshold && isLeanAlertArmed && (now - lastLeanAlertTime > 2000L)) {
                lastLeanAlertTime = now
                isLeanAlertArmed = false
                com.kalaiselvan.ride.audio.AudioAlertManager.playLeanAlert(isLeft = isLeftLean)
            } else if (leanAngle < (threshold - 8f)) {
                // Once bike straightens up by at least 8 degrees, re-arm for next apex
                isLeanAlertArmed = true
            }
        }
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
