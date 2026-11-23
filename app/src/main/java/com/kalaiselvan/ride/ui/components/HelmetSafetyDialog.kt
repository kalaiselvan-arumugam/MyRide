package com.kalaiselvan.ride.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalaiselvan.ride.R
import com.kalaiselvan.ride.theme.*
import kotlinx.coroutines.delay

/**
 * Road Safety Helmet Alert Dialog.
 * Matches active AMOLED Dark / Light theme dynamically.
 * Features a high-contrast circular badge so the black helmet icon remains crisp and prominent.
 * Automatically dismisses after 4 seconds (or upon rider tap).
 */
@Composable
fun HelmetSafetyDialog(
    autoDismissSeconds: Int = 4,
    onDismiss: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(autoDismissSeconds) }
    val themeColors = LocalThemeColors.current

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        onDismiss()
    }

    // Dynamic AMOLED Black & Light surface palette
    val cardBg = if (themeColors.isLight) themeColors.bgCard else Color(0xFF0C0E12)
    val cardBorder = if (themeColors.isLight) themeColors.chipBorder else Color(0xFF1E2632)
    val backdropColor = Color.Black.copy(alpha = if (themeColors.isLight) 0.55f else 0.85f)

    // Contrasting badge background so the black helmet is bold and distinct in both themes
    val badgeBg = if (themeColors.isLight) Color(0xFFFFFFFF) else Color(0xFFF0F3F7)
    val badgeBorder = if (themeColors.isLight) Color(0xFFD4DCE6) else Color(0xFFD0D7E2)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume inner click
                    )
                    .padding(top = 26.dp, bottom = 22.dp, start = 22.dp, end = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // High-contrast circular tile: ensures black helmet details are 100% visible against AMOLED background
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(badgeBg)
                        .border(1.5.dp, badgeBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_helmet_safety),
                        contentDescription = "Helmet Safety Icon",
                        modifier = Modifier.size(76.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "ALWAYS WEAR YOUR HELMET",
                    color = themeColors.textMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "ROAD SAFETY FIRST",
                    color = themeColors.accentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Safety message
                Text(
                    text = "Fasten your chin strap securely and ride with full protective gear on every trip. Arrive alive, arrive safe!",
                    color = themeColors.textMuted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action button with auto-dismiss countdown
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(themeColors.accentGreen)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "I'M GEARED UP",
                            color = if (themeColors.isLight) Color.White else Color(0xFF0D1117),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${secondsLeft}s)",
                            color = if (themeColors.isLight) Color.White.copy(alpha = 0.85f) else Color(0xFF1B5E20),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
