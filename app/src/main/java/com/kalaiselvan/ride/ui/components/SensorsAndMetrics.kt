package com.kalaiselvan.ride.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.telemetry.TelemetryState
import com.kalaiselvan.ride.theme.RpmGreen
import com.kalaiselvan.ride.theme.TextMain
import com.kalaiselvan.ride.theme.TextMuted
import com.kalaiselvan.ride.ui.theme.neumorphicCircleInner
import com.kalaiselvan.ride.ui.theme.neumorphicCircleOuter

@Composable
fun RideModesAndStatsRow(
    rangeKm: Int,
    avgMil: Float,
    activeMode: String = "",
    unitLabel: String = "KM/H",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(0.88f)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Range on Left
        Column(horizontalAlignment = Alignment.Start) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (rangeKm > 0) "$rangeKm" else "--",
                    color = TextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = if (unitLabel == "MPH") "mi" else "km",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
            Text(
                text = "RANGE",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }

        // Ride Modes Center Row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val modes = listOf("RAIN", "ROAD", "SPORT", "RIDER")
            modes.forEach { mode ->
                val isActive = activeMode.isNotBlank() && mode.equals(activeMode, ignoreCase = true)
                Text(
                    text = mode,
                    color = if (isActive) TextMain else Color(0x40FFFFFF),
                    fontSize = 10.5.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Avg Mileage on Right
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (avgMil > 0f) String.format(java.util.Locale.US, "%.1f", avgMil) else "--",
                    color = TextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    text = if (unitLabel == "MPH") "mpg" else "km/l",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
            Text(
                text = "AVG MIL",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

/**
 * Throttle Bar with balanced metallic recessed groove.
 */
@Composable
fun ThrottleBar(
    throttlePercent: Float,
    shiftLampRpm: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(0.88f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "THROTTLE",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (shiftLampRpm > 0) {
                    Text(
                        text = "• SHIFT %,d RPM".format(java.util.Locale.US, shiftLampRpm),
                        color = Color(0x88FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${throttlePercent.toInt()}",
                    color = TextMain,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "%",
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        val animatedFill by animateFloatAsState(
            targetValue = (throttlePercent / 100f).coerceIn(0f, 1f),
            label = "throttleFillAnim"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
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
            if (animatedFill > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFill)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RpmGreen)
                )
            }
        }
    }
}

/**
 * 4 Orbiting Satellites with circular pods (WATER, FUEL, AIR, VOLT).
 */
@Composable
fun SatellitesRow(
    coolantTemp: Int,
    fuelPercent: Int,
    airTemp: Int,
    batteryVolt: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(0.88f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SatellitePod(value = if (coolantTemp > 0) "$coolantTemp°" else "--°", label = "WATER")
        SatellitePod(value = if (fuelPercent > 0) "$fuelPercent%" else "--%", label = "FUEL")
        SatellitePod(value = if (airTemp > 0) "$airTemp°" else "--°", label = "AIR")
        SatellitePod(value = if (batteryVolt > 0f) String.format(java.util.Locale.US, "%.1f", batteryVolt) else "--", label = "VOLT")
    }
}

@Composable
private fun SatellitePod(
    value: String,
    label: String
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .neumorphicCircleOuter(blurRadius = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .neumorphicCircleInner(shadowWidth = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value,
                        color = TextMain,
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = TextMuted,
                        style = TextStyle(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Clean 2-Row Session Summary Card matching user reference design exactly.
 */
@Composable
fun SessionSummaryCard(
    telemetry: TelemetryState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.88f)
            .drawBehind {
                drawLine(
                    color = Color(0x1AFFFFFF),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SESSION SUMMARY",
            color = TextMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Row 1: RIDE TIME & DISTANCE
        Row(
            modifier = Modifier.fillMaxWidth(0.72f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ride Time
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val totalSec = telemetry.sessionTimeMs / 1000
                val mins = (totalSec / 60).toInt()
                val secs = (totalSec % 60).toInt()
                val timeStr = "%02d:%02d".format(mins, secs)
                Text(
                    text = timeStr,
                    color = TextMain,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "RIDE TIME",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            // Distance
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val distVal = if (telemetry.speedUnitLabel == "MPH") telemetry.sessionDistanceKm * 0.621371 else telemetry.sessionDistanceKm
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", distVal),
                        color = TextMain,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 21.sp
                    )
                    Text(
                        text = if (telemetry.speedUnitLabel == "MPH") "mi" else "km",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                    )
                }
                Text(
                    text = "DISTANCE",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 2: 5 metrics across (LEFT LEAN, RIGHT LEAN, WHEELIE, STOPPIE, G-FORCE)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryMetricItem("${telemetry.sessionMaxLeanL.toInt()}°", "LEFT LEAN")
            SummaryMetricItem("${telemetry.sessionMaxLeanR.toInt()}°", "RIGHT LEAN")
            SummaryMetricItem(String.format(java.util.Locale.US, "%.1f°", telemetry.sessionMaxWheelie), "WHEELIE")
            SummaryMetricItem(String.format(java.util.Locale.US, "%.1f°", telemetry.sessionMaxStoppie), "STOPPIE")
            val gForceDisplay = if (telemetry.isBtConnected && telemetry.sessionMaxG > 0f) String.format(java.util.Locale.US, "%.2fG", telemetry.sessionMaxG) else "--"
            SummaryMetricItem(gForceDisplay, "G-FORCE")
        }
    }
}

@Composable
private fun SummaryMetricItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = TextMain,
            fontSize = 16.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
