package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.DateRangePreferences
import com.example.data.preferences.DeveloperPreferences
import com.example.data.repository.NetworkStatsRepositoryImpl
import com.example.data.source.NetworkStatsDataSource
import com.example.domain.repository.LargeDateRangeBreakdownUnavailableException
import com.example.model.DailyNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.ui.viewmodel.DailyBreakdownUiState
import com.example.ui.viewmodel.NetworkUsageViewModel
import com.example.util.ConnectionStatus
import com.example.util.ConnectivityObserver
import com.example.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DailyBreakdownAndTotalIndependenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedZone = ZoneId.of("UTC")
    private val fixedInstant = Instant.parse("2024-06-15T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, fixedZone)

    private lateinit var context: Context
    private lateinit var dateRangePreferences: DateRangePreferences
    private lateinit var appSettingsPreferences: AppSettingsPreferences
    private lateinit var developerPreferences: DeveloperPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        DateTimeUtils.setClockForTesting(fixedClock)
        context = ApplicationProvider.getApplicationContext()
        dateRangePreferences = DateRangePreferences(context)
        appSettingsPreferences = AppSettingsPreferences(context)
        developerPreferences = DeveloperPreferences(context)
    }

    @After
    fun tearDown() {
        DateTimeUtils.resetClockForTesting()
        Dispatchers.resetMain()
    }

    private class MockDataSource : NetworkStatsDataSource {
        var callCount = 0
        var permissionGranted = true

        override fun hasUsageStatsPermission(): Boolean = permissionGranted

        override suspend fun getUsageForNetwork(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): NetworkUsage {
            callCount++
            val factor = if (networkType == NetworkType.WIFI) 2L else 1L
            return NetworkUsage(
                downloadBytes = 1000L * factor,
                uploadBytes = 500L * factor,
                startTime = startTimeMs,
                endTime = endTimeMs,
                networkType = networkType
            )
        }

        override suspend fun getDebugInfo(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): NetworkStatsDebugInfo {
            return NetworkStatsDebugInfo(
                queryStartMs = startTimeMs,
                queryEndMs = endTimeMs,
                queryStartFormatted = "",
                queryEndFormatted = "",
                rawRxBytes = 0L,
                rawTxBytes = 0L,
                rawTotalBytes = 0L,
                rawRxPackets = 0L,
                rawTxPackets = 0L,
                trafficStatsBootTotalBytes = 0L,
                trafficStatsComparisonNote = "",
                detailedBuckets = emptyList(),
                discrepancyAnalysis = emptyList()
            )
        }
    }

    private class FakeConnectivityObserver(context: Context) : ConnectivityObserver(context) {
        val flow = MutableSharedFlow<ConnectionStatus>(replay = 1)
        override fun observe(): Flow<ConnectionStatus> = flow
    }

    @Test
    fun `getUsageForRange executes exactly 2 queries independently of range length`() = runTest {
        val mockDataSource = MockDataSource()
        val repository = NetworkStatsRepositoryImpl(mockDataSource)

        // Range of 180 days
        val range = DateRange(
            startDate = LocalDate.of(2023, 12, 1),
            endDate = LocalDate.of(2024, 5, 28),
            zoneId = fixedZone
        )

        val result = repository.getUsageForRange(range)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()
        assertNotNull(summary)
        assertEquals(2000L, summary!!.wifi.downloadBytes)
        assertEquals(1000L, summary.mobile.downloadBytes)
        assertEquals(3000L, summary.total.downloadBytes)

        // Strictly 2 IPC calls: 1 for WIFI, 1 for MOBILE
        assertEquals(2, mockDataSource.callCount)
    }

    @Test
    fun `daily breakdown enforces 60-day cap and succeeds for ranges up to 60 days`() = runTest {
        val mockDataSource = MockDataSource()
        val repository = NetworkStatsRepositoryImpl(mockDataSource)

        val start60 = LocalDate.of(2024, 4, 17)
        val end60 = LocalDate.of(2024, 6, 15) // Exactly 60 days (4/17 to 6/15 inclusive)
        val daysCount = DateTimeUtils.generateDaysBetween(start60, end60).size
        assertEquals(60, daysCount)

        val result60 = repository.getDailyUsageBreakdown(start60, end60)
        assertTrue(result60.isSuccess)
        assertEquals(60, result60.getOrNull()?.size)

        // 61 days should fail with LargeDateRangeBreakdownUnavailableException
        val start61 = LocalDate.of(2024, 4, 16)
        val result61 = repository.getDailyUsageBreakdown(start61, end60)
        assertTrue(result61.isFailure)
        assertTrue(result61.exceptionOrNull() is LargeDateRangeBreakdownUnavailableException)
    }

    @Test
    fun `Total calculation succeeds even when daily breakdown is unavailable for large range`() = runTest {
        val mockDataSource = MockDataSource()
        val repository = NetworkStatsRepositoryImpl(mockDataSource)

        // 1-year date range (365 days)
        val oneYearRange = DateRange(
            startDate = LocalDate.of(2023, 6, 16),
            endDate = LocalDate.of(2024, 6, 15),
            zoneId = fixedZone
        )

        val totalResult = repository.getUsageForRange(oneYearRange)
        val breakdownResult = repository.getDailyUsageBreakdown(oneYearRange.startDate, oneYearRange.endDate)

        // Total MUST succeed
        assertTrue(totalResult.isSuccess)
        assertNotNull(totalResult.getOrNull()?.total)

        // Breakdown MUST report LargeDateRangeBreakdownUnavailableException
        assertTrue(breakdownResult.isFailure)
        assertTrue(breakdownResult.exceptionOrNull() is LargeDateRangeBreakdownUnavailableException)
    }

    @Test
    fun `ViewModel preserves Total usage when daily breakdown fails with large range notice`() = runTest(testDispatcher) {
        val mockDataSource = MockDataSource()
        val repository = NetworkStatsRepositoryImpl(mockDataSource)
        val connectivityObserver = FakeConnectivityObserver(context)
        connectivityObserver.flow.tryEmit(ConnectionStatus.Disconnected)

        // Save a large range > 60 days
        val largeRange = DateRange(
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 6, 15),
            zoneId = fixedZone
        )
        dateRangePreferences.saveDateRange(largeRange)

        val viewModel = NetworkUsageViewModel(
            repository = repository,
            dateRangePreferences = dateRangePreferences,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = connectivityObserver
        )

        advanceUntilIdle()
        var retries = 0
        while (viewModel.uiState.value.isLoading && retries < 50) {
            Thread.sleep(20)
            advanceUntilIdle()
            retries++
        }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)

        // Total summary MUST be present and valid
        assertNotNull(state.summary)
        assertEquals(3000L, state.summary?.total?.downloadBytes)

        // Daily breakdown state MUST be LargeRangeNotice, not a fatal error
        assertTrue(state.dailyBreakdownState is DailyBreakdownUiState.LargeRangeNotice)
    }

    @Test
    fun `Daily breakdown returns failure when startDate is after endDate`() = runTest {
        val mockDataSource = MockDataSource()
        val repository = NetworkStatsRepositoryImpl(mockDataSource)

        val later = LocalDate.of(2024, 6, 10)
        val earlier = LocalDate.of(2024, 6, 1)

        val result = repository.getDailyUsageBreakdown(later, earlier)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
