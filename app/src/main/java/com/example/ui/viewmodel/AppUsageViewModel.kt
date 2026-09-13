package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.DateRangePreferences
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.AppNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkType
import com.example.model.PeriodPreset
import com.example.util.AppLogger
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppUsageViewModel(
    private val repository: NetworkStatsRepository,
    private val dateRangePreferences: DateRangePreferences? = null,
    val appSettingsPreferences: AppSettingsPreferences? = null,
    initialDateRange: DateRange? = null
) : ViewModel() {

    private val tag = "AppUsageViewModel"
    private var loadJob: Job? = null

    private val initialRange = initialDateRange
        ?: dateRangePreferences?.loadInitialRange()
        ?: DateRange.today()

    private val _uiState = MutableStateFlow(
        AppUsageUiState(
            dateRange = initialRange,
            language = appSettingsPreferences?.getLanguage() ?: com.example.data.preferences.AppLanguage.AR,
            dataUnit = appSettingsPreferences?.getDataUnit() ?: com.example.data.preferences.AppDataUnit.AUTO,
            themeMode = appSettingsPreferences?.getThemeMode() ?: com.example.data.preferences.AppThemeMode.SYSTEM
        )
    )
    val uiState: StateFlow<AppUsageUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        loadData()
    }

    private fun observeSettings() {
        val prefs = appSettingsPreferences ?: return
        viewModelScope.launch {
            prefs.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            prefs.language.collect { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
        viewModelScope.launch {
            prefs.dataUnit.collect { unit ->
                _uiState.update { it.copy(dataUnit = unit) }
            }
        }
    }

    fun setNetworkFilter(networkType: NetworkType) {
        if (_uiState.value.selectedFilter == networkType) return
        _uiState.update { it.copy(selectedFilter = networkType) }
        loadData()
    }

    fun setSortOption(sortOption: AppSortOption) {
        if (_uiState.value.selectedSort == sortOption) return
        _uiState.update { state ->
            val currentState = state.contentState
            if (currentState is AppUsageContentState.Success) {
                val reSorted = sortApps(currentState.apps, sortOption)
                state.copy(
                    selectedSort = sortOption,
                    contentState = AppUsageContentState.Success(reSorted)
                )
            } else {
                state.copy(selectedSort = sortOption)
            }
        }
    }

    fun selectPreset(preset: PeriodPreset) {
        if (preset == PeriodPreset.CUSTOM) return
        val current = _uiState.value.dateRange
        val newRange = DateRange.fromPreset(preset, current.zoneId)
        setDateRange(newRange)
    }

    fun setCustomRange(startDate: LocalDate, endDate: LocalDate) {
        try {
            val range = DateRange.custom(startDate, endDate, _uiState.value.dateRange.zoneId)
            setDateRange(range)
        } catch (e: Exception) {
            AppLogger.e(tag, "Invalid custom date range: $startDate to $endDate", e)
        }
    }

    fun navigatePreviousPeriod() {
        val previous = _uiState.value.dateRange.previousPeriod()
        setDateRange(previous)
    }

    fun navigateNextPeriod() {
        val next = _uiState.value.dateRange.nextPeriod()
        val today = DateTimeUtils.today(_uiState.value.dateRange.zoneId)
        if (!next.startDate.isAfter(today)) {
            val clampedEnd = if (next.endDate.isAfter(today)) today else next.endDate
            val safeRange = DateRange.custom(next.startDate, clampedEnd, next.zoneId)
            setDateRange(safeRange)
        }
    }

    private fun setDateRange(newRange: DateRange) {
        if (_uiState.value.dateRange == newRange) return
        _uiState.update { it.copy(dateRange = newRange) }
        dateRangePreferences?.saveDateRange(newRange)
        loadData()
    }

    fun refreshData() {
        loadData()
    }

    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(contentState = AppUsageContentState.Loading) }

            if (!repository.hasUsageStatsPermission()) {
                _uiState.update { it.copy(contentState = AppUsageContentState.PermissionError()) }
                return@launch
            }

            val currentRange = _uiState.value.dateRange
            val currentFilter = _uiState.value.selectedFilter

            val result = repository.getAppUsageForRange(
                dateRange = currentRange,
                networkType = currentFilter
            )

            result.fold(
                onSuccess = { rawApps ->
                    val sorted = sortApps(rawApps, _uiState.value.selectedSort)
                    _uiState.update { state ->
                        if (sorted.isEmpty()) {
                            state.copy(contentState = AppUsageContentState.Empty)
                        } else {
                            state.copy(contentState = AppUsageContentState.Success(sorted))
                        }
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    AppLogger.e(tag, "Failed to load app usage data", error)
                    if (error is SecurityException) {
                        _uiState.update { it.copy(contentState = AppUsageContentState.PermissionError(error.message)) }
                    } else {
                        _uiState.update { it.copy(contentState = AppUsageContentState.Error(error.message ?: "Failed to load app usage")) }
                    }
                }
            )
        }
    }

    companion object {
        fun sortApps(
            apps: List<AppNetworkUsage>,
            sortOption: AppSortOption
        ): List<AppNetworkUsage> {
            return when (sortOption) {
                AppSortOption.TOTAL_USAGE -> apps.sortedWith(
                    compareByDescending<AppNetworkUsage> { it.totalBytes }
                        .thenBy { it.displayName.lowercase() }
                        .thenBy { it.uid }
                )
                AppSortOption.DOWNLOAD -> apps.sortedWith(
                    compareByDescending<AppNetworkUsage> { it.downloadBytes }
                        .thenBy { it.displayName.lowercase() }
                        .thenBy { it.uid }
                )
                AppSortOption.UPLOAD -> apps.sortedWith(
                    compareByDescending<AppNetworkUsage> { it.uploadBytes }
                        .thenBy { it.displayName.lowercase() }
                        .thenBy { it.uid }
                )
                AppSortOption.APP_NAME -> apps.sortedWith(
                    compareBy<AppNetworkUsage> { it.displayName.lowercase() }
                        .thenByDescending { it.totalBytes }
                        .thenBy { it.uid }
                )
            }
        }
    }
}
