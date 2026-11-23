package com.kalaiselvan.ride.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.utils.DeviceUtils

@Composable
fun FirstLaunchDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val family = DeviceUtils.getDeviceFamily()

    var step by remember { mutableIntStateOf(1) }
    var riderName by remember { mutableStateOf("") }
    var brandName by remember { mutableStateOf("MyRide") }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                    }
                },
                label = "stepAnim"
            ) { currentStep ->
                when (currentStep) {
                    1 -> StepPersonalise(
                        riderName = riderName,
                        brandName = brandName,
                        onRiderNameChange = { riderName = it },
                        onBrandNameChange = { brandName = it },
                        onContinue = {
                            val name = riderName.trim()
                            val brand = brandName.trim().ifBlank { "MyRide" }
                            if (name.isNotBlank()) appPrefs.setRiderName(name)
                            appPrefs.setCustomBrandName(brand)
                            step = 2
                        }
                    )
                    2 -> StepPermissions(
                        family = family,
                        context = context,
                        onBack = { step = 1 },
                        onContinue = { step = 3 }
                    )
                    else -> StepAllSet(
                        riderName = riderName.trim().ifBlank { "Rider" },
                        onBack = { step = 2 },
                        onDone = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun StepPersonalise(
    riderName: String,
    brandName: String,
    onRiderNameChange: (String) -> Unit,
    onBrandNameChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(BgCard)
            .border(1.dp, ChipBorder, RoundedCornerShape(24.dp))
            .padding(22.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Step 1 of 3",
            color = TextMuted.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Personalise MyRide",
            color = TextMain,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Tell us your name so the app can greet you and use it in safety alerts",
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        // Rider Name field
        NameInputField(
            label = "Your Name",
            hint = "e.g. Kalai",
            value = riderName,
            onValueChange = onRiderNameChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        // App Display Name field
        NameInputField(
            label = "App Display Name",
            hint = "e.g. MyRide (shown in dashboard)",
            value = brandName,
            onValueChange = { if (it.length <= 15) onBrandNameChange(it) }
        )

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Continue →",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NameInputField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Default
            ),
            cursorBrush = SolidColor(AccentGreen),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A2030))
                .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        color = TextMuted.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun StepPermissions(
    family: DeviceUtils.DeviceFamily,
    context: Context,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(BgCard)
            .border(1.dp, ChipBorder, RoundedCornerShape(24.dp))
            .padding(22.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                color = BluetoothBlue,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Step 2 of 3",
                color = TextMuted.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Welcome to MyRide",
            color = TextMain,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Setup Automatic Ignition Connection",
            color = TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DialTrackBg)
                .border(1.dp, ChipBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (family) {
                    DeviceUtils.DeviceFamily.XIAOMI_HYPEROS_MIUI -> {
                        Text(
                            text = "To let MyRide automatically turn on your dashboard when your motorcycle key is turned ON, Xiaomi HyperOS requires two background settings:",
                            color = TextMain, fontSize = 12.sp, lineHeight = 17.sp
                        )
                        SetupBullet("1", "Display pop-up windows while running in background (allows hands-free dash opening).")
                        SetupBullet("2", "Autostart (allows the Bluetooth scanner to wake the app from sleep).")
                    }
                    DeviceUtils.DeviceFamily.ONEPLUS_OPPO_REALME,
                    DeviceUtils.DeviceFamily.VIVO_IQOO -> {
                        Text(
                            text = "To ensure seamless connection with your bike while your phone is locked or in your pocket, please enable background autostart:",
                            color = TextMain, fontSize = 12.sp, lineHeight = 17.sp
                        )
                        SetupBullet("1", "Autostart / Background App Management.")
                        SetupBullet("2", "Display over other apps.")
                    }
                    else -> {
                        Text(
                            text = "To ensure seamless connection with your bike while your phone is locked or in your pocket, please allow unrestricted background activity:",
                            color = TextMain, fontSize = 12.sp, lineHeight = 17.sp
                        )
                        SetupBullet("1", "Disable Battery Optimization (Unrestricted).")
                        SetupBullet("2", "Allow display over other apps for instant dash popup.")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (family) {
                DeviceUtils.DeviceFamily.XIAOMI_HYPEROS_MIUI -> {
                    Button(
                        onClick = { DeviceUtils.openXiaomiPopUpSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("1. Enable Pop-up Windows", color = Color.Black, fontWeight = FontWeight.Bold) }

                    OutlinedButton(
                        onClick = { DeviceUtils.openAutostartSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("2. Enable Autostart", color = TextMain, fontWeight = FontWeight.Bold) }
                }
                DeviceUtils.DeviceFamily.ONEPLUS_OPPO_REALME,
                DeviceUtils.DeviceFamily.VIVO_IQOO -> {
                    Button(
                        onClick = { DeviceUtils.openAutostartSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Configure Autostart", color = Color.Black, fontWeight = FontWeight.Bold) }

                    OutlinedButton(
                        onClick = { DeviceUtils.openOverlaySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Display Over Other Apps", color = TextMain, fontWeight = FontWeight.Bold) }
                }
                else -> {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                DeviceUtils.openAppDetails(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Disable Battery Restrictions", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Continue →",
                    color = BluetoothBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StepAllSet(
    riderName: String,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(BgCard)
            .border(1.dp, ChipBorder, RoundedCornerShape(24.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                color = BluetoothBlue,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Step 3 of 3",
                color = TextMuted.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "✅", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You're all set, $riderName!",
            color = TextMain,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "MyRide is ready to connect to your bike. You can update your name and preferences anytime in Settings.",
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Go to Dashboard →", color = Color.Black, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SetupBullet(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF262E38)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = text, color = TextMuted, fontSize = 11.5.sp, lineHeight = 16.sp)
    }
}
