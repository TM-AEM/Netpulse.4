package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ByteUnit
import com.example.model.DataPlanConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val labelAr: String, val labelEn: String) {
    SYSTEM("تلقائي (النظام)", "System Default"),
    LIGHT("فاتح (Light)", "Light"),
    DARK("داكن (Dark)", "Dark")
}

enum class AppLanguage(val labelAr: String, val labelEn: String, val code: String) {
    AR("العربية", "Arabic", "ar"),
    EN("English", "English", "en")
}

enum class AppDataUnit(val labelAr: String, val labelEn: String) {
    AUTO("تلقائي (Auto)", "Auto (B/KB/MB/GB)"),
    MB("ميجابايت (MB)", "Megabytes (MB)"),
    GB("جيجابايت (GB)", "Gigabytes (GB)")
}

enum class AppRefreshMode(val labelAr: String, val labelEn: String) {
    SMART("تلقائي ذكي (Smart)", "Smart Auto-Refresh"),
    MANUAL("يدوي فقط (Manual Only)", "Manual Only")
}

class AppSettingsPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _dataUnit = MutableStateFlow(loadDataUnit())
    val dataUnit: StateFlow<AppDataUnit> = _dataUnit.asStateFlow()

    private val _refreshMode = MutableStateFlow(loadRefreshMode())
    val refreshMode: StateFlow<AppRefreshMode> = _refreshMode.asStateFlow()

    private val _dataPlanConfig = MutableStateFlow(loadDataPlanConfig())
    val dataPlanConfig: StateFlow<DataPlanConfig> = _dataPlanConfig.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_THEME -> _themeMode.value = loadThemeMode()
            KEY_LANGUAGE -> _language.value = loadLanguage()
            KEY_DATA_UNIT -> _dataUnit.value = loadDataUnit()
            KEY_REFRESH_MODE -> _refreshMode.value = loadRefreshMode()
            KEY_DATA_PLAN_ENABLED,
            KEY_DATA_PLAN_LIMIT_BYTES,
            KEY_DATA_PLAN_BILLING_DAY,
            KEY_DATA_PLAN_UNIT -> _dataPlanConfig.value = loadDataPlanConfig()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadThemeMode(): AppThemeMode {
        val name = prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return try { AppThemeMode.valueOf(name) } catch (e: Exception) { AppThemeMode.SYSTEM }
    }

    private fun loadLanguage(): AppLanguage {
        val name = prefs.getString(KEY_LANGUAGE, AppLanguage.AR.name) ?: AppLanguage.AR.name
        return try { AppLanguage.valueOf(name) } catch (e: Exception) { AppLanguage.AR }
    }

    private fun loadDataUnit(): AppDataUnit {
        val name = prefs.getString(KEY_DATA_UNIT, AppDataUnit.AUTO.name) ?: AppDataUnit.AUTO.name
        return try { AppDataUnit.valueOf(name) } catch (e: Exception) { AppDataUnit.AUTO }
    }

    private fun loadRefreshMode(): AppRefreshMode {
        val name = prefs.getString(KEY_REFRESH_MODE, AppRefreshMode.SMART.name) ?: AppRefreshMode.SMART.name
        return try { AppRefreshMode.valueOf(name) } catch (e: Exception) { AppRefreshMode.SMART }
    }

    private fun loadDataPlanConfig(): DataPlanConfig {
        val enabled = try {
            prefs.getBoolean(KEY_DATA_PLAN_ENABLED, false)
        } catch (e: Exception) {
            false
        }
        val limitBytes = try {
            prefs.getLong(KEY_DATA_PLAN_LIMIT_BYTES, 0L)
        } catch (e: Exception) {
            0L
        }
        val billingDay = try {
            prefs.getInt(KEY_DATA_PLAN_BILLING_DAY, DataPlanConfig.DEFAULT_BILLING_CYCLE_START_DAY)
        } catch (e: Exception) {
            DataPlanConfig.DEFAULT_BILLING_CYCLE_START_DAY
        }
        val unitName = try {
            prefs.getString(KEY_DATA_PLAN_UNIT, ByteUnit.GB.name) ?: ByteUnit.GB.name
        } catch (e: Exception) {
            ByteUnit.GB.name
        }
        val unit = try {
            ByteUnit.valueOf(unitName)
        } catch (e: Exception) {
            ByteUnit.GB
        }

        return DataPlanConfig(
            enabled = enabled,
            limitBytes = limitBytes,
            billingCycleStartDay = billingDay,
            unit = unit
        ).normalized()
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, lang.name).apply()
        _language.value = lang
    }

    fun setDataUnit(unit: AppDataUnit) {
        prefs.edit().putString(KEY_DATA_UNIT, unit.name).apply()
        _dataUnit.value = unit
    }

    fun setRefreshMode(mode: AppRefreshMode) {
        prefs.edit().putString(KEY_REFRESH_MODE, mode.name).apply()
        _refreshMode.value = mode
    }

    fun setDataPlanConfig(config: DataPlanConfig) {
        val normalized = config.normalized()
        prefs.edit()
            .putBoolean(KEY_DATA_PLAN_ENABLED, normalized.enabled)
            .putLong(KEY_DATA_PLAN_LIMIT_BYTES, normalized.limitBytes)
            .putInt(KEY_DATA_PLAN_BILLING_DAY, normalized.billingCycleStartDay)
            .putString(KEY_DATA_PLAN_UNIT, normalized.unit.name)
            .apply()
        _dataPlanConfig.value = normalized
    }

    fun setDataPlanEnabled(enabled: Boolean) {
        val current = _dataPlanConfig.value
        setDataPlanConfig(current.copy(enabled = enabled))
    }

    fun setDataPlanLimit(limitBytes: Long, unit: ByteUnit = _dataPlanConfig.value.unit) {
        val current = _dataPlanConfig.value
        setDataPlanConfig(current.copy(limitBytes = limitBytes, unit = unit))
    }

    fun setDataPlanBillingDay(day: Int) {
        val current = _dataPlanConfig.value
        setDataPlanConfig(current.copy(billingCycleStartDay = day))
    }

    fun getThemeMode(): AppThemeMode = _themeMode.value
    fun getLanguage(): AppLanguage = _language.value
    fun getDataUnit(): AppDataUnit = _dataUnit.value
    fun getRefreshMode(): AppRefreshMode = _refreshMode.value
    fun getDataPlanConfig(): DataPlanConfig = _dataPlanConfig.value

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_DATA_UNIT = "app_data_unit"
        private const val KEY_REFRESH_MODE = "app_refresh_mode"
        const val KEY_DATA_PLAN_ENABLED = "data_plan_enabled"
        const val KEY_DATA_PLAN_LIMIT_BYTES = "data_plan_limit_bytes"
        const val KEY_DATA_PLAN_BILLING_DAY = "data_plan_billing_day"
        const val KEY_DATA_PLAN_UNIT = "data_plan_unit"
    }
}
