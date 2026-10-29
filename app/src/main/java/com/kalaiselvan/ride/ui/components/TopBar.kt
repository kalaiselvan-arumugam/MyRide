package com.kalaiselvan.ride.ui.components

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.theme.BluetoothBlue
import com.kalaiselvan.ride.theme.TextMain
import com.kalaiselvan.ride.theme.TextMuted
import com.kalaiselvan.ride.ui.theme.neumorphicCircleOuter

@Composable
fun TopBar(
    isBtConnected: Boolean,
    isBtConnecting: Boolean,
    connectedDeviceName: String?,
    onBtClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()

    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val infiniteTransition = rememberInfiniteTransition(label = "btPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand Group with Secret 5-Tap Developer Mode trigger
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime > 1500) {
                    tapCount = 1
                } else {
                    tapCount++
                }
                lastTapTime = now

                val isUnlocked = appPrefs.userPreferences.value.isDevUnlocked
                if (isUnlocked) {
                    when (tapCount) {
                        3 -> Toast.makeText(context, "2 taps to lock Developer Mode...", Toast.LENGTH_SHORT).show()
                        4 -> Toast.makeText(context, "1 tap to lock Developer Mode...", Toast.LENGTH_SHORT).show()
                        5 -> {
                            appPrefs.setDevUnlocked(false)
                            Toast.makeText(context, "Developer Mode Locked & Hidden", Toast.LENGTH_LONG).show()
                            tapCount = 0
                        }
                    }
                } else {
                    when (tapCount) {
                        3 -> Toast.makeText(context, "You are 2 steps away from Developer Mode...", Toast.LENGTH_SHORT).show()
                        4 -> Toast.makeText(context, "You are 1 step away from Developer Mode...", Toast.LENGTH_SHORT).show()
                        5 -> {
                            appPrefs.setDevUnlocked(true)
                            Toast.makeText(context, "Developer Mode Unlocked!", Toast.LENGTH_LONG).show()
                            tapCount = 0
                        }
                    }
                }
            }
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val brandName = userPrefs.customBrandName.ifBlank { "MYRIDE" }.uppercase()
                val brandFontSize = when {
                    brandName.length <= 6 -> 20.sp
                    brandName.length <= 9 -> 17.sp
                    brandName.length <= 12 -> 14.sp
                    else -> 12.sp
                }
                Text(
                    text = brandName,
                    color = TextMain,
                    fontSize = brandFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    softWrap = false
                )
                val rawName = connectedDeviceName ?: userPrefs.bikeModelName.ifBlank { null }
                val cleanModel = com.kalaiselvan.ride.bluetooth.BikeModelDecoder.decode(rawName, userPrefs.bikeVin).ifBlank {
                    rawName?.trim()?.uppercase()
                }
                if (!cleanModel.isNullOrBlank()) {
                    Text(
                        text = cleanModel,
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
            val syncText = if (userPrefs.lastSyncDateTime.isNotEmpty()) {
                "LAST SYNC: ${userPrefs.lastSyncDateTime}"
            } else {
                "LAST SYNC: --"
            }
            Text(
                text = syncText,
                color = TextMuted.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Bluetooth Smart Button
        val btColor = when {
            isBtConnected -> BluetoothBlue
            isBtConnecting -> BluetoothBlue.copy(alpha = pulseAlpha)
            else -> TextMuted
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .neumorphicCircleOuter(blurRadius = 6.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBtClick
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
                val strokePx = 2.dp.toPx()
                val w = size.width
                val h = size.height

                if (isBtConnected) {
                    drawCircle(
                        color = BluetoothBlue.copy(alpha = 0.15f),
                        radius = w * 0.9f,
                        center = center
                    )
                }

                val path = Path().apply {
                    moveTo(w * 0.29f, h * 0.29f)
                    lineTo(w * 0.71f, h * 0.71f)
                    lineTo(w * 0.50f, h * 0.92f)
                    lineTo(w * 0.50f, h * 0.08f)
                    lineTo(w * 0.71f, h * 0.29f)
                    lineTo(w * 0.29f, h * 0.71f)
                }
                drawPath(
                    path = path,
                    color = btColor,
                    style = Stroke(
                        width = strokePx,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
    }
}
