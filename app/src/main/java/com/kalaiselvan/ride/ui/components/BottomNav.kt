package com.kalaiselvan.ride.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.theme.BgDark
import com.kalaiselvan.ride.theme.RpmGreen
import com.kalaiselvan.ride.theme.TextMain
import com.kalaiselvan.ride.theme.TextMuted

enum class AppTab {
    DASH, MAP, SERVICE, ACHIEVEMENTS, DEV, SETTINGS
}

@Composable
fun BottomNav(
    selectedTab: AppTab,
    isDevUnlocked: Boolean,
    showMapTab: Boolean = false,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(BgDark)
            .drawBehind {
                drawLine(
                    color = Color(0x14FFFFFF),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                title = "DASH",
                iconType = 0,
                isSelected = selectedTab == AppTab.DASH,
                onClick = { onTabSelected(AppTab.DASH) }
            )
            if (showMapTab) {
                NavItem(
                    title = "MAP",
                    iconType = 5,
                    isSelected = selectedTab == AppTab.MAP,
                    onClick = { onTabSelected(AppTab.MAP) }
                )
            }
            NavItem(
                title = "SERVICE",
                iconType = 4,
                isSelected = selectedTab == AppTab.SERVICE,
                onClick = { onTabSelected(AppTab.SERVICE) }
            )
            NavItem(
                title = "ACHIEVEMENTS",
                iconType = 1,
                isSelected = selectedTab == AppTab.ACHIEVEMENTS,
                onClick = { onTabSelected(AppTab.ACHIEVEMENTS) }
            )
            if (isDevUnlocked) {
                NavItem(
                    title = "DEV",
                    iconType = 3,
                    isSelected = selectedTab == AppTab.DEV,
                    onClick = { onTabSelected(AppTab.DEV) }
                )
            }
            NavItem(
                title = "SETTINGS",
                iconType = 2,
                isSelected = selectedTab == AppTab.SETTINGS,
                onClick = { onTabSelected(AppTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun NavItem(
    title: String,
    iconType: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) RpmGreen else TextMuted,
        label = "navIconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) TextMain else TextMuted,
        label = "navTextColor"
    )

    val dashPath = remember {
        PathParser().parsePathString(
            "M12 2C6.48 2 2 6.48 2 12C2 15.65 3.96 18.84 6.96 20.61C7.57 20.97 8.32 20.59 8.36 19.89C8.38 19.46 8.09 19.07 7.69 18.9C5.46 17.92 4 15.65 4 13C4 8.58 7.58 5 12 5C16.42 5 20 8.58 20 13C20 15.65 18.54 17.92 16.31 18.9C15.91 19.07 15.62 19.46 15.64 19.89C15.68 20.59 16.43 20.97 17.04 20.61C20.04 18.84 22 15.65 22 12C22 6.48 17.52 2 12 2ZM13.88 10.12C13.59 9.83 13.11 9.83 12.82 10.12L10.71 12.23C10.53 12.41 10.53 12.71 10.71 12.89C10.89 13.07 11.19 13.07 11.37 12.89L13.48 10.78C13.77 10.49 13.77 10.01 13.48 9.72V10.12Z"
        ).toPath()
    }

    val trophyPath = remember {
        PathParser().parsePathString(
            "M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94A5.01 5.01 0 0011 15.9V19H7v2h10v-2h-4v-3.1a5.01 5.01 0 003.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z"
        ).toPath()
    }

    val wrenchPath = remember {
        PathParser().parsePathString(
            "M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z"
        ).toPath()
    }

    val settingsPath = remember {
        PathParser().parsePathString(
            "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.06-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.73,8.87C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.06,0.94l-2.03,1.58c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.49-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"
        ).toPath()
    }

    val devCodePath = remember {
        PathParser().parsePathString(
            "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"
        ).toPath()
    }

    val navArrowPath = remember {
        PathParser().parsePathString(
            "M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71L12 2z"
        ).toPath()
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height

            when (iconType) {
                0 -> {
                    // DASH: Authentic Tachometer Vector from design spec
                    scale(scaleX = w / 24f, scaleY = h / 24f, pivot = Offset(0f, 0f)) {
                        drawPath(path = dashPath, color = iconColor, style = Fill)
                        drawCircle(color = iconColor, radius = 2.5f, center = Offset(12f, 14f))
                    }
                }
                1 -> {
                    // ACHIEVEMENTS: Authentic Trophy Cup from design spec
                    scale(scaleX = w / 24f, scaleY = h / 24f, pivot = Offset(0f, 0f)) {
                        drawPath(path = trophyPath, color = iconColor, style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                2 -> {
                    // SETTINGS: Authentic Cogwheel from design spec
                    scale(scaleX = w / 24f, scaleY = h / 24f, pivot = Offset(0f, 0f)) {
                        drawPath(path = settingsPath, color = iconColor, style = Fill)
                    }
                }
                3 -> {
                    // DEV: Material Code Bracket Icon </>
                    scale(scaleX = w / 24f, scaleY = h / 24f, pivot = Offset(0f, 0f)) {
                        drawPath(path = devCodePath, color = iconColor, style = Fill)
                    }
                }
                4 -> {
                    // SERVICE: Wrench Icon
                    scale(scaleX = w / 24f, scaleY = h / 24f, pivot = Offset(0f, 0f)) {
                        drawPath(path = wrenchPath, color = iconColor, style = Fill)
                    }
                }
                5 -> {
                    // MAP: Navigation Arrow Icon
                    scale(scaleX = w / 24f, scaleY = h / 24f, pivot = Offset(0f, 0f)) {
                        drawPath(path = navArrowPath, color = iconColor, style = Fill)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = title,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            letterSpacing = 0.5.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}
