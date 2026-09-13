package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppRefreshMode
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.DateRangePreferences
import com.example.data.preferences.DeveloperPreferences
import com.example.domain.repository.LargeDateRangeBreakdownUnavailableException
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.DateRange
import com.example.model.NetworkType
import com.example.model.PeriodPreset
import com.example.util.AppLogger
import com.example.util.ConnectivityObserver
import com.example.util.DateTimeUtils
import com.example.util.SmartRefreshManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class NetworkUsageViewModel(
    private val repository: NetworkStatsRepository,
    private val dateRangePreferences: DateRangePreferences,
    val appSettingsPreferences: AppSettingsPreferences,
    val developerPreferences: DeveloperPreferences,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val tag = "NetworkUsageViewModel"
    private val smartRefreshManager = SmartRefreshManager()
    private val refreshMutex = Mutex()
    private var activeLoadJob: Job? = null

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            dateRange = dateRangePreferences.loadInitialRange(),
            themeMode = appSettingsPreferences.getThemeMode(),
            language = appSettingsPreferences.getLanguage(),
            dataUnit = appSettingsPreferences.getDataUnit(),
            refreshMode = appSettingsPreferences.getRefreshMode(),
            isDeveloperModeEnabled = developerPreferences.isDeveloperModeEnabled()
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeConnectivity()
        checkPermissionAndLoad(force = false)
    }

    private fun observeSettings() {
        viewModelScope.launch {
            appSettingsPreferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            appSettingsPreferences.language.collect { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
        viewModelScope.launch {
            appSettingsPreferences.dataUnit.collect { unit ->
                _uiState.update { it.copy(dataUnit = unit) }
            }
        }
        viewModelScope.launch {
            appSettingsPreferences.refreshMode.collect { mode ->
                _uiState.update { it.copy(refreshMode = mode) }
            }
        }
        viewModelScope.launch {
            developerPreferences.isDeveloperModeEnabled.collect { isDev ->
                _uiState.update { it.copy(isDeveloperModeEnabled = isDev) }
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.observe().collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
                if (status.isConnected && _uiState.value.refreshMode == AppRefreshMode.SMART) {
                    if (smartRefreshManager.isDataStale()) {
                        refreshData(force = false)
                    }
                }
            }
        }
    }

    fun onLifecycleResume() {
        val currentZone = DateTimeUtils.getLocalZoneId()
        val currentRange = _uiState.value.dateRange
        var dateRangeChanged = false
        if (currentRange.preset != PeriodPreset.CUSTOM) {
            val updatedPresetRange = DateRange.fromPreset(currentRange.preset, currentZone)
            if (updatedPresetRange != currentRange) {
                _uiState.update { it.copy(dateRange = updatedPresetRange) }
                dateRangePreferences.saveDateRange(updatedPresetRange)
                dateRangeChanged = true
            }
        } else if (currentRange.zoneId != currentZone) {
            val updatedCustomRange = DateRange.custom(currentRange.startDate, currentRange.endDate, currentZone)
            _uiState.update { it.copy(dateRange = updatedCustomRange) }
            dateRangePreferences.saveDateRange(updatedCustomRange)
            dateRangeChanged = true
        }

        val hasPermission = repository.hasUsageStatsPermission()
        val wasPermissionGranted = _uiState.value.isPermissionGranted
        _uiState.update { it.copy(isPermissionGranted = hasPermission) }

        if (!hasPermission) return

        // If permission was just granted or data has never loaded
        if (!wasPermissionGranted || _uiState.value.summary == null) {
            loadUsageForCurrentRange(force = false)
            return
        }

        // If date range changed (e.g. day roll-over / timezone change across midnight)
        if (dateRangeChanged) {
            loadUsageForCurrentRange(force = true)
            return
        }

        // In MANUAL mode, do not auto-refresh on resume due to stale data
        if (_uiState.value.refreshMode == AppRefreshMode.MANUAL) {
            return
        }

        // Avoid duplicate refresh if data is fresh
        if (smartRefreshManager.isDataStale()) {
            loadUsageForCurrentRange(force = false)
        }
    }

    fun checkPermissionAndLoad(force: Boolean = false) {
        val hasPermission = repository.hasUsageStatsPermission()
        _uiState.update { it.copy(isPermissionGranted = hasPermission) }
        if (hasPermission) {
            loadUsageForCurrentRange(force = force)
        }
    }

    fun selectPreset(preset: PeriodPreset) {
        val newRange = DateRange.fromPreset(preset)
        if (newRange == _uiState.value.dateRange && _uiState.value.summary != null) {
            return
        }
        dateRangePreferences.saveDateRange(newRange)
        _uiState.update { it.copy(dateRange = newRange) }
        loadUsageForCurrentRange(force = true)
    }

    fun setCustomRange(startDate: LocalDate, endDate: LocalDate) {
        val newRange = DateRange.custom(startDate, endDate)
        if (newRange == _uiState.value.dateRange && _uiState.value.summary != null) {
            return
        }
        dateRangePreferences.saveDateRange(newRange)
        _uiState.update { it.copy(dateRange = newRange) }
        loadUsageForCurrentRange(force = true)
    }

    fun navigatePreviousPeriod() {
        val newRange = _uiState.value.dateRange.previousPeriod()
        dateRangePreferences.saveDateRange(newRange)
        _uiState.update { it.copy(dateRange = newRange) }
        loadUsageForCurrentRange(force = true)
    }

    fun navigateNextPeriod() {
        val currentRange = _uiState.value.dateRange
        val newRange = currentRange.nextPeriod()
        if (newRange != currentRange) {
            dateRangePreferences.saveDateRange(newRange)
            _uiState.update { it.copy(dateRange = newRange) }
            loadUsageForCurrentRange(force = true)
        }
    }

    fun refreshData(force: Boolean = true) {
        if (!smartRefreshManager.canTriggerRefresh(force)) return
        loadUsageForCurrentRange(force = force)
    }

    private fun loadUsageForCurrentRange(force: Boolean) {
        val currentRange = _uiState.value.dateRange
        if (!repository.hasUsageStatsPermission()) {
            _uiState.update { it.copy(isPermissionGranted = false) }
            return
        }

        // Structured Concurrency guard against redundant concurrent requests
        if (!force && activeLoadJob?.isActive == true) {
            return
        }

        val previousJob = activeLoadJob
        activeLoadJob = viewModelScope.launch {
            if (previousJob?.isActive == true) {
                previousJob.join()
            }
            refreshMutex.withLock {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        dailyBreakdownState = DailyBreakdownUiState.Loading
                    )
                }
                smartRefreshManager.setRefreshing(true)
                try {
                    val (summaryResult, breakdownResult) = coroutineScope {
                        val summaryDeferred = async { repository.getUsageForRange(currentRange) }
                        val breakdownDeferred = async {
                            repository.getDailyUsageBreakdown(
                                currentRange.startDate,
                                currentRange.endDate
                            )
                        }
                        Pair(summaryDeferred.await(), breakdownDeferred.await())
                    }

                    smartRefreshManager.recordRefresh()

                    summaryResult.fold(
                        onSuccess = { summary ->
                            val breakdownState = breakdownResult.fold(
                                onSuccess = { list -> DailyBreakdownUiState.Success(list) },
                                onFailure = { error ->
                                    if (error is LargeDateRangeBreakdownUnavailableException) {
                                        DailyBreakdownUiState.LargeRangeNotice(
                                            error.message ?: "فترة كبيرة"
                                        )
                                    } else {
                                        AppLogger.e(tag, "Daily breakdown error", error)
                                        DailyBreakdownUiState.Error(
                                            message = error.localizedMessage ?: "خطأ في تحميل تفاصيل الأيام",
                                            throwable = error
                                        )
                                    }
                                }
                            )

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    summary = summary,
                                    dailyBreakdownState = breakdownState,
                                    lastRefreshFormatted = smartRefreshManager.formatTimeSinceLastRefresh()
                                )
                            }

                            if (_uiState.value.isDeveloperModeEnabled) {
                                loadDiagnostics(currentRange)
                            }
                        },
                        onFailure = { error ->
                            AppLogger.e(tag, "Failed to load summary usage from NetworkStatsManager", error)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = error.localizedMessage ?: "تعذر قراءة البيانات من NetworkStatsManager",
                                    dailyBreakdownState = DailyBreakdownUiState.Error(
                                        message = error.localizedMessage ?: "خطأ في قراءة البيانات",
                                        throwable = error
                                    )
                                )
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    AppLogger.e(tag, "Unexpected error in usage loading", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.localizedMessage ?: "خطأ غير متوقع",
                            dailyBreakdownState = DailyBreakdownUiState.Error(
                                message = e.localizedMessage ?: "خطأ غير متوقع",
                                throwable = e
                            )
                        )
                    }
                } finally {
                    smartRefreshManager.setRefreshing(false)
                }
            }
        }
    }

    fun loadDiagnostics(
        range: DateRange = _uiState.value.dateRange,
        networkType: NetworkType = NetworkType.TOTAL
    ) {
        viewModelScope.launch {
            val debugRes = repository.getDebugInfo(
                networkType,
                range.getStartEpochMs(),
                range.getEndEpochMs()
            )
            debugRes.onSuccess { info ->
                _uiState.update { it.copy(debugInfo = info) }
            }
        }
    }
}
