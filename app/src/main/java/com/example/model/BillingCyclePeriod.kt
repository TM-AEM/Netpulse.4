package com.example.model

import com.example.util.DateTimeUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Represents the calendar interval for a billing cycle.
 * [startDate] is inclusive start day of the current cycle.
 * [endDate] is inclusive last day of the current cycle.
 * [nextCycleStartDate] is inclusive start day of the following cycle ([endDate] + 1).
 */
data class BillingCyclePeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val nextCycleStartDate: LocalDate,
    val zoneId: ZoneId
) {
    /**
     * Converts to a DateRange bounded by [today] (defaulting to current date in [zoneId])
     * for safely querying NetworkStatsManager without violating DateRange future-date preconditions.
     */
    fun toQueryDateRange(today: LocalDate = DateTimeUtils.today(zoneId)): DateRange {
        val safeStart = if (startDate.isAfter(today)) today else startDate
        val safeEnd = if (endDate.isAfter(today)) today else endDate
        return DateRange.custom(safeStart, safeEnd, zoneId)
    }
}

/**
 * Pure calculation logic for billing cycles with short-month handling and timezone awareness.
 */
object BillingCycleCalculator {

    /**
     * Calculates the billing cycle period containing [referenceDate] (defaulting to today in [zoneId])
     * for a given [billingCycleStartDay] (1..31).
     *
     * Deterministic short-month handling:
     * If a month has fewer days than [billingCycleStartDay], the cycle begins on the last day of that month.
     * For example, if billingDay is 31:
     * - February (non-leap year, 28 days): cycle starts Feb 28.
     * - April (30 days): cycle starts April 30.
     */
    fun calculateCurrentPeriod(
        billingCycleStartDay: Int,
        referenceDate: LocalDate? = null,
        zoneId: ZoneId = DateTimeUtils.getLocalZoneId()
    ): BillingCyclePeriod {
        val safeBillingDay = billingCycleStartDay.coerceIn(
            DataPlanConfig.MIN_BILLING_CYCLE_START_DAY,
            DataPlanConfig.MAX_BILLING_CYCLE_START_DAY
        )
        val date = referenceDate ?: DateTimeUtils.today(zoneId)
        val currentYearMonth = YearMonth.from(date)

        val cycleDateThisMonth = getEffectiveDateInMonth(currentYearMonth, safeBillingDay)

        val cycleStart: LocalDate
        val nextCycleStart: LocalDate

        if (!date.isBefore(cycleDateThisMonth)) {
            // Today is on or after cycleDateThisMonth -> cycle started in this month
            cycleStart = cycleDateThisMonth
            val nextYearMonth = currentYearMonth.plusMonths(1)
            nextCycleStart = getEffectiveDateInMonth(nextYearMonth, safeBillingDay)
        } else {
            // Today is before cycleDateThisMonth -> cycle started in previous month
            val prevYearMonth = currentYearMonth.minusMonths(1)
            cycleStart = getEffectiveDateInMonth(prevYearMonth, safeBillingDay)
            nextCycleStart = cycleDateThisMonth
        }

        val endDate = nextCycleStart.minusDays(1)

        return BillingCyclePeriod(
            startDate = cycleStart,
            endDate = endDate,
            nextCycleStartDate = nextCycleStart,
            zoneId = zoneId
        )
    }

    private fun getEffectiveDateInMonth(yearMonth: YearMonth, billingDay: Int): LocalDate {
        val daysInMonth = yearMonth.lengthOfMonth()
        val effectiveDay = minOf(billingDay, daysInMonth)
        return yearMonth.atDay(effectiveDay)
    }
}
