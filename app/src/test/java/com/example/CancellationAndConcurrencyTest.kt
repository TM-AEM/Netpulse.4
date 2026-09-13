package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.DateRangePreferences
import com.example.data.preferences.DeveloperPreferences
import com.example.data.repository.NetworkStatsRepositoryImpl
import com.example.data.source.NetworkStatsDataSource
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.DailyNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.NetworkUsageSummary
import com.example.model.PeriodPreset
import com.example.ui.viewmodel.NetworkUsageViewModel
import com.example.util.ConnectionStatus
import com.example.util.ConnectivityObserver
import com.example.util.SmartRefreshManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CancellationAndConcurrencyTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var dateRangePreferences: DateRangePreferences
    private lateinit var appSettingsPreferences: AppSettingsPreferences
    private lateinit var developerPreferences: DeveloperPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        dateRangePreferences = DateRangePreferences(context)
        appSettingsPreferences = AppSettingsPreferences(context)
        developerPreferences = DeveloperPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class DelayableDataSource : NetworkStatsDataSource {
        val gate = CompletableDeferred<Unit>()

        override fun hasUsageStatsPermission(): Boolean = true

        override suspend fun getUsageForNetwork(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): NetworkUsage {
            gate.await()
            return NetworkUsage(500L, 500L, startTimeMs, endTimeMs, networkType)
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

    private class CountingRepository : NetworkStatsRepository {
        var callCount = 0
        var concurrentExecutions = 0
        var maxConcurrentExecutions = 0

        override fun hasUsageStatsPermission(): Boolean = true

        override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> {
            concurrentExecutions++
            if (concurrentExecutions > maxConcurrentExecutions) {
                maxConcurrentExecutions = concurrentExecutions
            }
            callCount++
            kotlinx.coroutines.delay(50)
            concurrentExecutions--
            return Result.success(
                NetworkUsageSummary(
                    wifi = NetworkUsage(100L, 100L, 0L, 1000L, NetworkType.WIFI),
                    mobile = NetworkUsage(200L, 200L, 0L, 1000L, NetworkType.MOBILE),
                    total = NetworkUsage(300L, 300L, 0L, 1000L, NetworkType.TOTAL),
                    dateRange = dateRange
                )
            )
        }

        override suspend fun getDailyUsageBreakdown(
            startDate: LocalDate,
            endDate: LocalDate
        ): Result<List<DailyNetworkUsage>> {
            return Result.success(emptyList())
        }

        override suspend fun getDebugInfo(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): Result<NetworkStatsDebugInfo> {
            return Result.success(
                NetworkStatsDebugInfo(
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
            )
        }
    }

    private class FakeConnectivityObserver(context: Context) : ConnectivityObserver(context) {
        val flow = MutableSharedFlow<ConnectionStatus>(replay = 1)
        override fun observe(): Flow<ConnectionStatus> = flow
    }

    @Test
    fun `CancellationException propagates through Repository without being swallowed`() = runTest {
        val dataSource = DelayableDataSource()
        val repository = NetworkStatsRepositoryImpl(dataSource)
        val range = DateRange.fromPreset(PeriodPreset.TODAY)

        var cancellationThrown = false
        val job = launch {
            try {
                repository.getUsageForRange(range)
            } catch (e: CancellationException) {
                cancellationThrown = true
                throw e
            }
        }

        testScheduler.advanceTimeBy(10)
        job.cancelAndJoin()

        assertTrue("CancellationException must be propagated and rethrown", cancellationThrown)
    }

    @Test
    fun `Multiple rapid refresh requests are serialized and do not execute concurrently`() = runTest {
        val repository = CountingRepository()
        val connectivityObserver = FakeConnectivityObserver(context)
        connectivityObserver.flow.tryEmit(ConnectionStatus.Disconnected)

        val viewModel = NetworkUsageViewModel(
            repository = repository,
            dateRangePreferences = dateRangePreferences,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = connectivityObserver
        )

        // Initial load
        advanceUntilIdle()
        assertEquals(1, repository.callCount)

        // Trigger 3 forced rapid refreshes
        viewModel.refreshData(force = true)
        viewModel.refreshData(force = true)
        viewModel.refreshData(force = true)

        advanceUntilIdle()

        // Because of the Mutex and previousJob join pattern, executions should never overlap
        assertEquals(1, repository.maxConcurrentExecutions)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.summary)
    }

    @Test
    fun `SmartRefreshManager prevents triggering refresh while refresh is active`() {
        val refreshManager = SmartRefreshManager(debounceIntervalMs = 1000L)

        assertTrue(refreshManager.canTriggerRefresh(force = false))

        refreshManager.setRefreshing(true)
        assertFalse("Cannot trigger refresh while in flight", refreshManager.canTriggerRefresh(force = false))
        assertFalse("Even force refresh should be blocked while isRefreshing", refreshManager.canTriggerRefresh(force = true))

        refreshManager.setRefreshing(false)
        refreshManager.recordRefresh()

        // Debounce test
        assertFalse("Debounce should prevent immediate second non-forced refresh", refreshManager.canTriggerRefresh(force = false))
        assertTrue("Force refresh bypasses debounce when not refreshing", refreshManager.canTriggerRefresh(force = true))
    }
}
