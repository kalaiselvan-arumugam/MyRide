package com.kalaiselvan.ride.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.bluetooth.BikeGattManager
import com.kalaiselvan.ride.navigation.*
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.preferences.RecentDestination
import com.kalaiselvan.ride.telemetry.TelemetryEngine
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.components.VisualMapView
import com.kalaiselvan.ride.ui.theme.neumorphicCircleInner
import com.kalaiselvan.ride.ui.theme.neumorphicCircleOuter
import com.kalaiselvan.ride.ui.theme.neumorphicInner
import com.kalaiselvan.ride.ui.theme.neumorphicOuter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MapScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()
    val recentDestinations by appPrefs.recentDestinations.collectAsState()

    // Telemetry & Bike Connection State
    val telemetryEngine = remember { TelemetryEngine.getInstance(context) }
    val telemetryState by telemetryEngine.state.collectAsState()
    val isBikeConnected = telemetryState.isBtConnected || (BikeGattManager.instance?.isConnected == true)

    // Navigation engine states (persisted across tab switches)
    val isNavigating by OlaNavEngine.isNavigating.collectAsState()
    val currentRoute by OlaNavEngine.currentRoute.collectAsState()
    val previewRoute by OlaNavEngine.previewRoute.collectAsState()
    val candidateRoutes by OlaNavEngine.candidateRoutes.collectAsState()
    val selectedRouteIndex by OlaNavEngine.selectedRouteIndex.collectAsState()
    val searchQuery by OlaNavEngine.searchQuery.collectAsState()
    val predictions by OlaNavEngine.predictions.collectAsState()
    val isCalculatingRoute by OlaNavEngine.isCalculatingRoute.collectAsState()
    val lastLocation by OlaNavEngine.lastLocation.collectAsState()

    val distanceToNextTurn by OlaNavEngine.distanceToNextTurn.collectAsState()
    val currentInstruction by OlaNavEngine.currentInstruction.collectAsState()
    val currentBikeIcon by OlaNavEngine.currentBikeIcon.collectAsState()
    val totalRemainingDist by OlaNavEngine.totalRemainingDistanceMeters.collectAsState()
    val isRerouting by OlaNavEngine.isRerouting.collectAsState()
    val isWrongRoute by OlaNavEngine.isWrongRoute.collectAsState()
    val currentStepIndex by OlaNavEngine.currentStepIndex.collectAsState()

    var mapBearing by remember { mutableStateOf(0f) }
    var resetNorthTrigger by remember { mutableStateOf(0L) }

    var searchError by remember { mutableStateOf<String?>(null) }
    var recenterTrigger by remember { mutableStateOf(0L) }
    var showGpsDisabledDialog by remember { mutableStateOf(false) }
    var showBikeNotConnectedDialog by remember { mutableStateOf(false) }

    // Permission launcher for Location
    var pendingStartAfterPermission by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            val routeToStart = previewRoute
            if (pendingStartAfterPermission && routeToStart != null) {
                pendingStartAfterPermission = false
                if (!isBikeConnected) {
                    showBikeNotConnectedDialog = true
                    return@rememberLauncherForActivityResult
                }
                verifyGpsAndStart(
                    context = context,
                    route = routeToStart,
                    apiKey = userPrefs.olaMapsApiKey,
                    onGpsDisabled = { showGpsDisabledDialog = true }
                )
            }
        } else {
            Toast.makeText(context, "Location permission is required for navigation", Toast.LENGTH_LONG).show()
        }
    }

    // Keep GPS updates active while on Map tab so rider coordinates and 'find me' are immediate
    DisposableEffect(Unit) {
        OlaNavEngine.startLocationUpdates(context)
        onDispose {
            OlaNavEngine.stopLocationUpdatesIfNotNavigating()
        }
    }

    // Debounced Autocomplete search
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length < 2) {
            OlaNavEngine.setPredictions(emptyList())
            searchError = null
            return@LaunchedEffect
        }
        delay(350) // Debounce typing
        searchError = null

        val lastLoc = OlaNavEngine.lastLocation.value
        val result = OlaMapsClient.autocomplete(
            input = searchQuery,
            apiKey = userPrefs.olaMapsApiKey,
            currentLat = lastLoc?.latitude,
            currentLng = lastLoc?.longitude
        )
        result.onSuccess {
            OlaNavEngine.setPredictions(it)
        }.onFailure { err ->
            searchError = err.message
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        val activeOrPreviewRoute = if (isNavigating) currentRoute else previewRoute
        val isMapVisible = isNavigating || previewRoute != null

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── TOP SECTION: Search Bar OR Preview Target Header OR Active Guidance HUD ──────────────────
            if (isNavigating) {
                CockpitGuidanceHUD(
                    distanceMeters = distanceToNextTurn,
                    instruction = currentInstruction,
                    bikeIcon = currentBikeIcon,
                    isRerouting = isRerouting,
                    isWrongRoute = isWrongRoute,
                    isBikeConnected = isBikeConnected,
                    stepTotalMeters = currentRoute?.steps?.getOrNull(currentStepIndex)?.distanceMeters ?: 500
                )
            } else if (previewRoute != null) {
                CockpitRouteTargetHeader(
                    destinationName = previewRoute!!.destinationName,
                    onCancel = { OlaNavEngine.setPreviewRoute(null) }
                )
            } else {
                // Missing API Key Notification Card (Graceful Neumorphic)
                if (userPrefs.olaMapsApiKey.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphicOuter(cornerRadius = 16.dp, blurRadius = 8.dp)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FlatVectorIcon(
                                    pathString = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z",
                                    tint = RpmYellow,
                                    size = 16.dp
                                )
                                Text(
                                    text = "OLA MAPS KEY REQUIRED",
                                    color = RpmYellow,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Enter your Ola Maps API key in Settings to activate in-app routing and map tiles.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF222A36), Color(0xFF14181F))
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xCC00FF88), Color(0x3300FF88), Color(0x0500FF88))
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onNavigateToSettings() }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "CONFIGURE SETTINGS",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                // Neumorphic Search Bar
                NeumorphicSearchBar(
                    query = searchQuery,
                    isLocationEnabled = OlaNavEngine.isLocationServiceEnabled(context),
                    onLocationDisabledClick = { showGpsDisabledDialog = true },
                    onQueryChange = { query ->
                        if (!OlaNavEngine.isLocationServiceEnabled(context)) {
                            showGpsDisabledDialog = true
                            return@NeumorphicSearchBar
                        }
                        OlaNavEngine.setSearchQuery(query)
                        if (previewRoute != null) {
                            OlaNavEngine.setPreviewRoute(null)
                        }
                    },
                    isSearching = isCalculatingRoute,
                    onClear = {
                        OlaNavEngine.clearSearchAndPreview()
                        focusManager.clearFocus()
                    }
                )
            }

            // ── CONTENT SECTION ──────────────────────────────────────────────
            if (isMapVisible) {
                // Visual Map Container (Shown ONLY when a route is previewed or navigating)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .drawBehind {
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0x18FFFFFF), Color(0x05FFFFFF), Color(0x00000000)),
                                    start = Offset(0f, 0f),
                                    end = Offset(size.width, size.height)
                                ),
                                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                ) {
                    VisualMapView(
                        route = activeOrPreviewRoute,
                        candidateRoutes = if (isNavigating) emptyList() else candidateRoutes,
                        selectedRouteIndex = selectedRouteIndex,
                        onRouteSelected = { idx ->
                            OlaNavEngine.selectRouteIndex(idx)
                        },
                        currentLocation = lastLocation,
                        compassHeading = telemetryState.headingDegrees,
                        onMapBearingChanged = { bearing -> mapBearing = bearing },
                        apiKey = userPrefs.olaMapsApiKey,
                        mapStyle = userPrefs.olaMapStyle.styleId,
                        isNavigating = isNavigating,
                        recenterTrigger = recenterTrigger,
                        resetNorthTrigger = resetNorthTrigger,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Tactical Compass & Recenter Floating Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Tactical Compass Needle Button (Rotates with map bearing, snaps to North on tap)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .neumorphicCircleOuter(blurRadius = 6.dp)
                                .clickable {
                                    resetNorthTrigger = System.currentTimeMillis()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .neumorphicCircleInner(shadowWidth = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            rotationZ = -mapBearing
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    FlatVectorIcon(
                                        pathString = "M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z",
                                        tint = RpmRed,
                                        size = 18.dp
                                    )
                                }
                            }
                        }

                        // Tactical Recenter Dial Button (Neumorphic Satellite Pod Bevel Cue)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .neumorphicCircleOuter(blurRadius = 6.dp)
                                .clickable {
                                    if (!OlaNavEngine.isLocationServiceEnabled(context)) {
                                        showGpsDisabledDialog = true
                                        return@clickable
                                    }
                                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                    val loc = lm?.let {
                                        try {
                                            it.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                                ?: it.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                                        } catch (_: Exception) { null }
                                    }
                                    if (loc != null) {
                                        OlaNavEngine.updateLocationManually(loc)
                                    }
                                    OlaNavEngine.startLocationUpdates(context)
                                    recenterTrigger = System.currentTimeMillis()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .neumorphicCircleInner(shadowWidth = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FlatVectorIcon(
                                    pathString = "M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
                                    tint = RpmGreen,
                                    size = 17.dp
                                )
                            }
                        }
                    }
                }

                // Bottom Flight Deck for Map State
                if (isNavigating) {
                    CockpitActiveFlightDeck(
                        destinationName = currentRoute?.destinationName ?: "Destination",
                        totalRemainingMeters = totalRemainingDist,
                        totalInitialMeters = currentRoute?.totalDistanceMeters ?: 1,
                        onEndNavigation = {
                            OlaNavEngine.stopNavigation(context)
                        }
                    )
                } else if (previewRoute != null) {
                    RoutePreviewFlightDeck(
                        route = previewRoute!!,
                        candidateRoutes = candidateRoutes,
                        selectedRouteIndex = selectedRouteIndex,
                        onSelectRouteIndex = { idx ->
                            OlaNavEngine.selectRouteIndex(idx)
                        },
                        autoChooseFastest = userPrefs.autoChooseFastestRoute,
                        onStartNavigation = {
                            if (!isBikeConnected) {
                                showBikeNotConnectedDialog = true
                                return@RoutePreviewFlightDeck
                            }
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasPermission) {
                                pendingStartAfterPermission = true
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                verifyGpsAndStart(
                                    context = context,
                                    route = previewRoute!!,
                                    apiKey = userPrefs.olaMapsApiKey,
                                    onGpsDisabled = { showGpsDisabledDialog = true }
                                )
                            }
                        },
                        onCancel = {
                            OlaNavEngine.clearSearchAndPreview()
                        }
                    )
                }
            } else {
                // Pre-Search State: NO empty map container!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isCalculatingRoute) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = RpmGreen,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "CALCULATING ROUTE...",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    } else if (predictions.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .neumorphicOuter(cornerRadius = 16.dp, blurRadius = 8.dp)
                                .background(Color(0xF51A1D20))
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            items(predictions) { pred ->
                                PredictionRowItem(
                                    prediction = pred,
                                    onClick = {
                                        focusManager.clearFocus()
                                        coroutineScope.launch {
                                            OlaNavEngine.setCalculatingRoute(true)
                                            searchError = null
                                            fetchAndPreviewRoute(
                                                context = context,
                                                prediction = pred,
                                                apiKey = userPrefs.olaMapsApiKey,
                                                autoChooseFastest = userPrefs.autoChooseFastestRoute,
                                                appPrefs = appPrefs,
                                                onSuccess = { routes ->
                                                    if (userPrefs.autoChooseFastestRoute || routes.size <= 1) {
                                                        OlaNavEngine.setPreviewRoute(routes.first())
                                                    } else {
                                                        OlaNavEngine.setCandidateRoutes(routes, selectedIndex = 0)
                                                    }
                                                    OlaNavEngine.setCalculatingRoute(false)
                                                },
                                                onError = { err ->
                                                    OlaNavEngine.setCalculatingRoute(false)
                                                    searchError = err
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    } else if (recentDestinations.isNotEmpty() && searchQuery.isBlank()) {
                        RecentDestinationsSection(
                            recentItems = recentDestinations,
                            onClearAll = { appPrefs.clearRecentDestinations() },
                            onSelectRecent = { item ->
                                if (!OlaNavEngine.isLocationServiceEnabled(context)) {
                                    showGpsDisabledDialog = true
                                    return@RecentDestinationsSection
                                }
                                coroutineScope.launch {
                                    OlaNavEngine.setCalculatingRoute(true)
                                    searchError = null
                                    fetchAndPreviewRouteFromRecent(
                                        context = context,
                                        item = item,
                                        apiKey = userPrefs.olaMapsApiKey,
                                        autoChooseFastest = userPrefs.autoChooseFastestRoute,
                                        onSuccess = { routes ->
                                            if (userPrefs.autoChooseFastestRoute || routes.size <= 1) {
                                                OlaNavEngine.setPreviewRoute(routes.first())
                                            } else {
                                                OlaNavEngine.setCandidateRoutes(routes, selectedIndex = 0)
                                            }
                                            OlaNavEngine.setCalculatingRoute(false)
                                        },
                                        onError = { err ->
                                            OlaNavEngine.setCalculatingRoute(false)
                                            searchError = err
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        )
                    } else {
                        CockpitStandbyView()
                    }
                }
            }
        }
    }

    // ── Bike Not Connected Alert Dialog ──────────────────────────────────────
    if (showBikeNotConnectedDialog) {
        AlertDialog(
            onDismissRequest = { showBikeNotConnectedDialog = false },
            title = {
                Text(
                    text = "Bike Not Connected",
                    color = TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Motorcycle TFT meter is not connected via Bluetooth. Live turn-by-turn guidance and distance will stream directly to your dash once connected.",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF242C38), Color(0xFF151921))
                            )
                        )
                        .border(1.dp, Color(0x4400FF88), RoundedCornerShape(10.dp))
                        .clickable { showBikeNotConnectedDialog = false }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(text = "OK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── Location Services (GPS) Disabled Alert Dialog ────────────────────────
    if (showGpsDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDisabledDialog = false },
            title = {
                Text(
                    text = "GPS Location Disabled",
                    color = TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Location service (GPS) is turned off on your device. Turn-by-turn motorcycle navigation requires GPS to track maneuvers and stream turns to your motorcycle display.",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF242C38), Color(0xFF151921))
                            )
                        )
                        .border(1.dp, Color(0x4400FF88), RoundedCornerShape(10.dp))
                        .clickable {
                            showGpsDisabledDialog = false
                            try {
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = "ENABLE GPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsDisabledDialog = false }) {
                    Text(text = "CANCEL", color = TextMuted, fontSize = 11.5.sp)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Validates GPS enabled state and starts navigation.
 */
private fun verifyGpsAndStart(
    context: Context,
    route: OlaRoute,
    apiKey: String,
    onGpsDisabled: () -> Unit
) {
    if (!OlaNavEngine.isLocationServiceEnabled(context)) {
        onGpsDisabled()
        return
    }
    OlaNavEngine.startNavigation(context, route, apiKey)
}

/**
 * Resolves destination coordinates and queries Directions API.
 * Saves successful destinations to recent history (5-day persistence).
 */
@SuppressLint("MissingPermission")
private suspend fun fetchAndPreviewRoute(
    context: Context,
    prediction: OlaPrediction,
    apiKey: String,
    autoChooseFastest: Boolean,
    appPrefs: AppPreferences,
    onSuccess: (List<OlaRoute>) -> Unit,
    onError: (String) -> Unit
) {
    var destLat = prediction.lat
    var destLng = prediction.lng

    if (destLat == null || destLng == null) {
        val detailsResult = OlaMapsClient.getPlaceDetails(prediction.placeId, apiKey)
        detailsResult.onSuccess { latLng ->
            destLat = latLng.latitude
            destLng = latLng.longitude
        }.onFailure {
            onError("Unable to resolve coordinates for ${prediction.mainText}")
            return
        }
    }

    if (destLat == null || destLng == null) {
        onError("Destination coordinates unavailable")
        return
    }

    // Save destination to last 5 recent items (5 days expiry)
    appPrefs.saveRecentDestination(
        RecentDestination(
            placeId = prediction.placeId,
            mainText = prediction.mainText,
            secondaryText = prediction.secondaryText,
            lat = destLat,
            lng = destLng
        )
    )

    // Current Origin Location (High-accuracy GPS only, eliminating parallel highway / stale Point A bug)
    val originLoc = OlaNavEngine.getAccurateOriginLocation(context)
    if (originLoc == null) {
        onError("Accurate GPS location required. Please enable Location and ensure clear view of sky.")
        return
    }

    val origin = OlaLatLng(originLoc.latitude, originLoc.longitude)
    val destination = OlaLatLng(destLat, destLng)

    val routeResult = OlaMapsClient.getRoutes(
        origin = origin,
        destination = destination,
        apiKey = apiKey,
        destinationName = prediction.mainText,
        alternatives = !autoChooseFastest
    )

    routeResult.onSuccess { routes ->
        onSuccess(routes)
    }.onFailure { err ->
        onError(err.message ?: "Failed to calculate route")
    }
}

@SuppressLint("MissingPermission")
private suspend fun fetchAndPreviewRouteFromRecent(
    context: Context,
    item: RecentDestination,
    apiKey: String,
    autoChooseFastest: Boolean,
    onSuccess: (List<OlaRoute>) -> Unit,
    onError: (String) -> Unit
) {
    // Current Origin Location (High-accuracy GPS only, eliminating parallel highway / stale Point A bug)
    val originLoc = OlaNavEngine.getAccurateOriginLocation(context)
    if (originLoc == null) {
        onError("Accurate GPS location required. Please enable Location and ensure clear view of sky.")
        return
    }

    val origin = OlaLatLng(originLoc.latitude, originLoc.longitude)
    val destination = OlaLatLng(item.lat, item.lng)

    val routeResult = OlaMapsClient.getRoutes(
        origin = origin,
        destination = destination,
        apiKey = apiKey,
        destinationName = item.mainText,
        alternatives = !autoChooseFastest
    )

    routeResult.onSuccess { routes ->
        onSuccess(routes)
    }.onFailure { err ->
        onError(err.message ?: "Failed to calculate route")
    }
}

// ── NEUMORPHIC UI SUBCOMPONENTS ──────────────────────────────────────────────

@Composable
private fun NeumorphicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    onClear: () -> Unit,
    isLocationEnabled: Boolean = true,
    onLocationDisabledClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicInner(cornerRadius = 14.dp, shadowWidth = 3.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlatVectorIcon(
                pathString = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
                tint = if (isLocationEnabled) TextMuted else RpmYellow,
                size = 15.dp
            )
            Spacer(modifier = Modifier.width(10.dp))

            if (!isLocationEnabled) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onLocationDisabledClick
                        )
                ) {
                    Text(
                        text = "Enable Location to search destination...",
                        color = RpmYellow.copy(alpha = 0.85f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextMain,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(RpmGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Search destination...",
                                color = TextMuted,
                                fontSize = 12.5.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = RpmGreen
                )
            } else if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClear
                        )
                        .padding(4.dp)
                ) {
                    FlatVectorIcon(
                        pathString = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
                        tint = TextMuted,
                        size = 14.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun PredictionRowItem(
    prediction: OlaPrediction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlatVectorIcon(
            pathString = "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
            tint = TextMuted,
            size = 16.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prediction.mainText,
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (prediction.secondaryText.isNotBlank()) {
                Text(
                    text = prediction.secondaryText,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentDestinationsSection(
    recentItems: List<RecentDestination>,
    onSelectRecent: (RecentDestination) -> Unit,
    onClearAll: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (recentItems.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT DESTINATIONS",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "CLEAR ALL",
                    color = RpmRed,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphicOuter(cornerRadius = 16.dp, blurRadius = 8.dp)
                    .padding(vertical = 4.dp)
            ) {
                Column {
                    recentItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectRecent(item) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FlatVectorIcon(
                                pathString = "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z",
                                tint = TextMuted,
                                size = 15.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.mainText,
                                    color = TextMain,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.secondaryText.isNotBlank()) {
                                    Text(
                                        text = item.secondaryText,
                                        color = TextMuted,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (index < recentItems.size - 1) {
                            HorizontalDivider(
                                color = Color(0x0DFFFFFF),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitRouteTargetHeader(
    destinationName: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(RpmGreen)
                )
                Column {
                    Text(
                        text = "TARGET DESTINATION",
                        color = RpmGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = destinationName,
                        color = TextMain,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B2028))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                FlatVectorIcon(
                    pathString = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
                    tint = TextMuted,
                    size = 14.dp
                )
            }
        }
    }
}


@Composable
private fun RoutePreviewFlightDeck(
    route: OlaRoute,
    candidateRoutes: List<OlaRoute> = emptyList(),
    selectedRouteIndex: Int = 0,
    onSelectRouteIndex: (Int) -> Unit = {},
    autoChooseFastest: Boolean = true,
    onStartNavigation: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distanceKm = String.format(java.util.Locale.US, "%.1f", route.totalDistanceMeters / 1000f)
    val totalSeconds = route.totalDurationSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val formattedDuration = String.format(java.util.Locale.US, "%02d:%02d", hours, minutes)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 18.dp, blurRadius = 10.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Interactive Route Selection Chips (Shown when alternatives are available)
            if (candidateRoutes.size > 1 && !autoChooseFastest) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fastestSeconds = candidateRoutes.firstOrNull()?.totalDurationSeconds ?: 0
                    candidateRoutes.forEachIndexed { idx, candidate ->
                        val isSelected = idx == selectedRouteIndex
                        val durationMinutes = (candidate.totalDurationSeconds + 59) / 60
                        val diffSec = candidate.totalDurationSeconds - fastestSeconds
                        val diffMin = (diffSec + 59) / 60
                        val label = when {
                            idx == 0 -> "${durationMinutes}m • Fastest"
                            diffMin > 0 -> "${durationMinutes}m • +${diffMin}m"
                            else -> "${durationMinutes}m"
                        }
                        val summaryText = if (candidate.summary.isNotBlank()) candidate.summary else "Route ${idx + 1}"

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0xFF132A1F) else Color(0xFF1A1E24)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) RpmGreen else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectRouteIndex(idx) }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) RpmGreen else TextMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = summaryText.uppercase(),
                                    color = if (isSelected) Color(0xCCFFFFFF) else TextMuted,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Symmetrical Telemetry Row (Matching DashScreen Left, Center, Right columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Flank: Distance
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = distanceKm,
                            color = TextMain,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = "km",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                    Text(
                        text = "DISTANCE",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Center Flank: Est Time
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedDuration,
                        color = RpmGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Text(
                        text = "EST. TIME (HH:MM)",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Right Flank: Maneuvers
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${route.steps.size}",
                            color = TextMain,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = "turns",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                    Text(
                        text = "MANEUVERS",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Real Route Information (Zero extra API calls, pure cockpit telemetry)
            val viaRouteName = remember(route) {
                when {
                    route.summary.isNotBlank() -> "VIA ${route.summary.uppercase()}"
                    else -> {
                        val longestStep = route.steps.maxByOrNull { it.distanceMeters }
                        val raw = longestStep?.instruction ?: ""
                        val cleaned = raw.replace(
                            Regex("^(Turn left onto|Turn right onto|Head|Continue onto|Keep left onto|Keep right onto|Take the ramp onto)\\s*", RegexOption.IGNORE_CASE),
                            ""
                        ).trim()
                        if (cleaned.isNotBlank()) "VIA ${cleaned.uppercase()}" else "FASTEST DIRECT ROUTE"
                    }
                }
            }

            val avgSpeedKmh = remember(route) {
                if (route.totalDurationSeconds > 0) {
                    ((route.totalDistanceMeters / 1000f) / (route.totalDurationSeconds / 3600f)).toInt().coerceIn(15, 120)
                } else 0
            }
            val routeDetailTag = if (avgSpeedKmh > 0) "AVG $avgSpeedKmh KM/H • FASTEST" else "FASTEST ROUTE"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphicInner(cornerRadius = 10.dp, shadowWidth = 2.dp)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FlatVectorIcon(
                                pathString = "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
                                tint = RpmGreen,
                                size = 11.dp
                            )
                            Text(
                                text = viaRouteName,
                                color = TextMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = routeDetailTag,
                            color = Color(0x99FFFFFF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Progress bar showing distance remaining to next turn
                    if (route.steps.isNotEmpty()) {
                        val firstTurn = route.steps.first()
                        val turnDistText = if (firstTurn.distanceMeters >= 1000) {
                            String.format(java.util.Locale.US, "%.1f km", firstTurn.distanceMeters / 1000f)
                        } else {
                            "${firstTurn.distanceMeters}m"
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "NEXT: ${firstTurn.instruction.uppercase()}",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = turnDistText,
                                    color = RpmGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Specular Hairline (Dashboard Cue)
            HorizontalDivider(
                color = Color(0x1AFFFFFF),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // Tactile Cockpit Action Switch (Matte Gray, No Outer Glow)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF262A32))
                    .clickable(onClick = onStartNavigation),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlatVectorIcon(
                        pathString = "M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z",
                        tint = Color.White,
                        size = 15.dp
                    )
                    Text(
                        text = "START NAVIGATION",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitGuidanceHUD(
    distanceMeters: Int,
    instruction: String,
    bikeIcon: Int,
    isRerouting: Boolean,
    isWrongRoute: Boolean = false,
    isBikeConnected: Boolean,
    stepTotalMeters: Int,
    modifier: Modifier = Modifier
) {
    val distanceDisplay = when {
        distanceMeters >= 1000 -> String.format(java.util.Locale.US, "%.1f", distanceMeters / 1000f)
        distanceMeters >= 10 -> "${(distanceMeters / 10) * 10}"
        else -> "$distanceMeters"
    }
    val distanceUnit = when {
        distanceMeters >= 1000 -> "km"
        else -> "m"
    }

    // Flashing pulse animation when rider took the wrong route
    val infiniteTransition = rememberInfiniteTransition(label = "wrongRoutePulse")
    val flashingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashAlpha"
    )

    val statusDotColor = when {
        isWrongRoute -> RpmRed
        !isBikeConnected -> RpmRed
        isRerouting -> RpmYellow
        else -> RpmGreen
    }
    val statusText = when {
        isWrongRoute -> "WRONG ROUTE • U-TURN REQUIRED"
        !isBikeConnected -> "DISCONNECTED • SEARCHING"
        isRerouting -> "RE-ROUTING TO BEST PATH"
        else -> "TFT CONNECTED • LIVE STREAM"
    }

    val baseDistColor = when {
        isWrongRoute -> RpmRed
        distanceMeters <= 100 -> RpmYellow
        else -> RpmGreen
    }
    val distColor = if (isWrongRoute) baseDistColor.copy(alpha = flashingAlpha) else baseDistColor

    // Google Maps style turn approach countdown progress (e.g. 100m, 50m countdown)
    val safeStepTotal = if (stepTotalMeters > 0) stepTotalMeters else distanceMeters.coerceAtLeast(100)
    val approachProgress = ((safeStepTotal - distanceMeters).toFloat() / safeStepTotal).coerceIn(0.04f, 1f)
    val animatedApproach by animateFloatAsState(
        targetValue = approachProgress,
        label = "turnApproachProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 16.dp, blurRadius = 8.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Status row + Hero maneuver distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Text(
                        text = statusText,
                        color = statusDotColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = distanceDisplay,
                        color = distColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 24.sp,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                    Text(
                        text = distanceUnit,
                        color = distColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                }
            }

            HorizontalDivider(
                color = Color(0x1AFFFFFF),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // Turn Instruction with Flat Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E242C)),
                    contentAlignment = Alignment.Center
                ) {
                    FlatVectorIcon(
                        pathString = "M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z",
                        tint = if (distanceMeters <= 100) RpmYellow else RpmGreen,
                        size = 16.dp
                    )
                }

                Text(
                    text = instruction,
                    color = TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Google Maps Turn Approach Countdown Progress Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (distanceMeters <= 100) "TURN IMMINENT" else "APPROACHING TURN",
                        color = if (distanceMeters <= 100) RpmYellow else TextMuted,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${distanceDisplay} ${distanceUnit} REMAINING",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0xFF16191F))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedApproach)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = if (distanceMeters <= 100) {
                                        listOf(RpmYellow, Color(0xFFFFB300))
                                    } else {
                                        listOf(Color(0xFF00B050), RpmGreen)
                                    }
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitActiveFlightDeck(
    destinationName: String,
    totalRemainingMeters: Int,
    totalInitialMeters: Int,
    onEndNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingDisplay = when {
        totalRemainingMeters >= 1000 -> String.format(java.util.Locale.US, "%.1f", totalRemainingMeters / 1000f)
        totalRemainingMeters >= 10 -> "${(totalRemainingMeters / 10) * 10}"
        else -> "$totalRemainingMeters"
    }
    val remainingUnit = if (totalRemainingMeters >= 1000) "km" else "m"

    val progressPercent = if (totalInitialMeters > 0) {
        (((totalInitialMeters - totalRemainingMeters).toFloat() / totalInitialMeters) * 100f).coerceIn(0f, 100f)
    } else 0f

    val animatedFill by animateFloatAsState(
        targetValue = (progressPercent / 100f).coerceIn(0.02f, 1f),
        label = "activeRouteProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 18.dp, blurRadius = 10.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Symmetrical Active Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Destination info on Left
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "DESTINATION",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = destinationName,
                        color = TextMain,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Remaining Distance on Right
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = remainingDisplay,
                            color = TextMain,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = remainingUnit,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                    Text(
                        text = "REMAINING",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Live Route Progress Groove (Direct ThrottleBar Cue)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "JOURNEY PROGRESS",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${progressPercent.toInt()}",
                            color = TextMain,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "%",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1E2228))
                        .drawBehind {
                            drawLine(
                                color = Color(0x88000000),
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(0x22FFFFFF),
                                start = Offset(0f, size.height - 0.5f),
                                end = Offset(size.width, size.height - 0.5f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFill)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF00B050), RpmGreen)
                                )
                            )
                    )
                }
            }

            // Specular Hairline
            HorizontalDivider(
                color = Color(0x1AFFFFFF),
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            // Tactile End Navigation Switch (Matte Gray, No Outer Glow)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF262A32))
                    .clickable(onClick = onEndNavigation),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlatVectorIcon(
                        pathString = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z",
                        tint = Color.White,
                        size = 14.dp
                    )
                    Text(
                        text = "END NAVIGATION",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitStandbyView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 18.dp, blurRadius = 8.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Symmetrical Radar Dial Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .neumorphicCircleOuter(blurRadius = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .neumorphicCircleInner(shadowWidth = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FlatVectorIcon(
                        pathString = "M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z",
                        tint = RpmGreen,
                        size = 20.dp
                    )
                }
            }

            Text(
                text = "COCKPIT NAVIGATION STANDBY",
                color = TextMain,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Text(
                text = "Search for a destination above to calculate routes, preview maneuvers on the live map, and start turn-by-turn navigation.",
                color = TextMuted,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Reusable flat monochrome SVG Canvas renderer (matching the app's established FlatIcon).
 */
@Composable
private fun FlatVectorIcon(
    pathString: String,
    modifier: Modifier = Modifier,
    tint: Color = TextMain,
    size: androidx.compose.ui.unit.Dp = 16.dp
) {
    val parsedPath = remember(pathString) {
        PathParser().parsePathString(pathString).toPath()
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        scale(scaleX = w / 24f, scaleY = h / 24f, pivot = Offset(0f, 0f)) {
            drawPath(path = parsedPath, color = tint, style = Fill)
        }
    }
}
