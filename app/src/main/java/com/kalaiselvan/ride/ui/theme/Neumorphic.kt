package com.kalaiselvan.ride.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kalaiselvan.ride.theme.LocalThemeColors

/**
 * 100% Hardware-Accelerated Neumorphic Circle Outer Shadow & Bevel.
 * Dynamically reacts to Light and Dark themes.
 */
@Composable
fun Modifier.neumorphicCircleOuter(
    blurRadius: Dp = 10.dp,
    offset: Dp = 5.dp
): Modifier {
    val theme = LocalThemeColors.current
    val baseColor = theme.bgDark
    val shadowDark = if (theme.isLight) Color(0x25000000) else theme.shadowDark
    val shadowLight = theme.shadowLight
    val rimLight = if (theme.isLight) Color(0x80FFFFFF) else Color(0x18FFFFFF)
    val rimDark = if (theme.isLight) Color(0x1A000000) else Color(0x22000000)

    return this.drawBehind {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val offsetPx = offset.toPx()
        val blurPx = blurRadius.toPx()

        // 1. Dark Shadow at bottom-right (+offset, +offset)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(shadowDark, shadowDark.copy(alpha = shadowDark.alpha * 0.4f), Color.Transparent),
                center = Offset(center.x + offsetPx, center.y + offsetPx),
                radius = radius + blurPx
            ),
            radius = radius + blurPx,
            center = Offset(center.x + offsetPx, center.y + offsetPx)
        )

        // 2. Light Top-Left Specular Glow (-offset, -offset)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(shadowLight, shadowLight.copy(alpha = shadowLight.alpha * 0.4f), Color.Transparent),
                center = Offset(center.x - offsetPx * 0.7f, center.y - offsetPx * 0.7f),
                radius = radius + blurPx * 0.8f
            ),
            radius = radius + blurPx * 0.8f,
            center = Offset(center.x - offsetPx * 0.7f, center.y - offsetPx * 0.7f)
        )

        // 3. Base Surface Circle Fill
        drawCircle(
            color = baseColor,
            radius = radius,
            center = center
        )

        // 4. Subtle Outer Rim Highlight
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(rimLight, Color.Transparent, rimDark),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            radius = radius - 0.5f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * Inset / Recessed Circle (for sat pods & speedometer centers).
 * Dynamically reacts to Light and Dark themes.
 */
@Composable
fun Modifier.neumorphicCircleInner(
    shadowWidth: Dp = 4.dp
): Modifier {
    val theme = LocalThemeColors.current
    val baseColor = theme.podInnerBase
    val shadowDark = if (theme.isLight) Color(0x28000000) else theme.shadowDark
    val shadowLight = theme.shadowLight
    val rimColor = if (theme.isLight) Color(0x22000000) else Color(0x33000000)

    return this.drawBehind {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val swPx = shadowWidth.toPx()

        // 1. Recessed Floor
        drawCircle(
            color = baseColor,
            radius = radius,
            center = center
        )

        // 2. Inset Top-Left Shadow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(shadowDark, Color.Transparent),
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                radius = radius + swPx
            ),
            radius = radius,
            center = center
        )

        // 3. Inset Bottom-Right Specular Edge
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(shadowLight, Color.Transparent),
                center = Offset(center.x + radius * 0.35f, center.y + radius * 0.35f),
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // 4. Subtle Rim Stroke
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(rimColor, Color.Transparent, if (theme.isLight) Color(0x80FFFFFF) else Color(0x18FFFFFF)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            radius = radius - 0.5f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * Neumorphic Card / Container Box.
 * Dynamically reacts to Light and Dark themes.
 * Uses a single diagonal linear gradient rim border:
 * In Dark Mode: Crisp top-left specular rim that smoothly fades into bottom-right dark canvas.
 * In Light Mode: Smooth crisp specular top-left edge that softly fades into bottom-right shadow,
 *                matching the exact same clean, non-embossed aesthetic as dark mode.
 */
@Composable
fun Modifier.neumorphicOuter(
    cornerRadius: Dp = 16.dp,
    blurRadius: Dp = 8.dp,
    offset: Dp = 4.dp
): Modifier {
    val theme = LocalThemeColors.current
    val baseColor = theme.bgCard
    val rimColors = if (theme.isLight) {
        listOf(Color(0xFFFFFFFF), Color(0x20FFFFFF), Color(0x18000000))
    } else {
        listOf(Color(0x18FFFFFF), Color(0x05FFFFFF), Color(0x22000000))
    }

    return this.drawBehind {
        val rPx = cornerRadius.toPx()

        // 1. Clean card background
        drawRoundRect(
            color = baseColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx)
        )

        // 2. Single diagonal gradient border (highlight on one side, smoothly disappearing on the other)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = rimColors,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
fun Modifier.neumorphicInner(
    cornerRadius: Dp = 12.dp,
    shadowWidth: Dp = 3.dp
): Modifier {
    val theme = LocalThemeColors.current
    val baseColor = theme.podInnerBase
    val shadowColor = if (theme.isLight) Color(0x1F000000) else Color(0x44000000)
    val highlightColor = if (theme.isLight) Color(0x80FFFFFF) else Color(0x10FFFFFF)

    return this.drawBehind {
        val rPx = cornerRadius.toPx()
        drawRoundRect(
            color = baseColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx)
        )
        // Inset border shadow
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(shadowColor, highlightColor),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
    }
}
