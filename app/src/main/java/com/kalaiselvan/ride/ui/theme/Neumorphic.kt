package com.kalaiselvan.ride.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kalaiselvan.ride.theme.BgDark

/**
 * 100% Hardware-Accelerated Neumorphic Circle Outer Shadow & Bevel.
 * Emulates: box-shadow: -6px -6px 16px rgba(255, 255, 255, 0.03), 6px 6px 16px rgba(0, 0, 0, 0.4);
 */
fun Modifier.neumorphicCircleOuter(
    blurRadius: Dp = 10.dp,
    offset: Dp = 5.dp,
    baseColor: Color = BgDark
): Modifier = this.drawBehind {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f
    val offsetPx = offset.toPx()
    val blurPx = blurRadius.toPx()

    // 1. Dark Shadow at bottom-right (+offset, +offset)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x66000000), Color(0x33000000), Color.Transparent),
            center = Offset(center.x + offsetPx, center.y + offsetPx),
            radius = radius + blurPx
        ),
        radius = radius + blurPx,
        center = Offset(center.x + offsetPx, center.y + offsetPx)
    )

    // 2. Light Top-Left Specular Glow (-offset, -offset)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x12FFFFFF), Color(0x05FFFFFF), Color.Transparent),
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
            colors = listOf(Color(0x18FFFFFF), Color(0x00FFFFFF), Color(0x22000000)),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        radius = radius,
        center = center,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )
}

/**
 * 100% Hardware-Accelerated Neumorphic Circle Inner Inset Shadow.
 * Emulates: box-shadow: inset -4px -4px 8px rgba(255, 255, 255, 0.03), inset 4px 4px 8px rgba(0, 0, 0, 0.4);
 */
fun Modifier.neumorphicCircleInner(
    shadowWidth: Dp = 8.dp,
    baseColor: Color = BgDark
): Modifier = this.drawBehind {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f
    val swPx = shadowWidth.toPx()

    // 1. Fill base circle
    drawCircle(
        color = baseColor,
        radius = radius,
        center = center
    )

    // 2. Inset Dark Shadow from Top-Left
    val clipPath = Path().apply {
        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
    }

    clipPath(clipPath) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x77000000), Color(0x44000000), Color.Transparent),
                center = Offset(center.x - radius * 0.5f, center.y - radius * 0.5f),
                radius = radius + swPx
            ),
            radius = radius,
            center = center
        )

        // Inset Light Highlight from Bottom-Right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x12FFFFFF), Color.Transparent),
                center = Offset(center.x + radius * 0.6f, center.y + radius * 0.6f),
                radius = radius * 0.8f
            ),
            radius = radius,
            center = center
        )

        // Inner Bevel Rim
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0x33000000), Color(0x00000000), Color(0x18FFFFFF)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            ),
            radius = radius - 0.5f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
    }
}

fun Modifier.neumorphicOuter(
    cornerRadius: Dp = 16.dp,
    blurRadius: Dp = 8.dp,
    offset: Dp = 4.dp
): Modifier = this.drawBehind {
    val rPx = cornerRadius.toPx()
    val offPx = offset.toPx()

    // Simple, clean card background with subtle border
    drawRoundRect(
        color = BgDark,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0x18FFFFFF), Color(0x05FFFFFF), Color(0x22000000)),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )
}

fun Modifier.neumorphicInner(
    cornerRadius: Dp = 12.dp,
    shadowWidth: Dp = 3.dp
): Modifier = this.drawBehind {
    val rPx = cornerRadius.toPx()
    drawRoundRect(
        color = Color(0xFF14171A),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx)
    )
    drawRoundRect(
        color = Color(0x44000000),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(rPx, rPx),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
    )
}
