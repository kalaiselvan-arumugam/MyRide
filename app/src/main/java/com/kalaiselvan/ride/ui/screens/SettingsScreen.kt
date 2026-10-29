package com.kalaiselvan.ride.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.preferences.NavSource
import com.kalaiselvan.ride.preferences.OlaMapStyle
import com.kalaiselvan.ride.preferences.SpeedUnit
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.components.LegalDisclaimerDialog
import com.kalaiselvan.ride.ui.theme.neumorphicCircleOuter
import com.kalaiselvan.ride.ui.theme.neumorphicInner
import com.kalaiselvan.ride.ui.theme.neumorphicOuter
import com.kalaiselvan.ride.utils.DeviceUtils
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()
    val scrollState = rememberScrollState()

    var devTapCount by remember { mutableIntStateOf(0) }
    var devLastTapTime by remember { mutableLongStateOf(0L) }
    var showLegalDisclaimer by remember { mutableStateOf(false) }
    var showBrandDialog by remember { mutableStateOf(false) }
    var brandInput by remember { mutableStateOf(userPrefs.customBrandName) }
    var isNotifListenerEnabled by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // ── 1. Permissions Center Card ──────────────────────────────
        Text(
            text = "SYSTEM PERMISSIONS CENTER",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 20.dp, blurRadius = 10.dp)
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Check permissions dynamically
                val isBtGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isBatteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

                var isCameraGranted by remember {
                    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    isCameraGranted = granted
                }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            isCameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            isNotifListenerEnabled = isNotificationServiceEnabled(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                PermissionRow(
                    title = "Bluetooth & Nearby Devices",
                    subtitle = "Required for bike TFT and ECU connection",
                    isGranted = isBtGranted,
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )

                PermissionRow(
                    title = "Notification Access (Listener)",
                    subtitle = "Mirrors Call, SMS & Navigation TBT to meter",
                    isGranted = isNotifListenerEnabled,
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                PermissionRow(
                    title = "Battery Optimization Whitelist",
                    subtitle = "Prevents Android from stopping telemetry in pocket",
                    isGranted = isBatteryIgnored,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )

                PermissionRow(
                    title = "Camera Access",
                    subtitle = "Required for capturing & scanning service receipts",
                    isGranted = isCameraGranted,
                    onClick = {
                        if (!isCameraGranted) {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                // Show HyperOS Pop-up Windows setting ONLY on Xiaomi / HyperOS / MIUI devices
                if (DeviceUtils.isXiaomiOrHyperOs()) {
                    val isPopUpGranted = DeviceUtils.isXiaomiPopUpGranted(context)
                    PermissionRow(
                        title = "HyperOS Pop-up Windows (Auto-Open)",
                        subtitle = "Enables app to open to screen when ignition turns ON",
                        isGranted = isPopUpGranted,
                        onClick = { DeviceUtils.openXiaomiPopUpSettings(context) }
                    )
                }

                val isCompanionLinked = remember { mutableStateOf(com.kalaiselvan.ride.bluetooth.BikePresenceService.isAssociated(context)) }

                PermissionRow(
                    title = "Ignition Auto-Connect (Hardware Standby)",
                    subtitle = if (isCompanionLinked.value) "Active via OS Companion + Hardware BLE Scanner" else "Active via Hardware BLE Scanner",
                    isGranted = true,
                    onClick = {
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            if (!isCompanionLinked.value) {
                                com.kalaiselvan.ride.bluetooth.BikePresenceService.associate(activity) {
                                    isCompanionLinked.value = true
                                    Toast.makeText(context, "Bike linked for automatic pocket connection!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                com.kalaiselvan.ride.bluetooth.BikePresenceService.disassociate(context)
                                isCompanionLinked.value = false
                                Toast.makeText(context, "Companion link removed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 2. App Preferences Card ─────────────────────────────────
        Text(
            text = "APP CONFIGURATION",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 20.dp, blurRadius = 10.dp)
                .padding(18.dp)
        ) {
            Column {
                // Speed Unit Toggle: KM/H vs MPH
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Speed Units",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (userPrefs.speedUnit == SpeedUnit.KMH) "Displaying in km/h (ECU native)" else "Displaying in mph (Calculated)",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E222A))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UnitPill(
                            label = "KM/H",
                            selected = userPrefs.speedUnit == SpeedUnit.KMH,
                            onClick = { appPrefs.setSpeedUnit(SpeedUnit.KMH) }
                        )
                        UnitPill(
                            label = "MPH",
                            selected = userPrefs.speedUnit == SpeedUnit.MPH,
                            onClick = { appPrefs.setSpeedUnit(SpeedUnit.MPH) }
                        )
                    }
                }

                Divider()

                // Parked Location Expiry Dropdown
                var isExpiryDropdownExpanded by remember { mutableStateOf(false) }
                val expiryOptions = listOf(1 to "1h", 3 to "3h", 6 to "6h", 12 to "12h", 24 to "24h")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Parked Location Expiry",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Expire 'Take me to the bike' after ${userPrefs.parkedExpiryHours}h",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E222A))
                                .clickable { isExpiryDropdownExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${userPrefs.parkedExpiryHours}h",
                                    color = RpmGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "▼",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isExpiryDropdownExpanded,
                            onDismissRequest = { isExpiryDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E222A))
                        ) {
                            expiryOptions.forEach { (hours, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "$hours ${if (hours == 1) "Hour" else "Hours"} ($label)",
                                                color = if (userPrefs.parkedExpiryHours == hours) RpmGreen else TextMain,
                                                fontSize = 12.sp,
                                                fontWeight = if (userPrefs.parkedExpiryHours == hours) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (userPrefs.parkedExpiryHours == hours) {
                                                Text(text = " ✓", color = RpmGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    },
                                    onClick = {
                                        appPrefs.setParkedExpiryHours(hours)
                                        isExpiryDropdownExpanded = false
                                        Toast.makeText(context, "Parked location expiry set to $label", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                Divider()

                // Dashboard Brand Name (Customizable, max 15 chars, letters only)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            brandInput = userPrefs.customBrandName
                            showBrandDialog = true
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Dashboard Brand Name",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Cluster header • Tap to rename (15 chars max, letters only)",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E222A))
                            .border(1.dp, Color(0xFF2C3440), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userPrefs.customBrandName.ifBlank { "MyRide" }.uppercase(),
                            color = RpmGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Divider()

                // Currency Preference Dropdown
                var isCurrencyDropdownExpanded by remember { mutableStateOf(false) }
                val currencyOptions = remember {
                    val defaultSymbol = AppPreferences.getDefaultCurrencySymbol()
                    val baseList = listOf(
                        "₹" to "INR (₹) - Indian Rupee",
                        "$" to "USD ($) - US Dollar",
                        "€" to "EUR (€) - Euro",
                        "£" to "GBP (£) - British Pound",
                        "¥" to "JPY (¥) - Japanese / Chinese",
                        "C$" to "CAD (C$) - Canadian Dollar",
                        "A$" to "AUD (A$) - Australian Dollar",
                        "CHF" to "CHF - Swiss Franc",
                        "R$" to "BRL (R$) - Brazilian Real",
                        "AED" to "AED - UAE Dirham",
                        "₩" to "KRW (₩) - South Korean Won",
                        "฿" to "THB (฿) - Thai Baht",
                        "RM" to "MYR (RM) - Malaysian Ringgit",
                        "₱" to "PHP (₱) - Philippine Peso",
                        "Rp" to "IDR (Rp) - Indonesian Rupiah",
                        "NZ$" to "NZD (NZ$) - New Zealand Dollar",
                        "zł" to "PLN (zł) - Polish Zloty",
                        "kr" to "SEK (kr) - Swedish Krona",
                        "ZAR" to "ZAR - South African Rand"
                    )
                    if (baseList.none { it.first == defaultSymbol }) {
                        val currCode = try { java.util.Currency.getInstance(Locale.getDefault()).currencyCode } catch (_: Exception) { defaultSymbol }
                        listOf(defaultSymbol to "$currCode ($defaultSymbol) - System Default") + baseList
                    } else {
                        baseList
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Currency Symbol",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Used for maintenance & service logs (Default: ${AppPreferences.getDefaultCurrencySymbol()})",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E222A))
                                .clickable { isCurrencyDropdownExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = userPrefs.preferredCurrency,
                                    color = RpmGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "▼",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isCurrencyDropdownExpanded,
                            onDismissRequest = { isCurrencyDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E222A))
                        ) {
                            currencyOptions.forEach { (symbol, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (userPrefs.preferredCurrency == symbol) RpmGreen else TextMain,
                                                fontSize = 12.sp,
                                                fontWeight = if (userPrefs.preferredCurrency == symbol) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (userPrefs.preferredCurrency == symbol) {
                                                Text(text = " ✓", color = RpmGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    },
                                    onClick = {
                                        appPrefs.setPreferredCurrency(symbol)
                                        isCurrencyDropdownExpanded = false
                                        Toast.makeText(context, "Currency set to $symbol", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                SettingToggleRow(
                    label = "Call Notifications Mirroring",
                    checked = userPrefs.callNotifications,
                    onCheckedChange = { appPrefs.setCallNotifications(it) }
                )

                Divider()

                SettingToggleRow(
                    label = "SMS & Messaging Notifications",
                    checked = userPrefs.smsNotifications,
                    onCheckedChange = { appPrefs.setSmsNotifications(it) }
                )

                Divider()

                SettingToggleRow(
                    label = "Turn-by-Turn Navigation",
                    checked = userPrefs.turnByTurnNav,
                    onCheckedChange = { appPrefs.setTurnByTurnNav(it) }
                )

                // ── Navigation Source Dropdown (Gated by turnByTurnNav) ──
                var isNavSourceDropdownExpanded by remember { mutableStateOf(false) }
                val isNavEnabled = userPrefs.turnByTurnNav
                val navSourceOptions = listOf(
                    NavSource.DEFAULT_MAP to "Default Map (Google Maps)",
                    NavSource.OLA_MAPS to "Ola Maps (In-App Routing)"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .alpha(if (isNavEnabled) 1.0f else 0.4f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Navigation Source",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (!isNavEnabled) {
                                "Turn-by-Turn navigation is disabled"
                            } else if (userPrefs.navSource == NavSource.DEFAULT_MAP) {
                                "Mirroring turns from Google Maps notifications"
                            } else {
                                "Native turn-by-turn routing using Ola Maps"
                            },
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E222A))
                                .clickable(enabled = isNavEnabled) { isNavSourceDropdownExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (userPrefs.navSource == NavSource.DEFAULT_MAP) "DEFAULT" else "OLA MAPS",
                                    color = if (isNavEnabled) RpmGreen else TextMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "▼",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isNavSourceDropdownExpanded && isNavEnabled,
                            onDismissRequest = { isNavSourceDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E222A))
                        ) {
                            navSourceOptions.forEach { (source, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (userPrefs.navSource == source) RpmGreen else TextMain,
                                                fontSize = 12.sp,
                                                fontWeight = if (userPrefs.navSource == source) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (userPrefs.navSource == source) {
                                                Text(text = " ✓", color = RpmGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    },
                                    onClick = {
                                        appPrefs.setNavSource(source)
                                        isNavSourceDropdownExpanded = false
                                        Toast.makeText(context, "Navigation Source: $label", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Ola Maps API Key Card (Visible when Ola Maps selected or configured) ──
                if (isNavEnabled && userPrefs.navSource == NavSource.OLA_MAPS) {
                    var apiKeyInput by remember(userPrefs.olaMapsApiKey) { mutableStateOf(userPrefs.olaMapsApiKey) }

                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14171E))
                            .border(1.dp, Color(0xFF222834), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OLA MAPS API KEY",
                                    color = if (userPrefs.olaMapsApiKey.isNotBlank()) TextMain else Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            Text(
                                text = "Required for in-app routing & map tiles (maps.olakrutrim.com)",
                                color = TextMuted,
                                fontSize = 10.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F1218))
                                    .border(1.dp, Color(0xFF1F2430), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = TextMain,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (apiKeyInput.isEmpty()) {
                                            Text(
                                                text = "Paste your Ola Maps API key here",
                                                color = Color(0xFF555E6E),
                                                fontSize = 11.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (apiKeyInput.trim() != userPrefs.olaMapsApiKey) RpmGreen else Color(0xFF222834))
                                        .clickable {
                                            appPrefs.setOlaMapsApiKey(apiKeyInput)
                                            Toast.makeText(context, if (apiKeyInput.isNotBlank()) "Ola Maps API key saved!" else "API key cleared", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "SAVE",
                                        color = if (apiKeyInput.trim() != userPrefs.olaMapsApiKey) Color.Black else TextMuted,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Divider()

                    // ── Map Style Dropdown ──
                    var isMapStyleDropdownExpanded by remember { mutableStateOf(false) }
                    val mapStyleOptions = OlaMapStyle.entries.map { it to it.displayName }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Map Style",
                                color = TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Visual theme for OLA Maps",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E222A))
                                    .clickable { isMapStyleDropdownExpanded = true }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = userPrefs.olaMapStyle.displayName.uppercase(),
                                        color = RpmGreen,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "▼",
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isMapStyleDropdownExpanded,
                                onDismissRequest = { isMapStyleDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1E222A))
                            ) {
                                mapStyleOptions.forEach { (style, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (userPrefs.olaMapStyle == style) RpmGreen else TextMain,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (userPrefs.olaMapStyle == style) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (userPrefs.olaMapStyle == style) {
                                                    Text(text = " ✓", color = RpmGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        },
                                        onClick = {
                                            appPrefs.setOlaMapStyle(style)
                                            isMapStyleDropdownExpanded = false
                                            Toast.makeText(context, "Map style: $label", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Divider()

                    // Auto-choose Fastest Route Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Auto-choose Fastest Route",
                                color = TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (userPrefs.autoChooseFastestRoute)
                                    "Automatically selects fastest route. Disable to tap and pick alternate routes on map."
                                else
                                    "Multiple routes shown on map. Tap any polyline to choose route.",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        NeumorphicSwitch(
                            checked = userPrefs.autoChooseFastestRoute,
                            onCheckedChange = { appPrefs.setAutoChooseFastestRoute(it) }
                        )
                    }
                }

                // Show notification access banner ONLY when using Default Map (Google Maps Notifications)
                if (userPrefs.turnByTurnNav && userPrefs.navSource == NavSource.DEFAULT_MAP && !isNotifListenerEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFB300))
                            .border(1.dp, Color(0x66FFB300), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "⚠️", fontSize = 13.sp)
                                Text(
                                    text = "Notification Access Required",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Android blocks apps from reading Google Maps turn-by-turn directions until Notification Access is enabled. On Xiaomi / HyperOS: if grayed out, first open App Info -> 3 dots (top right) -> 'Allow restricted settings'.",
                                color = TextMain,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFB300))
                                    .clickable {
                                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "ENABLE NOTIFICATION ACCESS",
                                    color = Color.Black,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }



                Divider()

                SettingToggleRow(
                    label = "Phone Battery Mirroring",
                    checked = userPrefs.phoneBatteryMirroring,
                    onCheckedChange = { appPrefs.setPhoneBatteryMirroring(it) }
                )

                Divider()

                SettingToggleRow(
                    label = "Dash Clock Synchronization",
                    checked = userPrefs.dashClockSync,
                    onCheckedChange = { appPrefs.setDashClockSync(it) }
                )

                Divider()

                SettingToggleRow(
                    label = "Keep Dashboard Screen Active",
                    checked = userPrefs.keepDashboardScreenOn,
                    onCheckedChange = { appPrefs.setKeepDashboardScreenOn(it) }
                )
            }
        }

        // ── 3. Developer Mode Card (Visible when unlocked) ──────────
        if (userPrefs.isDevUnlocked) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "DEVELOPER OPTIONS",
                color = RpmOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphicOuter(cornerRadius = 20.dp, blurRadius = 10.dp)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingToggleRow(
                        label = "Show Developer & Hex Tab",
                        checked = userPrefs.isDevUnlocked,
                        onCheckedChange = { 
                            appPrefs.setDevUnlocked(it)
                            if (!it) {
                                Toast.makeText(context, "Developer Mode Locked & Hidden", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Reset Achievements Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x22FF003C))
                .clickable {
                    appPrefs.resetAchievements()
                    Toast.makeText(context, "Achievements Reset", Toast.LENGTH_SHORT).show()
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "RESET ACHIEVEMENTS",
                color = RpmRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 4. Legal & Compliance Card ──────────────────────────────
        Text(
            text = "LEGAL & SAFETY COMPLIANCE",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 20.dp, blurRadius = 10.dp)
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLegalDisclaimer = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Safety Disclaimer & Liability",
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Review terms of use, data latency warnings & closed-course limitations",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x2E00E676))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VIEW",
                        color = RpmGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 5. Developer / About Card ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 20.dp, blurRadius = 10.dp)
                .padding(vertical = 24.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Layer icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .neumorphicCircleOuter(blurRadius = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val w = size.width
                        val h = size.height
                        val topPath = Path().apply {
                            moveTo(w * 0.5f, h * 0.10f)
                            lineTo(w * 0.9f, h * 0.32f)
                            lineTo(w * 0.5f, h * 0.54f)
                            lineTo(w * 0.1f, h * 0.32f)
                            close()
                        }
                        drawPath(path = topPath, color = RpmGreen, style = Stroke(width = 2.dp.toPx()))

                        val midChevrons = Path().apply {
                            moveTo(w * 0.1f, h * 0.54f)
                            lineTo(w * 0.5f, h * 0.74f)
                            lineTo(w * 0.9f, h * 0.54f)
                        }
                        drawPath(path = midChevrons, color = RpmGreen, style = Stroke(width = 2.dp.toPx()))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "MyRide",
                    color = TextMain,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Clean Native Telemetry & TFT Mirroring for your bike",
                    color = TextMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Text(
                    text = "Developed by",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Text(
                    text = "Kalaiselvan Arumugam",
                    color = RpmGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kalaiselvan-arumugam/MyRide"))
                            context.startActivity(intent)
                        }
                )

                // Version text with Secret 5-Tap trigger
                Text(
                    text = run {
                    val vName = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (_: Exception) {
                        "1.0.0"
                    }
                    "Version ${vName ?: "1.0.0"}"
                },
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val now = System.currentTimeMillis()
                            if (now - devLastTapTime > 1500) {
                                devTapCount = 1
                            } else {
                                devTapCount++
                            }
                            devLastTapTime = now

                            val isUnlocked = userPrefs.isDevUnlocked
                            if (isUnlocked) {
                                when (devTapCount) {
                                    3 -> Toast.makeText(context, "2 taps to lock Developer Mode...", Toast.LENGTH_SHORT).show()
                                    4 -> Toast.makeText(context, "1 tap to lock Developer Mode...", Toast.LENGTH_SHORT).show()
                                    5 -> {
                                        appPrefs.setDevUnlocked(false)
                                        Toast.makeText(context, "Developer Mode Locked & Hidden", Toast.LENGTH_LONG).show()
                                        devTapCount = 0
                                    }
                                }
                            } else {
                                when (devTapCount) {
                                    3 -> Toast.makeText(context, "You are 2 steps away from Developer Mode...", Toast.LENGTH_SHORT).show()
                                    4 -> Toast.makeText(context, "You are 1 step away from Developer Mode...", Toast.LENGTH_SHORT).show()
                                    5 -> {
                                        appPrefs.setDevUnlocked(true)
                                        Toast.makeText(context, "Developer Mode Unlocked!", Toast.LENGTH_LONG).show()
                                        devTapCount = 0
                                    }
                                }
                            }
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showLegalDisclaimer) {
        LegalDisclaimerDialog(
            isMandatory = false,
            onDismiss = { showLegalDisclaimer = false }
        )
    }

    if (showBrandDialog) {
        Dialog(onDismissRequest = { showBrandDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, Color(0xFF2C3440), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Rename Dashboard Brand",
                        color = TextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enter custom header text for your speedometer cluster. Characters only, up to 15 letters.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    // Text Input Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDark)
                            .border(1.dp, Color(0xFF2C3440), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = brandInput,
                            onValueChange = { newText ->
                                if (newText.length <= 15 && newText.all { it.isLetter() || it.isWhitespace() }) {
                                    brandInput = newText
                                }
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = TextMain,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(AccentGreen),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (brandInput.isEmpty()) {
                            Text(
                                text = "MyRide",
                                color = TextMuted.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Character counter & Cluster Preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val preview = brandInput.ifBlank { "MyRide" }.uppercase()
                        Text(
                            text = "Preview: $preview",
                            color = RpmGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${brandInput.length}/15",
                            color = if (brandInput.length >= 15) RpmOrange else TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AFFFFFF))
                                .clickable {
                                    appPrefs.setCustomBrandName("MyRide")
                                    showBrandDialog = false
                                    Toast.makeText(context, "Brand reset to default (MyRide)", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "RESET",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AFFFFFF))
                                .clickable { showBrandDialog = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CANCEL",
                                color = TextMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentGreen)
                                .clickable {
                                    val toSave = brandInput.trim().ifBlank { "MyRide" }
                                    appPrefs.setCustomBrandName(toSave)
                                    showBrandDialog = false
                                    Toast.makeText(context, "Brand updated to $toSave", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SAVE",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) RpmGreen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) BgDark else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextMain,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isGranted) Color(0x2E00E676) else Color(0x2EFF1744))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isGranted) "GRANTED" else "ALLOW",
                color = if (isGranted) RpmGreen else RpmRed,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextMain,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        NeumorphicSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = Color(0x0DFFFFFF),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    )
}

@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "switchOffset"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) RpmGreen else TextMuted,
        label = "switchThumbColor"
    )

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .neumorphicInner(cornerRadius = 12.dp, shadowWidth = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .drawBehind {
                    if (checked) {
                        drawCircle(color = Color(0x6600FF88), radius = size.minDimension / 1.5f)
                    }
                    drawCircle(color = thumbColor)
                }
        )
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val comp1 = ComponentName(context, com.kalaiselvan.ride.notifications.BikeNotificationListenerService::class.java).flattenToString()
    return flat.contains(comp1)
}
