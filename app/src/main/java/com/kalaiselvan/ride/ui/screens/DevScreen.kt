package com.kalaiselvan.ride.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.bluetooth.BikeGattManager
import com.kalaiselvan.ride.logs.ExportManager
import com.kalaiselvan.ride.logs.HexFrameEntry
import com.kalaiselvan.ride.logs.LogStore
import com.kalaiselvan.ride.logs.RideSession
import com.kalaiselvan.ride.logs.RideSessionRecorder
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.theme.neumorphicInner
import com.kalaiselvan.ride.ui.theme.neumorphicOuter

enum class DevPill {
    TFT_TESTER, RIDE_LOGS, HEX_STREAM, SYSTEM_LOGS
}

@Composable
fun DevScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPill by remember { mutableStateOf(DevPill.TFT_TESTER) }

    val systemLogs by LogStore.systemLogs.collectAsState()
    val hexFrames by LogStore.hexFrames.collectAsState()
    val dtcCodes by LogStore.dtcCodes.collectAsState()
    val completedSessions by RideSessionRecorder.completedSessions.collectAsState()
    val telemetry by com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(context).state.collectAsState()

    var isHexPaused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with One-Tap Export Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DEVELOPER SUITE",
                    color = TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "ECU Telemetry & Cluster Reverse Engineering",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            // Export / Share Logs Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF25D366)) // Share Green
                    .clickable {
                        ExportManager.exportLogsAndShare(context)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EXPORT",
                    color = BgDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4-Pill Segmented Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E222A))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PillTab(title = "TESTER", isSelected = selectedPill == DevPill.TFT_TESTER) { selectedPill = DevPill.TFT_TESTER }
            PillTab(title = "RIDES", isSelected = selectedPill == DevPill.RIDE_LOGS) { selectedPill = DevPill.RIDE_LOGS }
            PillTab(title = "HEX", isSelected = selectedPill == DevPill.HEX_STREAM) { selectedPill = DevPill.HEX_STREAM }
            PillTab(title = "SYSTEM", isSelected = selectedPill == DevPill.SYSTEM_LOGS) { selectedPill = DevPill.SYSTEM_LOGS }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedPill) {
                DevPill.TFT_TESTER -> TftTesterContent(context, telemetry)
                DevPill.RIDE_LOGS -> RideLogsContent(context, completedSessions)
                DevPill.HEX_STREAM -> HexStreamContent(context, hexFrames, isHexPaused) { isHexPaused = it }
                DevPill.SYSTEM_LOGS -> SystemLogsContent(context, systemLogs, dtcCodes)
            }
        }
    }
}

@Composable
private fun PillTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) RpmGreen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) BgDark else TextMuted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TftTesterContent(context: Context, telemetry: com.kalaiselvan.ride.telemetry.TelemetryState) {
    val isConnected = telemetry.isBtConnected || (BikeGattManager.instance?.isConnected == true)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // BLE Link Status Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isConnected) Color(0xFF14291E) else Color(0xFF2E1919))
                    .border(
                        1.dp,
                        if (isConnected) RpmGreen.copy(alpha = 0.5f) else RpmRed.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (isConnected) RpmGreen else RpmRed)
                        )
                        Column {
                            Text(
                                text = if (isConnected) "BIKE CONNECTED (FRAME 0x14 ACTIVE)" else "BIKE DISCONNECTED",
                                color = if (isConnected) RpmGreen else RpmRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isConnected) "TFT cluster receives live packets" else "Turn bike ignition ON to transmit packets",
                                color = TextMuted,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    if (!isConnected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RpmGreen)
                                .clickable {
                                    val mac = com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context).userPreferences.value.lastBikeMac
                                    if (mac.isNotBlank()) {
                                        BikeGattManager.instance?.connect(mac, isAutoConnect = false, isDirect = true)
                                        Toast.makeText(context, "Attempting direct BLE connect to $mac...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No saved bike MAC address found.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CONNECT",
                                color = BgDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Active Navigation Preview if set
        if (telemetry.isNavActive) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E222A))
                        .border(1.dp, Color(0xFF2C3440), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CURRENT QUEUED NAV STATE",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = telemetry.navInstruction.ifBlank { "Navigating" },
                                color = TextMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${telemetry.navDistanceMeters}m (Icon ${telemetry.navIconId})",
                            color = RpmGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        item {
            TesterCard(title = "📞 INCOMING CALL MIRRORING (FRAME 0x12)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionBtn("Trigger Call", RpmGreen, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.sendNotification(1, 0)
                            Toast.makeText(context, "✅ Frame 0x12 Call Trigger sent to TFT", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected! Turn ignition ON.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ActionBtn("Clear Call", RpmRed, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.sendNotification(1, 2)
                            Toast.makeText(context, "✅ Frame 0x12 Call Clear sent to TFT", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        item {
            TesterCard(title = "💬 SMS & MESSAGING MIRRORING (FRAME 0x12)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionBtn("Trigger SMS", RpmGreen, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.sendNotification(6, 0)
                            Toast.makeText(context, "✅ Frame 0x12 SMS Trigger sent to TFT", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected! Turn ignition ON.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ActionBtn("Clear SMS", RpmRed, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.sendNotification(6, 2)
                            Toast.makeText(context, "✅ Frame 0x12 SMS Clear sent to TFT", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        item {
            TesterCard(title = "🗺️ TURN-BY-TURN NAVIGATION PUSH (FRAME 0x14)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionBtn("➡️ Right 500m", TextMain, Modifier.weight(1f)) {
                            val gatt = BikeGattManager.instance
                            if (gatt != null && gatt.isConnected) {
                                gatt.sendNavigationData(0, 2, 500, 1, instruction = "In 500m, turn right")
                                Toast.makeText(context, "✅ Sent Right 500m to Bike TFT", Toast.LENGTH_SHORT).show()
                            } else {
                                com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(context).updateNavigationState(true, "In 500m, turn right", 500, 2)
                                Toast.makeText(context, "⚠️ Bike disconnected! Local state updated. Turn ignition ON for TFT.", Toast.LENGTH_LONG).show()
                            }
                        }
                        ActionBtn("⬅️ Left 250m", TextMain, Modifier.weight(1f)) {
                            val gatt = BikeGattManager.instance
                            if (gatt != null && gatt.isConnected) {
                                gatt.sendNavigationData(0, 1, 250, 1, instruction = "In 250m, turn left")
                                Toast.makeText(context, "✅ Sent Left 250m to Bike TFT", Toast.LENGTH_SHORT).show()
                            } else {
                                com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(context).updateNavigationState(true, "In 250m, turn left", 250, 1)
                                Toast.makeText(context, "⚠️ Bike disconnected! Local state updated. Turn ignition ON for TFT.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionBtn("🔄 Roundabout Exit 2", TextMain, Modifier.weight(1f)) {
                            val gatt = BikeGattManager.instance
                            if (gatt != null && gatt.isConnected) {
                                gatt.sendNavigationData(0, 19, 300, 1, instruction = "At roundabout, exit 2")
                                Toast.makeText(context, "✅ Sent Roundabout Exit 2 to Bike TFT", Toast.LENGTH_SHORT).show()
                            } else {
                                com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(context).updateNavigationState(true, "At roundabout, exit 2", 300, 19)
                                Toast.makeText(context, "⚠️ Bike disconnected! Local state updated. Turn ignition ON for TFT.", Toast.LENGTH_LONG).show()
                            }
                        }
                        ActionBtn("↩️ U-Turn 100m", TextMain, Modifier.weight(1f)) {
                            val gatt = BikeGattManager.instance
                            if (gatt != null && gatt.isConnected) {
                                gatt.sendNavigationData(0, 7, 100, 1, instruction = "In 100m, make a U-Turn")
                                Toast.makeText(context, "✅ Sent U-Turn 100m to Bike TFT", Toast.LENGTH_SHORT).show()
                            } else {
                                com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(context).updateNavigationState(true, "In 100m, make a U-Turn", 100, 7)
                                Toast.makeText(context, "⚠️ Bike disconnected! Local state updated. Turn ignition ON for TFT.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionBtn("🏁 Destination Arrived", RpmGreen, Modifier.weight(1f)) {
                            val gatt = BikeGattManager.instance
                            if (gatt != null && gatt.isConnected) {
                                gatt.sendNavigationData(0, 43, 0, 1, instruction = "Destination Arrived")
                                Toast.makeText(context, "✅ Sent Destination Arrived to Bike TFT", Toast.LENGTH_SHORT).show()
                            } else {
                                com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(context).updateNavigationState(true, "Destination Arrived", 0, 43)
                                Toast.makeText(context, "⚠️ Bike disconnected! Local state updated. Turn ignition ON for TFT.", Toast.LENGTH_LONG).show()
                            }
                        }
                        ActionBtn("❌ Clear Navigation", RpmRed, Modifier.weight(1f)) {
                            val gatt = BikeGattManager.instance
                            if (gatt != null && gatt.isConnected) {
                                gatt.sendNavigationData(3, 0xFF, 0, 0x0F, instruction = "")
                                Toast.makeText(context, "✅ Cleared Navigation on Bike TFT", Toast.LENGTH_SHORT).show()
                            } else {
                                com.kalaiselvan.ride.telemetry.TelemetryEngine.getInstance(context).updateNavigationState(false, "", 0, 0)
                                Toast.makeText(context, "Cleared navigation state", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        item {
            TesterCard(title = "🔋 PHONE BATTERY MIRRORING (FRAME 0x13)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionBtn("100%", RpmGreen, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.sendPhoneStatus(100, false)
                            Toast.makeText(context, "Sent Battery 100% to TFT", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ActionBtn("50%", RpmYellow, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.sendPhoneStatus(50, false)
                            Toast.makeText(context, "Sent Battery 50% to TFT", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ActionBtn("15% Low", RpmRed, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.sendPhoneStatus(15, false)
                            Toast.makeText(context, "Sent Battery 15% to TFT", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected", Toast.LENGTH_SHORT).show()
                        }
                    }
                    ActionBtn("⚡ Charging", BluetoothBlue, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.sendPhoneStatus(85, true)
                            Toast.makeText(context, "Sent Battery 85% Charging to TFT", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        item {
            TesterCard(title = "⏰ DASH CLOCK SYNCHRONIZATION (FRAME 0x1B)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionBtn("Sync Clock Now", BluetoothBlue, Modifier.weight(1f)) {
                        val gatt = BikeGattManager.instance
                        if (gatt != null && gatt.isConnected) {
                            gatt.syncDashClock(force = true)
                            Toast.makeText(context, "✅ Frame 0x1B Dash Clock Sync sent", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "⚠️ Bike not connected! Turn ignition ON.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        item {
            TesterCard(title = "⚡ 0-100 SPRINT SIMULATOR & TESTER") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionBtn("Test 3.8s", RpmGreen, Modifier.weight(1f)) {
                        com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context).updateZeroToHundred(3.8f)
                        Toast.makeText(context, "0-100 KM/H set to 3.8s in Achievements", Toast.LENGTH_SHORT).show()
                    }
                    ActionBtn("Test 4.5s", RpmYellow, Modifier.weight(1f)) {
                        com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context).updateZeroToHundred(4.5f)
                        Toast.makeText(context, "0-100 KM/H set to 4.5s in Achievements", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        item {
            val isMockActive = com.kalaiselvan.ride.preferences.MockAchievementsHolder.isMocked
            TesterCard(title = "🏆 ACHIEVEMENTS MOCK (IN-MEMORY)") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionBtn(
                            label = if (isMockActive) "RESET MOCK ACHIEVEMENTS" else "MOCK ACHIEVEMENTS",
                            accentColor = if (isMockActive) RpmRed else RpmGreen,
                            modifier = Modifier.weight(1f)
                        ) {
                            com.kalaiselvan.ride.preferences.MockAchievementsHolder.isMocked = !isMockActive
                            Toast.makeText(
                                context,
                                if (!isMockActive) "✅ Mock applied: Top Speed 246, 0-100 3.4s, Max G 0.99" else "Real achievement records restored",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    Text(
                        text = "Top Speed: 246 km/h (18th Oct) • 0-100: 3.4s • Max G: 0.99\nTemporary in-memory: Reverts to actual values when app is killed & reopened.",
                        color = TextMuted,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RideLogsContent(context: Context, sessions: List<RideSession>) {
    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No recorded rides yet.", color = TextMuted, fontSize = 12.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sessions) { s ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgCard)
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = s.formattedDate,
                                color = TextMain,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF25D366).copy(alpha = 0.2f))
                                    .clickable {
                                        ExportManager.exportRideSession(context, s)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(text = "SHARE", color = Color(0xFF25D366), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            RideMetric("DIST", String.format("%.1f km", s.distanceKm))
                            RideMetric("TIME", s.formattedDuration)
                            RideMetric("TOP SPEED", "${s.topSpeedKmh.toInt()} km/h")
                            RideMetric("LEAN L/R", "${s.maxLeanLeft.toInt()}°/${s.maxLeanRight.toInt()}°")
                            RideMetric("AVG FUEL", String.format("%.1f", s.avgFuelMileage))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RideMetric(label: String, value: String) {
    Column {
        Text(text = label, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(text = value, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun HexStreamContent(
    context: Context,
    hexFrames: List<HexFrameEntry>,
    isPaused: Boolean,
    onPauseToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${hexFrames.size} FRAMES IN BUFFER",
                color = TextMuted,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPaused) RpmOrange else Color(0x22FFFFFF))
                        .clickable { onPauseToggle(!isPaused) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isPaused) "RESUME" else "PAUSE",
                        color = if (isPaused) BgDark else TextMain,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable {
                            val allHex = hexFrames.joinToString("\n") { "[${it.timestamp}] ${it.direction} ${it.frameId}: ${it.hex}" }
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("MyRide Hex", allHex))
                            Toast.makeText(context, "Hex buffer copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = "COPY", color = TextMain, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { LogStore.clearHexFrames() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = "CLEAR", color = RpmRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0D0F12))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            if (hexFrames.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Awaiting BLE frames from bike...", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(hexFrames) { f ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(
                                text = "${f.timestamp} ",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${f.direction} ${f.frameId}: ",
                                color = if (f.direction == "TX") BluetoothBlue else RpmGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = f.hex,
                                color = TextMain,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemLogsContent(
    context: Context,
    logs: List<String>,
    dtcCodes: List<String>
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // DTC Codes Banner
        if (dtcCodes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RpmRed.copy(alpha = 0.15f))
                    .border(1.dp, RpmRed, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "⚠️ ACTIVE DTC CODES: ${dtcCodes.joinToString(", ")}",
                    color = RpmRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "${logs.size} SYSTEM EVENTS", color = TextMuted, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x22FFFFFF))
                    .clickable { LogStore.clearSystemLogs() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(text = "CLEAR", color = RpmRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0D0F12))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No system logs recorded.", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = TextSecondary,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TesterCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 6.dp)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            content()
        }
    }
}

@Composable
private fun ActionBtn(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF22262C))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
