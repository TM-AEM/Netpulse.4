package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.ui.components.ConnectionStatusBanner
import com.example.ui.components.CustomDateRangeDialog
import com.example.ui.components.DateRangeSelector
import com.example.ui.components.DiagnosticsSheet
import com.example.ui.components.MetricCard
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.components.UsageBarChart
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.MobileAccent
import com.example.ui.theme.TotalAccent
import com.example.ui.theme.WifiAccent
import com.example.ui.viewmodel.DailyBreakdownUiState
import com.example.ui.viewmodel.DashboardUiState
import com.example.ui.viewmodel.NetworkUsageViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: NetworkUsageViewModel,
    uiState: DashboardUiState,
    onNavigateToSettings: () -> Unit,
    onNavigateToAppUsage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val language = uiState.language
    val isArabic = language == com.example.data.preferences.AppLanguage.AR

    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    if (showCustomRangeDialog) {
        CustomDateRangeDialog(
            initialStartDate = uiState.dateRange.startDate,
            initialEndDate = uiState.dateRange.endDate,
            onConfirm = { s, e ->
                viewModel.setCustomRange(s, e)
                showCustomRangeDialog = false
            },
            onDismiss = { showCustomRangeDialog = false },
            language = language
        )
    }

    if (showDiagnosticsSheet) {
        DiagnosticsSheet(
            debugInfo = uiState.debugInfo,
            sheetState = sheetState,
            onDismiss = {
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    showDiagnosticsSheet = false
                }
            },
            language = language,
            onSelectNetworkType = { type ->
                viewModel.loadDiagnostics(networkType = type)
            }
        )
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyDialog = false },
            language = language
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "NetPulse",
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.lastRefreshFormatted.isNotEmpty()) {
                            Text(
                                text = "${if (isArabic) "تم التحديث" else "Updated"}: ${uiState.lastRefreshFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.isDeveloperModeEnabled) {
                        IconButton(
                            onClick = { showDiagnosticsSheet = true },
                            modifier = Modifier.testTag("diagnostics_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = if (isArabic) "التشخيص" else "Diagnostics",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onNavigateToAppUsage,
                        modifier = Modifier.testTag("app_usage_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataUsage,
                            contentDescription = if (isArabic) "استهلاك التطبيقات" else "App Usage"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshData(force = true) },
                        modifier = Modifier.testTag("refresh_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (isArabic) "تحديث" else "Refresh"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = if (isArabic) "الإعدادات" else "Settings"
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
                .padding(bottom = DesignTokens.SpacingXLarge)
        ) {
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            ConnectionStatusBanner(
                connectionStatus = uiState.connectionStatus,
                language = language,
                modifier = Modifier.padding(horizontal = DesignTokens.SpacingMedium)
            )

            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            DateRangeSelector(
                selectedRange = uiState.dateRange,
                onPresetSelected = { viewModel.selectPreset(it) },
                onCustomClick = { showCustomRangeDialog = true },
                language = language,
                onNavigatePrevious = { viewModel.navigatePreviousPeriod() },
                onNavigateNext = { viewModel.navigateNextPeriod() }
            )

            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            if (uiState.isLoading && uiState.summary == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("loading_indicator"))
                }
            } else if (uiState.errorMessage != null && uiState.summary == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DesignTokens.SpacingMedium),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(DesignTokens.SpacingMedium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
                        Button(onClick = { viewModel.refreshData(force = true) }) {
                            Text(if (isArabic) "إعادة المحاولة" else "Retry")
                        }
                    }
                }
            } else {
                val summary = uiState.summary ?: com.example.model.NetworkUsageSummary(
                    wifi = NetworkUsage.zero(NetworkType.WIFI),
                    mobile = NetworkUsage.zero(NetworkType.MOBILE),
                    total = NetworkUsage.zero(NetworkType.TOTAL),
                    dateRange = uiState.dateRange
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.SpacingMedium),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
                ) {
                    val wifiSharePct = (summary.wifiShareRatio * 100).toInt()
                    val mobileSharePct = (summary.mobileShareRatio * 100).toInt()

                    // Overall Total as primary HERO metric at the top
                    MetricCard(
                        title = if (isArabic) "الإجمالي الشامل" else "Overall Total",
                        usage = summary.total,
                        icon = Icons.Default.Public,
                        accentColor = TotalAccent,
                        language = language,
                        forcedUnit = uiState.dataUnit,
                        secondaryInfo = if (isArabic) "100% الإجمالي" else "100% Total",
                        testTag = "overall_metric_card",
                        isHero = true
                    )

                    // Wi-Fi and Mobile Data as responsive secondary cards
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        if (maxWidth >= 320.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
                            ) {
                                MetricCard(
                                    title = if (isArabic) "واي فاي" else "Wi-Fi",
                                    usage = summary.wifi,
                                    icon = Icons.Default.Wifi,
                                    accentColor = WifiAccent,
                                    language = language,
                                    forcedUnit = uiState.dataUnit,
                                    secondaryInfo = "$wifiSharePct%",
                                    testTag = "wifi_metric_card",
                                    modifier = Modifier.weight(1f),
                                    isHero = false
                                )

                                MetricCard(
                                    title = if (isArabic) "بيانات الجوال" else "Mobile Data",
                                    usage = summary.mobile,
                                    icon = Icons.Default.SignalCellularAlt,
                                    accentColor = MobileAccent,
                                    language = language,
                                    forcedUnit = uiState.dataUnit,
                                    secondaryInfo = "$mobileSharePct%",
                                    testTag = "mobile_metric_card",
                                    modifier = Modifier.weight(1f),
                                    isHero = false
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
                            ) {
                                MetricCard(
                                    title = if (isArabic) "واي فاي" else "Wi-Fi",
                                    usage = summary.wifi,
                                    icon = Icons.Default.Wifi,
                                    accentColor = WifiAccent,
                                    language = language,
                                    forcedUnit = uiState.dataUnit,
                                    secondaryInfo = "$wifiSharePct%",
                                    testTag = "wifi_metric_card",
                                    isHero = false
                                )

                                MetricCard(
                                    title = if (isArabic) "بيانات الجوال" else "Mobile Data",
                                    usage = summary.mobile,
                                    icon = Icons.Default.SignalCellularAlt,
                                    accentColor = MobileAccent,
                                    language = language,
                                    forcedUnit = uiState.dataUnit,
                                    secondaryInfo = "$mobileSharePct%",
                                    testTag = "mobile_metric_card",
                                    isHero = false
                                )
                            }
                        }
                    }

                    when (val breakdown = uiState.dailyBreakdownState) {
                        is DailyBreakdownUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                        is DailyBreakdownUiState.Success -> {
                            if (!uiState.dateRange.isSingleDay) {
                                UsageBarChart(
                                    dailyData = breakdown.dailyUsageList,
                                    language = language,
                                    forcedUnit = uiState.dataUnit
                                )
                            }
                        }
                        is DailyBreakdownUiState.LargeRangeNotice -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("large_range_notice_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(DesignTokens.CardCornerRadius)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(DesignTokens.SpacingMedium),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.size(DesignTokens.SpacingSmall))
                                    Text(
                                        text = if (isArabic) {
                                            breakdown.message
                                        } else {
                                            "Daily breakdown is available for periods up to 60 days to protect device performance. Overall range total is fully calculated from NetworkStatsManager."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is DailyBreakdownUiState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(DesignTokens.CardCornerRadius)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(DesignTokens.SpacingMedium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = breakdown.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(onClick = { viewModel.refreshData(force = true) }) {
                                        Text(if (isArabic) "إعادة المحاولة" else "Retry")
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
