package com.kalaiselvan.ride.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.kalaiselvan.ride.theme.RpmGreen
import com.kalaiselvan.ride.theme.TextMuted
import com.kalaiselvan.ride.ui.theme.neumorphicInner

@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "switchOffset"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) RpmGreen else TextMuted,
        label = "switchThumbColor"
    )

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .neumorphicInner(cornerRadius = 12.dp, shadowWidth = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .drawBehind {
                    if (checked) {
                        drawCircle(color = thumbColor.copy(alpha = 0.35f), radius = size.minDimension / 1.5f)
                    }
                    drawCircle(color = thumbColor)
                }
        )
    }
}

