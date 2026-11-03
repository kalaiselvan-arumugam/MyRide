package com.kalaiselvan.ride.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kalaiselvan.ride.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CustomDatePickerDialog(
    initialDateMs: Long,
    minDateMs: Long? = null,
    maxDateMs: Long? = null,
    title: String = "SELECT DATE",
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val initialCal = remember(initialDateMs) {
        Calendar.getInstance().apply { timeInMillis = initialDateMs }
    }

    var displayedYear by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }
    var selectedDateMs by remember { mutableLongStateOf(initialDateMs) }

    val selectedCal = remember(selectedDateMs) {
        Calendar.getInstance().apply { timeInMillis = selectedDateMs }
    }
    val todayCal = remember {
        Calendar.getInstance()
    }

    val minCal = remember(minDateMs) {
        minDateMs?.let { Calendar.getInstance().apply { timeInMillis = it } }
    }
    val maxCal = remember(maxDateMs) {
        maxDateMs?.let { Calendar.getInstance().apply { timeInMillis = it } }
    }

    // Determine if previous month navigation is allowed
    val canGoPrevMonth = remember(displayedYear, displayedMonth, minCal) {
        if (minCal == null) true
        else {
            val prevMonthCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, displayedYear)
                set(Calendar.MONTH, displayedMonth)
                add(Calendar.MONTH, -1)
            }
            prevMonthCal.get(Calendar.YEAR) > minCal.get(Calendar.YEAR) ||
                    (prevMonthCal.get(Calendar.YEAR) == minCal.get(Calendar.YEAR) &&
                            prevMonthCal.get(Calendar.MONTH) >= minCal.get(Calendar.MONTH))
        }
    }

    // Determine if next month navigation is allowed
    val canGoNextMonth = remember(displayedYear, displayedMonth, maxCal) {
        if (maxCal == null) true
        else {
            val nextMonthCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, displayedYear)
                set(Calendar.MONTH, displayedMonth)
                add(Calendar.MONTH, 1)
            }
            nextMonthCal.get(Calendar.YEAR) < maxCal.get(Calendar.YEAR) ||
                    (nextMonthCal.get(Calendar.YEAR) == maxCal.get(Calendar.YEAR) &&
                            nextMonthCal.get(Calendar.MONTH) <= maxCal.get(Calendar.MONTH))
        }
    }

    // Calculate calendar grid for displayed month
    val monthCal = remember(displayedYear, displayedMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayedYear)
            set(Calendar.MONTH, displayedMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 7 = Saturday
    val offset = firstDayOfWeek - 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BgCard)
                    .border(1.dp, Color(0xFF2A313C), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Title & Selected Date in Dark Cockpit style
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title.uppercase(),
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMs)),
                        color = AccentGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)

                // Month / Year Switcher Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (canGoPrevMonth) Color(0xFF14181C) else Color.Transparent)
                            .border(1.dp, if (canGoPrevMonth) Color(0xFF2E3744) else Color.Transparent, CircleShape)
                            .clickable(enabled = canGoPrevMonth) {
                                if (displayedMonth == 0) {
                                    displayedMonth = 11
                                    displayedYear--
                                } else {
                                    displayedMonth--
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "‹",
                            color = if (canGoPrevMonth) TextMain else Color(0xFF333A44),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val monthName = remember(displayedMonth) {
                        SimpleDateFormat("MMMM", Locale.getDefault()).format(
                            Calendar.getInstance().apply { set(Calendar.MONTH, displayedMonth) }.time
                        ).uppercase()
                    }
                    Text(
                        text = "$monthName $displayedYear",
                        color = TextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (canGoNextMonth) Color(0xFF14181C) else Color.Transparent)
                            .border(1.dp, if (canGoNextMonth) Color(0xFF2E3744) else Color.Transparent, CircleShape)
                            .clickable(enabled = canGoNextMonth) {
                                if (displayedMonth == 11) {
                                    displayedMonth = 0
                                    displayedYear++
                                } else {
                                    displayedMonth++
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "›",
                            color = if (canGoNextMonth) TextMain else Color(0xFF333A44),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Days of Week Header Row (SUN to SAT)
                val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    dayHeaders.forEach { h ->
                        Text(
                            text = h,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }

                // Days Grid (always fixed 6 rows to prevent dialog resizing/jerking across months)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in 0 until 6) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (c in 0 until 7) {
                                val index = r * 7 + c
                                val dayNum = index - offset + 1

                                if (dayNum in 1..daysInMonth) {
                                    val cellCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, displayedYear)
                                        set(Calendar.MONTH, displayedMonth)
                                        set(Calendar.DAY_OF_MONTH, dayNum)
                                    }

                                    val isBeforeMin = minCal != null && (
                                            cellCal.get(Calendar.YEAR) < minCal.get(Calendar.YEAR) ||
                                                    (cellCal.get(Calendar.YEAR) == minCal.get(Calendar.YEAR) &&
                                                            cellCal.get(Calendar.DAY_OF_YEAR) < minCal.get(Calendar.DAY_OF_YEAR))
                                            )
                                    val isAfterMax = maxCal != null && (
                                            cellCal.get(Calendar.YEAR) > maxCal.get(Calendar.YEAR) ||
                                                    (cellCal.get(Calendar.YEAR) == maxCal.get(Calendar.YEAR) &&
                                                            cellCal.get(Calendar.DAY_OF_YEAR) > maxCal.get(Calendar.DAY_OF_YEAR))
                                            )
                                    val isEnabled = !isBeforeMin && !isAfterMax

                                    val isSelected = cellCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                            cellCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)

                                    val isToday = cellCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                            cellCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> AccentGreen
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .border(
                                                width = if (isToday && !isSelected) 1.dp else 0.dp,
                                                color = if (isToday && !isSelected) AccentGreen.copy(alpha = 0.6f) else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable(
                                                enabled = isEnabled,
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                selectedDateMs = cellCal.apply {
                                                    set(Calendar.HOUR_OF_DAY, 12)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                }.timeInMillis
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$dayNum",
                                            color = when {
                                                isSelected -> Color.Black
                                                !isEnabled -> Color(0xFF333A44)
                                                isToday -> AccentGreen
                                                else -> TextMain
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)

                // Dialog Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                        border = BorderStroke(1.dp, Color(0xFF333A44)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onDateSelected(selectedDateMs)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Confirm", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
