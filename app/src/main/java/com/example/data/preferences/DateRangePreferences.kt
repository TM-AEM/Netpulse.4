package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.model.DateRange
import com.example.model.PeriodPreset
import com.example.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class DateRangePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("netpulse_date_range_prefs", Context.MODE_PRIVATE)

    private val _selectedRangeFlow = MutableStateFlow(loadInitialRange())
    val selectedRangeFlow: StateFlow<DateRange> = _selectedRangeFlow.asStateFlow()

    fun loadInitialRange(): DateRange {
        val presetName = prefs.getString(KEY_PRESET, PeriodPreset.TODAY.name) ?: PeriodPreset.TODAY.name
        val preset = try { PeriodPreset.valueOf(presetName) } catch (e: Exception) { PeriodPreset.TODAY }
        val zoneId = DateTimeUtils.getLocalZoneId()

        return if (preset == PeriodPreset.CUSTOM) {
            val startIso = prefs.getString(KEY_CUSTOM_START, null)
            val endIso = prefs.getString(KEY_CUSTOM_END, null)
            if (startIso != null && endIso != null) {
                try {
                    val s = LocalDate.parse(startIso)
                    val e = LocalDate.parse(endIso)
                    DateRange.custom(s, e, zoneId)
                } catch (e: Exception) {
                    DateRange.today(zoneId)
                }
            } else {
                DateRange.today(zoneId)
            }
        } else {
            DateRange.fromPreset(preset, zoneId)
        }
    }

    fun saveDateRange(range: DateRange) {
        val editor = prefs.edit()
        editor.putString(KEY_PRESET, range.preset.name)
        if (range.preset == PeriodPreset.CUSTOM) {
            val today = DateTimeUtils.today(range.zoneId)
            val s = minOf(range.startDate, range.endDate)
            val safeStart = if (s.isAfter(today)) today else s
            val safeEnd = minOf(maxOf(range.startDate, range.endDate), today)
            editor.putString(KEY_CUSTOM_START, safeStart.toString())
            editor.putString(KEY_CUSTOM_END, safeEnd.toString())
        }
        editor.apply()
        _selectedRangeFlow.value = range
    }

    companion object {
        private const val KEY_PRESET = "selected_preset"
        private const val KEY_CUSTOM_START = "custom_start_date"
        private const val KEY_CUSTOM_END = "custom_end_date"
    }
}
