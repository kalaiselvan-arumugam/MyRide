package com.kalaiselvan.ride.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.telemetry.CruiseState
import com.kalaiselvan.ride.telemetry.TelemetryState

import com.kalaiselvan.ride.theme.BgCard
import com.kalaiselvan.ride.theme.RpmGreen
import com.kalaiselvan.ride.theme.RpmOrange
import com.kalaiselvan.ride.theme.TextMain
import com.kalaiselvan.ride.theme.TextMuted
import com.kalaiselvan.ride.ui.components.*
import com.kalaiselvan.ride.ui.theme.neumorphicOuter

/**
 * Single-View Telemetry Dashboard matching the user's reference design pixel-perfect.
 * Zero-scroll high-density motorcycle instrument cluster with adaptive portrait and landscape layouts.
 */
@Composable
fun DashScreen(
    telemetry: TelemetryState,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var hasRunEntranceAnimation by rememberSaveable { mutableStateOf(false) }
    val shouldAnimate = !hasRunEntranceAnimation

    // Cascading staggered fall-in animations for each section
    val animProgress1 = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val animProgress2 = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val animProgress3 = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val animProgress4 = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    val animProgress5 = remember { Animatable(if (shouldAnimate) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (!hasRunEntranceAnimation) {
            hasRunEntranceAnimation = true
            val springSpec = spring<Float>(
                dampingRatio = 0.65f, // Bouncy spring
                stiffness = 180f     // Distinct, clearly visible speed
            )
            // Staggered launch sequence in coroutine scope
            launch { animProgress1.animateTo(1f, springSpec) }
            delay(70)
            launch { animProgress2.animateTo(1f, springSpec) }
            delay(70)
            launch { animProgress3.animateTo(1f, springSpec) }
            delay(70)
            launch { animProgress4.animateTo(1f, springSpec) }
            delay(70)
            launch { animProgress5.animateTo(1f, springSpec) }
        }
    }

    val animProgressList = listOf(animProgress1.value, animProgress2.value, animProgress3.value, animProgress4.value, animProgress5.value)

    if (isLandscape) {
        DashScreenLandscape(telemetry = telemetry, animProgress = animProgressList, modifier = modifier)
    } else {
        DashScreenPortrait(telemetry = telemetry, animProgress = animProgressList, modifier = modifier)
    }
}

@Composable
fun DashScreenPortrait(
    telemetry: TelemetryState,
    animProgress: List<Float> = listOf(1f, 1f, 1f, 1f, 1f),
    modifier: Modifier = Modifier
) {
    var showParkedViewWhenDisconnected by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Helper to apply physics drop-in translation & alpha
        fun Modifier.fallIn(stepIndex: Int): Modifier {
            val p = animProgress.getOrElse(stepIndex) { 1f }
            val offsetY = (1f - p) * -160f
            return this.graphicsLayer {
                translationY = offsetY
                alpha = p.coerceIn(0f, 1f)
            }
        }

        // 1. Hero Dial Flanked by Left & Right HUD Columns
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp)
                .fallIn(0),
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
            // When disconnected, user can swipe horizontally on the dial to switch between Gauge & Parked Bike view
            if (telemetry.isBtConnected) {
                GaugeDial(
                    telemetry = telemetry,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                var totalDragX by remember { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onDragEnd = {
                                    if (kotlin.math.abs(totalDragX) > 40f) {
                                        showParkedViewWhenDisconnected = !showParkedViewWhenDisconnected
                                    }
                                    totalDragX = 0f
                                },
                                onDragCancel = { totalDragX = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDragX += dragAmount
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = showParkedViewWhenDisconnected,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "HeroDialSwipeTransition"
                    ) { showParked ->
                        if (showParked) {
                            ParkedBikeView(telemetry = telemetry)
                        } else {
                            GaugeDial(telemetry = telemetry)
                        }
                    }
                }
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
        Box(modifier = Modifier.fallIn(1)) {
            GearRow(
                currentGear = telemetry.gear,
                isShifting = telemetry.isShifting,
                isShiftIndicatorActive = telemetry.isShiftIndicatorActive
            )
        }

        // 3. Ride Modes & Stats Row
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fallIn(2)) {
            RideModesAndStatsRow(
                rangeKm = telemetry.displayRange,
                avgMil = telemetry.displayAvgMil,
                activeMode = telemetry.ridingMode,
                unitLabel = telemetry.speedUnitLabel
            )
        }

        // 4. Throttle Bar
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fallIn(3)) {
            ThrottleBar(
                throttlePercent = telemetry.throttle,
                shiftLampRpm = telemetry.shiftLampRpm
            )
        }

        // 5. 4 Orbiting Satellites
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fallIn(4)) {
            SatellitesRow(
                coolantTemp = telemetry.displayCoolantTemp,
                fuelPercent = telemetry.fuelPercent,
                airTemp = telemetry.displayAirTemp,
                batteryVolt = telemetry.batteryVolt
            )
        }

        // 6. Session Summary
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fallIn(4)) {
            SessionSummaryCard(telemetry = telemetry)
        }
    }
}

/**
 * High-Density Widescreen Landscape Cockpit matching the user's authentic design.
 * 100% seamless cockpit surface on BgDark (NO cards, NO enclosing card boxes).
 * Left Side: Exact Portrait Hero Dial flanked symmetrically by Left HUD (Cruise, KTRC, Trip A)
 *            and Right HUD (G-Force, KQS, Trip B) with Gear Indicator Row underneath.
 * Right Side: Telemetry Flight Deck (Riding Modes, Range, Avg Mil, 4 Satellites, Throttle, Session Summary).
 * Zero vertical scrolling across all standard aspect ratios.
 */
@Composable
fun DashScreenLandscape(
    telemetry: TelemetryState,
    animProgress: List<Float> = listOf(1f, 1f, 1f, 1f, 1f),
    modifier: Modifier = Modifier
) {
    var showParkedViewWhenDisconnected by remember { mutableStateOf(true) }

    fun Modifier.fallIn(stepIndex: Int): Modifier {
        val p = animProgress.getOrElse(stepIndex) { 1f }
        val offsetY = (1f - p) * -160f
        return this.graphicsLayer {
            translationY = offsetY
            alpha = p.coerceIn(0f, 1f)
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // =========================================================================
        // === LEFT SIDE: HERO CLUSTER (DIAL FLANKED SIDES BY CRUISE/KTRC/TRIPS) ====
        // =========================================================================
        Column(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight()
                .fallIn(0),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upper: Hero Dial flanked directly on left and right, identical to portrait!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Left HUD Column (Cruise, KTRC, Trip A)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Text(
                            text = cruiseValueText,
                            color = cruiseColor,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CRUISE",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    // KTRC Icon + Level
                    val ktrcInfiniteTransition = rememberInfiniteTransition(label = "ktrcLandscape")
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
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
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
                                color = if (telemetry.ktrcActive) RpmOrange else if (telemetry.isBtConnected) TextMain else TextMuted,
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
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Trip A
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", telemetry.displayTripA),
                            color = TextMain,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (telemetry.speedUnitLabel == "MPH") "TRIP A (MI)" else "TRIP A (KM)",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }

                // Center Hero Dial (230dp full size, identical to portrait)
                // When disconnected, user can swipe horizontally on the dial to switch between Gauge & Parked Bike view
                if (telemetry.isBtConnected) {
                    GaugeDial(
                        telemetry = telemetry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    var totalDragX by remember { mutableStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDragX = 0f },
                                    onDragEnd = {
                                        if (kotlin.math.abs(totalDragX) > 40f) {
                                            showParkedViewWhenDisconnected = !showParkedViewWhenDisconnected
                                        }
                                        totalDragX = 0f
                                    },
                                    onDragCancel = { totalDragX = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        totalDragX += dragAmount
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = showParkedViewWhenDisconnected,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "HeroDialSwipeTransitionLandscape"
                        ) { showParked ->
                            if (showParked) {
                                ParkedBikeView(telemetry = telemetry)
                            } else {
                                GaugeDial(telemetry = telemetry)
                            }
                        }
                    }
                }

                // Right HUD Column (G-Force, KQS, Trip B)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // G-Force
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (telemetry.isBtConnected) String.format(java.util.Locale.US, "%.2f", telemetry.gForce) else "--",
                                color = RpmOrange,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 19.sp
                            )
                            Text(
                                text = "G",
                                color = if (telemetry.isBtConnected) RpmOrange else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                            )
                        }
                        Text(
                            text = "G-FORCE",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    // Quickshifter Icon + Label
                    val kqsColor by animateColorAsState(
                        targetValue = if (telemetry.kqsActive || telemetry.kqsStatus == "ON") RpmGreen else TextMuted,
                        label = "kqsColorLandscape"
                    )
                    val kqsArrowPath = androidx.compose.runtime.remember {
                        androidx.compose.ui.graphics.vector.PathParser().parsePathString("M8 9l4-4 4 4H8zm8 6l-4 4-4-4h8z").toPath()
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
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
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Trip B
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", telemetry.displayTripB),
                            color = TextMain,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (telemetry.speedUnitLabel == "MPH") "TRIP B (MI)" else "TRIP B (KM)",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            }

            // Lower: Large Gear Indicator Row (1 N 2 3 4 5 6) directly anchored under Hero Cluster
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                GearRow(
                    currentGear = telemetry.gear,
                    isShifting = telemetry.isShifting,
                    isShiftIndicatorActive = telemetry.isShiftIndicatorActive
                )
            }
        }

        // =========================================================================
        // === VERTICAL HAIRLINE DIVIDER ===========================================
        // =========================================================================
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.92f)
                .background(TextMuted.copy(alpha = 0.2f))
        )

        // =========================================================================
        // === RIGHT SIDE: TELEMETRY FLIGHT DECK (~52% width) =======================
        // =========================================================================
        Column(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: Range & Avg Mil + Riding Modes (RAIN ROAD SPORT RIDER)
            Box(modifier = Modifier.fillMaxWidth().fallIn(1)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Estimated Range
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = if (telemetry.displayRange > 0) "${telemetry.displayRange} km" else "-- km",
                            color = TextMain,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "RANGE",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Riding Modes
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("RAIN", "ROAD", "SPORT", "RIDER").forEach { mode ->
                            val isActive = telemetry.ridingMode.isNotBlank() && telemetry.ridingMode.equals(mode, ignoreCase = true)
                            Text(
                                text = mode,
                                color = if (isActive) TextMain else TextMuted.copy(alpha = 0.35f),
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Average Mileage
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (telemetry.displayAvgMil > 0f) String.format(java.util.Locale.US, "%.1f", telemetry.displayAvgMil) else "--",
                            color = TextMain,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (telemetry.speedUnitLabel == "MPH") "AVG MPG" else "AVG KM/L",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Row 2: 4 Satellite Pods in a unified clean line (WATER, FUEL, AIR, VOLT)
            Box(modifier = Modifier.fillMaxWidth().fallIn(2)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SatellitePod(value = if (telemetry.displayCoolantTemp > 0) "${telemetry.displayCoolantTemp}°" else "--°", label = "WATER", modifier = Modifier.size(54.dp))
                    SatellitePod(value = if (telemetry.fuelPercent > 0) "${telemetry.fuelPercent}%" else "--%", label = "FUEL", modifier = Modifier.size(54.dp))
                    SatellitePod(value = if (telemetry.displayAirTemp > 0) "${telemetry.displayAirTemp}°" else "--°", label = "AIR", modifier = Modifier.size(54.dp))
                    SatellitePod(value = if (telemetry.batteryVolt > 0f) String.format(java.util.Locale.US, "%.1f", telemetry.batteryVolt) else "--", label = "VOLT", modifier = Modifier.size(54.dp))
                }
            }

            // Row 3: Wide Throttle Progress Bar
            Box(modifier = Modifier.fillMaxWidth().fallIn(3)) {
                ThrottleBar(
                    throttlePercent = telemetry.throttle,
                    shiftLampRpm = telemetry.shiftLampRpm,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Row 4: Full-Width Session Summary (Hairline divider + Ride Time, Distance, and 5 Sensor Metrics)
            Box(modifier = Modifier.fillMaxWidth().fallIn(4)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TextMuted.copy(alpha = 0.15f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (telemetry.isBtConnected) "SESSION SUMMARY" else "LAST SESSION SUMMARY",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalSec = telemetry.sessionTimeMs / 1000
                        val mins = (totalSec / 60).toInt()
                        val secs = (totalSec % 60).toInt()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "%02d:%02d".format(mins, secs),
                                color = TextMain,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(text = "RIDE TIME", color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }

                        val distVal = if (telemetry.speedUnitLabel == "MPH") com.kalaiselvan.ride.utils.UnitConversions.kmToMiles(telemetry.sessionDistanceKm) else telemetry.sessionDistanceKm
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f %s", distVal, if (telemetry.speedUnitLabel == "MPH") "mi" else "km"),
                                color = TextMain,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(text = "DISTANCE", color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${telemetry.sessionMaxLeanL.toInt()}°", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(text = "L LEAN", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${telemetry.sessionMaxLeanR.toInt()}°", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(text = "R LEAN", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = String.format(java.util.Locale.US, "%.1f°", telemetry.sessionMaxWheelie), color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(text = "WHEELIE", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = String.format(java.util.Locale.US, "%.1f°", telemetry.sessionMaxStoppie), color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(text = "STOPPIE", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        val gMaxDisplay = if (telemetry.isBtConnected && telemetry.sessionMaxG > 0f) String.format(java.util.Locale.US, "%.2f", telemetry.sessionMaxG) else "--"
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = gMaxDisplay, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(text = "G-FORCE", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

