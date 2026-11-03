package com.kalaiselvan.ride.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.bluetooth.BikeForegroundService
import com.kalaiselvan.ride.bluetooth.BikeGattManager
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.telemetry.TelemetryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Native Turn-by-Turn Navigation Engine for Ola Maps.
 * Executes 1 Hz local GPS polyline snapping, distance countdowns, off-route detection,
 * and direct streaming to bike TFT via Frame 0x14 without repeated cloud API calls.
 */
object OlaNavEngine {

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var rerouteJob: Job? = null

    // State Flows for UI and Telemetry synchronization
    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _isWrongRoute = MutableStateFlow(false)
    val isWrongRoute: StateFlow<Boolean> = _isWrongRoute.asStateFlow()

    private val _currentRoute = MutableStateFlow<OlaRoute?>(null)
    val currentRoute: StateFlow<OlaRoute?> = _currentRoute.asStateFlow()

    // Preview & Search State preserved across tab navigation
    private val _previewRoute = MutableStateFlow<OlaRoute?>(null)
    val previewRoute: StateFlow<OlaRoute?> = _previewRoute.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _predictions = MutableStateFlow<List<OlaPrediction>>(emptyList())
    val predictions: StateFlow<List<OlaPrediction>> = _predictions.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    // Candidate Routes (when alternatives are available)
    private val _candidateRoutes = MutableStateFlow<List<OlaRoute>>(emptyList())
    val candidateRoutes: StateFlow<List<OlaRoute>> = _candidateRoutes.asStateFlow()

    private val _selectedRouteIndex = MutableStateFlow(0)
    val selectedRouteIndex: StateFlow<Int> = _selectedRouteIndex.asStateFlow()

    fun setCandidateRoutes(routes: List<OlaRoute>, selectedIndex: Int = 0) {
        _candidateRoutes.value = routes
        val validIndex = selectedIndex.coerceIn(0, maxOf(0, routes.size - 1))
        _selectedRouteIndex.value = validIndex
        _previewRoute.value = routes.getOrNull(validIndex)
    }

    fun selectRouteIndex(index: Int) {
        if (index in _candidateRoutes.value.indices) {
            _selectedRouteIndex.value = index
            _previewRoute.value = _candidateRoutes.value[index]
        }
    }

    fun setPreviewRoute(route: OlaRoute?) {
        _previewRoute.value = route
        _candidateRoutes.value = if (route != null) listOf(route) else emptyList()
        _selectedRouteIndex.value = 0
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPredictions(list: List<OlaPrediction>) {
        _predictions.value = list
    }

    fun setCalculatingRoute(calculating: Boolean) {
        _isCalculatingRoute.value = calculating
    }

    fun clearSearchAndPreview() {
        _candidateRoutes.value = emptyList()
        _selectedRouteIndex.value = 0
        _previewRoute.value = null
        _searchQuery.value = ""
        _predictions.value = emptyList()
        _isCalculatingRoute.value = false
    }

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _distanceToNextTurn = MutableStateFlow(0)
    val distanceToNextTurn: StateFlow<Int> = _distanceToNextTurn.asStateFlow()

    private val _currentInstruction = MutableStateFlow("")
    val currentInstruction: StateFlow<String> = _currentInstruction.asStateFlow()

    private val _currentBikeIcon = MutableStateFlow(0)
    val currentBikeIcon: StateFlow<Int> = _currentBikeIcon.asStateFlow()

    private val _totalRemainingDistanceMeters = MutableStateFlow(0)
    val totalRemainingDistanceMeters: StateFlow<Int> = _totalRemainingDistanceMeters.asStateFlow()

    private val _isRerouting = MutableStateFlow(false)
    val isRerouting: StateFlow<Boolean> = _isRerouting.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    private var locationManager: LocationManager? = null
    private var navWakeLock: android.os.PowerManager.WakeLock? = null
    private var activeApiKey: String = ""
    private var offRouteCount = 0
    private var oppositeDirectionCount = 0
    private var lastSmoothedDist = -1
    private var lastProcessedStepIndex = -1
    private var lastTftSendTime = 0L
    private var lastSentIcon = -1
    private var lastSentDist = -1

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Discard low-accuracy fixes (> 25m) to prevent random distance jumping
            if (location.hasAccuracy() && location.accuracy > 25.0f) {
                return
            }
            _lastLocation.value = location
            if (_isNavigating.value) {
                processLocationUpdate(location)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            LogStore.logEvent("Location provider enabled: $provider")
        }
        override fun onProviderDisabled(provider: String) {
            LogStore.logEvent("Location provider disabled: $provider")
        }
    }

    /**
     * Checks if device GPS location service is enabled.
     */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Obtains a fresh, high-accuracy GPS origin fix for route planning.
     * Rejects stale cached fixes (e.g. from hours ago at Point A) and rejects
     * coarse cell-tower fixes to ensure routes originate from the exact service road
     * or lane rather than parallel highways.
     */
    @SuppressLint("MissingPermission")
    suspend fun getAccurateOriginLocation(context: Context): Location? {
        val appContext = context.applicationContext
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!hasLocationPermission(appContext) || !isLocationServiceEnabled(appContext)) return null

        // 1. Check if our active last fix is fresh (< 20s) and high accuracy (<= 35m)
        val candidate = _lastLocation.value ?: try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { null }

        val now = System.currentTimeMillis()
        if (candidate != null &&
            (now - candidate.time < 20_000L) &&
            candidate.hasAccuracy() && candidate.accuracy <= 35.0f
        ) {
            return candidate
        }

        // 2. If no fresh high-accuracy fix exists (e.g. user just traveled to Point B),
        // actively wait up to 4.0 seconds for an accurate GPS fix.
        return withTimeoutOrNull(4000L) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        if (loc.hasAccuracy() && loc.accuracy <= 35.0f) {
                            _lastLocation.value = loc
                            try { lm.removeUpdates(this) } catch (_: Exception) {}
                            if (continuation.isActive) {
                                continuation.resume(loc)
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                try {
                    if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        lm.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            500L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                    } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            1000L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                    } else {
                        if (continuation.isActive) continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    try { lm.removeUpdates(listener) } catch (_: Exception) {}
                }
            }
        } ?: _lastLocation.value ?: try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { null }
    }

    /**
     * Starts listening for device GPS location updates so the map has live rider coordinates
     * even before navigation is started.
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context) {
        if (!hasLocationPermission(context)) return
        val appContext = context.applicationContext
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm

        try {
            val lastKnown = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                _lastLocation.value = lastKnown
            }

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            LogStore.logEvent("Failed to start preview location updates: ${e.message}")
        }
    }

    /**
     * Stops location updates if not actively navigating.
     */
    fun stopLocationUpdatesIfNotNavigating() {
        if (!_isNavigating.value) {
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (_: Exception) {}
        }
    }

    /**
     * Manually updates last known location.
     */
    fun updateLocationManually(location: Location) {
        _lastLocation.value = location
    }

    /**
     * Starts native turn-by-turn navigation session with the given OlaRoute.
     */
    @SuppressLint("MissingPermission")
    fun startNavigation(
        context: Context,
        route: OlaRoute,
        apiKey: String
    ): Boolean {
        if (!hasLocationPermission(context)) {
            LogStore.logEvent("Cannot start Ola Navigation: Location permission not granted")
            return false
        }

        activeApiKey = apiKey
        _currentRoute.value = route
        _currentStepIndex.value = 0
        _isNavigating.value = true
        _isRerouting.value = false
        _isWrongRoute.value = false
        offRouteCount = 0
        oppositeDirectionCount = 0
        lastSmoothedDist = -1
        lastProcessedStepIndex = -1
        lastTftSendTime = 0L
        lastSentIcon = -1
        lastSentDist = -1

        val firstTarget = if (route.steps.size > 1) route.steps[1] else route.steps.firstOrNull()
        if (firstTarget != null) {
            _currentInstruction.value = firstTarget.instruction
            _currentBikeIcon.value = firstTarget.bikeIcon
            _distanceToNextTurn.value = if (route.steps.size > 1) route.steps[0].distanceMeters else firstTarget.distanceMeters
        }
        _totalRemainingDistanceMeters.value = route.totalDistanceMeters

        // Ensure foreground service is running so OS doesn't kill GPS in pocket
        BikeForegroundService.start(context)

        val appContext = context.applicationContext

        // Acquire dedicated navigation WakeLock so CPU does not sleep when phone is locked
        try {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (navWakeLock == null) {
                navWakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "MyRide:OlaNavWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (navWakeLock?.isHeld != true) {
                navWakeLock?.acquire()
            }
        } catch (e: Exception) {
            LogStore.logEvent("OlaNav: WakeLock acquire failed: ${e.message}")
        }

        // Register GPS listener on ApplicationContext (Strictly HIGH-ACCURACY GPS ONLY to eliminate dual-provider distance jumps)
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        locationManager = lm

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                lm?.isProviderEnabled(LocationManager.FUSED_PROVIDER) == true
            ) {
                lm.requestLocationUpdates(
                    LocationManager.FUSED_PROVIDER,
                    1000L,
                    0.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            } else if (lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            // Fetch last known location to initialize immediately
            val lastKnown = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnown != null) {
                _lastLocation.value = lastKnown
                processLocationUpdate(lastKnown)
            }
        } catch (e: Exception) {
            LogStore.logEvent("Failed to register location updates: ${e.message}")
        }

        LogStore.logEvent("Ola Navigation Started to: ${route.destinationName} (${route.totalDistanceMeters / 1000f} km)")
        return true
    }

    /**
     * Stops active navigation, clears bike TFT screen, and unregisters GPS listener.
     */
    fun stopNavigation(context: Context) {
        _isNavigating.value = false
        _isRerouting.value = false
        _isWrongRoute.value = false
        rerouteJob?.cancel()
        rerouteJob = null

        try {
            if (navWakeLock?.isHeld == true) {
                navWakeLock?.release()
            }
        } catch (_: Exception) {}

        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}

        _currentRoute.value = null
        _previewRoute.value = null

        // Send Frame 0x14 Mode 3 (Nav Clear) to bike TFT
        BikeGattManager.instance?.sendNavigationData(3, 0xFF, 0, 0x0F)
        TelemetryEngine.getInstance(context).updateNavigationState(
            isActive = false,
            instruction = "",
            distanceMeters = 0,
            iconId = 0
        )

        LogStore.logEvent("Ola Navigation Stopped & Bike TFT cleared")
    }

    /**
     * Core 1 Hz location processing loop:
     * - Direction/bearing awareness: detects reverse travel and triggers reroute.
     * - Jitter filtering: suppresses erratic dual-provider distance jumps.
     * - Latency dead-reckoning & heading-based early advance: eliminates the 30m turn apex lag.
     */
    private fun processLocationUpdate(location: Location) {
        val route = _currentRoute.value ?: return
        val steps = route.steps
        if (steps.isEmpty()) return

        var stepIndex = _currentStepIndex.value
        var targetIndex = if (steps.size > 1) (stepIndex + 1).coerceAtMost(steps.size - 1) else 0
        var targetStep = steps[targetIndex]
        var effectiveSegmentIndex = stepIndex

        // 1. Calculate distance from current rider position to the target maneuver point
        val distResults = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            targetStep.location.latitude,
            targetStep.location.longitude,
            distResults
        )
        val rawDistMeters = distResults[0].toInt()

        // 2. Heading / Bearing Awareness (Reverse Travel Detection)
        val targetLoc = Location("").apply {
            latitude = targetStep.location.latitude
            longitude = targetStep.location.longitude
        }
        val bearingToTarget = location.bearingTo(targetLoc)
        var isTravellingOpposite = false

        if (location.hasSpeed() && location.speed > 1.2f && location.hasBearing()) {
            var bearingDiff = Math.abs(location.bearing - bearingToTarget)
            if (bearingDiff > 180f) bearingDiff = 360f - bearingDiff
            if (bearingDiff > 115f) {
                isTravellingOpposite = true
                oppositeDirectionCount++
                if (oppositeDirectionCount >= 4 && !_isRerouting.value) {
                    LogStore.logEvent("OlaNav: Opposite direction detected ($bearingDiff° diff) for 4 consecutive fixes -> Auto-rerouting...")
                    triggerReroute(location, route.destinationLatLng, route.destinationName)
                }
            } else {
                oppositeDirectionCount = 0
            }
        }

        // 3. GPS 1 Hz Latency Dead-Reckoning Compensation
        val speedMps = if (location.hasSpeed()) location.speed else 0.0f
        val latencyOffset = (speedMps * 0.9f).toInt()
        val compensatedDist = (rawDistMeters - latencyOffset).coerceAtLeast(0)

        // 4. Maneuver Step Advancement
        val isFinalSegment = effectiveSegmentIndex >= steps.size - 2
        val isDestinationArrived = (targetStep.bikeIcon == 43 || isFinalSegment) && rawDistMeters <= 15

        if (!isDestinationArrived) {
            val canAdvanceByDistance = !isTravellingOpposite && (compensatedDist <= 12 || rawDistMeters <= 12)
            var canAdvanceByHeading = false

            // Heading-based early advance: if within 35m of turn and bearing has already rotated toward next segment
            if (!isTravellingOpposite && rawDistMeters <= 35 && (targetIndex + 1) < steps.size &&
                location.hasBearing() && location.hasSpeed() && location.speed > 1.2f
            ) {
                val nextTargetLoc = Location("").apply {
                    latitude = steps[targetIndex + 1].location.latitude
                    longitude = steps[targetIndex + 1].location.longitude
                }
                val bearingToNext = location.bearingTo(nextTargetLoc)
                var diffToNext = Math.abs(location.bearing - bearingToNext)
                if (diffToNext > 180f) diffToNext = 360f - diffToNext
                if (diffToNext < 40f) {
                    canAdvanceByHeading = true
                }
            }

            if (canAdvanceByDistance || canAdvanceByHeading) {
                val nextSegment = effectiveSegmentIndex + 1
                if (nextSegment < steps.size) {
                    _currentStepIndex.value = nextSegment
                    effectiveSegmentIndex = nextSegment
                    targetIndex = (effectiveSegmentIndex + 1).coerceAtMost(steps.size - 1)
                    targetStep = steps[targetIndex]
                    lastSmoothedDist = -1

                    Location.distanceBetween(
                        location.latitude,
                        location.longitude,
                        targetStep.location.latitude,
                        targetStep.location.longitude,
                        distResults
                    )
                    LogStore.logEvent("OlaNav: Step advanced to segment $effectiveSegmentIndex (Next maneuver: ${targetStep.instruction})")
                }
            }
        }

        // 5. Monotonic Distance Jitter Smoothing
        val currentRaw = distResults[0].toInt()
        val currentCompensated = (currentRaw - latencyOffset).coerceAtLeast(0)
        var displayDist = if (isDestinationArrived) 0 else currentCompensated

        if (!isDestinationArrived) {
            if (effectiveSegmentIndex == lastProcessedStepIndex && lastSmoothedDist > 0 && !isTravellingOpposite) {
                if (displayDist > lastSmoothedDist + 15) {
                    // Suppress erratic upward jump caused by GPS noise
                    displayDist = (lastSmoothedDist * 0.65f + displayDist * 0.35f).toInt()
                }
            }
            if (displayDist <= 10) {
                displayDist = 0 // Clean snap at turn apex
            }
            lastSmoothedDist = displayDist
        }
        lastProcessedStepIndex = effectiveSegmentIndex

        val displayIcon = if (isDestinationArrived) 43 else if (isTravellingOpposite) 7 else targetStep.bikeIcon
        val displayInstruction = if (isDestinationArrived) "Destination Arrived" else if (isTravellingOpposite) "Make a U-Turn" else targetStep.instruction

        _distanceToNextTurn.value = displayDist
        _currentBikeIcon.value = displayIcon
        _currentInstruction.value = displayInstruction

        // Live Total Remaining Distance calculation
        var remainingMeters = displayDist
        for (i in (targetIndex + 1) until steps.size) {
            remainingMeters += steps[i].distanceMeters
        }
        _totalRemainingDistanceMeters.value = remainingMeters

        // 6. Off-Route Detection (> 45m from polyline for 3 consecutive GPS updates)
        val minPolylineDist = computeDistanceToPolyline(location, route.polyline)
        if (minPolylineDist > 45.0) {
            offRouteCount++
            if (offRouteCount >= 3 && !_isRerouting.value && !isDestinationArrived) {
                triggerReroute(location, route.destinationLatLng, route.destinationName)
            }
        } else {
            offRouteCount = 0
        }

        // Rider is on wrong route if traveling opposite direction, drifted off course, or rerouting
        _isWrongRoute.value = isTravellingOpposite || offRouteCount >= 2 || _isRerouting.value

        // 7. Stream Frame 0x14 to bike TFT Meter
        dispatchTftFrame(
            mode = if (_isRerouting.value) 1 else if (isDestinationArrived) 2 else 0,
            icon = displayIcon,
            distanceMeters = displayDist,
            instruction = displayInstruction
        )
    }

    /**
     * Forces an immediate re-send of Frame 0x14 to the bike TFT display.
     * Used when reconnecting after BLE drops or when a phone call finishes,
     * bypassing the distance-change rate limiter.
     */
    fun forceRefreshTft() {
        if (!_isNavigating.value) return
        lastSentIcon = -1
        lastSentDist = -1
        lastTftSendTime = 0L
        _lastLocation.value?.let { processLocationUpdate(it) }
    }

    /**
     * Sends Frame 0x14 to the motorcycle TFT display.
     * Rate-limited to 1 Hz unless icon changes, near turn apex (<=25m), or distance drops >= 15m.
     */
    private fun dispatchTftFrame(mode: Int, icon: Int, distanceMeters: Int, instruction: String) {
        // If a phone call is actively ringing/in-call on the bike dash, pause sending 0x14
        if (BikeGattManager.instance?.isCallActive == true) {
            return
        }

        val now = System.currentTimeMillis()
        val iconChanged = icon != lastSentIcon
        val distSignificant = Math.abs(distanceMeters - lastSentDist) >= 15
        val nearApex = distanceMeters <= 25 && distanceMeters != lastSentDist
        val timeElapsed = (now - lastTftSendTime) >= 1000L

        if (iconChanged || (timeElapsed && (distSignificant || nearApex)) || mode != 0) {
            lastTftSendTime = now
            lastSentIcon = icon
            lastSentDist = distanceMeters

            BikeGattManager.instance?.sendNavigationData(
                mode = mode,
                icon = icon,
                distance = distanceMeters,
                unit = 1, // 1 = meters
                instruction = instruction
            )
        }
    }

    /**
     * Silent, automatic re-routing when the rider deviates > 45m off-course.
     */
    private fun triggerReroute(
        currentLocation: Location,
        destination: OlaLatLng,
        destinationName: String
    ) {
        _isRerouting.value = true
        LogStore.logEvent("OlaNav: Off-route detected (>45m) -> Requesting auto-reroute...")

        // Send Frame 0x14 Mode 1 (Under Routing / Rerouting)
        BikeGattManager.instance?.sendNavigationData(
            mode = 1,
            icon = 0xFF,
            distance = 0,
            unit = 1,
            instruction = "Rerouting..."
        )

        rerouteJob?.cancel()
        rerouteJob = engineScope.launch {
            val origin = OlaLatLng(currentLocation.latitude, currentLocation.longitude)
            val result = OlaMapsClient.getDirections(origin, destination, activeApiKey, destinationName)
            result.onSuccess { newRoute ->
                LogStore.logEvent("OlaNav: Reroute successful! New route: ${newRoute.totalDistanceMeters}m")
                _currentRoute.value = newRoute
                _currentStepIndex.value = 0
                _isRerouting.value = false
                offRouteCount = 0
                processLocationUpdate(currentLocation)
            }.onFailure { err ->
                LogStore.logEvent("OlaNav: Reroute failed (${err.message}) - will retry next GPS fix")
                _isRerouting.value = false
            }
        }
    }

    /**
     * Computes the perpendicular distance in meters from the rider's coordinate to the nearest polyline segment.
     */
    private fun computeDistanceToPolyline(location: Location, polyline: List<OlaLatLng>): Double {
        if (polyline.isEmpty()) return 0.0
        if (polyline.size == 1) {
            val res = FloatArray(1)
            Location.distanceBetween(
                location.latitude,
                location.longitude,
                polyline[0].latitude,
                polyline[0].longitude,
                res
            )
            return res[0].toDouble()
        }

        var minDistance = Double.MAX_VALUE
        val pLat = location.latitude
        val pLng = location.longitude
        val res = FloatArray(1)

        // Sample nearby polyline points and segment midpoints to prevent false off-route spikes on long straightaways
        val step = if (polyline.size > 500) 2 else 1
        for (i in 0 until polyline.size - 1 step step) {
            val pt1 = polyline[i]
            val pt2 = polyline[i + 1]
            Location.distanceBetween(pLat, pLng, pt1.latitude, pt1.longitude, res)
            if (res[0] < minDistance) {
                minDistance = res[0].toDouble()
            }
            Location.distanceBetween(pLat, pLng, (pt1.latitude + pt2.latitude) / 2.0, (pt1.longitude + pt2.longitude) / 2.0, res)
            if (res[0] < minDistance) {
                minDistance = res[0].toDouble()
            }
        }
        return minDistance
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED
    }
}
