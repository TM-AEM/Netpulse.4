package com.example.model

import com.example.data.preferences.AppLanguage
import com.example.util.DateTimeUtils
import java.time.LocalDate
import java.time.ZoneId

data class DateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val preset: PeriodPreset = PeriodPreset.CUSTOM,
    val zoneId: ZoneId = DateTimeUtils.getLocalZoneId()
) {
    init {
        require(!startDate.isAfter(endDate)) {
            "startDate ($startDate) cannot be after endDate ($endDate)"
        }
        val today = DateTimeUtils.today(zoneId)
        require(!startDate.isAfter(today)) {
            "startDate ($startDate) cannot be in the future (today is $today)"
        }
        require(!endDate.isAfter(today)) {
            "endDate ($endDate) cannot be in the future (today is $today)"
        }
    }

    val isSingleDay: Boolean get() = startDate == endDate

    val isCurrentDay: Boolean get() {
        val today = DateTimeUtils.today(zoneId)
        return isSingleDay && startDate == today
    }

    fun getStartEpochMs(): Long = DateTimeUtils.getStartOfDayEpochMs(startDate, zoneId)

    fun getEndEpochMs(): Long = DateTimeUtils.getEndOfDayEpochMs(endDate, zoneId)

    fun previousPeriod(): DateRange {
        val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1
        val newEnd = startDate.minusDays(1)
        val newStart = newEnd.minusDays(days - 1)
        return DateRange(newStart, newEnd, PeriodPreset.CUSTOM, zoneId)
    }

    fun nextPeriod(): DateRange {
        val today = DateTimeUtils.today(zoneId)
        if (!endDate.isBefore(today)) {
            return this
        }
        val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1
        val newStart = endDate.plusDays(1)
        if (newStart.isAfter(today)) {
            return this
        }
        val candidateEnd = newStart.plusDays(days - 1)
        val newEnd = if (candidateEnd.isAfter(today)) today else candidateEnd
        return DateRange(newStart, newEnd, PeriodPreset.CUSTOM, zoneId)
    }

    fun formatDisplay(language: AppLanguage): String {
        return if (isSingleDay) {
            if (language == AppLanguage.AR) {
                DateTimeUtils.formatDateArabic(startDate)
            } else {
                DateTimeUtils.formatDateEnglish(startDate)
            }
        } else {
            if (language == AppLanguage.AR) {
                "${DateTimeUtils.formatDateArabic(startDate)} - ${DateTimeUtils.formatDateArabic(endDate)}"
            } else {
                "${DateTimeUtils.formatDateEnglish(startDate)} - ${DateTimeUtils.formatDateEnglish(endDate)}"
            }
        }
    }

    companion object {
        fun today(zoneId: ZoneId = DateTimeUtils.getLocalZoneId()): DateRange {
            val now = DateTimeUtils.today(zoneId)
            return DateRange(now, now, PeriodPreset.TODAY, zoneId)
        }

        fun custom(
            startDate: LocalDate,
            endDate: LocalDate,
            zoneId: ZoneId = DateTimeUtils.getLocalZoneId()
        ): DateRange {
            val today = DateTimeUtils.today(zoneId)
            val s = minOf(startDate, endDate)
            val e = maxOf(startDate, endDate)
            val safeStart = if (s.isAfter(today)) today else s
            val safeEnd = if (e.isAfter(today)) today else e
            return DateRange(safeStart, safeEnd, PeriodPreset.CUSTOM, zoneId)
        }

        fun fromPreset(preset: PeriodPreset, zoneId: ZoneId = DateTimeUtils.getLocalZoneId()): DateRange {
            val today = DateTimeUtils.today(zoneId)
            return when (preset) {
                PeriodPreset.TODAY -> DateRange(today, today, preset, zoneId)
                PeriodPreset.YESTERDAY -> {
                    val y = today.minusDays(1)
                    DateRange(y, y, preset, zoneId)
                }
                PeriodPreset.LAST_7_DAYS -> DateRange(today.minusDays(6), today, preset, zoneId)
                PeriodPreset.LAST_30_DAYS -> DateRange(today.minusDays(29), today, preset, zoneId)
                PeriodPreset.THIS_MONTH -> {
                    val firstDay = today.withDayOfMonth(1)
                    DateRange(firstDay, today, preset, zoneId)
                }
                PeriodPreset.CUSTOM -> DateRange(today, today, preset, zoneId)
            }
        }
    }
}
