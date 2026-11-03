package com.kalaiselvan.ride.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.utils.DeviceUtils

@Composable
fun FirstLaunchDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val family = DeviceUtils.getDeviceFamily()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgCard)
                    .border(1.dp, Color(0xFF2C3440), RoundedCornerShape(24.dp))
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {


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

                // Explanation card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF14171A))
                        .border(1.dp, Color(0xFF232A33), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (family) {
                            DeviceUtils.DeviceFamily.XIAOMI_HYPEROS_MIUI -> {
                                Text(
                                    text = "To let MyRide automatically turn on your dashboard when your motorcycle key is turned ON, Xiaomi HyperOS requires two background settings:",
                                    color = TextMain,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                SetupBullet("1", "Display pop-up windows while running in background (allows hands-free dash opening).")
                                SetupBullet("2", "Autostart (allows the Bluetooth scanner to wake the app from sleep).")
                            }
                            DeviceUtils.DeviceFamily.ONEPLUS_OPPO_REALME,
                            DeviceUtils.DeviceFamily.VIVO_IQOO -> {
                                Text(
                                    text = "To ensure seamless connection with your bike while your phone is locked or in your pocket, please enable background autostart:",
                                    color = TextMain,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                SetupBullet("1", "Autostart / Background App Management.")
                                SetupBullet("2", "Display over other apps.")
                            }
                            else -> {
                                Text(
                                    text = "To ensure seamless connection with your bike while your phone is locked or in your pocket, please allow unrestricted background activity:",
                                    color = TextMain,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                SetupBullet("1", "Disable Battery Optimization (Unrestricted).")
                                SetupBullet("2", "Allow display over other apps for instant dash popup.")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons tailored to the device
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
                            ) {
                                Text("1. Enable Pop-up Windows", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { DeviceUtils.openAutostartSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B4654)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("2. Enable Autostart", color = TextMain, fontWeight = FontWeight.Bold)
                            }
                        }
                        DeviceUtils.DeviceFamily.ONEPLUS_OPPO_REALME,
                        DeviceUtils.DeviceFamily.VIVO_IQOO -> {
                            Button(
                                onClick = { DeviceUtils.openAutostartSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Configure Autostart", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { DeviceUtils.openOverlaySettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B4654)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Display Over Other Apps", color = TextMain, fontWeight = FontWeight.Bold)
                            }
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
                            ) {
                                Text("Disable Battery Restrictions", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Continue to Dashboard →",
                            color = BluetoothBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
