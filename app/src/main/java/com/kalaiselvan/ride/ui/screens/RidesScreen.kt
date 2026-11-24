package com.kalaiselvan.ride.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.logs.RideSession
import com.kalaiselvan.ride.logs.RideSessionRecorder
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.preferences.SpeedUnit
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.components.FullscreenMapDialog
import com.kalaiselvan.ride.ui.components.RideHistoryCalendar
import com.kalaiselvan.ride.ui.components.RideMapColorMode
import com.kalaiselvan.ride.ui.components.RideMapView
import com.kalaiselvan.ride.ui.theme.neumorphicOuter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RidesSubTab {
    HISTORY,
    RECORDS
}

@Composable
fun RidesScreen(
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(RidesSubTab.HISTORY) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top Segmented Bar: [ RIDE HISTORY ] | [ PERSONAL RECORDS ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (activeSubTab == RidesSubTab.HISTORY) RpmGreen.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { activeSubTab = RidesSubTab.HISTORY }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RIDE HISTORY",
                    color = if (activeSubTab == RidesSubTab.HISTORY) RpmGreen else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (activeSubTab == RidesSubTab.RECORDS) RpmGreen.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { activeSubTab = RidesSubTab.RECORDS }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PERSONAL RECORDS",
                    color = if (activeSubTab == RidesSubTab.RECORDS) RpmGreen else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        when (activeSubTab) {
            RidesSubTab.HISTORY -> RideHistoryContent()
            RidesSubTab.RECORDS -> AchievementsScreen()
        }
    }
}

@Composable
private fun RideHistoryContent() {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()

    val completedSessions by RideSessionRecorder.completedSessions.collectAsState()

    var selectedDateKey by remember { mutableStateOf<String?>(null) } // "yyyy-MM-dd" or null
    var showAllSessions by remember { mutableStateOf(false) }
    var expandedSessionId by remember { mutableStateOf<String?>(null) }
    var fullscreenSession by remember { mutableStateOf<RideSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<RideSession?>(null) }
    var sessionToRename by remember { mutableStateOf<RideSession?>(null) }
    var renameInput by remember { mutableStateOf("") }

    // Filter sessions by selected date or default to last 10 (with show all option)
    val displayedSessions = remember(completedSessions, selectedDateKey, showAllSessions) {
        val sorted = completedSessions.sortedByDescending { it.startTime }
        if (selectedDateKey != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sorted.filter { sdf.format(Date(it.startTime)) == selectedDateKey }
        } else if (showAllSessions) {
            sorted
        } else {
            sorted.take(10)
        }
    }

    val allRideTimestamps = remember(completedSessions) {
        completedSessions.map { it.startTime }
    }

    if (completedSessions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "NO RECORDED RIDES YET",
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect to your motorcycle and go for a ride! Once rolling, your GPS route, speed heatmaps, lean angles, and G-forces will automatically appear here.",
                color = TextMuted,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Item 1: Collapsible Calendar Header
            item {
                RideHistoryCalendar(
                    rideTimestamps = allRideTimestamps,
                    selectedDateKey = selectedDateKey,
                    onDateSelected = { selectedDateKey = it }
                )
            }

            // Item 2: Section Header (Showing X rides)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countLabel = if (selectedDateKey != null) {
                        "RIDES ON SELECTED DATE (${displayedSessions.size})"
                    } else {
                        "RECENT RIDES (${displayedSessions.size})"
                    }
                    Text(
                        text = countLabel,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    if (selectedDateKey == null && completedSessions.size > 10) {
                        Text(
                            text = "TOTAL: ${completedSessions.size}",
                            color = TextMuted.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Empty state for selected date filter
            if (displayedSessions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No rides recorded on this date.",
                            color = TextMuted,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            // Vertical list of Ride Cards with Inline Accordion
            items(displayedSessions, key = { it.id }) { session ->
                val isExpanded = session.id == expandedSessionId

                RideLogCard(
                    session = session,
                    isExpanded = isExpanded,
                    speedUnit = userPrefs.speedUnit,
                    onToggleExpand = {
                        expandedSessionId = if (isExpanded) null else session.id
                    },
                    onOpenFullscreen = {
                        fullscreenSession = session
                    },
                    onDelete = {
                        sessionToDelete = session
                    },
                    onRename = {
                        renameInput = session.title
                        sessionToRename = session
                    }
                )
            }

            if (selectedDateKey == null && completedSessions.size > 10) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ChipBg)
                            .border(1.dp, ChipBorder, RoundedCornerShape(10.dp))
                            .clickable { showAllSessions = !showAllSessions }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showAllSessions) "SHOW LATEST 10 RIDES" else "LOAD ALL ${completedSessions.size} RIDES",
                            color = AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Fullscreen Map Dialog
    if (fullscreenSession != null) {
        FullscreenMapDialog(
            session = fullscreenSession!!,
            speedUnit = userPrefs.speedUnit,
            onDismiss = { fullscreenSession = null }
        )
    }

    // Confirmation dialog for deleting a ride
    if (sessionToDelete != null) {
        val session = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            shape = RoundedCornerShape(14.dp),
            title = {
                Text(
                    text = "Delete Ride Session?",
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the ride from ${session.formattedDate}? This cannot be undone.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        RideSessionRecorder.deleteSession(session.id)
                        if (expandedSessionId == session.id) expandedSessionId = null
                        sessionToDelete = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RpmRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { sessionToDelete = null },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                ) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = BgCard
        )
    }

    // Rename Ride Dialog (Max 15 characters limit)
    sessionToRename?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = {
                Text(
                    text = "Name This Ride",
                    color = TextMain,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Set a custom title (e.g. Hill Run, Track Day). Max 15 characters.",
                        color = TextMuted,
                        fontSize = 12.5.sp
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = {
                            if (it.length <= 15) renameInput = it
                        },
                        singleLine = true,
                        placeholder = { Text("e.g. Track Day", color = TextMuted.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = ChipBorder,
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain
                        ),
                        trailingIcon = {
                            Text(
                                text = "${renameInput.length}/15",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        RideSessionRecorder.renameSession(session.id, renameInput)
                        sessionToRename = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { sessionToRename = null },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                ) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = BgCard
        )
    }
}

@Composable
private fun RideLogCard(
    session: RideSession,
    isExpanded: Boolean,
    speedUnit: SpeedUnit,
    onToggleExpand: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var colorMode by remember { mutableStateOf(RideMapColorMode.SPEED) }

    val distDisplay = if (speedUnit == SpeedUnit.MPH) {
        String.format(Locale.US, "%.1f mi", com.kalaiselvan.ride.utils.UnitConversions.kmToMiles(session.distanceKm))
    } else {
        String.format(Locale.US, "%.1f km", session.distanceKm)
    }
    val peakSpeedDisplay = if (speedUnit == SpeedUnit.MPH) {
        String.format(Locale.US, "%.0f mph", com.kalaiselvan.ride.utils.UnitConversions.kmhToMph(session.topSpeedKmh))
    } else {
        String.format(Locale.US, "%.0f km/h", session.topSpeedKmh)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
    ) {
        // Card Clickable Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Top line: Custom Title or Date + Rename Button
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
                            text = session.displayTitle,
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x15FFFFFF))
                                .clickable { onRename() }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EDIT",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (!isExpanded) {
                        Text(
                            text = session.formattedDuration,
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Show distance, peak speed, and lean angle only when collapsed
                if (!isExpanded) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom line: Distance, Peak Speed, Max Lean
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = distDisplay,
                            color = TextMain,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = "Peak: $peakSpeedDisplay",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = "L ${session.maxLeanLeft.toInt()}° / R ${session.maxLeanRight.toInt()}°",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Expand/Collapse Chevron Canvas
            Canvas(modifier = Modifier.size(12.dp)) {
                val p = Path()
                if (isExpanded) {
                    p.moveTo(0f, size.height * 0.75f)
                    p.lineTo(size.width * 0.5f, size.height * 0.25f)
                    p.lineTo(size.width, size.height * 0.75f)
                } else {
                    p.moveTo(0f, size.height * 0.25f)
                    p.lineTo(size.width * 0.5f, size.height * 0.75f)
                    p.lineTo(size.width, size.height * 0.25f)
                }
                drawPath(path = p, color = if (isExpanded) Color(0xFF30D158) else Color(0xFF7A828A), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Inline Accordion: Expanded Map & Telemetry Details
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

                Spacer(modifier = Modifier.height(10.dp))

                // Map Header & Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Heatmap toggle: Speed vs Lean
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgDark)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (colorMode == RideMapColorMode.SPEED) RpmGreen.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { colorMode = RideMapColorMode.SPEED }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "SPEED",
                                color = if (colorMode == RideMapColorMode.SPEED) RpmGreen else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (colorMode == RideMapColorMode.LEAN) RpmOrange.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { colorMode = RideMapColorMode.LEAN }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "LEAN",
                                color = if (colorMode == RideMapColorMode.LEAN) RpmOrange else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Fullscreen Button (Rounded box)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgDark)
                            .clickable { onOpenFullscreen() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Fullscreen Icon
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val w = size.width
                            val h = size.height
                            val arm = 3.dp.toPx()
                            val strokeW = 1.5.dp.toPx()
                            val c = Color.White
                            // Top-left
                            drawLine(c, Offset(0f, 0f), Offset(arm, 0f), strokeW)
                            drawLine(c, Offset(0f, 0f), Offset(0f, arm), strokeW)
                            // Top-right
                            drawLine(c, Offset(w, 0f), Offset(w - arm, 0f), strokeW)
                            drawLine(c, Offset(w, 0f), Offset(w, arm), strokeW)
                            // Bottom-left
                            drawLine(c, Offset(0f, h), Offset(arm, h), strokeW)
                            drawLine(c, Offset(0f, h), Offset(0f, h - arm), strokeW)
                            // Bottom-right
                            drawLine(c, Offset(w, h), Offset(w - arm, h), strokeW)
                            drawLine(c, Offset(w, h), Offset(w, h - arm), strokeW)
                        }
                        Text(
                            text = "FULLSCREEN",
                            color = TextMain,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Legend row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Leaflet Map
                RideMapView(
                    breadcrumbs = session.breadcrumbs,
                    colorMode = colorMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Telemetry Breakdown (Arranged exactly like DashScreen LAST SESSION SUMMARY)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgDark)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Line 1: Primary Metrics (RIDE TIME, DISTANCE, TOP SPEED, AVG SPEED, AVG FUEL)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DashSummaryTile(
                            value = session.formattedDuration,
                            label = "RIDE TIME"
                        )
                        DashSummaryTile(
                            value = distDisplay,
                            label = "DISTANCE"
                        )
                        DashSummaryTile(
                            value = peakSpeedDisplay,
                            label = "TOP SPEED"
                        )
                        DashSummaryTile(
                            value = if (speedUnit == SpeedUnit.MPH) String.format(Locale.US, "%.0f mph", com.kalaiselvan.ride.utils.UnitConversions.kmhToMph(session.avgSpeedKmh)) else String.format(Locale.US, "%.0f km/h", session.avgSpeedKmh),
                            label = "AVG SPEED"
                        )
                    }

                    HorizontalDivider(color = Color(0x15FFFFFF), thickness = 1.dp)

                    // Line 2: Dynamics & Sensor Metrics (L LEAN, R LEAN, WHEELIE, STOPPIE, G-FORCE, AVG FUEL)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DashSummaryTile(
                            value = "${session.maxLeanLeft.toInt()}°",
                            label = "L LEAN"
                        )
                        DashSummaryTile(
                            value = "${session.maxLeanRight.toInt()}°",
                            label = "R LEAN"
                        )
                        DashSummaryTile(
                            value = if (session.maxWheelie > 0f) String.format(Locale.US, "%.1f°", session.maxWheelie) else "--",
                            label = "WHEELIE"
                        )
                        DashSummaryTile(
                            value = if (session.maxStoppie > 0f) String.format(Locale.US, "%.1f°", session.maxStoppie) else "--",
                            label = "STOPPIE"
                        )
                        DashSummaryTile(
                            value = if (session.maxGForce > 0f) String.format(Locale.US, "%.2f G", session.maxGForce) else "--",
                            label = "G-FORCE"
                        )
                        DashSummaryTile(
                            value = String.format(Locale.US, "%.1f", session.avgFuelMileage),
                            label = if (speedUnit == SpeedUnit.MPH) "MPG" else "KM/L"
                        )
                    }
                }

                // Action Row: Share as GPX & Delete Ride
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x1500E676))
                            .clickable {
                                com.kalaiselvan.ride.logs.ExportManager.exportRideAsGpx(context, session)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SHARE GPX",
                            color = Color(0xFF00E676),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "DELETE THIS RIDE",
                        color = Color(0xFFFF5252).copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDelete() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashSummaryTile(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = TextMain,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun LegendChip(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
