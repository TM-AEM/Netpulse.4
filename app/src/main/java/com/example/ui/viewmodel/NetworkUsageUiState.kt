package com.example.ui.viewmodel

import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppRefreshMode
import com.example.data.preferences.AppThemeMode
import com.example.model.DailyNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkUsageSummary
import com.example.util.ConnectionStatus

sealed interface DailyBreakdownUiState {
    data object Loading : DailyBreakdownUiState
    data class Success(val dailyUsageList: List<DailyNetworkUsage>) : DailyBreakdownUiState
    data class Error(val message: String, val throwable: Throwable? = null) : DailyBreakdownUiState
    data class LargeRangeNotice(val message: String) : DailyBreakdownUiState
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val summary: NetworkUsageSummary? = null,
    val dailyBreakdownState: DailyBreakdownUiState = DailyBreakdownUiState.Loading,
    val dateRange: DateRange = DateRange.today(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val errorMessage: String? = null,
    val lastRefreshFormatted: String = "",
    val isDeveloperModeEnabled: Boolean = false,
    val debugInfo: NetworkStatsDebugInfo? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.AR,
    val dataUnit: AppDataUnit = AppDataUnit.AUTO,
    val refreshMode: AppRefreshMode = AppRefreshMode.SMART
)
