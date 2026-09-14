package com.example.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.AppLanguage
import com.example.model.NetworkType
import com.example.model.NetworkUsageComparison
import com.example.model.PercentageChange
import com.example.model.TrendDirection
import com.example.model.UsageMetricComparison
import com.example.ui.components.CustomDateRangeDialog
import com.example.ui.components.DateRangeSelector
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.DownloadAccent
import com.example.ui.theme.TotalAccent
import com.example.ui.theme.UploadAccent
import com.example.ui.viewmodel.UsageInsightsContentState
import com.example.ui.viewmodel.UsageInsightsUiState
import com.example.ui.viewmodel.UsageInsightsViewModel
import com.example.util.ByteFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageInsightsScreen(
    viewModel: UsageInsightsViewModel,
    uiState: UsageInsightsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language = uiState.language

    val localizedContext = remember(context, language) {
        val conf = Configuration(context.resources.configuration)
        conf.setLocale(Locale(language.code))
        context.createConfigurationContext(conf)
    }

    var showCustomRangeDialog by remember { mutableStateOf(false) }

    if (showCustomRangeDialog) {
        CustomDateRangeDialog(
            initialStartDate = uiState.dateRange.startDate,
            initialEndDate = uiState.dateRange.endDate,
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showCustomRangeDialog = false
            },
            onDismiss = { showCustomRangeDialog = false },
            language = language
        )
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("usage_insights_screen"),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.usage_insights),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.usage_insights_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("insights_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshData() },
                            modifier = Modifier.testTag("insights_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Filter Chips Row
                NetworkFilterRow(
                    selectedFilter = uiState.selectedFilter,
                    onFilterSelected = { viewModel.setNetworkFilter(it) },
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.SpacingMedium,
                        vertical = DesignTokens.SpacingSmall
                    )
                )

                // Date Range Selector
                DateRangeSelector(
                    selectedRange = uiState.dateRange,
                    onPresetSelected = { viewModel.selectPreset(it) },
                    onCustomClick = { showCustomRangeDialog = true },
                    language = language,
                    onNavigatePrevious = { viewModel.navigatePreviousPeriod() },
                    onNavigateNext = { viewModel.navigateNextPeriod() }
                )

                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))

                // Content States
                when (val state = uiState.contentState) {
                    is UsageInsightsContentState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .testTag("insights_loading_state"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.testTag("insights_loading_indicator")
                            )
                        }
                    }
                    is UsageInsightsContentState.PermissionRequired -> {
                        PermissionRequiredCard(
                            onOpenSettings = {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallback = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(fallback)
                                }
                            },
                            onRetry = { viewModel.refreshData() }
                        )
                    }
                    is UsageInsightsContentState.Error -> {
                        ErrorCard(
                            errorMessage = state.message,
                            onRetry = { viewModel.refreshData() }
                        )
                    }
                    is UsageInsightsContentState.Empty -> {
                        ZeroUsageCard()
                    }
                    is UsageInsightsContentState.ZeroUsage -> {
                        val comparison = state.insights.forNetworkType(uiState.selectedFilter)
                        ZeroUsageCard()
                        Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
                        InsightsDataContent(
                            comparison = comparison,
                            uiState = uiState
                        )
                    }
                    is UsageInsightsContentState.Success -> {
                        val comparison = state.insights.forNetworkType(uiState.selectedFilter)
                        InsightsDataContent(
                            comparison = comparison,
                            uiState = uiState
                        )
                    }
                }

                Spacer(modifier = Modifier.height(DesignTokens.SpacingXLarge))
            }
        }
    }
}

@Composable
private fun NetworkFilterRow(
    selectedFilter: NetworkType,
    onFilterSelected: (NetworkType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filters = listOf(
            NetworkType.TOTAL to stringResource(R.string.total),
            NetworkType.WIFI to stringResource(R.string.wifi),
            NetworkType.MOBILE to stringResource(R.string.mobile)
        )

        filters.forEach { (type, label) ->
            FilterChip(
                selected = selectedFilter == type,
                onClick = { onFilterSelected(type) },
                label = { Text(label) },
                modifier = Modifier.testTag("filter_chip_${type.name}"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun InsightsDataContent(
    comparison: NetworkUsageComparison,
    uiState: UsageInsightsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.SpacingMedium),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
    ) {
        // A. Current Period
        CurrentPeriodCard(
            comparison = comparison,
            uiState = uiState
        )

        // B. Average Daily Usage
        AverageDailyUsageCard(
            comparison = comparison,
            uiState = uiState
        )

        // C. Previous Period Comparison
        PreviousPeriodComparisonSection(
            comparison = comparison,
            uiState = uiState
        )
    }
}

@Composable
private fun CurrentPeriodCard(
    comparison: NetworkUsageComparison,
    uiState: UsageInsightsUiState,
    modifier: Modifier = Modifier
) {
    val language = uiState.language
    val dataUnit = uiState.dataUnit

    val totalFormatted = ByteFormatter.format(comparison.currentUsage.totalBytes, dataUnit).getDisplay(language)
    val downloadFormatted = ByteFormatter.format(comparison.currentUsage.downloadBytes, dataUnit).getDisplay(language)
    val uploadFormatted = ByteFormatter.format(comparison.currentUsage.uploadBytes, dataUnit).getDisplay(language)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("current_period_card"),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignTokens.CardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingMedium)
        ) {
            Text(
                text = stringResource(R.string.insights_current_period),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))

            // Total Usage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("current_total_metric"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = totalFormatted,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TotalAccent
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            // Download & Upload Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.testTag("current_download_metric"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = DownloadAccent
                        )
                        Text(
                            text = stringResource(R.string.download),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = downloadFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier.testTag("current_upload_metric"),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.upload),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = UploadAccent
                        )
                    }
                    Text(
                        text = uploadFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AverageDailyUsageCard(
    comparison: NetworkUsageComparison,
    uiState: UsageInsightsUiState,
    modifier: Modifier = Modifier
) {
    val language = uiState.language
    val dataUnit = uiState.dataUnit
    val daily = comparison.averageDailyUsage
    val perDaySuffix = stringResource(R.string.insights_per_day)

    val totalPerDay = "${ByteFormatter.format(daily.totalBytes, dataUnit).getDisplay(language)} $perDaySuffix"
    val downloadPerDay = "${ByteFormatter.format(daily.downloadBytes, dataUnit).getDisplay(language)} $perDaySuffix"
    val uploadPerDay = "${ByteFormatter.format(daily.uploadBytes, dataUnit).getDisplay(language)} $perDaySuffix"

    val daysText = when (language) {
        AppLanguage.AR -> "${daily.daysCount} أيام"
        AppLanguage.FR -> "${daily.daysCount} jours"
        AppLanguage.EN -> "${daily.daysCount} days"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_average_card"),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignTokens.CardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingMedium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.insights_daily_average),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(DesignTokens.BadgeCornerRadius),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = daysText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))

            // Total / day
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("daily_avg_total_metric"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = totalPerDay,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TotalAccent
                )
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            // Download/day & Upload/day
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.testTag("daily_avg_download_metric"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = DownloadAccent
                        )
                        Text(
                            text = stringResource(R.string.download),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = downloadPerDay,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier.testTag("daily_avg_upload_metric"),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.upload),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = UploadAccent
                        )
                    }
                    Text(
                        text = uploadPerDay,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviousPeriodComparisonSection(
    comparison: NetworkUsageComparison,
    uiState: UsageInsightsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("comparison_section"),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall)
    ) {
        Text(
            text = stringResource(R.string.insights_comparison),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Total Metric Comparison
        MetricComparisonCard(
            label = stringResource(R.string.total),
            metricComparison = comparison.totalComparison,
            accentColor = TotalAccent,
            uiState = uiState,
            cardTag = "comparison_card_total",
            currentTag = "total_comp_current",
            previousTag = "total_comp_previous",
            diffTag = "total_comp_diff",
            trendTag = "total_trend_indicator",
            pctTag = "total_pct_change"
        )

        // Download Metric Comparison
        MetricComparisonCard(
            label = stringResource(R.string.download),
            metricComparison = comparison.downloadComparison,
            accentColor = DownloadAccent,
            uiState = uiState,
            cardTag = "comparison_card_download",
            currentTag = "download_comp_current",
            previousTag = "download_comp_previous",
            diffTag = "download_comp_diff",
            trendTag = "download_trend_indicator",
            pctTag = "download_pct_change"
        )

        // Upload Metric Comparison
        MetricComparisonCard(
            label = stringResource(R.string.upload),
            metricComparison = comparison.uploadComparison,
            accentColor = UploadAccent,
            uiState = uiState,
            cardTag = "comparison_card_upload",
            currentTag = "upload_comp_current",
            previousTag = "upload_comp_previous",
            diffTag = "upload_comp_diff",
            trendTag = "upload_trend_indicator",
            pctTag = "upload_pct_change"
        )
    }
}

@Composable
private fun MetricComparisonCard(
    label: String,
    metricComparison: UsageMetricComparison,
    accentColor: Color,
    uiState: UsageInsightsUiState,
    cardTag: String,
    currentTag: String,
    previousTag: String,
    diffTag: String,
    trendTag: String,
    pctTag: String,
    modifier: Modifier = Modifier
) {
    val language = uiState.language
    val dataUnit = uiState.dataUnit

    val currentFormatted = ByteFormatter.format(metricComparison.currentBytes, dataUnit).getDisplay(language)
    val previousFormatted = ByteFormatter.format(metricComparison.previousBytes, dataUnit).getDisplay(language)
    val diffFormatted = ByteFormatter.format(metricComparison.absoluteDifference, dataUnit).getDisplay(language)
    val pctText = formatPercentage(metricComparison.percentageChange, language)

    val (trendIcon, trendText, trendColor, trendCd) = getTrendPresentation(metricComparison.direction)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(cardTag),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignTokens.CardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Label & Trend Indicator Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )

                TrendBadge(
                    icon = trendIcon,
                    text = trendText,
                    color = trendColor,
                    percentageText = pctText,
                    contentDescription = trendCd,
                    trendTag = trendTag,
                    pctTag = pctTag
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Values Row: Current, Previous, Difference
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Current
                Column(
                    modifier = Modifier.testTag(currentTag),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.insights_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Previous
                Column(
                    modifier = Modifier.testTag(previousTag),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.insights_previous),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = previousFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Difference
                Column(
                    modifier = Modifier.testTag(diffTag),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.insights_difference),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = diffFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendBadge(
    icon: ImageVector,
    text: String,
    color: Color,
    percentageText: String,
    contentDescription: String,
    trendTag: String,
    pctTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag(trendTag)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(DesignTokens.BadgeCornerRadius),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = "($percentageText)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.testTag(pctTag)
            )
        }
    }
}

@Composable
private fun getTrendPresentation(direction: TrendDirection): TrendPresentation {
    return when (direction) {
        TrendDirection.INCREASE -> TrendPresentation(
            icon = Icons.Default.TrendingUp,
            text = stringResource(R.string.insights_trend_increase),
            color = Color(0xFFD32F2F),
            contentDescription = stringResource(R.string.cd_insights_trend_increase)
        )
        TrendDirection.DECREASE -> TrendPresentation(
            icon = Icons.Default.TrendingDown,
            text = stringResource(R.string.insights_trend_decrease),
            color = Color(0xFF2E7D32),
            contentDescription = stringResource(R.string.cd_insights_trend_decrease)
        )
        TrendDirection.UNCHANGED -> TrendPresentation(
            icon = Icons.Default.TrendingFlat,
            text = stringResource(R.string.insights_trend_unchanged),
            color = MaterialTheme.colorScheme.outline,
            contentDescription = stringResource(R.string.cd_insights_trend_unchanged)
        )
        TrendDirection.UNAVAILABLE -> TrendPresentation(
            icon = Icons.Default.HelpOutline,
            text = stringResource(R.string.insights_trend_unavailable),
            color = MaterialTheme.colorScheme.outline,
            contentDescription = stringResource(R.string.cd_insights_trend_unavailable)
        )
    }
}

private data class TrendPresentation(
    val icon: ImageVector,
    val text: String,
    val color: Color,
    val contentDescription: String
)

private fun formatPercentage(change: PercentageChange, language: AppLanguage): String {
    return when (change) {
        is PercentageChange.Defined -> {
            val sign = if (change.percentage > 0.0) "+" else ""
            val rounded = String.format(Locale.US, "%.1f", change.percentage)
            "$sign$rounded%"
        }
        is PercentageChange.Undefined -> {
            if (language == AppLanguage.AR) "غير متاح" else "N/A"
        }
    }
}

@Composable
private fun ZeroUsageCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.SpacingMedium)
            .testTag("insights_zero_usage_card"),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
            Text(
                text = stringResource(R.string.insights_zero_usage_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
            Text(
                text = stringResource(R.string.insights_zero_usage_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(DesignTokens.SpacingMedium)
            .testTag("insights_permission_state"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.SpacingLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
                Text(
                    text = stringResource(R.string.insights_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
                Text(
                    text = stringResource(R.string.insights_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(DesignTokens.SpacingLarge))
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("open_settings_button")
                ) {
                    Text(stringResource(R.string.insights_grant_permission))
                }
                Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("retry_permission_button")
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(DesignTokens.SpacingMedium)
            .testTag("insights_error_state"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                .padding(DesignTokens.SpacingLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
                Text(
                    text = stringResource(R.string.insights_error_load_failed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.SpacingLarge))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("insights_retry_button")
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}
