package com.kalaiselvan.ride.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.telemetry.TelemetryState
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.theme.neumorphicCircleInner
import com.kalaiselvan.ride.ui.theme.neumorphicCircleOuter

/**
 * Authentic Parked Motorcycle View with Center Location Pin.
 * 
 * Replaces the Hero Gauge Dial when the bike is disconnected/parked:
 * 1. Center-aligned high-tech Location Pin with subtle GPS radar pulse.
 * 2. When location is active and within expiry timeout, tapping opens navigation in walk mode.
 * 3. When expiry timeout is exceeded, gracefully shows "LOCATION EXPIRED".
 * 4. When no location is found, cleanly displays "NO LOCATION".
 * 5. Perfectly maintains the 230dp bezel dimensions so all surrounding gauges stay undisturbed.
 */
@Composable
fun ParkedBikeView(
    telemetry: TelemetryState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appPrefs = remember { com.kalaiselvan.ride.preferences.AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()

    val hasParkedGps = telemetry.parkedLat != 0.0 && telemetry.parkedLon != 0.0
    val expiryMs = userPrefs.parkedExpiryHours * 3600_000L
    val isExpired = hasParkedGps && telemetry.parkedTimeMs > 0L && ((System.currentTimeMillis() - telemetry.parkedTimeMs) > expiryMs)
    val hasValidParkedGps = hasParkedGps && !isExpired

    val openWalkMode = {
        if (hasValidParkedGps) {
            val uri = Uri.parse(
                java.lang.String.format(
                    java.util.Locale.ROOT,
                    "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=walking",
                    telemetry.parkedLat,
                    telemetry.parkedLon
                )
            )
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No navigation app found", Toast.LENGTH_SHORT).show()
            }
        } else if (isExpired) {
            Toast.makeText(context, "Parked location expired (${userPrefs.parkedExpiryHours}h limit). Set in Settings.", Toast.LENGTH_SHORT).show()
        }
    }

    // Radar pulse animation when GPS location is locked and not expired
    val infiniteTransition = rememberInfiniteTransition(label = "gpsPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Smooth continuous angle unwrapping for North compass arrow
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

    val currentHeadingDeg = ((telemetry.headingDegrees % 360f + 360f) % 360f).toInt()
    val cardinalDirection = when (currentHeadingDeg) {
        in 23..67 -> "NE"
        in 68..112 -> "E"
        in 113..157 -> "SE"
        in 158..202 -> "S"
        in 203..247 -> "SW"
        in 248..292 -> "W"
        in 293..337 -> "NW"
        else -> "N"
    }

    Box(
        modifier = modifier
            .size(230.dp)
            .neumorphicCircleOuter(blurRadius = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer North Compass Arrow (Golden Triangle)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val arrowRadius = (size.minDimension / 2f) - 3.dp.toPx()
            val northAngle = 270f - animatedHeading

            withTransform({
                rotate(degrees = northAngle, pivot = center)
            }) {
                // North Arrow (Golden Triangle)
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
        }

        // Inner depth inset
        Box(
            modifier = Modifier
                .size(206.dp)
                .neumorphicCircleInner(shadowWidth = 6.dp)
                .clip(CircleShape)
                .clickable(enabled = hasParkedGps) { openWalkMode() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Real-time Compass Heading Text
                Text(
                    text = "$currentHeadingDeg° $cardinalDirection",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 1. Center Aligned Location Pin Icon
                Box(
                    modifier = Modifier.size(width = 68.dp, height = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val headCy = h * 0.36f
                        val headRadius = w * 0.32f
                        val tipY = h * 0.86f

                        // A. Ground contact shadow beneath pin
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x99000000), Color(0x00000000)),
                                center = Offset(cx, h * 0.93f),
                                radius = w * 0.35f
                            ),
                            topLeft = Offset(cx - w * 0.35f, h * 0.88f),
                            size = Size(w * 0.70f, h * 0.10f)
                        )

                        // B. Animated GPS Radar Pulse Ring
                        if (hasValidParkedGps) {
                            drawCircle(
                                color = Color(0xFF00E676).copy(alpha = pulseAlpha),
                                radius = headRadius * pulseScale,
                                center = Offset(cx, headCy),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // C. Geometric Pin Body Path (Mathematically exact tangent lines)
                        val dist = tipY - headCy
                        val sinAlpha = (headRadius / dist).coerceIn(0.1f, 0.99f)
                        val alphaRad = kotlin.math.asin(sinAlpha)
                        val alphaDeg = Math.toDegrees(alphaRad.toDouble()).toFloat()

                        val cosAlpha = kotlin.math.cos(alphaRad)
                        val tangentX = headRadius * cosAlpha
                        val tangentY = headRadius * sinAlpha

                        val pinPath = Path().apply {
                            // Start at the bottom sharp tip
                            moveTo(cx, tipY)
                            // Straight line up to left tangent point
                            lineTo(cx - tangentX, headCy + tangentY)
                            // Sweep clockwise over the circular top dome to right tangent point
                            arcTo(
                                rect = Rect(
                                    cx - headRadius,
                                    headCy - headRadius,
                                    cx + headRadius,
                                    headCy + headRadius
                                ),
                                startAngleDegrees = 180f - alphaDeg,
                                sweepAngleDegrees = 180f + (2f * alphaDeg),
                                forceMoveTo = false
                            )
                            // Straight line from right tangent point back to the tip
                            lineTo(cx, tipY)
                            close()
                        }

                        // Vibrant Gradient fill
                        val pinBrush = if (hasValidParkedGps) {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF00FF88), Color(0xFF00C853)),
                                startY = headCy - headRadius,
                                endY = tipY
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF757575), Color(0xFF424242)),
                                startY = headCy - headRadius,
                                endY = tipY
                            )
                        }
                        drawPath(path = pinPath, brush = pinBrush)

                        // Crisp highlight outline
                        drawPath(
                            path = pinPath,
                            color = if (hasValidParkedGps) Color(0xAAFFFFFF) else Color(0x33FFFFFF),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // D. Center Hole Cutout (Dark background circle)
                        drawCircle(
                            color = Color(0xFF1E2124),
                            radius = headRadius * 0.40f,
                            center = Offset(cx, headCy)
                        )

                        // E. Center Core Dot
                        if (hasValidParkedGps) {
                            drawCircle(
                                color = Color(0xFF00FF88),
                                radius = headRadius * 0.18f,
                                center = Offset(cx, headCy)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Status & Navigation Trigger (Clean Text, no badge)
                if (hasValidParkedGps) {
                    Text(
                        text = "Take me to the bike",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = if (telemetry.parkedTimeFormatted.isNotEmpty()) telemetry.parkedTimeFormatted else "Parked Location Saved",
                        color = TextMuted,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                } else if (isExpired) {
                    Text(
                        text = "LOCATION EXPIRED",
                        color = RpmOrange,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Expired (${userPrefs.parkedExpiryHours}h limit)",
                        color = TextMuted,
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "NO LOCATION",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Auto-saves when parked",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
