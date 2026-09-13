package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.DateRangePreferences
import com.example.data.preferences.DeveloperPreferences
import com.example.data.repository.NetworkStatsRepositoryImpl
import com.example.data.resolver.AppInfoResolverImpl
import com.example.data.source.NetworkStatsDataSourceImpl
import com.example.util.ConnectivityObserver

class NetworkUsageViewModelFactory(
    private val context: Context,
    private val appSettingsPreferences: AppSettingsPreferences? = null,
    private val developerPreferences: DeveloperPreferences? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        val dataSource = NetworkStatsDataSourceImpl(appContext)
        val resolver = AppInfoResolverImpl(appContext)
        val repository = NetworkStatsRepositoryImpl(dataSource, resolver)
        val dateRangePrefs = DateRangePreferences(appContext)
        val appSettingsPrefs = appSettingsPreferences ?: AppSettingsPreferences(appContext)
        val devPrefs = developerPreferences ?: DeveloperPreferences(appContext)

        if (modelClass.isAssignableFrom(NetworkUsageViewModel::class.java)) {
            val connectivity = ConnectivityObserver(appContext)
            return NetworkUsageViewModel(
                repository = repository,
                dateRangePreferences = dateRangePrefs,
                appSettingsPreferences = appSettingsPrefs,
                developerPreferences = devPrefs,
                connectivityObserver = connectivity
            ) as T
        } else if (modelClass.isAssignableFrom(AppUsageViewModel::class.java)) {
            return AppUsageViewModel(
                repository = repository,
                dateRangePreferences = dateRangePrefs,
                appSettingsPreferences = appSettingsPrefs
            ) as T
        } else if (modelClass.isAssignableFrom(DataPlanViewModel::class.java)) {
            return DataPlanViewModel(
                repository = repository,
                appSettingsPreferences = appSettingsPrefs
            ) as T
        } else if (modelClass.isAssignableFrom(UsageInsightsViewModel::class.java)) {
            return UsageInsightsViewModel(
                repository = repository,
                dateRangePreferences = dateRangePrefs,
                appSettingsPreferences = appSettingsPrefs
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
