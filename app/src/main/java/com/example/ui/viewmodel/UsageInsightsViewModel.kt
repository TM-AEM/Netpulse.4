package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.DateRangePreferences
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.DateRange
import com.example.model.NetworkType
import com.example.model.PeriodPreset
import com.example.model.UsageInsights
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

class UsageInsightsViewModel(
    private val repository: NetworkStatsRepository,
    private val dateRangePreferences: DateRangePreferences? = null,
    val appSettingsPreferences: AppSettingsPreferences? = null,
    initialDateRange: DateRange? = null
) : ViewModel() {

    private val tag = "UsageInsightsViewModel"
    private var loadJob: Job? = null

    private val initialRange = initialDateRange
        ?: dateRangePreferences?.loadInitialRange()
        ?: DateRange.today()

    private val _uiState = MutableStateFlow(
        UsageInsightsUiState(
            dateRange = initialRange,
            language = appSettingsPreferences?.getLanguage() ?: com.example.data.preferences.AppLanguage.AR,
            dataUnit = appSettingsPreferences?.getDataUnit() ?: com.example.data.preferences.AppDataUnit.AUTO,
            themeMode = appSettingsPreferences?.getThemeMode() ?: com.example.data.preferences.AppThemeMode.SYSTEM
        )
    )
    val uiState: StateFlow<UsageInsightsUiState> = _uiState.asStateFlow()

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
        _uiState.update { state ->
            val updatedState = state.copy(selectedFilter = networkType)
            val insights = when (val c = state.contentState) {
                is UsageInsightsContentState.Success -> c.insights
                is UsageInsightsContentState.ZeroUsage -> c.insights
                else -> null
            }
            if (insights != null) {
                val filterComparison = insights.forNetworkType(networkType)
                val newContentState = if (filterComparison.currentUsage.totalBytes == 0L) {
                    UsageInsightsContentState.ZeroUsage(insights)
                } else {
                    UsageInsightsContentState.Success(insights)
                }
                updatedState.copy(contentState = newContentState)
            } else {
                updatedState
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

    fun setDateRange(newRange: DateRange) {
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
            _uiState.update { it.copy(contentState = UsageInsightsContentState.Loading) }

            if (!repository.hasUsageStatsPermission()) {
                _uiState.update { it.copy(contentState = UsageInsightsContentState.PermissionRequired()) }
                return@launch
            }

            val currentRange = _uiState.value.dateRange

            val result = repository.getUsageInsights(currentRange)

            result.fold(
                onSuccess = { insights ->
                    _uiState.update { state ->
                        val filterComparison = insights.forNetworkType(state.selectedFilter)
                        val content = if (filterComparison.currentUsage.totalBytes == 0L) {
                            UsageInsightsContentState.ZeroUsage(insights)
                        } else {
                            UsageInsightsContentState.Success(insights)
                        }
                        state.copy(contentState = content)
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    AppLogger.e(tag, "Failed to load usage insights data", error)
                    if (error is SecurityException) {
                        _uiState.update { it.copy(contentState = UsageInsightsContentState.PermissionRequired(error.message)) }
                    } else {
                        _uiState.update { it.copy(contentState = UsageInsightsContentState.Error(error.message ?: "Failed to load usage insights")) }
                    }
                }
            )
        }
    }
}
