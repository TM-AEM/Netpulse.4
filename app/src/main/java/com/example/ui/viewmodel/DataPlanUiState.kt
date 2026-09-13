package com.example.ui.viewmodel

import com.example.model.DataPlanConfig
import com.example.model.DataPlanStatus

data class DataPlanUiState(
    val config: DataPlanConfig = DataPlanConfig.disabled(),
    val status: DataPlanStatus = DataPlanStatus.Disabled,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isPermissionGranted: Boolean = true
)
