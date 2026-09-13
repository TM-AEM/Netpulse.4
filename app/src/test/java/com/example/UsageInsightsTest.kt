package com.example

import com.example.data.repository.NetworkStatsRepositoryImpl
import com.example.data.source.NetworkStatsDataSource
import com.example.model.DateRange
import com.example.model.DailyAverageUsage
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.PercentageChange
import com.example.model.RawUidUsageBucket
import com.example.model.TrendDirection
import com.example.model.UsageMetricComparison
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class UsageInsightsTest {

    private val fixedZone = ZoneId.of("UTC")
    // Fixed instant: 2026-06-15 12:00:00 UTC
    private val fixedInstant = Instant.parse("2026-06-15T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, fixedZone)

    @Before
    fun setUp() {
        DateTimeUtils.setClockForTesting(fixedClock)
    }

    @After
    fun tearDown() {
        DateTimeUtils.resetClockForTesting()
    }

    // 1. Single-day average
    @Test
    fun `single-day average returns exact values without alteration`() {
        val usage = NetworkUsage(
            downloadBytes = 10_000L,
            uploadBytes = 5_000L,
            startTime = 1000L,
            endTime = 2000L,
            networkType = NetworkType.TOTAL
        )
        val avg = DailyAverageUsage.from(usage, daysCount = 1)

        assertEquals(10_000L, avg.downloadBytes)
        assertEquals(5_000L, avg.uploadBytes)
        assertEquals(15_000L, avg.totalBytes)
        assertEquals(1, avg.daysCount)
    }

    // 2. 7-day average
    @Test
    fun `7-day average correctly divides totals by 7`() {
        val usage = NetworkUsage(
            downloadBytes = 70_000L,
            uploadBytes = 35_000L,
            startTime = 1000L,
            endTime = 2000L,
            networkType = NetworkType.TOTAL
        )
        val avg = DailyAverageUsage.from(usage, daysCount = 7)

        assertEquals(10_000L, avg.downloadBytes)
        assertEquals(5_000L, avg.uploadBytes)
        assertEquals(15_000L, avg.totalBytes)
        assertEquals(7, avg.daysCount)
    }

    // 3. Zero usage
    @Test
    fun `zero usage handles averages and trends gracefully`() {
        val zeroUsage = NetworkUsage.zero(NetworkType.TOTAL)
        val avg = DailyAverageUsage.from(zeroUsage, daysCount = 14)

        assertEquals(0L, avg.downloadBytes)
        assertEquals(0L, avg.uploadBytes)
        assertEquals(0L, avg.totalBytes)
        assertEquals(14, avg.daysCount)

        val comparison = UsageMetricComparison.create(currentBytes = 0L, previousBytes = 0L)
        assertEquals(0L, comparison.currentBytes)
        assertEquals(0L, comparison.previousBytes)
        assertEquals(0L, comparison.absoluteDifference)
        assertEquals(TrendDirection.UNCHANGED, comparison.direction)
        assertTrue(comparison.percentageChange.isDefined)
        assertEquals(0.0, comparison.percentageChange.valueOrNull!!, 0.001)
    }

    // 4. Previous-period date calculation
    @Test
    fun `previous-period date calculation matches preceding window exactly`() {
        // Selected: Jan 10 – Jan 16 (7 days) -> previous: Jan 3 – Jan 9 (7 days)
        val current = DateRange.custom(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 16), fixedZone)
        val previous = current.previousPeriod()

        assertEquals(LocalDate.of(2026, 1, 3), previous.startDate)
        assertEquals(LocalDate.of(2026, 1, 9), previous.endDate)
        assertEquals(
            ChronoUnit.DAYS.between(current.startDate, current.endDate),
            ChronoUnit.DAYS.between(previous.startDate, previous.endDate)
        )
    }

    // 5. Month boundary
    @Test
    fun `previous-period date calculation correctly handles month boundary and leap year`() {
        // Non-leap year March boundary: March 1, 2025 to March 7, 2025 (7 days)
        val marchRange = DateRange.custom(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 7), fixedZone)
        val prevMarch = marchRange.previousPeriod()
        assertEquals(LocalDate.of(2025, 2, 22), prevMarch.startDate)
        assertEquals(LocalDate.of(2025, 2, 28), prevMarch.endDate)

        // Leap year March boundary: March 1, 2024 to March 7, 2024 (7 days)
        val leapMarchRange = DateRange.custom(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7), fixedZone)
        val prevLeapMarch = leapMarchRange.previousPeriod()
        assertEquals(LocalDate.of(2024, 2, 23), prevLeapMarch.startDate)
        assertEquals(LocalDate.of(2024, 2, 29), prevLeapMarch.endDate)

        // Single-day month boundary
        val singleDayStartOfMonth = DateRange.custom(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1), fixedZone)
        val prevSingleDay = singleDayStartOfMonth.previousPeriod()
        assertEquals(LocalDate.of(2026, 4, 30), prevSingleDay.startDate)
        assertEquals(LocalDate.of(2026, 4, 30), prevSingleDay.endDate)
    }

    // 6. Year boundary
    @Test
    fun `previous-period date calculation correctly handles year boundary`() {
        val startOfYear = DateRange.custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), fixedZone)
        val prevYearRange = startOfYear.previousPeriod()

        assertEquals(LocalDate.of(2025, 12, 25), prevYearRange.startDate)
        assertEquals(LocalDate.of(2025, 12, 31), prevYearRange.endDate)

        val singleDayJanFirst = DateRange.custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), fixedZone)
        val prevSingleDay = singleDayJanFirst.previousPeriod()
        assertEquals(LocalDate.of(2025, 12, 31), prevSingleDay.startDate)
        assertEquals(LocalDate.of(2025, 12, 31), prevSingleDay.endDate)
    }

    // 7. Current > previous
    @Test
    fun `current greater than previous produces INCREASE and positive percentage`() {
        val comparison = UsageMetricComparison.create(currentBytes = 300L, previousBytes = 200L)

        assertEquals(300L, comparison.currentBytes)
        assertEquals(200L, comparison.previousBytes)
        assertEquals(100L, comparison.absoluteDifference)
        assertEquals(100L, comparison.signedDifference)
        assertEquals(TrendDirection.INCREASE, comparison.direction)
        assertTrue(comparison.direction.isIncrease)
        assertFalse(comparison.direction.isDecrease)
        assertTrue(comparison.percentageChange.isDefined)
        assertEquals(50.0, comparison.percentageChange.valueOrNull!!, 0.001)
        assertEquals("+50.0%", comparison.percentageChange.format(1))
    }

    // 8. Current < previous
    @Test
    fun `current less than previous produces DECREASE and negative percentage`() {
        val comparison = UsageMetricComparison.create(currentBytes = 100L, previousBytes = 400L)

        assertEquals(100L, comparison.currentBytes)
        assertEquals(400L, comparison.previousBytes)
        assertEquals(300L, comparison.absoluteDifference)
        assertEquals(-300L, comparison.signedDifference)
        assertEquals(TrendDirection.DECREASE, comparison.direction)
        assertTrue(comparison.direction.isDecrease)
        assertTrue(comparison.percentageChange.isDefined)
        assertEquals(-75.0, comparison.percentageChange.valueOrNull!!, 0.001)
        assertEquals("-75.0%", comparison.percentageChange.format(1))
    }

    // 9. Equal values
    @Test
    fun `equal values produce UNCHANGED and zero percentage change`() {
        val comparison = UsageMetricComparison.create(currentBytes = 500L, previousBytes = 500L)

        assertEquals(500L, comparison.currentBytes)
        assertEquals(500L, comparison.previousBytes)
        assertEquals(0L, comparison.absoluteDifference)
        assertEquals(0L, comparison.signedDifference)
        assertEquals(TrendDirection.UNCHANGED, comparison.direction)
        assertTrue(comparison.direction.isUnchanged)
        assertTrue(comparison.percentageChange.isDefined)
        assertEquals(0.0, comparison.percentageChange.valueOrNull!!, 0.001)
        assertEquals("0.0%", comparison.percentageChange.format(1))
    }

    // 10. Previous = 0 and current > 0
    @Test
    fun `previous zero and current positive produces undefined percentage change and INCREASE`() {
        val comparison = UsageMetricComparison.create(currentBytes = 500L, previousBytes = 0L)

        assertEquals(500L, comparison.currentBytes)
        assertEquals(0L, comparison.previousBytes)
        assertEquals(500L, comparison.absoluteDifference)
        assertEquals(500L, comparison.signedDifference)
        assertEquals(TrendDirection.INCREASE, comparison.direction)
        assertTrue(comparison.direction.isIncrease)
        assertFalse(comparison.percentageChange.isDefined)
        assertNull(comparison.percentageChange.valueOrNull)
        assertTrue(comparison.percentageChange is PercentageChange.Undefined)
        assertEquals("—", comparison.percentageChange.format(1))
    }

    // 11. Both zero
    @Test
    fun `both zero produces UNCHANGED and exactly 0 percent change`() {
        val comparison = UsageMetricComparison.create(currentBytes = 0L, previousBytes = 0L)

        assertEquals(0L, comparison.currentBytes)
        assertEquals(0L, comparison.previousBytes)
        assertEquals(0L, comparison.absoluteDifference)
        assertEquals(0L, comparison.signedDifference)
        assertEquals(TrendDirection.UNCHANGED, comparison.direction)
        assertTrue(comparison.percentageChange.isDefined)
        assertEquals(0.0, comparison.percentageChange.valueOrNull!!, 0.0001)
        assertEquals("0.0%", comparison.percentageChange.format(1))
    }

    // 12. Overflow-safe calculations
    @Test
    fun `overflow-safe arithmetic does not throw and handles extreme bounds`() {
        val extremeCurrent = UsageMetricComparison.create(currentBytes = Long.MAX_VALUE, previousBytes = 0L)
        assertEquals(Long.MAX_VALUE, extremeCurrent.absoluteDifference)
        assertEquals(TrendDirection.INCREASE, extremeCurrent.direction)
        assertTrue(extremeCurrent.percentageChange is PercentageChange.Undefined)

        val extremePrevious = UsageMetricComparison.create(currentBytes = 0L, previousBytes = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, extremePrevious.absoluteDifference)
        assertEquals(TrendDirection.DECREASE, extremePrevious.direction)
        assertTrue(extremePrevious.percentageChange.isDefined)
        assertEquals(-100.0, extremePrevious.percentageChange.valueOrNull!!, 0.001)

        val extremeBoth = UsageMetricComparison.create(currentBytes = Long.MAX_VALUE, previousBytes = Long.MAX_VALUE)
        assertEquals(0L, extremeBoth.absoluteDifference)
        assertEquals(TrendDirection.UNCHANGED, extremeBoth.direction)
        assertEquals(0.0, extremeBoth.percentageChange.valueOrNull!!, 0.001)

        // Safe add and safe subtract bounds
        assertEquals(Long.MAX_VALUE, UsageMetricComparison.safeSubtract(Long.MAX_VALUE, -100L))
        assertEquals(Long.MIN_VALUE, UsageMetricComparison.safeSubtract(Long.MIN_VALUE, 100L))
    }

    // 13. WIFI/MOBILE/TOTAL consistency
    @Test
    fun `repository getUsageInsights preserves Total equals Wifi plus Mobile consistency`() = runTest {
        val fakeDataSource = FakeNetworkDataSource(
            wifiCurrentRx = 2000L, wifiCurrentTx = 500L,
            mobileCurrentRx = 3000L, mobileCurrentTx = 1500L,
            wifiPrevRx = 1000L, wifiPrevTx = 200L,
            mobilePrevRx = 4000L, mobilePrevTx = 1800L
        )
        val repository = NetworkStatsRepositoryImpl(fakeDataSource)
        val dateRange = DateRange.custom(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 15), fixedZone)

        val result = repository.getUsageInsights(dateRange)
        assertTrue(result.isSuccess)
        val insights = result.getOrThrow()

        // 7 days in range
        assertEquals(7, insights.daysCount)
        assertEquals(dateRange, insights.dateRange)
        assertEquals(dateRange.previousPeriod(), insights.previousDateRange)

        // Wi-Fi: current download = 2000, upload = 500, total = 2500
        assertEquals(2000L, insights.wifi.downloadComparison.currentBytes)
        assertEquals(500L, insights.wifi.uploadComparison.currentBytes)
        assertEquals(2500L, insights.wifi.totalComparison.currentBytes)

        // Mobile: current download = 3000, upload = 1500, total = 4500
        assertEquals(3000L, insights.mobile.downloadComparison.currentBytes)
        assertEquals(1500L, insights.mobile.uploadComparison.currentBytes)
        assertEquals(4500L, insights.mobile.totalComparison.currentBytes)

        // TOTAL current: download = 2000 + 3000 = 5000, upload = 500 + 1500 = 2000, total = 7000
        assertEquals(
            insights.wifi.downloadComparison.currentBytes + insights.mobile.downloadComparison.currentBytes,
            insights.total.downloadComparison.currentBytes
        )
        assertEquals(
            insights.wifi.uploadComparison.currentBytes + insights.mobile.uploadComparison.currentBytes,
            insights.total.uploadComparison.currentBytes
        )
        assertEquals(
            insights.wifi.totalComparison.currentBytes + insights.mobile.totalComparison.currentBytes,
            insights.total.totalComparison.currentBytes
        )

        // TOTAL previous: wifi(1200) + mobile(5800) = 7000
        assertEquals(
            insights.wifi.totalComparison.previousBytes + insights.mobile.totalComparison.previousBytes,
            insights.total.totalComparison.previousBytes
        )

        // Daily average for TOTAL: 7000 / 7 = 1000
        assertEquals(1000L, insights.total.averageDailyUsage.totalBytes)
        assertEquals(5000L / 7, insights.total.averageDailyUsage.downloadBytes)
        assertEquals(2000L / 7, insights.total.averageDailyUsage.uploadBytes)

        // forNetworkType accessor check
        assertEquals(insights.total, insights.forNetworkType(NetworkType.TOTAL))
        assertEquals(insights.wifi, insights.forNetworkType(NetworkType.WIFI))
        assertEquals(insights.mobile, insights.forNetworkType(NetworkType.MOBILE))
    }

    // 14. Concurrency & Cancellation
    @Test
    fun `getUsageInsights respects coroutine cancellation`() = runTest {
        val blockingDataSource = BlockingDataSource()
        val repository = NetworkStatsRepositoryImpl(blockingDataSource)
        val dateRange = DateRange.custom(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 15), fixedZone)

        val job = launch(Dispatchers.IO) {
            repository.getUsageInsights(dateRange)
        }

        blockingDataSource.queryStarted.await()
        job.cancelAndJoin()

        assertTrue("Job must be cancelled", job.isCancelled)
    }

    private class FakeNetworkDataSource(
        val wifiCurrentRx: Long, val wifiCurrentTx: Long,
        val mobileCurrentRx: Long, val mobileCurrentTx: Long,
        val wifiPrevRx: Long, val wifiPrevTx: Long,
        val mobilePrevRx: Long, val mobilePrevTx: Long
    ) : NetworkStatsDataSource {
        override fun hasUsageStatsPermission(): Boolean = true

        override suspend fun getUsageForNetwork(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): NetworkUsage {
            val midPoint = DateTimeUtils.getStartOfDayEpochMs(LocalDate.of(2026, 6, 9), ZoneId.of("UTC"))
            val isCurrent = startTimeMs >= midPoint

            val (rx, tx) = when (networkType) {
                NetworkType.WIFI -> if (isCurrent) Pair(wifiCurrentRx, wifiCurrentTx) else Pair(wifiPrevRx, wifiPrevTx)
                NetworkType.MOBILE -> if (isCurrent) Pair(mobileCurrentRx, mobileCurrentTx) else Pair(mobilePrevRx, mobilePrevTx)
                NetworkType.TOTAL -> throw IllegalArgumentException("DataSource query must specify WIFI or MOBILE")
            }
            return NetworkUsage(
                downloadBytes = rx,
                uploadBytes = tx,
                startTime = startTimeMs,
                endTime = endTimeMs,
                networkType = networkType
            )
        }

        override suspend fun getUidUsage(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): List<RawUidUsageBucket> = emptyList()

        override suspend fun getDebugInfo(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): NetworkStatsDebugInfo {
            throw UnsupportedOperationException("Not required for usage insights test")
        }
    }

    private class BlockingDataSource : NetworkStatsDataSource {
        val queryStarted = CompletableDeferred<Unit>()

        override fun hasUsageStatsPermission(): Boolean = true

        override suspend fun getUsageForNetwork(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): NetworkUsage {
            queryStarted.complete(Unit)
            kotlinx.coroutines.delay(10_000)
            return NetworkUsage.zero(networkType, startTimeMs, endTimeMs)
        }

        override suspend fun getUidUsage(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): List<RawUidUsageBucket> = emptyList()

        override suspend fun getDebugInfo(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): NetworkStatsDebugInfo {
            throw UnsupportedOperationException("Not required for usage insights test")
        }
    }
}
