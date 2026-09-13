package com.example.data.repository

import com.example.data.resolver.AppInfoResolver
import com.example.data.resolver.FallbackAppInfoResolver
import com.example.data.source.NetworkStatsDataSource
import com.example.domain.repository.LargeDateRangeBreakdownUnavailableException
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.AppNetworkUsage
import com.example.model.DailyNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.NetworkUsageSummary
import com.example.model.RawUidUsageBucket
import com.example.model.UsageInsights
import com.example.util.AppLogger
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate

class NetworkStatsRepositoryImpl(
    private val dataSource: NetworkStatsDataSource,
    private val appInfoResolver: AppInfoResolver? = null
) : NetworkStatsRepository {

    private val resolver: AppInfoResolver = appInfoResolver ?: FallbackAppInfoResolver()

    companion object {
        const val MAX_DAILY_BREAKDOWN_DAYS = 60

        internal fun safeAdd(a: Long, b: Long): Long {
            return try {
                Math.addExact(a, b)
            } catch (e: ArithmeticException) {
                if (a > 0L || b > 0L) Long.MAX_VALUE else Long.MIN_VALUE
            }
        }
    }

    private val tag = "NetworkStatsRepositoryImpl"

    override fun hasUsageStatsPermission(): Boolean {
        return dataSource.hasUsageStatsPermission()
    }

    override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> =
        withContext(Dispatchers.IO) {
            try {
                val startMs = dateRange.getStartEpochMs()
                val endMs = dateRange.getEndEpochMs()
                val summary = querySummaryInternal(startMs, endMs, dateRange)
                Result.success(summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(tag, "Failed to get usage for range ${dateRange.startDate} -> ${dateRange.endDate}", e)
                Result.failure(e)
            }
        }

    private suspend fun querySummaryInternal(
        startMs: Long,
        endMs: Long,
        dateRange: DateRange
    ): NetworkUsageSummary = coroutineScope {
        val wifiDeferred = async {
            dataSource.getUsageForNetwork(NetworkType.WIFI, startMs, endMs)
        }
        val mobileDeferred = async {
            dataSource.getUsageForNetwork(NetworkType.MOBILE, startMs, endMs)
        }

        val wifiUsage = wifiDeferred.await()
        val mobileUsage = mobileDeferred.await()
        val totalUsage = wifiUsage + mobileUsage

        NetworkUsageSummary(
            wifi = wifiUsage,
            mobile = mobileUsage,
            total = totalUsage,
            dateRange = dateRange
        )
    }

    override suspend fun getUsageInsights(dateRange: DateRange): Result<UsageInsights> =
        withContext(Dispatchers.IO) {
            try {
                val previousRange = dateRange.previousPeriod()
                val (currentSummary, previousSummary) = coroutineScope {
                    val currentDeferred = async {
                        querySummaryInternal(
                            dateRange.getStartEpochMs(),
                            dateRange.getEndEpochMs(),
                            dateRange
                        )
                    }
                    val previousDeferred = async {
                        querySummaryInternal(
                            previousRange.getStartEpochMs(),
                            previousRange.getEndEpochMs(),
                            previousRange
                        )
                    }
                    Pair(currentDeferred.await(), previousDeferred.await())
                }

                val insights = UsageInsights.create(
                    dateRange = dateRange,
                    previousDateRange = previousRange,
                    currentSummary = currentSummary,
                    previousSummary = previousSummary
                )
                Result.success(insights)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(tag, "Failed to get usage insights for range ${dateRange.startDate} -> ${dateRange.endDate}", e)
                Result.failure(e)
            }
        }

    override suspend fun getDailyUsageBreakdown(
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<DailyNetworkUsage>> = withContext(Dispatchers.IO) {
        try {
            val zoneId = DateTimeUtils.getLocalZoneId()
            val today = DateTimeUtils.today(zoneId)
            val safeStart = if (startDate.isAfter(today)) today else startDate
            val safeEnd = if (endDate.isAfter(today)) today else endDate

            if (safeStart.isAfter(safeEnd)) {
                return@withContext Result.failure(
                    IllegalArgumentException("startDate ($startDate) cannot be after endDate ($endDate)")
                )
            }

            val days = DateTimeUtils.generateDaysBetween(safeStart, safeEnd)

            // High performance strategy for large ranges:
            // Prevent launching hundreds/thousands of queries while preserving full Range Total.
            if (days.size > MAX_DAILY_BREAKDOWN_DAYS) {
                return@withContext Result.failure(
                    LargeDateRangeBreakdownUnavailableException(
                        "التفصيل اليومي متاح للفترات حتى $MAX_DAILY_BREAKDOWN_DAYS يوماً فقط لحماية أداء الجهاز."
                    )
                )
            }

            val resultList = mutableListOf<DailyNetworkUsage>()
            for (day in days) {
                val (dayStartMs, dayEndMs) = DateTimeUtils.getDayRange(day, zoneId)
                val (wifiUsage, mobileUsage) = coroutineScope {
                    val w = async { dataSource.getUsageForNetwork(NetworkType.WIFI, dayStartMs, dayEndMs) }
                    val m = async { dataSource.getUsageForNetwork(NetworkType.MOBILE, dayStartMs, dayEndMs) }
                    Pair(w.await(), m.await())
                }
                val totalUsage = wifiUsage + mobileUsage
                resultList.add(
                    DailyNetworkUsage(
                        date = day,
                        wifi = wifiUsage,
                        mobile = mobileUsage,
                        total = totalUsage
                    )
                )
            }
            Result.success(resultList.sortedByDescending { it.date })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to get daily usage breakdown from $startDate to $endDate", e)
            Result.failure(e)
        }
    }

    override suspend fun getDebugInfo(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<NetworkStatsDebugInfo> = withContext(Dispatchers.IO) {
        try {
            val info = dataSource.getDebugInfo(networkType, startTimeMs, endTimeMs)
            Result.success(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to get debug info", e)
            Result.failure(e)
        }
    }

    override suspend fun getAppUsageForRange(
        dateRange: DateRange,
        networkType: NetworkType
    ): Result<List<AppNetworkUsage>> = withContext(Dispatchers.IO) {
        try {
            val startMs = dateRange.getStartEpochMs()
            val endMs = dateRange.getEndEpochMs()
            if (startMs > endMs) {
                return@withContext Result.failure(
                    IllegalArgumentException("Start time ($startMs) cannot be after end time ($endMs)")
                )
            }

            val appUsages = when (networkType) {
                NetworkType.WIFI -> {
                    val wifiBuckets = dataSource.getUidUsage(NetworkType.WIFI, startMs, endMs)
                    val wifiMap = aggregateBuckets(wifiBuckets)
                    buildAppUsageListForSingleType(wifiMap, startMs, endMs, NetworkType.WIFI)
                }
                NetworkType.MOBILE -> {
                    val mobileBuckets = dataSource.getUidUsage(NetworkType.MOBILE, startMs, endMs)
                    val mobileMap = aggregateBuckets(mobileBuckets)
                    buildAppUsageListForSingleType(mobileMap, startMs, endMs, NetworkType.MOBILE)
                }
                NetworkType.TOTAL -> {
                    val (wifiBuckets, mobileBuckets) = coroutineScope {
                        val w = async { dataSource.getUidUsage(NetworkType.WIFI, startMs, endMs) }
                        val m = async { dataSource.getUidUsage(NetworkType.MOBILE, startMs, endMs) }
                        Pair(w.await(), m.await())
                    }
                    val wifiMap = aggregateBuckets(wifiBuckets)
                    val mobileMap = aggregateBuckets(mobileBuckets)
                    buildAppUsageListMerged(wifiMap, mobileMap, startMs, endMs)
                }
            }

            val sortedList = appUsages.sortedWith(
                compareByDescending<AppNetworkUsage> { it.totalUsage.totalBytes }
                    .thenBy { it.uid }
            )
            Result.success(sortedList)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(tag, "Failed to get app usage for range ${dateRange.startDate} -> ${dateRange.endDate}", e)
            Result.failure(e)
        }
    }

    private class UidAccumulator(val uid: Int) {
        var rxBytes: Long = 0L
        var txBytes: Long = 0L
        var rxPackets: Long = 0L
        var txPackets: Long = 0L
        var hasRxPackets: Boolean = false
        var hasTxPackets: Boolean = false

        fun addBucket(bucket: RawUidUsageBucket) {
            rxBytes = safeAdd(rxBytes, bucket.rxBytes)
            txBytes = safeAdd(txBytes, bucket.txBytes)
            if (bucket.rxPackets >= 0L) {
                hasRxPackets = true
                rxPackets = safeAdd(rxPackets, bucket.rxPackets)
            }
            if (bucket.txPackets >= 0L) {
                hasTxPackets = true
                txPackets = safeAdd(txPackets, bucket.txPackets)
            }
        }

        val finalRxPackets: Long get() = if (hasRxPackets) rxPackets else -1L
        val finalTxPackets: Long get() = if (hasTxPackets) txPackets else -1L
    }

    private fun aggregateBuckets(buckets: List<RawUidUsageBucket>): Map<Int, UidAccumulator> {
        val map = mutableMapOf<Int, UidAccumulator>()
        for (bucket in buckets) {
            val acc = map.getOrPut(bucket.uid) { UidAccumulator(bucket.uid) }
            acc.addBucket(bucket)
        }
        return map
    }

    private fun buildAppUsageListForSingleType(
        map: Map<Int, UidAccumulator>,
        startMs: Long,
        endMs: Long,
        networkType: NetworkType
    ): List<AppNetworkUsage> {
        val result = mutableListOf<AppNetworkUsage>()
        for ((uid, acc) in map) {
            val rxPackets = acc.finalRxPackets
            val txPackets = acc.finalTxPackets
            val byteUsage = NetworkUsage(
                downloadBytes = acc.rxBytes,
                uploadBytes = acc.txBytes,
                startTime = startMs,
                endTime = endMs,
                networkType = networkType
            )

            val hasBytes = byteUsage.totalBytes > 0L
            val hasPackets = (rxPackets > 0L) || (txPackets > 0L)
            if (!hasBytes && !hasPackets) {
                continue
            }

            val wifiUsage = if (networkType == NetworkType.WIFI) byteUsage else NetworkUsage.zero(NetworkType.WIFI, startMs, endMs)
            val mobileUsage = if (networkType == NetworkType.MOBILE) byteUsage else NetworkUsage.zero(NetworkType.MOBILE, startMs, endMs)
            val totalUsage = byteUsage

            val resolved = resolver.resolveAppInfo(uid)
            result.add(
                AppNetworkUsage(
                    uid = uid,
                    packageNames = resolved.packageNames,
                    displayName = resolved.displayName,
                    wifiUsage = wifiUsage,
                    mobileUsage = mobileUsage,
                    totalUsage = totalUsage,
                    rxPackets = rxPackets,
                    txPackets = txPackets
                )
            )
        }
        return result
    }

    private fun buildAppUsageListMerged(
        wifiMap: Map<Int, UidAccumulator>,
        mobileMap: Map<Int, UidAccumulator>,
        startMs: Long,
        endMs: Long
    ): List<AppNetworkUsage> {
        val result = mutableListOf<AppNetworkUsage>()
        val allUids = (wifiMap.keys + mobileMap.keys).toSet()

        for (uid in allUids) {
            val wifiAcc = wifiMap[uid]
            val mobileAcc = mobileMap[uid]

            val wifiUsage = if (wifiAcc != null) {
                NetworkUsage(
                    downloadBytes = wifiAcc.rxBytes,
                    uploadBytes = wifiAcc.txBytes,
                    startTime = startMs,
                    endTime = endMs,
                    networkType = NetworkType.WIFI
                )
            } else {
                NetworkUsage.zero(NetworkType.WIFI, startMs, endMs)
            }

            val mobileUsage = if (mobileAcc != null) {
                NetworkUsage(
                    downloadBytes = mobileAcc.rxBytes,
                    uploadBytes = mobileAcc.txBytes,
                    startTime = startMs,
                    endTime = endMs,
                    networkType = NetworkType.MOBILE
                )
            } else {
                NetworkUsage.zero(NetworkType.MOBILE, startMs, endMs)
            }

            val totalUsage = wifiUsage + mobileUsage

            val combinedRxPackets = when {
                wifiAcc?.hasRxPackets == true && mobileAcc?.hasRxPackets == true -> safeAdd(wifiAcc.rxPackets, mobileAcc.rxPackets)
                wifiAcc?.hasRxPackets == true -> wifiAcc.rxPackets
                mobileAcc?.hasRxPackets == true -> mobileAcc.rxPackets
                else -> -1L
            }

            val combinedTxPackets = when {
                wifiAcc?.hasTxPackets == true && mobileAcc?.hasTxPackets == true -> safeAdd(wifiAcc.txPackets, mobileAcc.txPackets)
                wifiAcc?.hasTxPackets == true -> wifiAcc.txPackets
                mobileAcc?.hasTxPackets == true -> mobileAcc.txPackets
                else -> -1L
            }

            val hasBytes = totalUsage.totalBytes > 0L
            val hasPackets = (combinedRxPackets > 0L) || (combinedTxPackets > 0L)
            if (!hasBytes && !hasPackets) {
                continue
            }

            val resolved = resolver.resolveAppInfo(uid)
            result.add(
                AppNetworkUsage(
                    uid = uid,
                    packageNames = resolved.packageNames,
                    displayName = resolved.displayName,
                    wifiUsage = wifiUsage,
                    mobileUsage = mobileUsage,
                    totalUsage = totalUsage,
                    rxPackets = combinedRxPackets,
                    txPackets = combinedTxPackets
                )
            )
        }
        return result
    }
}
