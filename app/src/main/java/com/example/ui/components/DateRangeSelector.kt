package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppLanguage
import com.example.model.DateRange
import com.example.model.PeriodPreset
import com.example.ui.theme.DesignTokens
import com.example.util.DateTimeUtils

@Composable
fun DateRangeSelector(
    selectedRange: DateRange,
    onPresetSelected: (PeriodPreset) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.AR,
    onNavigatePrevious: (() -> Unit)? = null,
    onNavigateNext: (() -> Unit)? = null
) {
    val isArabic = language == AppLanguage.AR
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isNarrow = screenWidth < 480

    val presets = listOf(
        PeriodPreset.TODAY,
        PeriodPreset.YESTERDAY,
        PeriodPreset.LAST_7_DAYS,
        PeriodPreset.LAST_30_DAYS,
        PeriodPreset.THIS_MONTH,
        PeriodPreset.CUSTOM
    )

    val today = DateTimeUtils.today(selectedRange.zoneId)
    val canNavigateNext = selectedRange.endDate.isBefore(today)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.SpacingMedium, vertical = 2.dp)
            .testTag("date_range_selector"),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignTokens.CardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            // Row 1: Period Navigation and Formatted Date Range Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onNavigatePrevious?.invoke() },
                    enabled = onNavigatePrevious != null,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("previous_period_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (isArabic) "الفترة السابقة" else "Previous Period",
                        modifier = Modifier.size(18.dp),
                        tint = if (onNavigatePrevious != null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedRange.formatDisplay(language),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("date_range_display_text")
                    )
                }

                IconButton(
                    onClick = { onNavigateNext?.invoke() },
                    enabled = onNavigateNext != null && canNavigateNext,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("next_period_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (isArabic) "الفترة التالية" else "Next Period",
                        modifier = Modifier.size(18.dp),
                        tint = if (onNavigateNext != null && canNavigateNext) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Compact Single-Row Segmented Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.forEach { preset ->
                    val isSelected = selectedRange.preset == preset
                    val label = getPresetLabel(preset, language, isNarrow)

                    val containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        onClick = {
                            if (preset == PeriodPreset.CUSTOM) {
                                onCustomClick()
                            } else {
                                onPresetSelected(preset)
                            }
                        },
                        shape = RoundedCornerShape(DesignTokens.ChipCornerRadius),
                        color = containerColor,
                        contentColor = contentColor,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .testTag("preset_chip_${preset.name}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getPresetLabel(preset: PeriodPreset, language: AppLanguage, isNarrow: Boolean): String {
    return when (language) {
        AppLanguage.AR -> when (preset) {
            PeriodPreset.TODAY -> "اليوم"
            PeriodPreset.YESTERDAY -> "أمس"
            PeriodPreset.LAST_7_DAYS -> "7 أيام"
            PeriodPreset.LAST_30_DAYS -> "30 يوم"
            PeriodPreset.THIS_MONTH -> "الشهر"
            PeriodPreset.CUSTOM -> "مخصص"
        }
        AppLanguage.EN -> when (preset) {
            PeriodPreset.TODAY -> "Today"
            PeriodPreset.YESTERDAY -> if (isNarrow) "Yest" else "Yesterday"
            PeriodPreset.LAST_7_DAYS -> if (isNarrow) "7D" else "Last 7D"
            PeriodPreset.LAST_30_DAYS -> if (isNarrow) "30D" else "Last 30D"
            PeriodPreset.THIS_MONTH -> if (isNarrow) "Month" else "This Month"
            PeriodPreset.CUSTOM -> "Custom"
        }
    }
}
