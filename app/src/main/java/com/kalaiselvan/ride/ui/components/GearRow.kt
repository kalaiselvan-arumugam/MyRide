package com.kalaiselvan.ride.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.theme.RpmGreen
import com.kalaiselvan.ride.theme.RpmOrange
import com.kalaiselvan.ride.theme.RpmRed
import com.kalaiselvan.ride.theme.TextMain

/**
 * Clean Gear Indicator Row matching motorcycle shift pattern: 1 N 2 3 4 5 6.
 * Active gear is highlighted (Green for N, pure white for numerical gear, red when shifting,
 * and flashes vibrant orange when the Shift-Up indicator lamp threshold is reached).
 */
@Composable
fun GearRow(
    currentGear: Int? = null, // null = unknown / no data (all grayed out), 0 = N, 1..6
    isShifting: Boolean = false,
    isShiftIndicatorActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val gears = listOf(1, 0, 2, 3, 4, 5, 6)

    val shiftStrobe = rememberInfiniteTransition(label = "gearShiftStrobe")
    val shiftFlashAlpha by shiftStrobe.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shiftFlashAlpha"
    )
    val isShiftStrobeOn = shiftFlashAlpha > 0.45f

    Row(
        modifier = modifier
            .fillMaxWidth(0.58f)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        gears.forEach { gearNum ->
            val isSelected = currentGear != null && gearNum == currentGear
            val label = if (gearNum == 0) "N" else "$gearNum"

            val targetColor = when {
                !isSelected -> Color(0x33FFFFFF)
                isShiftIndicatorActive -> if (isShiftStrobeOn) RpmOrange else Color(0x22FFFFFF)
                isShifting -> RpmRed
                gearNum == 0 -> RpmGreen
                else -> TextMain
            }

            val textColor = if (isSelected && isShiftIndicatorActive) {
                if (isShiftStrobeOn) RpmOrange else Color(0x22FFFFFF)
            } else {
                val animatedColor by animateColorAsState(
                    targetValue = targetColor,
                    label = "gearColor_$gearNum"
                )
                animatedColor
            }

            Text(
                text = label,
                color = textColor,
                fontSize = if (isSelected) 18.sp else 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
