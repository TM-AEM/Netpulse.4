package com.example.domain.repository

import com.example.model.AppNetworkUsage
import com.example.model.DailyNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsageSummary
import com.example.model.UsageInsights
import java.time.LocalDate

class LargeDateRangeBreakdownUnavailableException(message: String) : Exception(message)

interface NetworkStatsRepository {
    fun hasUsageStatsPermission(): Boolean
    suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary>
    suspend fun getDailyUsageBreakdown(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyNetworkUsage>>
    suspend fun getAppUsageForRange(
        dateRange: DateRange,
        networkType: NetworkType = NetworkType.TOTAL
    ): Result<List<AppNetworkUsage>> = Result.success(emptyList())
    suspend fun getDebugInfo(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<NetworkStatsDebugInfo>
    suspend fun getUsageInsights(dateRange: DateRange): Result<UsageInsights> {
        val previousRange = dateRange.previousPeriod()
        val currentRes = getUsageForRange(dateRange)
        val prevRes = getUsageForRange(previousRange)
        if (currentRes.isFailure) return Result.failure(currentRes.exceptionOrNull() ?: Exception("Failed to load current usage"))
        if (prevRes.isFailure) return Result.failure(prevRes.exceptionOrNull() ?: Exception("Failed to load previous usage"))
        return Result.success(
            UsageInsights.create(
                dateRange = dateRange,
                previousDateRange = previousRange,
                currentSummary = currentRes.getOrThrow(),
                previousSummary = prevRes.getOrThrow()
            )
        )
    }
}
