package com.example.model

import java.time.temporal.ChronoUnit

/**
 * Direction of usage change between two periods.
 */
enum class TrendDirection {
    INCREASE,
    DECREASE,
    UNCHANGED,
    UNAVAILABLE;

    val isIncrease: Boolean get() = this == INCREASE
    val isDecrease: Boolean get() = this == DECREASE
    val isUnchanged: Boolean get() = this == UNCHANGED
    val isUnavailable: Boolean get() = this == UNAVAILABLE
}

/**
 * Explicit representation of percentage change.
 * When previous usage is 0 and current usage > 0, percentage change is mathematically
 * undefined (division by zero) and represented as [Undefined].
 */
sealed class PercentageChange {
    abstract val isDefined: Boolean
    abstract val valueOrNull: Double?

    data class Defined(val percentage: Double) : PercentageChange() {
        override val isDefined: Boolean get() = true
        override val valueOrNull: Double get() = percentage
    }

    data object Undefined : PercentageChange() {
        override val isDefined: Boolean get() = false
        override val valueOrNull: Double? get() = null
    }

    fun format(decimals: Int = 1): String {
        return when (this) {
            is Defined -> {
                val sign = if (percentage > 0.0) "+" else ""
                String.format(java.util.Locale.US, "%s%.${decimals}f%%", sign, percentage)
            }
            is Undefined -> "—"
        }
    }

    companion object {
        fun of(current: Long, previous: Long): PercentageChange {
            if (previous == 0L) {
                return if (current == 0L) Defined(0.0) else Undefined
            }
            val diff = safeSubtract(current, previous)
            val percent = (diff.toDouble() / previous.toDouble()) * 100.0
            return Defined(percent)
        }

        private fun safeSubtract(a: Long, b: Long): Long {
            return try {
                Math.subtractExact(a, b)
            } catch (e: ArithmeticException) {
                if (a > b) Long.MAX_VALUE else Long.MIN_VALUE
            }
        }
    }
}

/**
 * Average daily usage metrics for a specific period.
 * Byte calculations strictly use 64-bit signed integer (Long) arithmetic with no floating-point rounding.
 */
data class DailyAverageUsage(
    val downloadBytes: Long,
    val uploadBytes: Long,
    val totalBytes: Long,
    val daysCount: Int
) {
    companion object {
        fun from(usage: NetworkUsage, daysCount: Int): DailyAverageUsage {
            val safeDays = maxOf(1, daysCount)
            return DailyAverageUsage(
                downloadBytes = usage.downloadBytes / safeDays,
                uploadBytes = usage.uploadBytes / safeDays,
                totalBytes = usage.totalBytes / safeDays,
                daysCount = safeDays
            )
        }

        fun zero(daysCount: Int = 1): DailyAverageUsage = DailyAverageUsage(
            downloadBytes = 0L,
            uploadBytes = 0L,
            totalBytes = 0L,
            daysCount = maxOf(1, daysCount)
        )
    }
}

/**
 * Metric-level comparison between current and previous period.
 * Protects all arithmetic from overflow.
 */
data class UsageMetricComparison(
    val currentBytes: Long,
    val previousBytes: Long,
    val absoluteDifference: Long,
    val percentageChange: PercentageChange,
    val direction: TrendDirection
) {
    val signedDifference: Long get() = safeSubtract(currentBytes, previousBytes)

    companion object {
        fun create(currentBytes: Long, previousBytes: Long): UsageMetricComparison {
            val absDiff = safeAbsDifference(currentBytes, previousBytes)
            val dir = when {
                currentBytes > previousBytes -> TrendDirection.INCREASE
                currentBytes < previousBytes -> TrendDirection.DECREASE
                else -> TrendDirection.UNCHANGED
            }
            val pct = PercentageChange.of(currentBytes, previousBytes)
            return UsageMetricComparison(
                currentBytes = currentBytes,
                previousBytes = previousBytes,
                absoluteDifference = absDiff,
                percentageChange = pct,
                direction = dir
            )
        }

        fun unavailable(currentBytes: Long): UsageMetricComparison {
            return UsageMetricComparison(
                currentBytes = currentBytes,
                previousBytes = 0L,
                absoluteDifference = currentBytes,
                percentageChange = PercentageChange.Undefined,
                direction = TrendDirection.UNAVAILABLE
            )
        }

        internal fun safeSubtract(a: Long, b: Long): Long {
            return try {
                Math.subtractExact(a, b)
            } catch (e: ArithmeticException) {
                if (a > b) Long.MAX_VALUE else Long.MIN_VALUE
            }
        }

        internal fun safeAbsDifference(a: Long, b: Long): Long {
            val max = maxOf(a, b)
            val min = minOf(a, b)
            return try {
                Math.subtractExact(max, min)
            } catch (e: ArithmeticException) {
                Long.MAX_VALUE
            }
        }
    }
}

/**
 * Comprehensive usage comparison for a specific network interface (TOTAL, WIFI, or MOBILE).
 */
data class NetworkUsageComparison(
    val networkType: NetworkType,
    val currentUsage: NetworkUsage,
    val previousUsage: NetworkUsage,
    val totalComparison: UsageMetricComparison,
    val downloadComparison: UsageMetricComparison,
    val uploadComparison: UsageMetricComparison,
    val averageDailyUsage: DailyAverageUsage
) {
    companion object {
        fun create(
            current: NetworkUsage,
            previous: NetworkUsage,
            daysCount: Int
        ): NetworkUsageComparison {
            return NetworkUsageComparison(
                networkType = current.networkType,
                currentUsage = current,
                previousUsage = previous,
                totalComparison = UsageMetricComparison.create(current.totalBytes, previous.totalBytes),
                downloadComparison = UsageMetricComparison.create(current.downloadBytes, previous.downloadBytes),
                uploadComparison = UsageMetricComparison.create(current.uploadBytes, previous.uploadBytes),
                averageDailyUsage = DailyAverageUsage.from(current, daysCount)
            )
        }
    }
}

/**
 * Immutable domain model representing complete usage insights:
 * current period, previous period comparison, daily averages, and trends across TOTAL, WIFI, and MOBILE.
 */
data class UsageInsights(
    val dateRange: DateRange,
    val previousDateRange: DateRange,
    val currentSummary: NetworkUsageSummary,
    val previousSummary: NetworkUsageSummary,
    val total: NetworkUsageComparison,
    val wifi: NetworkUsageComparison,
    val mobile: NetworkUsageComparison
) {
    val daysCount: Int get() = total.averageDailyUsage.daysCount

    fun forNetworkType(networkType: NetworkType): NetworkUsageComparison = when (networkType) {
        NetworkType.TOTAL -> total
        NetworkType.WIFI -> wifi
        NetworkType.MOBILE -> mobile
    }

    companion object {
        fun create(
            dateRange: DateRange,
            previousDateRange: DateRange,
            currentSummary: NetworkUsageSummary,
            previousSummary: NetworkUsageSummary
        ): UsageInsights {
            val daysCount = ChronoUnit.DAYS.between(dateRange.startDate, dateRange.endDate).toInt() + 1
            return UsageInsights(
                dateRange = dateRange,
                previousDateRange = previousDateRange,
                currentSummary = currentSummary,
                previousSummary = previousSummary,
                total = NetworkUsageComparison.create(currentSummary.total, previousSummary.total, daysCount),
                wifi = NetworkUsageComparison.create(currentSummary.wifi, previousSummary.wifi, daysCount),
                mobile = NetworkUsageComparison.create(currentSummary.mobile, previousSummary.mobile, daysCount)
            )
        }
    }
}
