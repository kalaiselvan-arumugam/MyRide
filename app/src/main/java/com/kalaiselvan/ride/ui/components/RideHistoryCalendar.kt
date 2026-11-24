package com.kalaiselvan.ride.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalaiselvan.ride.theme.*
import com.kalaiselvan.ride.ui.theme.neumorphicOuter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RideHistoryCalendar(
    rideTimestamps: List<Long>,
    selectedDateKey: String?, // Format: "yyyy-MM-dd", or null for all
    onDateSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Map ride timestamps to set of "yyyy-MM-dd" strings
    val rideDateKeys = remember(rideTimestamps) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        rideTimestamps.map { sdf.format(Date(it)) }.toSet()
    }

    val dayKeyFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayKey = remember { dayKeyFormat.format(Date()) }

    // Current viewing month in calendar
    var calendarMonth by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val displayDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    val formattedHeaderSelection = remember(selectedDateKey) {
        if (selectedDateKey != null) {
            try {
                val parsed = dayKeyFormat.parse(selectedDateKey)
                if (parsed != null) displayDateFormat.format(parsed) else selectedDateKey
            } catch (e: Exception) {
                selectedDateKey
            }
        } else {
            "All Dates"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .neumorphicOuter(cornerRadius = 14.dp, blurRadius = 8.dp)
    ) {
        // Collapsible Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Calendar Icon
                Canvas(modifier = Modifier.size(16.dp)) {
                    val w = size.width
                    val h = size.height
                    val strokeW = 1.5.dp.toPx()
                    val c = if (selectedDateKey != null) Color(0xFF30D158) else Color(0xFF7A828A)

                    // Outline box
                    drawRoundRect(
                        color = c,
                        size = androidx.compose.ui.geometry.Size(w, h * 0.85f),
                        topLeft = Offset(0f, h * 0.15f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                        style = Stroke(width = strokeW)
                    )
                    // Top binder lines
                    drawLine(color = c, start = Offset(w * 0.3f, 0f), end = Offset(w * 0.3f, h * 0.25f), strokeWidth = strokeW, cap = StrokeCap.Round)
                    drawLine(color = c, start = Offset(w * 0.7f, 0f), end = Offset(w * 0.7f, h * 0.25f), strokeWidth = strokeW, cap = StrokeCap.Round)
                }

                Text(
                    text = "DATE: $formattedHeaderSelection",
                    color = if (selectedDateKey != null) RpmGreen else TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )

                // "Today" / "All Days" Toggle Button
                val isTodaySelected = selectedDateKey == todayKey
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isTodaySelected) RpmGreen else Color(0x22FFFFFF))
                        .clickable {
                            if (isTodaySelected) {
                                // If already showing Today, toggle back to All Days
                                onDateSelected(null)
                            } else {
                                // Select Today's date and align calendar month to Today
                                calendarMonth = Calendar.getInstance().apply {
                                    set(Calendar.DAY_OF_MONTH, 1)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onDateSelected(todayKey)
                            }
                        }
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = if (isTodaySelected) "ALL DAYS" else "TODAY",
                        color = if (isTodaySelected) Color.Black else TextMain,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isExpanded) {
                    Text(
                        text = "CLOSE",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Canvas(modifier = Modifier.size(10.dp)) {
                    val p = Path()
                    if (isExpanded) {
                        p.moveTo(0f, size.height * 0.75f)
                        p.lineTo(size.width * 0.5f, size.height * 0.25f)
                        p.lineTo(size.width, size.height * 0.75f)
                    } else {
                        p.moveTo(0f, size.height * 0.25f)
                        p.lineTo(size.width * 0.5f, size.height * 0.75f)
                        p.lineTo(size.width, size.height * 0.25f)
                    }
                    drawPath(path = p, color = Color(0xFF7A828A), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

        // Expandable Month Grid (Fixed Height to prevent juggling)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(268.dp) // Fixed calendar container height
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Month Navigation (< November 2026 >)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BgDark)
                            .clickable {
                                val prev = calendarMonth.clone() as Calendar
                                prev.add(Calendar.MONTH, -1)
                                calendarMonth = prev
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(8.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.75f, 0f)
                                lineTo(size.width * 0.25f, size.height * 0.5f)
                                lineTo(size.width * 0.75f, size.height)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }

                    Text(
                        text = monthYearFormat.format(calendarMonth.time).uppercase(Locale.US),
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BgDark)
                            .clickable {
                                val next = calendarMonth.clone() as Calendar
                                next.add(Calendar.MONTH, 1)
                                calendarMonth = next
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(8.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.25f, 0f)
                                lineTo(size.width * 0.75f, size.height * 0.5f)
                                lineTo(size.width * 0.25f, size.height)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }

                // Day-of-Week Headers: S M T W T F S
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysOfWeek.forEach { day ->
                        Text(
                            text = day,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Fixed 6-row month grid
                val cal = calendarMonth.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0-based Sunday=0
                val maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (row in 0 until 6) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(31.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val dayNumber = cellIndex - firstDayOfWeek + 1

                                if (dayNumber in 1..maxDaysInMonth) {
                                    val dayCal = calendarMonth.clone() as Calendar
                                    dayCal.set(Calendar.DAY_OF_MONTH, dayNumber)
                                    val dateKey = dayKeyFormat.format(dayCal.time)
                                    val hasRide = rideDateKeys.contains(dateKey)
                                    val isSelected = selectedDateKey == dateKey
                                    val isToday = dateKey == todayKey

                                    val isEnabled = hasRide

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isSelected -> RpmGreen
                                                        hasRide -> RpmGreen.copy(alpha = 0.18f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    width = when {
                                                        isSelected -> 1.5.dp
                                                        hasRide -> 1.dp
                                                        isToday -> 1.dp
                                                        else -> 0.dp
                                                    },
                                                    color = when {
                                                        isSelected -> RpmGreen
                                                        hasRide -> RpmGreen.copy(alpha = 0.7f)
                                                        isToday -> Color.White.copy(alpha = 0.5f)
                                                        else -> Color.Transparent
                                                    },
                                                    shape = CircleShape
                                                )
                                                .clickable(enabled = isEnabled) {
                                                    if (isSelected) {
                                                        onDateSelected(null) // deselect
                                                    } else {
                                                        onDateSelected(dateKey)
                                                        isExpanded = false // collapse after picking
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayNumber.toString(),
                                                color = when {
                                                    isSelected -> Color.Black
                                                    hasRide -> RpmGreen
                                                    isToday -> Color.White
                                                    else -> TextMuted.copy(alpha = 0.25f)
                                                },
                                                fontSize = 11.5.sp,
                                                fontWeight = when {
                                                    isSelected -> FontWeight.ExtraBold
                                                    hasRide || isToday -> FontWeight.Bold
                                                    else -> FontWeight.Normal
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
