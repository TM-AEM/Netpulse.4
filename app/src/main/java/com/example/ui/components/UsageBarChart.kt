package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.model.DailyNetworkUsage
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.MobileAccent
import com.example.ui.theme.WifiAccent
import com.example.util.ByteFormatter
import com.example.util.DateTimeUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun UsageBarChart(
    dailyData: List<DailyNetworkUsage>,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.AR,
    forcedUnit: AppDataUnit = AppDataUnit.AUTO
) {
    if (dailyData.isEmpty()) return

    val isArabic = language == AppLanguage.AR
    val maxBytes = dailyData.maxOfOrNull { it.total.totalBytes }?.coerceAtLeast(1L) ?: 1L
    var selectedDay by remember { mutableStateOf<DailyNetworkUsage?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("usage_bar_chart"),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignTokens.CardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingMedium)
        ) {
            // Header: Title and Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "تفصيل الاستهلاك اليومي" else "Daily Usage Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(DesignTokens.SpacingSmall)
                                .clip(CircleShape)
                                .background(WifiAccent)
                        )
                        Spacer(modifier = Modifier.width(DesignTokens.SpacingTiny))
                        Text(
                            text = if (isArabic) "واي فاي" else "Wi-Fi",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(DesignTokens.SpacingSmall)
                                .clip(CircleShape)
                                .background(MobileAccent)
                        )
                        Spacer(modifier = Modifier.width(DesignTokens.SpacingTiny))
                        Text(
                            text = if (isArabic) "بيانات جوال" else "Mobile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))

            val chronologicallySorted = remember(dailyData) { dailyData.sortedBy { it.date } }
            val totalDays = chronologicallySorted.size

            // Responsive Chart Container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                // Short ranges (<= 7 days) fit comfortably without scrolling
                val canFitAll = totalDays <= 7
                val barWidth = if (canFitAll) 26.dp else 22.dp
                val barTrackHeight = 115.dp
                val scrollState = rememberScrollState()

                val rowModifier = if (canFitAll) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = DesignTokens.SpacingTiny)
                }

                val horizontalArrangement = if (canFitAll) {
                    Arrangement.SpaceEvenly
                } else {
                    Arrangement.spacedBy(10.dp)
                }

                Row(
                    modifier = rowModifier,
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = Alignment.Bottom
                ) {
                    chronologicallySorted.forEach { dayUsage ->
                        val total = dayUsage.total.totalBytes
                        val totalFraction = if (maxBytes > 0) {
                            (total.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val wifiFraction = if (total > 0) {
                            (dayUsage.wifi.totalBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val mobileFraction = (1f - wifiFraction).coerceIn(0f, 1f)
                        val isSelected = selectedDay?.date == dayUsage.date

                        val dayName = getShortDayName(dayUsage.date, language)
                        val dateNumber = "${dayUsage.date.dayOfMonth}/${dayUsage.date.monthValue}"

                        val a11yDescription = if (isArabic) {
                            "$dayName $dateNumber: الإجمالي ${ByteFormatter.format(total, forcedUnit).getDisplay(language)}"
                        } else {
                            "$dayName $dateNumber: Total ${ByteFormatter.format(total, forcedUnit).getDisplay(language)}"
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(DesignTokens.SpacingSmall))
                                .clickable(role = Role.Button) {
                                    selectedDay = if (isSelected) null else dayUsage
                                }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .semantics { contentDescription = a11yDescription }
                        ) {
                            // Bar Track & Column
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .height(barTrackHeight),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                // Background track: subtle visual ceiling for the scale
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                        )
                                )

                                // Filled bar segments
                                if (total > 0 && totalFraction > 0.001f) {
                                    val effectiveFraction = totalFraction.coerceAtLeast(0.04f)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(effectiveFraction)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    ) {
                                        if (mobileFraction > 0.001f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(mobileFraction)
                                                    .background(MobileAccent)
                                            )
                                        }
                                        if (wifiFraction > 0.001f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(wifiFraction)
                                                    .background(WifiAccent)
                                            )
                                        }
                                    }
                                } else {
                                    // Clean 2dp baseline for zero-usage days
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                }

                                // Selection outline border
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(DesignTokens.SpacingTiny))

                            // Day labels
                            if (canFitAll) {
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = dateNumber,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = dateNumber,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Polished Selected Day Inspector
            AnimatedVisibility(
                visible = selectedDay != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedDay?.let { day ->
                    val formattedDate = if (isArabic) {
                        DateTimeUtils.formatDateArabic(day.date)
                    } else {
                        DateTimeUtils.formatDateEnglish(day.date)
                    }
                    val dayOfWeek = if (isArabic) {
                        DateTimeUtils.formatDayNameArabic(day.date)
                    } else {
                        day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    }
                    val totalStr = ByteFormatter.format(day.total.totalBytes, forcedUnit).getDisplay(language)
                    val wifiStr = ByteFormatter.format(day.wifi.totalBytes, forcedUnit).getDisplay(language)
                    val mobileStr = ByteFormatter.format(day.mobile.totalBytes, forcedUnit).getDisplay(language)

                    Surface(
                        shape = RoundedCornerShape(DesignTokens.ChipCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = DesignTokens.CardElevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = DesignTokens.SpacingSmall)
                            .testTag("chart_selected_day_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DesignTokens.SpacingMedium, vertical = DesignTokens.SpacingSmall + 2.dp)
                        ) {
                            // Header row: Date and dismiss button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))
                                    Text(
                                        text = "$formattedDate ($dayOfWeek)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = { selectedDay = null }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = if (isArabic) "إغلاق" else "Close",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

                            // Breakdown metrics row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Total
                                Column {
                                    Text(
                                        text = if (isArabic) "الإجمالي" else "Total",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = totalStr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Wi-Fi
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(DesignTokens.SpacingSmall)
                                            .clip(CircleShape)
                                            .background(WifiAccent)
                                    )
                                    Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))
                                    Column {
                                        Text(
                                            text = if (isArabic) "واي فاي" else "Wi-Fi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = wifiStr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Mobile
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(DesignTokens.SpacingSmall)
                                            .clip(CircleShape)
                                            .background(MobileAccent)
                                    )
                                    Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))
                                    Column {
                                        Text(
                                            text = if (isArabic) "بيانات جوال" else "Mobile",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = mobileStr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getShortDayName(date: LocalDate, language: AppLanguage): String {
    return if (language == AppLanguage.AR) {
        when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> "سبت"
            DayOfWeek.SUNDAY -> "أحد"
            DayOfWeek.MONDAY -> "إثنين"
            DayOfWeek.TUESDAY -> "ثلاثاء"
            DayOfWeek.WEDNESDAY -> "أربعاء"
            DayOfWeek.THURSDAY -> "خميس"
            DayOfWeek.FRIDAY -> "جمعة"
            null -> ""
        }
    } else {
        when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> "Sat"
            DayOfWeek.SUNDAY -> "Sun"
            DayOfWeek.MONDAY -> "Mon"
            DayOfWeek.TUESDAY -> "Tue"
            DayOfWeek.WEDNESDAY -> "Wed"
            DayOfWeek.THURSDAY -> "Thu"
            DayOfWeek.FRIDAY -> "Fri"
            null -> ""
        }
    }
}

