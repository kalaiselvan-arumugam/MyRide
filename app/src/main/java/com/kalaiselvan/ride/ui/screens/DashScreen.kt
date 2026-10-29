package com.kalaiselvan.ride.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.telemetry.CruiseState
import com.kalaiselvan.ride.telemetry.TelemetryState

import com.kalaiselvan.ride.theme.RpmGreen
import com.kalaiselvan.ride.theme.RpmOrange
import com.kalaiselvan.ride.theme.TextMain
import com.kalaiselvan.ride.theme.TextMuted
import com.kalaiselvan.ride.ui.components.*

/**
 * Single-View Telemetry Dashboard matching the user's reference design pixel-perfect.
 * Zero-scroll high-density motorcycle instrument cluster.
 */
@Composable
fun DashScreen(
    telemetry: TelemetryState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // 1. Hero Dial Flanked by Left & Right HUD Columns
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left HUD Column (Cruise, KTRC, Trip A)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cruise Control
                Column(horizontalAlignment = Alignment.Start) {
                    val cruiseColor = when (telemetry.cruiseState) {
                        CruiseState.ACTIVE -> RpmGreen
                        CruiseState.ON -> TextMain
                        CruiseState.OFF -> TextMuted
                    }
                    val cruiseValueText = when (telemetry.cruiseState) {
                        CruiseState.ACTIVE -> if (telemetry.cruiseSetSpeed > 0) "${telemetry.cruiseSetSpeed}" else "SET"
                        CruiseState.ON -> if (telemetry.cruiseSetSpeed > 0) "${telemetry.cruiseSetSpeed}" else "READY"
                        CruiseState.OFF -> if (telemetry.cruiseSetSpeed > 0) "${telemetry.cruiseSetSpeed}" else "OFF"
                    }
                    val cruiseSublabel = when (telemetry.cruiseState) {
                        CruiseState.ACTIVE -> if (telemetry.cruiseSetSpeed > 0) "CRUISE SET" else "CRUISE"
                        CruiseState.ON -> if (telemetry.cruiseSetSpeed > 0) "CRUISE RDY" else "CRUISE"
                        CruiseState.OFF -> "CRUISE"
                    }
                    Text(
                        text = cruiseValueText,
                        color = cruiseColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = cruiseSublabel,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // KTRC Icon + Numeric Level + Active Strobe Flash
                val ktrcInfiniteTransition = rememberInfiniteTransition(label = "ktrcAnimations")
                val ktrcFlashAlpha by ktrcInfiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 0.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(220, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ktrcFlash"
                )

                val ktrcArcColor = if (telemetry.ktrcActive) {
                    RpmOrange.copy(alpha = ktrcFlashAlpha)
                } else if (telemetry.isBtConnected) {
                    TextMain.copy(alpha = 0.85f)
                } else {
                    TextMuted
                }

                val ktrcTextColor = if (telemetry.ktrcActive) {
                    RpmOrange
                } else if (telemetry.isBtConnected) {
                    TextMain
                } else {
                    TextMuted
                }

                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = ktrcArcColor,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = if (telemetry.isBtConnected) telemetry.ktrcLevel else "--",
                            color = ktrcTextColor,
                            fontSize = if (telemetry.ktrcLevel == "OFF") 8.5.sp else 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                    Text(
                        text = "KTRC",
                        color = if (telemetry.ktrcActive) RpmOrange else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Trip A
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", telemetry.displayTripA),
                        color = TextMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (telemetry.speedUnitLabel == "MPH") "TRIP A (MI)" else "TRIP A (KM)",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            // Center Hero 240dp Cluster Dial (Or Parked Bike View when disconnected)
            if (telemetry.isBtConnected) {
                GaugeDial(
                    telemetry = telemetry,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                ParkedBikeView(
                    telemetry = telemetry,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Right HUD Column (G-Force, KQS, Trip B)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // G-Force (Shows '--' when disconnected, and live value when connected)
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (telemetry.isBtConnected) String.format(java.util.Locale.US, "%.2f", telemetry.gForce) else "--",
                            color = RpmOrange,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                        Text(
                            text = "G",
                            color = if (telemetry.isBtConnected) RpmOrange else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                        )
                    }
                    Text(
                        text = "G-FORCE",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // Quickshifter Icon + Label (Authentic double arrow vector)
                val kqsColor by animateColorAsState(
                    targetValue = if (telemetry.kqsActive || telemetry.kqsStatus == "ON") RpmGreen else TextMuted,
                    label = "kqsColor"
                )
                val kqsArrowPath = androidx.compose.runtime.remember {
                    androidx.compose.ui.graphics.vector.PathParser().parsePathString("M8 9l4-4 4 4H8zm8 6l-4 4-4-4h8z").toPath()
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val w = size.width
                        val h = size.height
                        scale(scaleX = w / 24f, scaleY = h / 24f, pivot = Offset.Zero) {
                            drawPath(path = kqsArrowPath, color = kqsColor, style = Fill)
                        }
                        drawLine(
                            color = kqsColor,
                            start = Offset(w * 0.5f, h * (6f / 24f)),
                            end = Offset(w * 0.5f, h * (18f / 24f)),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    Text(
                        text = "KQS",
                        color = kqsColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Trip B
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", telemetry.displayTripB),
                        color = TextMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (telemetry.speedUnitLabel == "MPH") "TRIP B (MI)" else "TRIP B (KM)",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }

        // 2. Gear Indicator Row
        Spacer(modifier = Modifier.height(8.dp))
        GearRow(
            currentGear = telemetry.gear,
            isShifting = telemetry.isShifting,
            isShiftIndicatorActive = telemetry.isShiftIndicatorActive
        )


        // 3. Ride Modes & Stats Row
        Spacer(modifier = Modifier.height(10.dp))
        RideModesAndStatsRow(
            rangeKm = telemetry.displayRange,
            avgMil = telemetry.displayAvgMil,
            activeMode = telemetry.ridingMode,
            unitLabel = telemetry.speedUnitLabel
        )

        // 4. Throttle Bar
        Spacer(modifier = Modifier.height(10.dp))
        ThrottleBar(
            throttlePercent = telemetry.throttle,
            shiftLampRpm = telemetry.shiftLampRpm
        )

        // 5. 4 Orbiting Satellites
        Spacer(modifier = Modifier.height(12.dp))
        SatellitesRow(
            coolantTemp = telemetry.coolantTemp,
            fuelPercent = telemetry.fuelPercent,
            airTemp = telemetry.airTemp,
            batteryVolt = telemetry.batteryVolt
        )

        // 6. Session Summary
        Spacer(modifier = Modifier.height(12.dp))
        SessionSummaryCard(telemetry = telemetry)
    }
}

