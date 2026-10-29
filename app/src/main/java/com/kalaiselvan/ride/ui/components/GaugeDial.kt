package com.kalaiselvan.ride.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.telemetry.TelemetryState
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.theme.neumorphicCircleInner
import com.kalaiselvan.ride.ui.theme.neumorphicCircleOuter
import kotlin.math.cos
import kotlin.math.sin

/**
 * Center Hero Instrument Cluster Dial matching user's reference design.
 * 230dp circular gauge with dynamic RPM sweep, enlarged speed display, mechanical odometer,
 * dual-arc dynamic lean angle indicator, and outer compass ring with real-time North arrow.
 */
@Composable
fun GaugeDial(
    telemetry: TelemetryState,
    modifier: Modifier = Modifier
) {
    // Smooth continuous angle unwrapping for North arrow
    var continuousHeading by remember { mutableFloatStateOf(telemetry.headingDegrees) }
    LaunchedEffect(telemetry.headingDegrees) {
        val diff = ((telemetry.headingDegrees - continuousHeading + 540f) % 360f) - 180f
        continuousHeading += diff
    }
    val animatedHeading by animateFloatAsState(
        targetValue = continuousHeading,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "compassNorth"
    )

    Box(
        modifier = modifier
            .size(230.dp)
            .neumorphicCircleOuter(blurRadius = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) - 13.dp.toPx()
            val strokeWidth = 12.dp.toPx()

            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // 0. Outer North Compass Arrow (Golden Triangle)
            // In Canvas coordinates: 270° is straight UP (12 o'clock).
            // When device azimuth is θ, North is at (270° - θ).
            val arrowRadius = (size.minDimension / 2f) - 3.dp.toPx()
            val northAngle = 270f - animatedHeading
            withTransform({
                rotate(degrees = northAngle, pivot = center)
            }) {
                val tipX = center.x + arrowRadius + 2.dp.toPx()
                val baseX = center.x + arrowRadius - 6.dp.toPx()
                val halfW = 4.dp.toPx()

                val arrowPath = Path().apply {
                    moveTo(tipX, center.y)
                    lineTo(baseX, center.y - halfW)
                    lineTo(baseX, center.y + halfW)
                    close()
                }

                drawPath(
                    path = arrowPath,
                    color = RpmYellow,
                    style = Fill
                )
            }

            // 1. Background RPM Track: starts at 150°, sweeps 240°
            drawArc(
                color = DialTrackBg,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Dynamic RPM Progress Arc (only drawn when RPM > 0)
            if (telemetry.rpm > 0f) {
                val rpmRatio = telemetry.rpmRatio.coerceIn(0.005f, 1f)
                val rpmSweep = (rpmRatio * 240f).coerceAtLeast(1f)

                // Tachometer arc starts at 150° and sweeps clockwise up to 240° (ending at 390° / 30°).
                // Using an Android SweepGradient with a localMatrix rotated by 150° ensures:
                // 0° (0 RPM / 150° screen) = Green
                // 65° (~3,200 RPM / 215° screen) = Green
                // 120° (~6,000 RPM / 270° screen, top) = Yellow
                // 173° (~8,600 RPM / 323° screen) = Orange
                // 223° (~11,000 RPM / 13° screen, redline) = Red
                // 260° (~13,000 RPM) = Red (covers rounded end cap at max sweep)
                // 315°..360° = Green (wraps around to ensure rounded start cap at 150° is 100% Green, never red)
                val colors = intArrayOf(
                    RpmGreen.toArgb(),
                    RpmGreen.toArgb(),
                    RpmYellow.toArgb(),
                    RpmOrange.toArgb(),
                    RpmRed.toArgb(),
                    RpmRed.toArgb(),
                    RpmGreen.toArgb(),
                    RpmGreen.toArgb()
                )
                val positions = floatArrayOf(
                    0.000f,
                    0.180f,
                    0.333f,
                    0.480f,
                    0.620f,
                    0.720f,
                    0.880f,
                    1.000f
                )
                val shader = android.graphics.SweepGradient(center.x, center.y, colors, positions).apply {
                    val matrix = android.graphics.Matrix()
                    matrix.postRotate(150f, center.x, center.y)
                    setLocalMatrix(matrix)
                }
                val brush = androidx.compose.ui.graphics.ShaderBrush(shader)

                drawArc(
                    brush = brush,
                    startAngle = 150f,
                    sweepAngle = rpmSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // 3. Lean Angle Bottom Reference Tracks (start from 90° bottom center)
            // Left reference track (positive sweep towards 140°)
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = 90f,
                sweepAngle = 50f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Right reference track (negative sweep towards 40°)
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = 90f,
                sweepAngle = -50f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Dynamic Real-Time Lean Indicator Arcs
            val currentLean = telemetry.currentLean.coerceIn(0f, 60f)
            val isLeft = telemetry.leanDirection == "LEFT"
            val isRight = telemetry.leanDirection == "RIGHT"

            val leftLeanRatio = if (isLeft) (currentLean / 55f).coerceIn(0f, 1f) else 0f
            val rightLeanRatio = if (isRight) (currentLean / 55f).coerceIn(0f, 1f) else 0f

            // Dynamic Left Lean Arc (grows on left lean)
            if (leftLeanRatio > 0.01f) {
                drawArc(
                    color = telemetry.leanColor,
                    startAngle = 90f,
                    sweepAngle = leftLeanRatio * 50f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Dynamic Right Lean Arc (grows on right lean)
            if (rightLeanRatio > 0.01f) {
                drawArc(
                    color = telemetry.leanColor,
                    startAngle = 90f,
                    sweepAngle = -(rightLeanRatio * 50f),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Inner Neumorphic Center Hub (180dp)
        Box(
            modifier = Modifier
                .size(180.dp)
                .neumorphicCircleInner(shadowWidth = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                // RPM Digital Text (Flashes authentic orange when Shift-Up indicator lamp threshold is reached)
                val shiftStrobe = rememberInfiniteTransition(label = "shiftRpmStrobe")
                val shiftFlashAlpha by shiftStrobe.animateFloat(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(120, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "shiftFlashAlpha"
                )

                val rpmTextColor = when {
                    telemetry.isShiftIndicatorActive -> if (shiftFlashAlpha > 0.45f) RpmOrange else Color.Transparent
                    telemetry.rpm > 10000f -> RpmRed
                    else -> TextMuted
                }
                Text(
                    text = "${telemetry.displayRpm} RPM",
                    color = rpmTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                // Big Digital Speed
                Text(
                    text = "${telemetry.displaySpeed}",
                    color = TextMain,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-2).sp,
                    lineHeight = 70.sp
                )

                // Speed Unit Label
                Text(
                    text = telemetry.speedUnitLabel,
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.offset(y = (-2).dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Split bottom row: Odometer & Lean Angle aligned in straight line
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Clean Odometer
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val odoInt = telemetry.displayOdo
                        val odoStr = "%05d".format(odoInt)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(20.dp)
                        ) {
                            odoStr.forEach { char ->
                                Text(
                                    text = "$char",
                                    color = TextMain,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                        Text(
                            text = if (telemetry.speedUnitLabel == "MPH") "ODO MI" else "ODO KM",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    // Lean Angle aligned with ODO
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text(
                                text = "${telemetry.currentLean.toInt()}°",
                                color = TextMain,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 17.sp
                            )
                        }
                        Text(
                            text = "LEAN",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
