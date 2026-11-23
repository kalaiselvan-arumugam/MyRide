package com.kalaiselvan.ride.ui.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import com.kalaiselvan.ride.ui.components.LegalDisclaimerDialog
import com.kalaiselvan.ride.ui.components.HelmetSafetyDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.telemetry.TelemetryEngine
import com.kalaiselvan.ride.theme.BgDark
import com.kalaiselvan.ride.ui.components.AppTab
import com.kalaiselvan.ride.ui.components.BottomNav
import com.kalaiselvan.ride.ui.components.FirstLaunchDialog
import com.kalaiselvan.ride.ui.components.TopBar
import com.kalaiselvan.ride.ui.screens.AchievementsScreen
import com.kalaiselvan.ride.ui.screens.DashScreen
import com.kalaiselvan.ride.ui.screens.MaintenanceScreen
import com.kalaiselvan.ride.ui.screens.MapScreen
import com.kalaiselvan.ride.ui.screens.RidesScreen
import com.kalaiselvan.ride.ui.screens.SettingsScreen

@Composable
fun MainAppScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryEngine = remember { TelemetryEngine.getInstance(context) }
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, userPrefs.hasAcceptedDisclaimer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (userPrefs.hasAcceptedDisclaimer) {
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        telemetryEngine.start()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                        telemetryEngine.stopUi()
                    }
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (userPrefs.hasAcceptedDisclaimer && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            telemetryEngine.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            telemetryEngine.stopUi()
        }
    }

    val telemetryState by telemetryEngine.state.collectAsState()

    // Road Safety Reminder (Always wear your helmet) - Triggers ONLY when app connects to bike
    var showHelmetDialog by remember { mutableStateOf(false) }
    var wasBikeConnected by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(telemetryState.isBtConnected) {
        if (telemetryState.isBtConnected && !wasBikeConnected) {
            showHelmetDialog = true
            wasBikeConnected = true
        } else if (!telemetryState.isBtConnected) {
            wasBikeConnected = false
        }
    }

    LaunchedEffect(userPrefs.crashDetectionEnabled) {
        if (userPrefs.crashDetectionEnabled) {
            com.kalaiselvan.ride.crash.CrashAlertManager.startMonitoring(context)
        } else {
            com.kalaiselvan.ride.crash.CrashAlertManager.stopMonitoring()
        }
    }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.DASH) }

    val showMapTab = userPrefs.turnByTurnNav && userPrefs.navSource == com.kalaiselvan.ride.preferences.NavSource.OLA_MAPS

    // If MAP tab gets hidden while on MAP tab, fall back to DASH
    LaunchedEffect(showMapTab) {
        if (!showMapTab && selectedTab == AppTab.MAP) {
            selectedTab = AppTab.DASH
        }
    }

    // Observe navigation request from shared location intent
    val navToMapTrigger by com.kalaiselvan.ride.navigation.OlaNavEngine.navigateToMapTabTrigger.collectAsState()
    LaunchedEffect(navToMapTrigger) {
        if (navToMapTrigger > 0L) {
            selectedTab = AppTab.MAP
        }
    }

    val isDevSupported = com.kalaiselvan.ride.dev.DevFeatureProviderImpl.isDevFeatureSupported
    val isDevActive = isDevSupported && userPrefs.isDevUnlocked

    // If DEV mode gets locked while on DEV tab, fall back to DASH
    LaunchedEffect(isDevActive) {
        if (!isDevActive && selectedTab == AppTab.DEV) {
            selectedTab = AppTab.DASH
        }
    }

    // Observe full-screen map mode
    val isMapFullScreen by com.kalaiselvan.ride.navigation.OlaNavEngine.isFullScreenMap.collectAsState()
    val isFullScreenMapActive = selectedTab == AppTab.MAP && isMapFullScreen

    // Reset full screen map when navigating away from MAP tab
    LaunchedEffect(selectedTab) {
        if (selectedTab != AppTab.MAP) {
            com.kalaiselvan.ride.navigation.OlaNavEngine.setFullScreenMap(false)
        }
    }

    // Back handler: If in full-screen map mode, exit full-screen first
    androidx.activity.compose.BackHandler(enabled = isFullScreenMapActive) {
        com.kalaiselvan.ride.navigation.OlaNavEngine.setFullScreenMap(false)
    }

    androidx.activity.compose.BackHandler(enabled = selectedTab != AppTab.DASH && !isFullScreenMapActive) {
        selectedTab = AppTab.DASH
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Dynamic Orientation & Immersive Fullscreen Control:
    // In Landscape on DASH or Full-Screen MAP: Hide system status bar & navigation bar (brings up when touched/swiped)
    DisposableEffect(selectedTab, isLandscape, userPrefs.autoRotateDashboard, isFullScreenMapActive) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if ((isLandscape && selectedTab == AppTab.DASH) || isFullScreenMapActive) {
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        if (userPrefs.autoRotateDashboard && (selectedTab == AppTab.DASH || selectedTab == AppTab.MAP)) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bm?.adapter?.isEnabled == true) {
            telemetryEngine.connect()
        }
    }

    fun promptEnableBluetooth() {
        try {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        } catch (e: Exception) {
            try {
                val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                context.startActivity(settingsIntent)
            } catch (_: Exception) {}
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Prompt for Bluetooth on launch or auto-connect to paired bike
    LaunchedEffect(userPrefs.hasAcceptedDisclaimer) {
        if (userPrefs.hasAcceptedDisclaimer) {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (bm?.adapter?.isEnabled != true) {
                promptEnableBluetooth()
            } else {
                if (!telemetryState.isBtConnected && !telemetryState.isBtConnecting) {
                    telemetryEngine.connect(isAutoConnect = true)
                }
            }
        }
    }

    // Automatic In-App GitHub Update Prompt
    var availableUpdate by remember { mutableStateOf<com.kalaiselvan.ride.utils.AppUpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val res = com.kalaiselvan.ride.utils.AppUpdateManager.checkForUpdate(context)
        availableUpdate = res.getOrNull()
    }

    if (availableUpdate != null) {
        val update = availableUpdate!!
        com.kalaiselvan.ride.ui.components.AppUpdateDialog(
            updateInfo = update,
            onDownload = {
                availableUpdate = null
                com.kalaiselvan.ride.utils.AppUpdateManager.downloadAndInstall(context, update.downloadUrl, update.latestVersionName)
            },
            onDismiss = { availableUpdate = null }
        )
    }

    // Handle launcher shortcut or tile request intent
    val activity = context as? ComponentActivity

    // Keep screen active while viewing dashboard when enabled in settings
    DisposableEffect(userPrefs.keepDashboardScreenOn, selectedTab) {
        val shouldKeepOn = userPrefs.keepDashboardScreenOn && (selectedTab == AppTab.DASH)
        if (shouldKeepOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(activity?.intent) {
        val intent = activity?.intent
        val action = intent?.action
        // Validate intent action is a recognized launcher or shortcut entry point
        val isValidAction = action == null ||
                action == Intent.ACTION_MAIN ||
                action == Intent.ACTION_VIEW
        val shouldConnect = isValidAction && (
                intent?.getBooleanExtra("ACTION_CONNECT", false) == true ||
                intent?.getBooleanExtra("ACTION_REQUEST_BT", false) == true
        )
        if (shouldConnect) {
            // Consume extras to prevent re-triggering on configuration changes or activity resume
            intent.removeExtra("ACTION_CONNECT")
            intent.removeExtra("ACTION_REQUEST_BT")
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (bm?.adapter?.isEnabled == true) {
                telemetryEngine.connect()
            } else {
                promptEnableBluetooth()
            }
        }

        // Universal Shared Location Handling (from WhatsApp, Google Maps, HERE Maps, Waze, etc.)
        if (action == Intent.ACTION_SEND || (action == Intent.ACTION_VIEW && intent.scheme == "geo")) {
            val sharedText = if (action == Intent.ACTION_SEND) {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            } else {
                intent.dataString
            }
            if (!sharedText.isNullOrBlank()) {
                intent.removeExtra(Intent.EXTRA_TEXT)
                intent.data = null
                val resolved = com.kalaiselvan.ride.navigation.SharedLocationResolver.resolve(sharedText)
                if (resolved != null) {
                    com.kalaiselvan.ride.navigation.OlaNavEngine.setPendingSharedLocation(resolved)
                    selectedTab = AppTab.MAP
                }
            }
        }
    }

    // List of active tabs in navigation order
    val currentTabs = remember(showMapTab, isDevActive) {
        mutableListOf<AppTab>().apply {
            add(AppTab.DASH)
            if (showMapTab) add(AppTab.MAP)
            add(AppTab.SERVICE)
            add(AppTab.RIDES)
            if (isDevActive) add(AppTab.DEV)
            add(AppTab.SETTINGS)
        }
    }

    var totalScreenDragX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .then(if ((isLandscape && selectedTab == AppTab.DASH) || isFullScreenMapActive) Modifier else Modifier.statusBarsPadding())
    ) {
        // Main Tab Content Area with Horizontal Swipe & Directional Slide Transitions
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .pointerInput(currentTabs, selectedTab, isFullScreenMapActive) {
                    if (isFullScreenMapActive) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { totalScreenDragX = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(totalScreenDragX) > 65f) {
                                val curIdx = currentTabs.indexOf(selectedTab)
                                if (curIdx != -1) {
                                    if (totalScreenDragX < -65f && curIdx < currentTabs.size - 1) {
                                        // Swiped Left -> Move to Next Tab
                                        selectedTab = currentTabs[curIdx + 1]
                                    } else if (totalScreenDragX > 65f && curIdx > 0) {
                                        // Swiped Right -> Move to Previous Tab
                                        selectedTab = currentTabs[curIdx - 1]
                                    }
                                }
                            }
                            totalScreenDragX = 0f
                        },
                        onDragCancel = { totalScreenDragX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            totalScreenDragX += dragAmount
                        }
                    )
                }
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val initialIdx = currentTabs.indexOf(initialState)
                    val targetIdx = currentTabs.indexOf(targetState)
                    if (targetIdx > initialIdx) {
                        // Slide left (forward)
                        (slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(260)) { it / 2 } +
                                fadeIn(animationSpec = androidx.compose.animation.core.tween(260)))
                            .togetherWith(
                                slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(220)) { -it / 3 } +
                                        fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                            )
                    } else {
                        // Slide right (backward)
                        (slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(260)) { -it / 2 } +
                                fadeIn(animationSpec = androidx.compose.animation.core.tween(260)))
                            .togetherWith(
                                slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(220)) { it / 3 } +
                                        fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                            )
                    }
                },
                label = "tabContentTransition"
            ) { tab ->
                when (tab) {
                    AppTab.DASH -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (!isFullScreenMapActive) {
                                TopBar(
                                    isBtConnected = telemetryState.isBtConnected,
                                    isBtConnecting = telemetryState.isBtConnecting,
                                    connectedDeviceName = telemetryState.connectedDeviceName,
                                    onBtClick = {
                                        if (telemetryState.isBtConnected || telemetryState.isBtConnecting) {
                                            telemetryEngine.disconnect()
                                        } else {
                                            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                                            val isEnabled = bm?.adapter?.isEnabled == true
                                            if (!isEnabled) {
                                                promptEnableBluetooth()
                                            } else {
                                                telemetryEngine.connect()
                                            }
                                        }
                                    }
                                )
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                DashScreen(telemetry = telemetryState)
                            }
                        }
                    }
                    AppTab.MAP -> MapScreen(onNavigateToSettings = { selectedTab = AppTab.SETTINGS })
                    AppTab.SERVICE -> MaintenanceScreen(telemetry = telemetryState)
                    AppTab.RIDES -> RidesScreen()
                    AppTab.DEV -> com.kalaiselvan.ride.dev.DevFeatureProviderImpl.RenderDevScreen(modifier = Modifier.fillMaxSize())
                    AppTab.SETTINGS -> SettingsScreen()
                }
            }
        }

        // Bottom Navigation Bar (Hidden in full-screen map mode)
        if (!isFullScreenMapActive) {
            BottomNav(
                selectedTab = selectedTab,
                isDevUnlocked = isDevActive,
                showMapTab = showMapTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.then(if (isLandscape && selectedTab == AppTab.DASH) Modifier else Modifier.navigationBarsPadding())
            )
        }

        // 1. Mandatory Legal & Safety Disclaimer Modal (Must be explicitly accepted before using app)
        if (!userPrefs.hasAcceptedDisclaimer) {
            LegalDisclaimerDialog(
                isMandatory = true,
                onAccept = {
                    appPrefs.setDisclaimerAccepted(true)
                    val mainActivity = context as? com.kalaiselvan.ride.MainActivity
                    mainActivity?.startInitialPermissionSequence()
                }
            )
        } else {
            // 2. Intelligently identify the device and prompt for OEM settings on first launch
            var showFirstLaunchDialog by remember { mutableStateOf(!userPrefs.hasShownFirstLaunchPrompt) }
            if (showFirstLaunchDialog) {
                FirstLaunchDialog(
                    onDismiss = {
                        appPrefs.setFirstLaunchPromptShown(true)
                        showFirstLaunchDialog = false
                    }
                )
            }

            // 3. Road Safety Reminder Popup (Always wear your helmet) - Displays ONLY when connected to bike
            if (showHelmetDialog) {
                HelmetSafetyDialog(
                    autoDismissSeconds = 4,
                    onDismiss = { showHelmetDialog = false }
                )
            }
        }

        // 3. Emergency Crash Alert Countdown Dialog
        val activeCrash by com.kalaiselvan.ride.crash.CrashAlertManager.activeCrash.collectAsState()
        activeCrash?.let {
            com.kalaiselvan.ride.ui.components.CrashCountdownDialog(
                countdownSeconds = userPrefs.crashCountdownSeconds,
                contacts = appPrefs.getCrashEmergencyContacts(),
                onCancel = { com.kalaiselvan.ride.crash.CrashAlertManager.cancelCrash(context) },
                onComplete = { com.kalaiselvan.ride.crash.CrashAlertManager.executeCrashSms(context) }
            )
        }
    }
}
