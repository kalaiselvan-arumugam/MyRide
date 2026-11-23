package com.kalaiselvan.ride.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalaiselvan.ride.logs.RideSession
import com.kalaiselvan.ride.preferences.SpeedUnit
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.screens.LegendChip
import java.util.Locale

@Composable
fun FullscreenMapDialog(
    session: RideSession,
    speedUnit: SpeedUnit,
    onDismiss: () -> Unit
) {
    var colorMode by remember { mutableStateOf(RideMapColorMode.SPEED) }

    val distStr = if (speedUnit == SpeedUnit.MPH) {
        String.format(Locale.US, "%.1f mi", com.kalaiselvan.ride.utils.UnitConversions.kmToMiles(session.distanceKm))
    } else {
        String.format(Locale.US, "%.1f km", session.distanceKm)
    }
    val peakSpeedStr = if (speedUnit == SpeedUnit.MPH) {
        String.format(Locale.US, "%.0f mph", com.kalaiselvan.ride.utils.UnitConversions.kmhToMph(session.topSpeedKmh))
    } else {
        String.format(Locale.US, "%.0f km/h", session.topSpeedKmh)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
        ) {
            // Fullscreen Map behind
            RideMapView(
                breadcrumbs = session.breadcrumbs,
                colorMode = colorMode,
                modifier = Modifier.fillMaxSize()
            )

            // Top Floating Overlay Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard.copy(alpha = 0.92f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = session.formattedDate,
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = distStr,
                                color = RpmGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "•",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = session.formattedDuration,
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "•",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Peak $peakSpeedStr",
                                color = TextMain,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Close Button (Rounded Rectangle)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgDark)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(11.dp)) {
                            val strokeW = 1.8.dp.toPx()
                            drawLine(color = Color.White, start = Offset(0f, 0f), end = Offset(size.width, size.height), strokeWidth = strokeW, cap = StrokeCap.Round)
                            drawLine(color = Color.White, start = Offset(0f, size.height), end = Offset(size.width, 0f), strokeWidth = strokeW, cap = StrokeCap.Round)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Toggle & Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Heatmap toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgCard.copy(alpha = 0.9f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (colorMode == RideMapColorMode.SPEED) RpmGreen.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { colorMode = RideMapColorMode.SPEED }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "SPEED",
                                color = if (colorMode == RideMapColorMode.SPEED) RpmGreen else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (colorMode == RideMapColorMode.LEAN) RpmOrange.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { colorMode = RideMapColorMode.LEAN }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "LEAN",
                                color = if (colorMode == RideMapColorMode.LEAN) RpmOrange else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Legend Chips row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val unitStr = if (speedUnit == SpeedUnit.MPH) "mph" else "km/h"
                        if (colorMode == RideMapColorMode.SPEED) {
                            LegendChip(color = Color(0xFF00E676), label = "<40")
                            LegendChip(color = Color(0xFFFFEA00), label = "40-75")
                            LegendChip(color = Color(0xFFFF9100), label = "75-105")
                            LegendChip(color = Color(0xFFFF1744), label = ">105 $unitStr")
                        } else {
                            LegendChip(color = Color(0xFF00E5FF), label = "<12°")
                            LegendChip(color = Color(0xFF76FF03), label = "12-22°")
                            LegendChip(color = Color(0xFFFFD600), label = "22-32°")
                            LegendChip(color = Color(0xFFFF1744), label = ">32°")
                        }
                    }
                }
            }
        }
    }
}
