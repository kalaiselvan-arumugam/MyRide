package com.kalaiselvan.ride.ui.main

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import com.kalaiselvan.ride.ui.components.LegalDisclaimerDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.telemetry.TelemetryEngine
import com.kalaiselvan.ride.theme.BgDark
import com.kalaiselvan.ride.ui.components.AppTab
import com.kalaiselvan.ride.ui.components.BottomNav
import com.kalaiselvan.ride.ui.components.FirstLaunchDialog
import com.kalaiselvan.ride.ui.components.TopBar
import com.kalaiselvan.ride.ui.screens.AchievementsScreen
import com.kalaiselvan.ride.ui.screens.DashScreen
import com.kalaiselvan.ride.ui.screens.DevScreen
import com.kalaiselvan.ride.ui.screens.MaintenanceScreen
import com.kalaiselvan.ride.ui.screens.MapScreen
import com.kalaiselvan.ride.ui.screens.SettingsScreen

@Composable
fun MainAppScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val telemetryEngine = remember { TelemetryEngine.getInstance(context) }
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()

    DisposableEffect(userPrefs.hasAcceptedDisclaimer) {
        if (userPrefs.hasAcceptedDisclaimer) {
            telemetryEngine.start()
        }
        onDispose {
            telemetryEngine.stopUi()
        }
    }

    val telemetryState by telemetryEngine.state.collectAsState()
    var selectedTab by remember { mutableStateOf(AppTab.DASH) }

    val showMapTab = userPrefs.turnByTurnNav && userPrefs.navSource == com.kalaiselvan.ride.preferences.NavSource.OLA_MAPS

    // If MAP tab gets hidden while on MAP tab, fall back to DASH
    LaunchedEffect(showMapTab) {
        if (!showMapTab && selectedTab == AppTab.MAP) {
            selectedTab = AppTab.DASH
        }
    }

    // If DEV mode gets locked while on DEV tab, fall back to DASH
    LaunchedEffect(userPrefs.isDevUnlocked) {
        if (!userPrefs.isDevUnlocked && selectedTab == AppTab.DEV) {
            selectedTab = AppTab.DASH
        }
    }

    androidx.activity.compose.BackHandler(enabled = selectedTab != AppTab.DASH) {
        selectedTab = AppTab.DASH
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
        val shouldConnect = activity?.intent?.getBooleanExtra("ACTION_CONNECT", false) == true ||
                activity?.intent?.getBooleanExtra("ACTION_REQUEST_BT", false) == true
        if (shouldConnect) {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (bm?.adapter?.isEnabled == true) {
                telemetryEngine.connect()
            } else {
                promptEnableBluetooth()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
    ) {
        // Top Bar Header with Secret 5-Tap Developer Mode & Smart Bluetooth Button
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

        // Main Tab Content Area with Smooth Transitions
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
                            slideInVertically(animationSpec = androidx.compose.animation.core.tween(300)) { 30 })
                        .togetherWith(
                            fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                        )
                },
                label = "tabContentTransition"
            ) { tab ->
                when (tab) {
                    AppTab.DASH -> DashScreen(telemetry = telemetryState)
                    AppTab.MAP -> MapScreen(onNavigateToSettings = { selectedTab = AppTab.SETTINGS })
                    AppTab.SERVICE -> MaintenanceScreen(telemetry = telemetryState)
                    AppTab.ACHIEVEMENTS -> AchievementsScreen()
                    AppTab.DEV -> DevScreen()
                    AppTab.SETTINGS -> SettingsScreen()
                }
            }
        }

        // Bottom Navigation Bar (Shows DEV tab when unlocked, MAP tab when Ola Maps active)
        BottomNav(
            selectedTab = selectedTab,
            isDevUnlocked = userPrefs.isDevUnlocked,
            showMapTab = showMapTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.navigationBarsPadding()
        )

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
        }
    }
}
