package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppSettingsPreferences
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.BillingCycleCalculator
import com.example.model.ByteUnit
import com.example.model.DataPlanCalculator
import com.example.model.DataPlanConfig
import com.example.model.DataPlanStatus
import com.example.util.AppLogger
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

class DataPlanViewModel(
    private val repository: NetworkStatsRepository,
    private val appSettingsPreferences: AppSettingsPreferences,
    private val zoneId: ZoneId = DateTimeUtils.getLocalZoneId()
) : ViewModel() {

    private val tag = "DataPlanViewModel"
    private var activeLoadJob: Job? = null

    private val _uiState = MutableStateFlow(
        DataPlanUiState(
            config = appSettingsPreferences.getDataPlanConfig()
        )
    )
    val uiState: StateFlow<DataPlanUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            appSettingsPreferences.dataPlanConfig.collect { config ->
                _uiState.update { it.copy(config = config) }
                if (config.enabled && config.limitBytes > 0L) {
                    loadUsageForCycle(config)
                } else {
                    activeLoadJob?.cancel()
                    _uiState.update {
                        it.copy(
                            status = DataPlanStatus.Disabled,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    fun checkPermission(): Boolean {
        val hasPermission = repository.hasUsageStatsPermission()
        _uiState.update { it.copy(isPermissionGranted = hasPermission) }
        return hasPermission
    }

    fun refresh() {
        val hasPermission = checkPermission()
        if (!hasPermission) return

        val config = _uiState.value.config
        if (config.enabled && config.limitBytes > 0L) {
            loadUsageForCycle(config)
        }
    }

    fun setPlanEnabled(enabled: Boolean) {
        appSettingsPreferences.setDataPlanEnabled(enabled)
    }

    fun setPlanLimit(limitBytes: Long, unit: ByteUnit = _uiState.value.config.unit) {
        appSettingsPreferences.setDataPlanLimit(limitBytes, unit)
    }

    fun setBillingCycleStartDay(day: Int) {
        appSettingsPreferences.setDataPlanBillingDay(day)
    }

    fun updateConfig(config: DataPlanConfig) {
        appSettingsPreferences.setDataPlanConfig(config)
    }

    private fun loadUsageForCycle(config: DataPlanConfig) {
        activeLoadJob?.cancel()
        activeLoadJob = viewModelScope.launch {
            val hasPermission = repository.hasUsageStatsPermission()
            if (!hasPermission) {
                _uiState.update {
                    it.copy(
                        isPermissionGranted = false,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isPermissionGranted = true,
                    isLoading = true,
                    errorMessage = null
                )
            }
            try {
                val cyclePeriod = BillingCycleCalculator.calculateCurrentPeriod(
                    billingCycleStartDay = config.billingCycleStartDay,
                    zoneId = zoneId
                )
                val queryRange = cyclePeriod.toQueryDateRange()

                val result = repository.getUsageForRange(queryRange)
                result.fold(
                    onSuccess = { summary ->
                        val totalBytes = summary.total.totalBytes
                        val status = DataPlanCalculator.calculate(
                            config = config,
                            cyclePeriod = cyclePeriod,
                            usedBytes = totalBytes
                        )
                        _uiState.update {
                            it.copy(
                                status = status,
                                isLoading = false,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        AppLogger.e(tag, "Failed to load usage for data plan cycle", error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load usage"
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(tag, "Unexpected error loading data plan usage", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Unexpected error"
                    )
                }
            }
        }
    }
}
