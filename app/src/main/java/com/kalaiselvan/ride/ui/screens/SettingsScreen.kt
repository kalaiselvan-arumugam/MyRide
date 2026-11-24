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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.kalaiselvan.ride.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.content.ContextCompat
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.preferences.NavSource
import com.kalaiselvan.ride.preferences.SpeedUnit
import com.kalaiselvan.ride.preferences.TemperatureUnit
import com.kalaiselvan.ride.preferences.ThemeMode
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.components.FlatIcon
import com.kalaiselvan.ride.ui.components.FlatIconType
import com.kalaiselvan.ride.ui.components.LegalDisclaimerDialog
import com.kalaiselvan.ride.ui.theme.neumorphicCircleOuter
import com.kalaiselvan.ride.ui.theme.neumorphicInner
import com.kalaiselvan.ride.ui.theme.neumorphicOuter
import com.kalaiselvan.ride.utils.DeviceUtils
import java.util.Locale
import androidx.documentfile.provider.DocumentFile
import com.kalaiselvan.ride.backup.BackupInspectionResult
import com.kalaiselvan.ride.backup.BackupScheduler
import com.kalaiselvan.ride.preferences.BackupSchedule
import com.kalaiselvan.ride.backup.BackupManager
import com.kalaiselvan.ride.backup.BackupManifest
import com.kalaiselvan.ride.backup.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import com.kalaiselvan.ride.crash.EmergencySmsManager
import com.kalaiselvan.ride.crash.SimInfo
import com.kalaiselvan.ride.preferences.CrashSensitivity
import com.kalaiselvan.ride.preferences.EmergencyContact

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()
    val theme = LocalThemeColors.current
    val scrollState = rememberScrollState()

    var devTapCount by remember { mutableIntStateOf(0) }
    var devLastTapTime by remember { mutableLongStateOf(0L) }
    var showLegalDisclaimer by remember { mutableStateOf(false) }
    var showBrandDialog by remember { mutableStateOf(false) }
    var brandInput by remember { mutableStateOf(userPrefs.customBrandName) }
    var isNotifListenerEnabled by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    // Collapsible Section Expansion States (All collapsed by default)
    var isPermissionsExpanded by remember { mutableStateOf(false) }
    var isAppConfigExpanded by remember { mutableStateOf(false) }
    var isDataBackupExpanded by remember { mutableStateOf(false) }
    var isDevModeExpanded by remember { mutableStateOf(false) }
    var isLegalExpanded by remember { mutableStateOf(false) }
    var isAboutExpanded by remember { mutableStateOf(false) }
    var isExperimentalExpanded by remember { mutableStateOf(false) }

    // Profile state
    var showRiderNameDialog by remember { mutableStateOf(false) }
    var riderNameInput by remember { mutableStateOf(userPrefs.riderName) }
    var showResetAchievementsDialog by remember { mutableStateOf(false) }

    // Crash Detection / Experimental state
    var availableSims by remember { mutableStateOf<List<SimInfo>>(emptyList()) }
    var crashContacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var pendingContactToAdd by remember { mutableStateOf<EmergencyContact?>(null) }
    var contactToRemove by remember { mutableStateOf<EmergencyContact?>(null) }
    var isSimDropdownExpanded by remember { mutableStateOf(false) }
    var isSensitivityDropdownExpanded by remember { mutableStateOf(false) }
    var isCountdownDropdownExpanded by remember { mutableStateOf(false) }
    var showEditSimNumberDialog by remember { mutableStateOf(false) }
    var simNumberInput by remember { mutableStateOf("") }
    var simNumberSubIdToEdit by remember { mutableIntStateOf(-1) }

    // Runtime permission launcher for SEND_SMS + READ_PHONE_STATE + READ_PHONE_NUMBERS
    var pendingCrashEnable by remember { mutableStateOf(false) }
    val crashPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.SEND_SMS] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val phoneGranted = results[Manifest.permission.READ_PHONE_STATE] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (smsGranted && phoneGranted) {
            if (pendingCrashEnable) {
                appPrefs.setCrashDetectionEnabled(true)
                pendingCrashEnable = false
            }
            availableSims = EmergencySmsManager.getAvailableSims(context)
        } else {
            pendingCrashEnable = false
            Toast.makeText(context, "SMS and Phone permissions required for crash detection", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(userPrefs.crashDetectionEnabled) {
        if (userPrefs.crashDetectionEnabled) {
            val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
            } else true
            if (!hasPhoneNumbers && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                crashPermissionLauncher.launch(
                    arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
                )
            } else {
                availableSims = EmergencySmsManager.getAvailableSims(context)
            }
            crashContacts = appPrefs.getCrashEmergencyContacts()
        }
    }

    LaunchedEffect(userPrefs.crashEmergencyContacts) {
        crashContacts = appPrefs.getCrashEmergencyContacts()
    }

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                        val rawNumber = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                        val number = rawNumber.replace("[^0-9+]".toRegex(), "")
                        if (number.isNotBlank()) {
                            val currentList = appPrefs.getCrashEmergencyContacts()
                            val alreadyAdded = currentList.any { c -> c.number == number }
                            // If already added, ignore the prompt
                            if (!alreadyAdded && currentList.size < 3) {
                                pendingContactToAdd = EmergencyContact(name = name, number = number)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ContactPicker", "Failed to query contact: ${e.message}", e)
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Backup & Restore State
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var pendingScheduleAfterConfig by remember { mutableStateOf<BackupSchedule?>(null) }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var restorePasswordInput by remember { mutableStateOf("") }
    var restorePasswordError by remember { mutableStateOf<String?>(null) }
    var isVerifyingPassword by remember { mutableStateOf(false) }

    var restoreManifestToConfirm by remember { mutableStateOf<BackupManifest?>(null) }
    var restorePasswordForExecution by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showBackupProgressDialog by remember { mutableStateOf(false) }
    var backupProgressTitle by remember { mutableStateOf("") }
    var backupProgress by remember { mutableFloatStateOf(0f) }
    var backupStatusText by remember { mutableStateOf("") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (_: Exception) {}

            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val folderName = doc?.name ?: "Selected Folder"
            appPrefs.setAutoBackupFolder(treeUri.toString(), folderName)

            // Forced encryption by default: collect key if not set
            if (appPrefs.getAutoBackupPassword().isBlank()) {
                passwordInput = ""
                confirmPasswordInput = ""
                passwordError = null
                showSetPasswordDialog = true
            } else {
                val sched = pendingScheduleAfterConfig ?: if (userPrefs.autoBackupSchedule != BackupSchedule.NEVER) userPrefs.autoBackupSchedule else BackupSchedule.DAILY
                appPrefs.setAutoBackupSchedule(sched)
                appPrefs.setAutoBackupEncrypt(true)
                appPrefs.setAutoBackupEnabled(true)
                BackupScheduler.scheduleNextBackup(context)
                pendingScheduleAfterConfig = null
                Toast.makeText(context, "Automated backup enabled: ${sched.displayName}", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (userPrefs.autoBackupFolderUri.isBlank()) {
                appPrefs.setAutoBackupEnabled(false)
                pendingScheduleAfterConfig = null
            }
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destUri ->
        if (destUri != null) {
            isBackingUp = true
            backupProgressTitle = "Creating Backup"
            backupProgress = 0.05f
            backupStatusText = "Preparing data archive..."
            showBackupProgressDialog = true

            coroutineScope.launch(Dispatchers.IO) {
                val pass = if (userPrefs.autoBackupEncrypt) appPrefs.getAutoBackupPassword() else null
                val ok = context.contentResolver.openOutputStream(destUri)?.use { out ->
                    BackupManager.createBackup(context, out, pass) { progress, status ->
                        backupProgress = progress
                        backupStatusText = status
                    }
                } ?: false

                withContext(Dispatchers.Main) {
                    isBackingUp = false
                    showBackupProgressDialog = false
                    if (ok) {
                        appPrefs.setLastBackupTimestamp(System.currentTimeMillis())
                        Toast.makeText(context, "Backup saved successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to create backup", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val restoreFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri != null) {
            pendingRestoreUri = pickedUri
            coroutineScope.launch(Dispatchers.IO) {
                val result = context.contentResolver.openInputStream(pickedUri)?.use { inStream ->
                    BackupManager.inspectBackup(inStream, password = null)
                } ?: BackupInspectionResult.Error("Could not open selected file")

                withContext(Dispatchers.Main) {
                    when (result) {
                        is BackupInspectionResult.Success -> {
                            restoreManifestToConfirm = result.manifest
                            restorePasswordForExecution = null
                            showRestoreConfirmDialog = true
                        }
                        is BackupInspectionResult.PasswordRequired, is BackupInspectionResult.InvalidPassword -> {
                            restorePasswordInput = ""
                            restorePasswordError = null
                            showRestorePasswordDialog = true
                        }
                        is BackupInspectionResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // ── 0. Profile Card ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Text(
                text = "PROFILE",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Rider Name row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        riderNameInput = userPrefs.riderName
                        showRiderNameDialog = true
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your Name", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = userPrefs.riderName.ifBlank { "Not set — tap to add" },
                        color = if (userPrefs.riderName.isBlank()) TextMuted.copy(alpha = 0.5f) else AccentGreen,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipBg)
                        .border(1.dp, ChipBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    FlatIcon(
                        type = FlatIconType.EDIT,
                        tint = TextMuted,
                        size = 14.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 1. Permissions Center Card ──────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isPermissionsExpanded) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp) else RoundedCornerShape(14.dp))
                    .clickable { isPermissionsExpanded = !isPermissionsExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "SYSTEM PERMISSIONS CENTER",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (isPermissionsExpanded) "Manage Bluetooth, Location, Camera & Notifications" else "Bluetooth, Location, Battery, Camera, Notifications",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                FlatIcon(
                    type = if (isPermissionsExpanded) FlatIconType.CHEVRON_UP else FlatIconType.CHEVRON_DOWN,
                    tint = TextMuted,
                    size = 18.dp
                )
            }

            AnimatedVisibility(
                visible = isPermissionsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                    subtitle = "Mirrors calls, SMS & turn-by-turn navigation to motorcycle display",
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 2. App Preferences Card ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isAppConfigExpanded) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp) else RoundedCornerShape(14.dp))
                    .clickable { isAppConfigExpanded = !isAppConfigExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "APP CONFIGURATION",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (isAppConfigExpanded) "Theme, Units, Display, Brand & Navigation" else "${userPrefs.themeMode.displayName} • ${userPrefs.speedUnit.name} • ${userPrefs.temperatureUnit.name}",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                FlatIcon(
                    type = if (isAppConfigExpanded) FlatIconType.CHEVRON_UP else FlatIconType.CHEVRON_DOWN,
                    tint = TextMuted,
                    size = 18.dp
                )
            }

            AnimatedVisibility(
                visible = isAppConfigExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    // Theme Mode Selector: Dark vs Light vs System
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Appearance & Theme",
                                color = TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Select your preferred visual style",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            UnitPill(
                                label = "DARK",
                                selected = userPrefs.themeMode == ThemeMode.DARK,
                                onClick = { appPrefs.setThemeMode(ThemeMode.DARK) }
                            )
                            UnitPill(
                                label = "LIGHT",
                                selected = userPrefs.themeMode == ThemeMode.LIGHT,
                                onClick = { appPrefs.setThemeMode(ThemeMode.LIGHT) }
                            )
                        }
                    }

                    Divider()

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
                            .background(ChipBg)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
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

                // Temperature Unit Toggle: Celsius vs Fahrenheit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Temperature Units",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (userPrefs.temperatureUnit == TemperatureUnit.CELSIUS) "Displaying in Celsius (°C)" else "Displaying in Fahrenheit (°F)",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChipBg)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UnitPill(
                            label = "°C",
                            selected = userPrefs.temperatureUnit == TemperatureUnit.CELSIUS,
                            onClick = { appPrefs.setTemperatureUnit(TemperatureUnit.CELSIUS) }
                        )
                        UnitPill(
                            label = "°F",
                            selected = userPrefs.temperatureUnit == TemperatureUnit.FAHRENHEIT,
                            onClick = { appPrefs.setTemperatureUnit(TemperatureUnit.FAHRENHEIT) }
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
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
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
                            modifier = Modifier.background(BgCard)
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
                            .background(ChipBg)
                            .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
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
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
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
                                    color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
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
                            modifier = Modifier.background(BgCard)
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
                    enabled = isNotifListenerEnabled,
                    subtitle = if (!isNotifListenerEnabled) "Requires Notification Access in Android Settings" else null,
                    onCheckedChange = { appPrefs.setCallNotifications(it) }
                )

                Divider()

                SettingToggleRow(
                    label = "SMS & Messaging Notifications",
                    checked = userPrefs.smsNotifications,
                    enabled = isNotifListenerEnabled,
                    subtitle = if (!isNotifListenerEnabled) "Requires Notification Access in Android Settings" else null,
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
                    NavSource.DEFAULT_MAP to "External Map (Google Maps, Waze, HERE)",
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
                                "Mirroring turns from Google Maps, Waze, or HERE"
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
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
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
                                    color = if (!isNavEnabled) TextMuted else if (theme.isLight) Color(0xFF008F48) else RpmGreen,
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
                            modifier = Modifier.background(BgCard)
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
                            .background(DialTrackBg)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
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
                                    color = if (userPrefs.olaMapsApiKey.isNotBlank()) TextMain else if (theme.isLight) Color(0xFFE65100) else Color(0xFFFFD54F),
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
                                    .background(BgDark)
                                    .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
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
                                                color = TextMuted.copy(alpha = 0.6f),
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
                                        .background(if (apiKeyInput.trim() != userPrefs.olaMapsApiKey) (if (theme.isLight) Color(0xFF00C853) else RpmGreen) else ChipBg)
                                        .border(1.dp, ChipBorder, RoundedCornerShape(6.dp))
                                        .clickable {
                                            appPrefs.setOlaMapsApiKey(apiKeyInput)
                                            Toast.makeText(context, if (apiKeyInput.isNotBlank()) "Ola Maps API key saved!" else "API key cleared", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "SAVE",
                                        color = if (apiKeyInput.trim() != userPrefs.olaMapsApiKey) (if (theme.isLight) Color.White else Color.Black) else TextMuted,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
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

                Divider()

                SettingToggleRow(
                    label = "Auto-Rotate Dashboard",
                    checked = userPrefs.autoRotateDashboard,
                    onCheckedChange = { appPrefs.setAutoRotateDashboard(it) }
                )

                Divider()

                // Reset Achievements Row (moved inside App Configuration with matching outline)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1AFF003C))
                        .border(1.dp, Color(0x66FF003C), RoundedCornerShape(12.dp))
                        .clickable {
                            showResetAchievementsDialog = true
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
            }
        }
        }

        // ── DATA & BACKUP CARD ────────────────────────────────────
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isDataBackupExpanded) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp) else RoundedCornerShape(14.dp))
                    .clickable { isDataBackupExpanded = !isDataBackupExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "DATA & BACKUP",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (isDataBackupExpanded) "Automated backup schedule, restore & encryption" else if (userPrefs.autoBackupEnabled) "Automated: ${userPrefs.autoBackupSchedule.displayName}" else "Automated Backup Disabled",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                FlatIcon(
                    type = if (isDataBackupExpanded) FlatIconType.CHEVRON_UP else FlatIconType.CHEVRON_DOWN,
                    tint = TextMuted,
                    size = 18.dp
                )
            }

            AnimatedVisibility(
                visible = isDataBackupExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                // 1. Single Backup Schedule Dropdown (Controls automated backup; no separate toggle button)
                var isScheduleDropdownExpanded by remember { mutableStateOf(false) }
                val currentSchedule = if (userPrefs.autoBackupEnabled) userPrefs.autoBackupSchedule else BackupSchedule.NEVER

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Backup Schedule",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = currentSchedule.description,
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
                                .clickable { isScheduleDropdownExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = currentSchedule.displayName.uppercase(),
                                    color = if (currentSchedule != BackupSchedule.NEVER) (if (theme.isLight) Color(0xFF008F48) else RpmGreen) else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "▼",
                                    color = TextMuted,
                                    fontSize = 8.sp
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isScheduleDropdownExpanded,
                            onDismissRequest = { isScheduleDropdownExpanded = false },
                            modifier = Modifier.background(BgCard)
                        ) {
                            listOf(BackupSchedule.NEVER, BackupSchedule.DAILY, BackupSchedule.WEEKLY).forEach { schedule ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                                Text(
                                                    text = schedule.displayName,
                                                    color = if (currentSchedule == schedule) RpmGreen else TextMain,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (currentSchedule == schedule) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = schedule.description,
                                                    color = TextMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            if (currentSchedule == schedule) {
                                                Text(text = " ✓", color = RpmGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    },
                                    onClick = {
                                        isScheduleDropdownExpanded = false
                                        if (schedule == BackupSchedule.NEVER) {
                                            appPrefs.setAutoBackupEnabled(false)
                                            appPrefs.setAutoBackupSchedule(BackupSchedule.NEVER)
                                            BackupScheduler.cancelBackup(context)
                                            Toast.makeText(context, "Automated backup disabled", Toast.LENGTH_SHORT).show()
                                        } else {
                                            pendingScheduleAfterConfig = schedule
                                            if (userPrefs.autoBackupFolderUri.isBlank()) {
                                                folderPickerLauncher.launch(null)
                                            } else if (appPrefs.getAutoBackupPassword().isBlank()) {
                                                passwordInput = ""
                                                confirmPasswordInput = ""
                                                passwordError = null
                                                showSetPasswordDialog = true
                                            } else {
                                                appPrefs.setAutoBackupSchedule(schedule)
                                                appPrefs.setAutoBackupEncrypt(true)
                                                appPrefs.setAutoBackupEnabled(true)
                                                BackupScheduler.scheduleNextBackup(context)
                                                pendingScheduleAfterConfig = null
                                                Toast.makeText(context, "Backup schedule: ${schedule.displayName}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Destination path display & Change Password link (Only when backup is active and folder chosen)
                if (currentSchedule != BackupSchedule.NEVER && userPrefs.autoBackupFolderUri.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DialTrackBg)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "BACKUP DESTINATION",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = userPrefs.autoBackupFolderName.ifBlank { "Not configured" },
                                color = if (userPrefs.autoBackupFolderName.isBlank()) TextMuted else (if (theme.isLight) Color(0xFF008F48) else RpmGreen),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Text(
                            text = "CHANGE",
                            color = TextMain,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(6.dp))
                                .clickable { folderPickerLauncher.launch(null) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Subtle link to change password
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Change Password",
                            color = if (theme.isLight) Color(0xFF008F48) else AccentGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable {
                                    passwordInput = ""
                                    confirmPasswordInput = ""
                                    passwordError = null
                                    showSetPasswordDialog = true
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        )
                    }
                }

                Divider()

                // 3. Manual Backup & Restore Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Back Up Now Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (theme.isLight) Color(0x1F00C853) else Color(0x1400E676))
                            .border(1.dp, if (theme.isLight) Color(0x4D00C853) else Color(0x3300E676), RoundedCornerShape(12.dp))
                            .clickable(enabled = !isBackingUp) {
                                if (appPrefs.getAutoBackupPassword().isBlank()) {
                                    passwordInput = ""
                                    confirmPasswordInput = ""
                                    passwordError = null
                                    showSetPasswordDialog = true
                                    Toast.makeText(context, "Please set a backup password first", Toast.LENGTH_SHORT).show()
                                } else {
                                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    createBackupLauncher.launch("MyRide_Backup_$timeStamp.myride")
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBackingUp) "BACKING UP (${(backupProgress * 100).toInt()}%)..." else "BACK UP NOW",
                            color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Restore Backup Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ChipBg)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                            .clickable(enabled = !isRestoring) {
                                restoreFilePickerLauncher.launch(arrayOf("*/*"))
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isRestoring) "RESTORING (${(backupProgress * 100).toInt()}%)..." else "RESTORE BACKUP",
                            color = TextMain,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // 4. Last backup timestamp display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lastBackupText = if (userPrefs.lastBackupTimestampMs > 0) {
                        SimpleDateFormat("dd MMM, yyyy • HH:mm", Locale.getDefault()).format(Date(userPrefs.lastBackupTimestampMs))
                    } else {
                        "Never"
                    }

                    Text(
                        text = "LAST BACKUP",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = lastBackupText,
                        color = if (userPrefs.lastBackupTimestampMs > 0) TextMain else TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        }

        // ── 3. Developer Mode Card (Visible when unlocked) ──────────
        if (com.kalaiselvan.ride.dev.DevFeatureProviderImpl.isDevFeatureSupported && userPrefs.isDevUnlocked) {
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDevModeExpanded = !isDevModeExpanded }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "DEVELOPER OPTIONS",
                            color = RpmOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = if (isDevModeExpanded) "Raw CAN/GATT trace & simulation tools" else "Hex & Diagnostic Tools Active",
                            color = TextMuted.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                    FlatIcon(
                        type = if (isDevModeExpanded) FlatIconType.CHEVRON_UP else FlatIconType.CHEVRON_DOWN,
                        tint = RpmOrange,
                        size = 18.dp
                    )
                }

                AnimatedVisibility(
                    visible = isDevModeExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 4. Legal & Compliance Card ──────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isLegalExpanded) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp) else RoundedCornerShape(14.dp))
                    .clickable { isLegalExpanded = !isLegalExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "LEGAL & SAFETY COMPLIANCE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (isLegalExpanded) "Review safety disclaimers & closed-course policy" else "Terms of Use & Closed-Course Policy",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                FlatIcon(
                    type = if (isLegalExpanded) FlatIconType.CHEVRON_UP else FlatIconType.CHEVRON_DOWN,
                    tint = TextMuted,
                    size = 18.dp
                )
            }

            AnimatedVisibility(
                visible = isLegalExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
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
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── ⚗ Experimental Card ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isExperimentalExpanded) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp) else RoundedCornerShape(14.dp))
                    .clickable { isExperimentalExpanded = !isExperimentalExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EXPERIMENTAL",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BETA",
                            color = if (theme.isLight) Color(0xFFD97706) else Color(0xFFFFB300),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = if (isExperimentalExpanded) "Features under active development" else "Crash Detection & Emergency SOS",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                FlatIcon(
                    type = if (isExperimentalExpanded) FlatIconType.CHEVRON_UP else FlatIconType.CHEVRON_DOWN,
                    tint = TextMuted,
                    size = 18.dp
                )
            }

            AnimatedVisibility(
                visible = isExperimentalExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Disclaimer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (theme.isLight) Color(0xFFFFFBEB) else Color(0xFF1A1500))
                            .border(1.dp, if (theme.isLight) Color(0xFFFDE68A) else Color(0xFF4A3800), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "⚠ Experimental features are in active development and may not work perfectly in all scenarios. Use as a supplementary safety layer only — never rely solely on automatic detection.",
                            color = if (theme.isLight) Color(0xFFB45309) else Color(0xFFFFC107),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    // Crash Detection master toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Crash Detection", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "Auto-detect crashes using bike IMU and phone sensors",
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        NeumorphicSwitch(
                            checked = userPrefs.crashDetectionEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val hasSms = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.SEND_SMS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val hasPhone = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.READ_PHONE_STATE
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.READ_PHONE_NUMBERS
                                        ) == PackageManager.PERMISSION_GRANTED
                                    } else true
                                    if (hasSms && hasPhone && hasPhoneNumbers) {
                                        appPrefs.setCrashDetectionEnabled(true)
                                        availableSims = EmergencySmsManager.getAvailableSims(context)
                                    } else {
                                        pendingCrashEnable = true
                                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
                                        } else {
                                            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
                                        }
                                        crashPermissionLauncher.launch(perms)
                                    }
                                } else {
                                    appPrefs.setCrashDetectionEnabled(false)
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = userPrefs.crashDetectionEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                            // 1. SIM Selector Dropdown Row
                            if (availableSims.isNotEmpty()) {
                                val selectedSim = availableSims.firstOrNull { it.subscriptionId == userPrefs.crashSmsSubscriptionId }
                                    ?: availableSims.firstOrNull()

                                val simDisplayLabel = selectedSim?.let { s ->
                                    val num = s.number.ifBlank { "Slot ${s.slotIndex + 1}" }
                                    val name = if (s.displayName.isNotBlank() && !s.displayName.startsWith("SIM", ignoreCase = true)) s.displayName else "SIM ${s.slotIndex + 1}"
                                    "$name ($num)"
                                } ?: "Select SIM"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(text = "Default SOS SIM", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = "SIM card used to send emergency SMS", color = TextMuted, fontSize = 10.sp)
                                    }

                                    Box {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ChipBg)
                                                .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                        ContextCompat.checkSelfPermission(
                                                            context, Manifest.permission.READ_PHONE_NUMBERS
                                                        ) == PackageManager.PERMISSION_GRANTED
                                                    } else true
                                                    if (!hasPhoneNumbers && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                        crashPermissionLauncher.launch(
                                                            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
                                                        )
                                                    } else {
                                                        availableSims = EmergencySmsManager.getAvailableSims(context)
                                                    }
                                                    isSimDropdownExpanded = true
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = simDisplayLabel,
                                                    color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(text = "▼", color = TextMuted, fontSize = 9.sp)
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = isSimDropdownExpanded,
                                            onDismissRequest = { isSimDropdownExpanded = false },
                                            modifier = Modifier.background(BgCard)
                                        ) {
                                            availableSims.forEach { sim ->
                                                val isSelected = (selectedSim?.subscriptionId == sim.subscriptionId)
                                                val numText = if (sim.number.isNotBlank()) sim.number else "Slot ${sim.slotIndex + 1}"
                                                val label = "${sim.displayName} ($numText)"

                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = label,
                                                            color = if (isSelected) (if (theme.isLight) Color(0xFF008F48) else RpmGreen) else TextMain,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        appPrefs.setCrashSmsSubscriptionId(sim.subscriptionId)
                                                        isSimDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Detection Sensitivity Dropdown Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(text = "Detection Sensitivity", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = "Bike IMU and phone accelerometer impact threshold", color = TextMuted, fontSize = 10.sp)
                                }

                                Box {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ChipBg)
                                            .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
                                            .clickable { isSensitivityDropdownExpanded = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = userPrefs.crashSensitivity.displayName,
                                                color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(text = "▼", color = TextMuted, fontSize = 9.sp)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = isSensitivityDropdownExpanded,
                                        onDismissRequest = { isSensitivityDropdownExpanded = false },
                                        modifier = Modifier.background(BgCard)
                                    ) {
                                        listOf(
                                            CrashSensitivity.LOW to "Low (Highway & Track)",
                                            CrashSensitivity.MEDIUM to "Medium (Recommended)",
                                            CrashSensitivity.HIGH to "High (City & Commute)"
                                        ).forEach { (sens, desc) ->
                                            val isSelected = userPrefs.crashSensitivity == sens
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = desc,
                                                            color = if (isSelected) (if (theme.isLight) Color(0xFF008F48) else RpmGreen) else TextMain,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (isSelected) {
                                                            Text(
                                                                text = " ✓",
                                                                color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    appPrefs.setCrashSensitivity(sens)
                                                    isSensitivityDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Alert Countdown Dropdown Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(text = "Alert Countdown", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = "Cancellation window before emergency SMS is dispatched", color = TextMuted, fontSize = 10.sp)
                                }

                                Box {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ChipBg)
                                            .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
                                            .clickable { isCountdownDropdownExpanded = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "${userPrefs.crashCountdownSeconds}s",
                                                    color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(text = "▼", color = TextMuted, fontSize = 9.sp)
                                            }
                                        }

                                    DropdownMenu(
                                        expanded = isCountdownDropdownExpanded,
                                        onDismissRequest = { isCountdownDropdownExpanded = false },
                                        modifier = Modifier.background(BgCard)
                                    ) {
                                        listOf(
                                            15 to "15 Seconds (Quick)",
                                            20 to "20 Seconds",
                                            30 to "30 Seconds (Default)",
                                            45 to "45 Seconds",
                                            60 to "60 Seconds (Extended)"
                                        ).forEach { (secs, desc) ->
                                            val isSelected = userPrefs.crashCountdownSeconds == secs
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = desc,
                                                            color = if (isSelected) (if (theme.isLight) Color(0xFF008F48) else RpmGreen) else TextMain,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (isSelected) {
                                                            Text(text = " ✓", color = if (theme.isLight) Color(0xFF008F48) else RpmGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    appPrefs.setCrashCountdownSeconds(secs)
                                                    isCountdownDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Emergency Contacts
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Emergency Contacts",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${crashContacts.size}/3",
                                        color = TextMuted.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                crashContacts.forEachIndexed { index, contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(ChipBg)
                                                .border(1.dp, ChipBorder, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = if (theme.isLight) Color(0xFF008F48) else AccentGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (contact.name.isNotBlank()) {
                                                Text(contact.name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Text(
                                                contact.number,
                                                color = TextMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ChipBg)
                                                .border(1.dp, ChipBorder, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    contactToRemove = contact
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            FlatIcon(
                                                type = FlatIconType.TRASH,
                                                tint = RpmRed,
                                                size = 13.dp
                                            )
                                        }
                                    }
                                }

                                if (crashContacts.size < 3) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val addBtnColor = if (theme.isLight) Color(0xFF008F48) else AccentGreen
                                    OutlinedButton(
                                        onClick = {
                                            val pickIntent = Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                            contactPickerLauncher.launch(pickIntent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = addBtnColor),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, addBtnColor.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            "+ Add Contact (${crashContacts.size}/3)",
                                            color = addBtnColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 4B. Cockpit & Helmet Audio Alerts ───────────────────────
        var isAudioAlertsCardExpanded by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isAudioAlertsCardExpanded) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp) else RoundedCornerShape(14.dp))
                    .clickable { isAudioAlertsCardExpanded = !isAudioAlertsCardExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "COCKPIT AUDIO ALERTS",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (isAudioAlertsCardExpanded) "Helmet & TWS synthesized sound cues" else (if (userPrefs.audioAlertsEnabled) "Enabled (Rev, Speed & Lean)" else "Disabled"),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                FlatIcon(
                    type = if (isAudioAlertsCardExpanded) FlatIconType.CHEVRON_UP else FlatIconType.CHEVRON_DOWN,
                    tint = TextMuted,
                    size = 20.dp
                )
            }

            AnimatedVisibility(
                visible = isAudioAlertsCardExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Divider()

                    // Master Audio Alerts Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Enable Audio Alerts", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "Plays synthesized pulse tones in helmet/earbuds when thresholds are reached",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        NeumorphicSwitch(
                            checked = userPrefs.audioAlertsEnabled,
                            onCheckedChange = { appPrefs.setAudioAlertsEnabled(it) }
                        )
                    }

                    AnimatedVisibility(
                        visible = userPrefs.audioAlertsEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Divider()

                            // 1. Rev Limit Alert
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Rev Limit Alert", color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "Triple chirp when reaching shift lamp / redline RPM",
                                        color = TextMuted,
                                        fontSize = 11.5.sp
                                    )
                                }
                                NeumorphicSwitch(
                                    checked = userPrefs.audioRevLimitAlert,
                                    onCheckedChange = { appPrefs.setAudioRevLimitAlert(it) }
                                )
                            }

                            // 2. Speed Threshold Alerts
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Speed Threshold Pulses", color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "2 pulses at 80 km/h, 3 pulses at 150 km/h, 4 pulses at 200 km/h",
                                        color = TextMuted,
                                        fontSize = 11.5.sp
                                    )
                                }
                                NeumorphicSwitch(
                                    checked = userPrefs.audioSpeedThresholdAlert,
                                    onCheckedChange = { appPrefs.setAudioSpeedThresholdAlert(it) }
                                )
                            }

                            // 3. Lean Angle Alert
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text("Lean Angle Alert", color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "Directional blip in corresponding ear (L/R) when apex lean is achieved",
                                        color = TextMuted,
                                        fontSize = 11.5.sp
                                    )
                                }
                                NeumorphicSwitch(
                                    checked = userPrefs.audioLeanAngleAlert,
                                    onCheckedChange = { appPrefs.setAudioLeanAngleAlert(it) }
                                )
                            }

                            if (userPrefs.audioLeanAngleAlert) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Lean Angle Trigger", color = TextMuted, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(30, 35, 40, 45).forEach { deg ->
                                            val isSelected = userPrefs.audioLeanThresholdDeg == deg
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) (if (theme.isLight) Color(0xFF00C853) else AccentGreen) else ChipBg)
                                                    .clickable { appPrefs.setAudioLeanThresholdDeg(deg) }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "$deg°",
                                                    color = if (isSelected) (if (theme.isLight) Color.White else Color.Black) else TextMuted,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Test Audio Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1000E676))
                                    .clickable {
                                        com.kalaiselvan.ride.audio.AudioAlertManager.playRevLimitAlert()
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TEST COCKPIT AUDIO TONE",
                                    color = Color(0xFF00E676),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 5. Developer / About Card ────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isAboutExpanded) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp) else RoundedCornerShape(14.dp))
                    .clickable { isAboutExpanded = !isAboutExpanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "ABOUT MYRIDE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (isAboutExpanded) "App version & author information" else "MyRide Native Telemetry",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                FlatIcon(
                    type = if (isAboutExpanded) FlatIconType.CHEVRON_UP else FlatIconType.CHEVRON_DOWN,
                    tint = TextMuted,
                    size = 18.dp
                )
            }

            AnimatedVisibility(
                visible = isAboutExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 18.dp),
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
                        text = stringResource(R.string.app_tagline),
                        color = TextMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Text(
                        text = stringResource(R.string.developed_by),
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    val devName = stringResource(R.string.developer_name)
                    val githubUrl = stringResource(R.string.github_repo_url)
                    Text(
                        text = devName,
                        color = RpmGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
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

                                val isSupported = com.kalaiselvan.ride.dev.DevFeatureProviderImpl.isDevFeatureSupported
                                if (!isSupported) {
                                    // Developer mode disabled in production release
                                    return@clickable
                                }
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
        }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Rider Name Edit Dialog
    if (showRiderNameDialog) {
        Dialog(onDismissRequest = { showRiderNameDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Your Name", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = riderNameInput,
                    onValueChange = { riderNameInput = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextMain, fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(if (theme.isLight) Color(0xFF00C853) else AccentGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChipBg)
                        .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showRiderNameDialog = false },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Cancel", color = TextMuted) }
                    Button(
                        onClick = {
                            appPrefs.setRiderName(riderNameInput.trim())
                            showRiderNameDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (theme.isLight) Color(0xFF00C853) else AccentGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Save", color = if (theme.isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    // SIM Phone Number Manual Override Dialog
    if (showEditSimNumberDialog) {
        Dialog(onDismissRequest = { showEditSimNumberDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "SIM Phone Number",
                    color = TextMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "If your carrier didn't program the phone number on your SIM card, enter it here so it appears in MyRide and can be included in emergency alerts.",
                    color = TextMuted,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
                BasicTextField(
                    value = simNumberInput,
                    onValueChange = { simNumberInput = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextMain, fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(if (theme.isLight) Color(0xFF00C853) else AccentGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ChipBg)
                        .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showEditSimNumberDialog = false },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Cancel", color = TextMuted) }
                    Button(
                        onClick = {
                            val subId = if (simNumberSubIdToEdit != -1) {
                                simNumberSubIdToEdit
                            } else {
                                availableSims.firstOrNull()?.subscriptionId ?: -1
                            }
                            if (subId != -1) {
                                appPrefs.setSimPhoneOverride(subId, simNumberInput.trim())
                                availableSims = EmergencySmsManager.getAvailableSims(context)
                            }
                            showEditSimNumberDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (theme.isLight) Color(0xFF00C853) else AccentGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Save", color = if (theme.isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    // Confirm Notification SMS dialog before adding contact
    if (pendingContactToAdd != null) {
        val contact = pendingContactToAdd!!
        Dialog(onDismissRequest = { pendingContactToAdd = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Emergency Contact?",
                    color = TextMain,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "An introductory SMS will be sent to ${contact.name.ifBlank { contact.number }} notifying them that their number has been set as your emergency contact on MyRide.\n\nStandard carrier SMS charges may apply.\n\nDo you want to continue?",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { pendingContactToAdd = null },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = TextMuted)
                    }
                    Button(
                        onClick = {
                            val contactToAdd = contact
                            pendingContactToAdd = null
                            val currentList = appPrefs.getCrashEmergencyContacts().toMutableList()
                            if (currentList.none { c -> c.number == contactToAdd.number } && currentList.size < 3) {
                                currentList.add(contactToAdd)
                                appPrefs.setCrashEmergencyContacts(currentList)
                                crashContacts = currentList

                                // Send notification SMS
                                val sims = EmergencySmsManager.getAvailableSims(context)
                                val subId = userPrefs.crashSmsSubscriptionId.let { id ->
                                    if (id == -1 && sims.isNotEmpty()) sims[0].subscriptionId else id
                                }
                                val riderPhone = sims.firstOrNull { s -> s.subscriptionId == subId }?.number ?: ""
                                EmergencySmsManager.sendNotificationSms(
                                    context = context,
                                    riderName = userPrefs.riderName.ifBlank { "A MyRide user" },
                                    riderPhone = riderPhone,
                                    contact = contactToAdd,
                                    subscriptionId = subId
                                )
                                appPrefs.markContactNotified(contactToAdd.number)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (theme.isLight) Color(0xFF00C853) else AccentGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Continue", color = if (theme.isLight) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Confirm Remove Emergency Contact Dialog
    if (contactToRemove != null) {
        val contact = contactToRemove!!
        Dialog(onDismissRequest = { contactToRemove = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Remove Emergency Contact?",
                    color = TextMain,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Are you sure you want to remove ${contact.name.ifBlank { contact.number }} (${contact.number}) from your emergency contacts?",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { contactToRemove = null },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = TextMuted)
                    }
                    Button(
                        onClick = {
                            val updated = crashContacts.toMutableList().also { list ->
                                list.removeAll { it.number == contact.number }
                            }
                            appPrefs.setCrashEmergencyContacts(updated)
                            crashContacts = updated
                            contactToRemove = null
                            Toast.makeText(context, "Contact removed", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RpmRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Confirm Reset Achievements Dialog
    if (showResetAchievementsDialog) {
        Dialog(onDismissRequest = { showResetAchievementsDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Reset Achievements?",
                    color = TextMain,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "This will permanently clear all your saved sprint personal records (0-60, 0-100, top speed) and reset all achievement badges to their initial state.\n\nThis action cannot be undone. Are you sure you want to reset?",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResetAchievementsDialog = false },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = TextMuted)
                    }
                    Button(
                        onClick = {
                            showResetAchievementsDialog = false
                            appPrefs.resetAchievements()
                            Toast.makeText(context, "Achievements Reset", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RpmRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Reset", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showLegalDisclaimer) {
        LegalDisclaimerDialog(
            isMandatory = false,
            onDismiss = { showLegalDisclaimer = false }
        )
    }

    // ── Set/Change Encryption Password Dialog ──────────────────
    if (showSetPasswordDialog) {
        Dialog(onDismissRequest = {
            if (appPrefs.getAutoBackupPassword().isBlank()) {
                appPrefs.setAutoBackupEnabled(false)
                pendingScheduleAfterConfig = null
            }
            showSetPasswordDialog = false
        }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Backup Password",
                        color = TextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Password notice
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x26FF5252))
                            .border(1.dp, Color(0x66FF5252), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Important: If you forget this password, this backup cannot be restored. Please keep it safe.",
                            color = Color(0xFFFF8A80),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Password Input
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDark)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                passwordError = null
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = TextMain,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            cursorBrush = SolidColor(if (theme.isLight) Color(0xFF00C853) else AccentGreen),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (passwordInput.isEmpty()) {
                            Text(
                                text = "Enter password (min 8 characters)",
                                color = TextMuted.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Confirm Password Input
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDark)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = confirmPasswordInput,
                            onValueChange = {
                                confirmPasswordInput = it
                                passwordError = null
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = TextMain,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            cursorBrush = SolidColor(if (theme.isLight) Color(0xFF00C853) else AccentGreen),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (confirmPasswordInput.isEmpty()) {
                            Text(
                                text = "Confirm password",
                                color = TextMuted.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (passwordError != null) {
                        Text(
                            text = passwordError!!,
                            color = RpmRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    if (appPrefs.getAutoBackupPassword().isBlank()) {
                                        appPrefs.setAutoBackupEnabled(false)
                                        pendingScheduleAfterConfig = null
                                    }
                                    showSetPasswordDialog = false
                                }
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
                                .background(if (theme.isLight) Color(0xFF00C853) else AccentGreen)
                                .clickable {
                                    when {
                                        passwordInput.length < 8 -> {
                                            passwordError = "Password must be at least 8 characters"
                                        }
                                        passwordInput != confirmPasswordInput -> {
                                            passwordError = "Passwords do not match"
                                        }
                                        else -> {
                                            appPrefs.setAutoBackupPassword(passwordInput)
                                            appPrefs.setAutoBackupEncrypt(true)
                                            val sched = pendingScheduleAfterConfig ?: if (userPrefs.autoBackupSchedule != BackupSchedule.NEVER) userPrefs.autoBackupSchedule else BackupSchedule.DAILY
                                            appPrefs.setAutoBackupSchedule(sched)
                                            appPrefs.setAutoBackupEnabled(true)
                                            BackupScheduler.scheduleNextBackup(context)
                                            pendingScheduleAfterConfig = null
                                            showSetPasswordDialog = false
                                            Toast.makeText(context, "Backup password saved", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SAVE",
                                color = if (theme.isLight) Color.White else Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Restore Decryption Password Prompt Dialog ─────────────
    if (showRestorePasswordDialog && pendingRestoreUri != null) {
        Dialog(onDismissRequest = { showRestorePasswordDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Password Required",
                        color = TextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "This backup is password protected. Enter the password to restore.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDark)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = restorePasswordInput,
                            onValueChange = {
                                restorePasswordInput = it
                                restorePasswordError = null
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = TextMain,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            cursorBrush = SolidColor(if (theme.isLight) Color(0xFF00C853) else AccentGreen),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (restorePasswordInput.isEmpty()) {
                            Text(
                                text = "Enter password",
                                color = TextMuted.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (isVerifyingPassword) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Verifying password...",
                                color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (restorePasswordError != null) {
                        Text(
                            text = restorePasswordError!!,
                            color = RpmRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                                .clickable(enabled = !isVerifyingPassword) { showRestorePasswordDialog = false }
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
                                .background(if (isVerifyingPassword) (if (theme.isLight) Color(0x3300C853) else Color(0x3300E676)) else (if (theme.isLight) Color(0xFF00C853) else AccentGreen))
                                .clickable(enabled = !isVerifyingPassword) {
                                    if (restorePasswordInput.isBlank()) {
                                        restorePasswordError = "Please enter the password"
                                        return@clickable
                                    }
                                    val uri = pendingRestoreUri ?: return@clickable
                                    isVerifyingPassword = true
                                    restorePasswordError = null
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val result = context.contentResolver.openInputStream(uri)?.use { inStream ->
                                            BackupManager.inspectBackup(inStream, restorePasswordInput)
                                        } ?: BackupInspectionResult.Error("Could not read backup file")

                                        withContext(Dispatchers.Main) {
                                            isVerifyingPassword = false
                                            when (result) {
                                                is BackupInspectionResult.Success -> {
                                                    restoreManifestToConfirm = result.manifest
                                                    restorePasswordForExecution = restorePasswordInput
                                                    showRestorePasswordDialog = false
                                                    showRestoreConfirmDialog = true
                                                }
                                                is BackupInspectionResult.InvalidPassword, is BackupInspectionResult.PasswordRequired -> {
                                                    restorePasswordError = "Incorrect password. Please verify and try again."
                                                }
                                                is BackupInspectionResult.Error -> {
                                                    restorePasswordError = result.message
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isVerifyingPassword) "VERIFYING..." else "VERIFY",
                                color = if (isVerifyingPassword) TextMuted else (if (theme.isLight) Color.White else Color.Black),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Restore Confirmation Modal ────────────────────────────
    if (showRestoreConfirmDialog && restoreManifestToConfirm != null) {
        val manifest = restoreManifestToConfirm!!
        Dialog(onDismissRequest = { showRestoreConfirmDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Restore Application Data?",
                        color = TextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Manifest info card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDark)
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Backup Date:", color = TextMuted, fontSize = 11.sp)
                                Text(manifest.formattedDate, color = TextMain, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Service Records:", color = TextMuted, fontSize = 11.sp)
                                Text("${manifest.serviceRecordCount} records", color = if (theme.isLight) Color(0xFF008F48) else RpmGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Receipt Attachments:", color = TextMuted, fontSize = 11.sp)
                                Text("${manifest.receiptFileCount} files", color = TextMain, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Encryption:", color = TextMuted, fontSize = 11.sp)
                                Text(if (manifest.isEncrypted) "Password Protected" else "None", color = if (manifest.isEncrypted) (if (theme.isLight) Color(0xFF008F48) else RpmGreen) else TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Text(
                        text = "Restoring will replace existing maintenance logs and preferences with the contents of this backup.",
                        color = RpmOrange,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                                .clickable { showRestoreConfirmDialog = false }
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
                                .background(if (theme.isLight) Color(0xFF00C853) else AccentGreen)
                                .clickable {
                                    val uri = pendingRestoreUri ?: return@clickable
                                    val pass = restorePasswordForExecution
                                    showRestoreConfirmDialog = false
                                    isRestoring = true
                                    backupProgressTitle = "Restoring Backup"
                                    backupProgress = 0.05f
                                    backupStatusText = "Opening archive..."
                                    showBackupProgressDialog = true

                                    coroutineScope.launch(Dispatchers.IO) {
                                        val restoreResult = context.contentResolver.openInputStream(uri)?.use { inStream ->
                                            BackupManager.restoreBackup(context, inStream, pass) { progress, status ->
                                                backupProgress = progress
                                                backupStatusText = status
                                            }
                                        } ?: RestoreResult.Error("Could not read backup file")

                                        withContext(Dispatchers.Main) {
                                            isRestoring = false
                                            showBackupProgressDialog = false
                                            when (restoreResult) {
                                                is RestoreResult.Success -> {
                                                    val parts = mutableListOf<String>()
                                                    if (restoreResult.serviceRecordsRestored > 0) parts.add("${restoreResult.serviceRecordsRestored} service records")
                                                    if (restoreResult.rideCountRestored > 0) parts.add("${restoreResult.rideCountRestored} rides")
                                                    if (restoreResult.receiptFilesRestored > 0) parts.add("${restoreResult.receiptFilesRestored} attachments")
                                                    if (restoreResult.settingsRestored) parts.add("settings & achievements")
                                                    val details = if (parts.isNotEmpty()) parts.joinToString(", ") else "all data"
                                                    Toast.makeText(
                                                        context,
                                                        "Restore complete: $details restored!",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                                is RestoreResult.InvalidPassword -> {
                                                    Toast.makeText(context, "Restore failed: Invalid password", Toast.LENGTH_LONG).show()
                                                }
                                                is RestoreResult.Error -> {
                                                    Toast.makeText(context, "Restore failed: ${restoreResult.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CONFIRM RESTORE",
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

    // ── Backup / Restore Progress Modal Dialog ─────────────────
    if (showBackupProgressDialog) {
        Dialog(
            onDismissRequest = { /* Modal: keep visible until finished */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
                    .padding(22.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = backupProgressTitle,
                            color = TextMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(backupProgress * 100).toInt()}%",
                            color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Neumorphic progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgDark)
                            .border(0.5.dp, ChipBorder, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = backupProgress.coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(if (theme.isLight) Color(0xFF00C853) else AccentGreen, RpmGreen)))
                        )
                    }

                    Text(
                        text = backupStatusText,
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2
                    )
                }
            }
        }
    }

    if (showBrandDialog) {
        Dialog(onDismissRequest = { showBrandDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, ChipBorder, RoundedCornerShape(20.dp))
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
                            .border(1.dp, ChipBorder, RoundedCornerShape(12.dp))
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
                            cursorBrush = SolidColor(if (theme.isLight) Color(0xFF00C853) else AccentGreen),
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
                            color = if (theme.isLight) Color(0xFF008F48) else RpmGreen,
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
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
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
                                .background(ChipBg)
                                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
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
                                .background(if (theme.isLight) Color(0xFF00C853) else AccentGreen)
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
                                color = if (theme.isLight) Color.White else Color.Black,
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
    val theme = LocalThemeColors.current
    val pillBg = if (selected) (if (theme.isLight) Color(0xFF34C759) else Color(0xFF30D158)) else Color.Transparent
    val pillTextColor = if (selected) (if (theme.isLight) Color.White else BgDark) else TextMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(pillBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = pillTextColor,
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
                color = if (isGranted) (if (LocalThemeColors.current.isLight) Color(0xFF008F48) else RpmGreen) else RpmRed,
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
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .alpha(if (enabled) 1.0f else 0.45f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = label,
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        NeumorphicSwitch(
            checked = checked && enabled,
            onCheckedChange = if (enabled) onCheckedChange else { _ -> }
        )
    }
}

@Composable
private fun Divider() {
    val divColor = TextMuted.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = divColor,
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
                        drawCircle(color = thumbColor.copy(alpha = 0.35f), radius = size.minDimension / 1.5f)
                    }
                    drawCircle(color = thumbColor)
                }
        )
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    return com.kalaiselvan.ride.utils.DeviceUtils.isNotificationAccessGranted(context)
}
