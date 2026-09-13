package com.example.ui.viewmodel

import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppThemeMode
import com.example.model.DateRange
import com.example.model.NetworkType
import com.example.model.NetworkUsageComparison
import com.example.model.UsageInsights

sealed interface UsageInsightsContentState {
    data object Loading : UsageInsightsContentState
    data class Success(val insights: UsageInsights) : UsageInsightsContentState
    data class ZeroUsage(val insights: UsageInsights) : UsageInsightsContentState
    data object Empty : UsageInsightsContentState
    data class PermissionRequired(val message: String? = null) : UsageInsightsContentState
    data class Error(val message: String) : UsageInsightsContentState
}

data class UsageInsightsUiState(
    val dateRange: DateRange,
    val selectedFilter: NetworkType = NetworkType.TOTAL,
    val contentState: UsageInsightsContentState = UsageInsightsContentState.Loading,
    val language: AppLanguage = AppLanguage.AR,
    val dataUnit: AppDataUnit = AppDataUnit.AUTO,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM
) {
    val activeComparison: NetworkUsageComparison?
        get() = when (contentState) {
            is UsageInsightsContentState.Success -> contentState.insights.forNetworkType(selectedFilter)
            is UsageInsightsContentState.ZeroUsage -> contentState.insights.forNetworkType(selectedFilter)
            else -> null
        }

    val isZeroUsage: Boolean
        get() = contentState is UsageInsightsContentState.ZeroUsage || contentState is UsageInsightsContentState.Empty
}
