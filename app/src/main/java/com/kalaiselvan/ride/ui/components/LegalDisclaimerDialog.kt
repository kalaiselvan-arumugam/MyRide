package com.kalaiselvan.ride.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalaiselvan.ride.theme.*

/**
 * Mandatory Legal & Safety Disclaimer Modal.
 * Must be explicitly accepted by the rider on first launch before the application can be used.
 * Can also be reviewed on-demand anytime from the Settings screen.
 */
@Composable
fun LegalDisclaimerDialog(
    isMandatory: Boolean = false,
    onAccept: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = {
            if (!isMandatory) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = !isMandatory,
            dismissOnBackPress = !isMandatory
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgCard)
                    .border(1.dp, Color(0xFF2C3440), RoundedCornerShape(24.dp))
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning Header - Red, Bold, All Caps text
                Text(
                    text = "WARNING",
                    color = Color(0xFFFF3B30),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Disclaimer & Limitation of Liability",
                    color = TextMain,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "FOR CLOSED-COURSE / TRACK REFERENCE USE ONLY",
                    color = RpmOrange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                // High-visibility Use at your own risk banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x22FFA000))
                        .border(1.dp, Color(0x55FFA000), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⚠️ MANDATORY NOTICE: USE AT YOUR OWN RISK\nYou expressly understand, acknowledge, and agree that any and all use of this application is strictly at your own sole and exclusive risk.",
                        color = Color(0xFFFFB300),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Detailed Disclaimer Points Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgDark)
                        .border(1.dp, Color(0xFF1E242C), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DisclaimerPoint(
                        number = "1",
                        title = "Rider Attention & Distraction Warning",
                        body = "Never interact with, configure, or look at this mobile application while operating a motor vehicle. Always keep your eyes on the road and both hands on the handlebars."
                    )

                    DisclaimerPoint(
                        number = "2",
                        title = "Not a Life-Saving Alternative",
                        body = "Do NOT treat or rely on this app as a life-saving alternative, safety device, or diagnostic fail-safe. Telemetry data may be delayed, intermittent, or inaccurate at times."
                    )

                    DisclaimerPoint(
                        number = "3",
                        title = "Wireless Data Latency Notice",
                        body = "Engine RPM, speed, gear position, temperatures, lean angles, and G-force readings are communicated wirelessly over Bluetooth Low Energy (BLE) and are subject to latency, packet drops, or sensor inaccuracy. Never use displayed values for split-second riding or cornering decisions."
                    )

                    DisclaimerPoint(
                        number = "4",
                        title = "Not an Official Instrument Cluster",
                        body = "This application is an independent community project and is NOT an official instrument cluster. It must not replace proper rider attention, standard OEM safety equipment, factory warning lamps, or scheduled certified maintenance."
                    )

                    DisclaimerPoint(
                        number = "5",
                        title = "Complete Limitation of Liability",
                        body = "The developers and contributors assume no liability for personal injury, death, property damage, mechanical wear, vehicle malfunction, traffic violations, or accidents resulting from the use or misuse of this software."
                    )

                    DisclaimerPoint(
                        number = "6",
                        title = "Assumption of Risk",
                        body = "By accepting and using this software, you assume 100% of all legal, physical, and financial risks. If you do not agree to these terms, close and uninstall this application immediately."
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                if (isMandatory) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentGreen)
                            .clickable { onAccept() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "I UNDERSTAND & ACCEPT",
                            color = Color.Black,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { onDismiss() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CLOSE",
                            color = TextMain,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclaimerPoint(
    number: String,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0x22FF9800)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = RpmOrange,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                color = TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
