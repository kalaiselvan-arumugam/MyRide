package com.kalaiselvan.ride.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.crash.CrashAlertManager
import com.kalaiselvan.ride.preferences.EmergencyContact
import com.kalaiselvan.ride.theme.*

@Composable
fun CrashCountdownDialog(
    countdownSeconds: Int,
    contacts: List<EmergencyContact>,
    onCancel: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val secondsLeft by CrashAlertManager.countdownSecondsLeft.collectAsState()
    val progress = secondsLeft.toFloat() / countdownSeconds.coerceAtLeast(1).toFloat()

    // Prevent back press from dismissing the alert screen
    BackHandler(enabled = true) {
        // Intentionally no-op: user must explicitly tap "I'm OK — Cancel"
    }

    // Pulsing animation for the alert icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (secondsLeft <= 10) 1.3f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (secondsLeft <= 10) 350 else 650, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val crashCrimson = RpmRed // #FF003C
    val ringTrackColor = Color(0xFF141822)
    val cardBackground = Color(0xFF0C0E12) // Consistent with MyRide DarkBgCard
    val cardBorder = Color(0xFF1E2632)     // Consistent with MyRide ChipBorder

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Absorb touches
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBackground)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Alert Header Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(crashCrimson.copy(alpha = 0.15f))
                        .border(1.dp, crashCrimson.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (secondsLeft <= 10) "CRITICAL • LOUD ALARM ACTIVE" else "CRASH DETECTED",
                        color = crashCrimson,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                }

                // Pulsing Flat Warning Icon & Title
                FlatIcon(
                    type = FlatIconType.WARNING,
                    tint = crashCrimson,
                    size = 46.dp,
                    modifier = Modifier.scale(pulseScale)
                )

                Text(
                    text = "Emergency SOS Alert",
                    color = TextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "SMS will be sent to your emergency contacts in:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                // Animated countdown ring matching Gauge dials
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(136.dp)) {
                        val strokeWidth = 10.dp.toPx()
                        val inset = strokeWidth / 2f
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(inset, inset)

                        // Dark Background ring
                        drawArc(
                            color = ringTrackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Crimson Progress arc
                        drawArc(
                            color = crashCrimson,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$secondsLeft",
                            color = if (secondsLeft <= 10) crashCrimson else TextMain,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "SECONDS",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Emergency contacts list
                if (contacts.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10141A))
                            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Alerting ${contacts.size} contact(s):",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        contacts.forEach { contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = contact.name.ifBlank { "Emergency Contact" },
                                    color = TextMain,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = contact.number,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Clean Cancel Button without any icon
                Button(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "I'M OK — CANCEL",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = "Tap 'I'M OK — CANCEL' below, or press your phone's lock button twice to dismiss the alert.",
                    color = TextMuted.copy(alpha = 0.7f),
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
            }
        }
    }
