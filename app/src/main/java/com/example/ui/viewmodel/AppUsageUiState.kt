package com.example.ui.viewmodel

import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppThemeMode
import com.example.model.AppNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkType

enum class AppSortOption {
    TOTAL_USAGE,
    DOWNLOAD,
    UPLOAD,
    APP_NAME
}

sealed interface AppUsageContentState {
    data object Loading : AppUsageContentState
    data class Success(val apps: List<AppNetworkUsage>) : AppUsageContentState
    data object Empty : AppUsageContentState
    data class PermissionError(val message: String? = null) : AppUsageContentState
    data class Error(val message: String) : AppUsageContentState
}

data class AppUsageUiState(
    val contentState: AppUsageContentState = AppUsageContentState.Loading,
    val dateRange: DateRange,
    val selectedFilter: NetworkType = NetworkType.TOTAL,
    val selectedSort: AppSortOption = AppSortOption.TOTAL_USAGE,
    val language: AppLanguage = AppLanguage.AR,
    val dataUnit: AppDataUnit = AppDataUnit.AUTO,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM
) {
    val isLoading: Boolean get() = contentState is AppUsageContentState.Loading
    val isEmpty: Boolean get() = contentState is AppUsageContentState.Empty
    val isPermissionError: Boolean get() = contentState is AppUsageContentState.PermissionError
    val isError: Boolean get() = contentState is AppUsageContentState.Error
    val apps: List<AppNetworkUsage> get() = (contentState as? AppUsageContentState.Success)?.apps ?: emptyList()
}
