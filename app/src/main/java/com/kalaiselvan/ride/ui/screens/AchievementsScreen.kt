package com.kalaiselvan.ride.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.preferences.AppPreferences
import com.kalaiselvan.ride.preferences.SpeedUnit
import com.kalaiselvan.ride.theme.*
import java.util.Locale

data class AchievementRecord(
    val title: String,
    val value: String,
    val unit: String? = null,
    val date: String,
    val iconType: Int,
    val iconColor: Color
)

@Composable
fun AchievementsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences.getInstance(context) }
    val userPrefs by appPrefs.userPreferences.collectAsState()

    val isMocked = com.kalaiselvan.ride.preferences.MockAchievementsHolder.isMocked
    val rawTopSpeed = if (isMocked) com.kalaiselvan.ride.preferences.MockAchievementsHolder.MOCK_TOP_SPEED else userPrefs.topSpeedKmh
    val topSpeedDateText = if (isMocked) com.kalaiselvan.ride.preferences.MockAchievementsHolder.MOCK_TOP_SPEED_DATE else userPrefs.topSpeedDate

    val displayTopSpeed = when (userPrefs.speedUnit) {
        SpeedUnit.KMH -> rawTopSpeed
        SpeedUnit.MPH -> rawTopSpeed * 0.621371f
    }
    val topSpeedUnitLabel = when (userPrefs.speedUnit) {
        SpeedUnit.KMH -> "KM/H"
        SpeedUnit.MPH -> "MPH"
    }

    val topSpeedAnim = remember { Animatable(0f) }

    LaunchedEffect(displayTopSpeed) {
        topSpeedAnim.animateTo(
            targetValue = displayTopSpeed,
            animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
        )
    }

    val distConverted = when (userPrefs.speedUnit) {
        SpeedUnit.KMH -> userPrefs.longestDistKm
        SpeedUnit.MPH -> userPrefs.longestDistKm * 0.621371
    }
    val distUnitLabel = when (userPrefs.speedUnit) {
        SpeedUnit.KMH -> "KM"
        SpeedUnit.MPH -> "MI"
    }

    val hrs = userPrefs.longestDurationSec / 3600
    val mins = (userPrefs.longestDurationSec % 3600) / 60
    val formattedDuration = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"

    val raw0to100 = if (isMocked) com.kalaiselvan.ride.preferences.MockAchievementsHolder.MOCK_ZERO_TO_HUNDRED else userPrefs.zeroToHundredSec
    val date0to100 = if (isMocked) com.kalaiselvan.ride.preferences.MockAchievementsHolder.MOCK_ZERO_TO_HUNDRED_DATE else userPrefs.zeroToHundredDate

    val rawGForce = if (isMocked) com.kalaiselvan.ride.preferences.MockAchievementsHolder.MOCK_MAX_G_FORCE else userPrefs.maxGForce
    val dateGForce = if (isMocked) com.kalaiselvan.ride.preferences.MockAchievementsHolder.MOCK_MAX_G_FORCE_DATE else userPrefs.maxGForceDate

    val records = listOf(
        AchievementRecord(
            title = if (userPrefs.speedUnit == SpeedUnit.MPH) "0-60 MPH" else "0-100 KM/H",
            value = if (raw0to100 > 0f) String.format(Locale.US, "%.1f", raw0to100) else "--",
            unit = if (raw0to100 > 0f) "S" else null,
            date = date0to100,
            iconType = 0,
            iconColor = TextMain
        ),
        AchievementRecord(
            title = "MAX G-FORCE",
            value = String.format(Locale.US, "%.2f", rawGForce),
            unit = null,
            date = dateGForce,
            iconType = 1,
            iconColor = RpmOrange
        ),
        AchievementRecord(
            title = "LEAN LEFT",
            value = "${userPrefs.maxLeanLeft.toInt()}°",
            unit = null,
            date = userPrefs.maxLeanLeftDate,
            iconType = 2,
            iconColor = RpmGreen
        ),
        AchievementRecord(
            title = "LEAN RIGHT",
            value = "${userPrefs.maxLeanRight.toInt()}°",
            unit = null,
            date = userPrefs.maxLeanRightDate,
            iconType = 3,
            iconColor = RpmGreen
        ),
        AchievementRecord(
            title = "MAX WHEELIE",
            value = String.format(Locale.US, "%.1f°", userPrefs.maxWheelie),
            unit = null,
            date = userPrefs.maxWheelieDate,
            iconType = 4,
            iconColor = TextMuted
        ),
        AchievementRecord(
            title = "MAX STOPPIE",
            value = String.format(Locale.US, "%.1f°", userPrefs.maxStoppie),
            unit = null,
            date = userPrefs.maxStoppieDate,
            iconType = 5,
            iconColor = TextMuted
        ),
        AchievementRecord(
            title = "LONG RIDE (DIST)",
            value = if (distConverted > 0.0) String.format(Locale.US, "%.1f", distConverted) else "--",
            unit = if (distConverted > 0.0) distUnitLabel else null,
            date = userPrefs.longestDistDate,
            iconType = 6,
            iconColor = TextMain
        ),
        AchievementRecord(
            title = "LONG RIDE (TIME)",
            value = if (userPrefs.longestDurationSec > 0L) formattedDuration else "--",
            unit = null,
            date = userPrefs.longestDurationDate,
            iconType = 7,
            iconColor = TextMain
        )
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Top Speed Display matching design spec with larger typography
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TOP SPEED",
                color = TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Text(
                text = "${topSpeedAnim.value.toInt()}",
                color = TextMain,
                fontSize = 118.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 118.sp
            )

            Text(
                text = topSpeedUnitLabel,
                color = TextMuted,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )

            Text(
                text = topSpeedDateText,
                color = Color(0x66FFFFFF),
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // RECORDS Header matching design spec
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
        ) {
            Text(
                text = "RECORDS",
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        // Horizontal Snap-Cards Carousel matching design spec
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(records) { item ->
                AchievementCard(item = item)
            }
        }
    }
}

@Composable
fun AchievementCard(
    item: AchievementRecord,
    modifier: Modifier = Modifier
) {
    val cardGradient = Brush.linearGradient(
        colors = listOf(Color(0x0DFFFFFF), Color(0x80000000)),
        start = Offset(0f, 0f),
        end = Offset(400f, 400f)
    )

    Box(
        modifier = modifier
            .width(172.dp)
            .height(195.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardGradient)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Icon container matching .ach-card-icon in design spec
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x4D000000))
                    .border(1.dp, Color(0x0AFFFFFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AchievementIcon(
                    iconType = item.iconType,
                    color = item.iconColor
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = item.title,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = item.value,
                        color = TextMain,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 27.sp
                    )
                    if (item.unit != null) {
                        Text(
                            text = item.unit,
                            color = TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                        )
                    }
                }

                Text(
                    text = item.date,
                    color = Color(0x4DFFFFFF),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * Authentic Achievement SVG Icons matching design spec viewBox="0 0 24 24"
 */
@Composable
private fun AchievementIcon(
    iconType: Int,
    color: Color
) {
    val navArrowPath = remember {
        PathParser().parsePathString("M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z").toPath()
    }
    val shieldPath = remember {
        PathParser().parsePathString("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z").toPath()
    }
    val distLinePath = remember {
        PathParser().parsePathString("M3 12h18M16 6l6 6-6 6M8 6l-6 6 6 6").toPath()
    }

    Canvas(modifier = Modifier.size(24.dp)) {
        val scaleFactor = size.width / 24f

        when (iconType) {
            0 -> {
                // 0-100 KM/H: Navigation Arrow from design spec (fill="currentColor")
                scale(scale = scaleFactor, pivot = Offset.Zero) {
                    drawPath(path = navArrowPath, color = color, style = Fill)
                }
            }
            1 -> {
                // MAX G-FORCE: Circle with line and dot from design spec (stroke="currentColor", stroke-width="2", fill="none")
                scale(scale = scaleFactor, pivot = Offset.Zero) {
                    drawCircle(color = color, radius = 9.5f, center = Offset(12f, 12f), style = Stroke(width = 2f))
                    drawLine(color = color, start = Offset(12f, 12f), end = Offset(12f, 16f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawCircle(color = color, radius = 1.0f, center = Offset(12f, 8f), style = Fill)
                }
            }
            2 -> {
                // LEAN LEFT: Shield rotated -15deg from design spec
                scale(scale = scaleFactor, pivot = Offset.Zero) {
                    withTransform({
                        rotate(degrees = -15f, pivot = Offset(12f, 12f))
                    }) {
                        drawPath(path = shieldPath, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            3 -> {
                // LEAN RIGHT: Shield rotated 15deg scaleX(-1) from design spec
                scale(scale = scaleFactor, pivot = Offset.Zero) {
                    withTransform({
                        rotate(degrees = 15f, pivot = Offset(12f, 12f))
                        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(12f, 12f))
                    }) {
                        drawPath(path = shieldPath, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            4 -> {
                // MAX WHEELIE: Circle with exclamation rotated -30deg from design spec
                scale(scale = scaleFactor, pivot = Offset.Zero) {
                    withTransform({
                        rotate(degrees = -30f, pivot = Offset(12f, 12f))
                    }) {
                        drawCircle(color = color, radius = 9.5f, center = Offset(12f, 12f), style = Stroke(width = 2f))
                        drawLine(color = color, start = Offset(12f, 12f), end = Offset(12f, 16f), strokeWidth = 2f, cap = StrokeCap.Round)
                        drawCircle(color = color, radius = 1.0f, center = Offset(12f, 8f), style = Fill)
                    }
                }
            }
            5 -> {
                // MAX STOPPIE: Circle with exclamation rotated 30deg from design spec
                scale(scale = scaleFactor, pivot = Offset.Zero) {
                    withTransform({
                        rotate(degrees = 30f, pivot = Offset(12f, 12f))
                    }) {
                        drawCircle(color = color, radius = 9.5f, center = Offset(12f, 12f), style = Stroke(width = 2f))
                        drawLine(color = color, start = Offset(12f, 12f), end = Offset(12f, 16f), strokeWidth = 2f, cap = StrokeCap.Round)
                        drawCircle(color = color, radius = 1.0f, center = Offset(12f, 8f), style = Fill)
                    }
                }
            }
            6 -> {
                // LONG RIDE (DIST): Horizontal line with forward/backward arrows from design spec
                scale(scale = scaleFactor, pivot = Offset.Zero) {
                    drawPath(path = distLinePath, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            7 -> {
                // LONG RIDE (TIME): Clock with hands from design spec
                scale(scale = scaleFactor, pivot = Offset.Zero) {
                    drawCircle(color = color, radius = 9.5f, center = Offset(12f, 12f), style = Stroke(width = 2f))
                    drawLine(color = color, start = Offset(12f, 6f), end = Offset(12f, 12f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawLine(color = color, start = Offset(12f, 12f), end = Offset(16f, 14f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
        }
    }
}
