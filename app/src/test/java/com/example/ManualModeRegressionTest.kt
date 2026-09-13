package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppRefreshMode
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.DateRangePreferences
import com.example.data.preferences.DeveloperPreferences
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ManualModeRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var appSettingsPreferences: AppSettingsPreferences
    private lateinit var dateRangePreferences: DateRangePreferences
    private lateinit var developerPreferences: DeveloperPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        appSettingsPreferences = AppSettingsPreferences(context)
        dateRangePreferences = DateRangePreferences(context)
        developerPreferences = DeveloperPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepository : NetworkStatsRepository {
        var hasPermission: Boolean = true
        var getUsageForRangeCallCount = 0
        var getDailyUsageBreakdownCallCount = 0

        override fun hasUsageStatsPermission(): Boolean = hasPermission

        override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> {
            getUsageForRangeCallCount++
            return Result.success(
                NetworkUsageSummary(
                    wifi = NetworkUsage(100L, 200L, 0L, 1000L, NetworkType.WIFI),
                    mobile = NetworkUsage(300L, 400L, 0L, 1000L, NetworkType.MOBILE),
                    total = NetworkUsage(400L, 600L, 0L, 1000L, NetworkType.TOTAL),
                    dateRange = dateRange
                )
            )
        }

        override suspend fun getDailyUsageBreakdown(
            startDate: LocalDate,
            endDate: LocalDate
        ): Result<List<DailyNetworkUsage>> {
            getDailyUsageBreakdownCallCount++
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
    fun `initial required load works as expected`() = runTest(testDispatcher) {
        val fakeRepo = FakeRepository().apply { hasPermission = true }
        val fakeConnectivity = FakeConnectivityObserver(context)
        fakeConnectivity.flow.tryEmit(ConnectionStatus.Disconnected)

        val viewModel = NetworkUsageViewModel(
            repository = fakeRepo,
            dateRangePreferences = dateRangePreferences,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = fakeConnectivity
        )

        advanceUntilIdle()

        assertEquals(1, fakeRepo.getUsageForRangeCallCount)
        assertNotNull(viewModel.uiState.value.summary)
        assertTrue(viewModel.uiState.value.isPermissionGranted)
    }

    @Test
    fun `manual mode plus ON_RESUME plus stale data does NOT trigger automatic refresh`() = runTest(testDispatcher) {
        appSettingsPreferences.setRefreshMode(AppRefreshMode.MANUAL)

        val fakeRepo = FakeRepository().apply { hasPermission = true }
        val fakeConnectivity = FakeConnectivityObserver(context)
        fakeConnectivity.flow.tryEmit(ConnectionStatus.Disconnected)

        val viewModel = NetworkUsageViewModel(
            repository = fakeRepo,
            dateRangePreferences = dateRangePreferences,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = fakeConnectivity
        )

        advanceUntilIdle()
        assertEquals("Initial load must occur", 1, fakeRepo.getUsageForRangeCallCount)
        assertEquals(AppRefreshMode.MANUAL, viewModel.uiState.value.refreshMode)

        // Simulate ON_RESUME after data became stale
        viewModel.onLifecycleResume()
        advanceUntilIdle()

        // Call count MUST remain 1 (no automatic refresh in MANUAL mode)
        assertEquals("ON_RESUME in MANUAL mode must not trigger auto-refresh", 1, fakeRepo.getUsageForRangeCallCount)
    }

    @Test
    fun `manual mode plus connectivity restored does NOT trigger automatic refresh`() = runTest(testDispatcher) {
        appSettingsPreferences.setRefreshMode(AppRefreshMode.MANUAL)

        val fakeRepo = FakeRepository().apply { hasPermission = true }
        val fakeConnectivity = FakeConnectivityObserver(context)
        fakeConnectivity.flow.tryEmit(ConnectionStatus.Disconnected)

        val viewModel = NetworkUsageViewModel(
            repository = fakeRepo,
            dateRangePreferences = dateRangePreferences,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = fakeConnectivity
        )

        advanceUntilIdle()
        assertEquals(1, fakeRepo.getUsageForRangeCallCount)
        assertEquals(AppRefreshMode.MANUAL, viewModel.uiState.value.refreshMode)

        // Connectivity recovery
        fakeConnectivity.flow.tryEmit(
            ConnectionStatus(
                isConnected = true,
                networkType = NetworkType.WIFI,
                isMetered = false,
                labelAr = "متصل عبر Wi-Fi",
                labelEn = "Connected to Wi-Fi"
            )
        )
        advanceUntilIdle()

        // Call count MUST remain 1
        assertEquals("Connectivity restoration in MANUAL mode must not trigger auto-refresh", 1, fakeRepo.getUsageForRangeCallCount)
    }

    @Test
    fun `manual refresh explicitly requested by user works in manual mode`() = runTest(testDispatcher) {
        appSettingsPreferences.setRefreshMode(AppRefreshMode.MANUAL)

        val fakeRepo = FakeRepository().apply { hasPermission = true }
        val fakeConnectivity = FakeConnectivityObserver(context)
        fakeConnectivity.flow.tryEmit(ConnectionStatus.Disconnected)

        val viewModel = NetworkUsageViewModel(
            repository = fakeRepo,
            dateRangePreferences = dateRangePreferences,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = fakeConnectivity
        )

        advanceUntilIdle()
        assertEquals(1, fakeRepo.getUsageForRangeCallCount)

        // Explicit user manual refresh
        viewModel.refreshData(force = true)
        advanceUntilIdle()

        assertEquals("Manual refresh requested by user must succeed", 2, fakeRepo.getUsageForRangeCallCount)
    }

    @Test
    fun `date range change works in manual mode`() = runTest(testDispatcher) {
        appSettingsPreferences.setRefreshMode(AppRefreshMode.MANUAL)

        val fakeRepo = FakeRepository().apply { hasPermission = true }
        val fakeConnectivity = FakeConnectivityObserver(context)
        fakeConnectivity.flow.tryEmit(ConnectionStatus.Disconnected)

        val viewModel = NetworkUsageViewModel(
            repository = fakeRepo,
            dateRangePreferences = dateRangePreferences,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = fakeConnectivity
        )

        advanceUntilIdle()
        assertEquals(1, fakeRepo.getUsageForRangeCallCount)

        // Change Date Range preset
        viewModel.selectPreset(PeriodPreset.THIS_MONTH)
        advanceUntilIdle()

        assertEquals("Date range change must trigger reload", 2, fakeRepo.getUsageForRangeCallCount)
        assertEquals(PeriodPreset.THIS_MONTH, viewModel.uiState.value.dateRange.preset)
    }

    @Test
    fun `load after usage access is granted works`() = runTest(testDispatcher) {
        val fakeRepo = FakeRepository().apply { hasPermission = false }
        val fakeConnectivity = FakeConnectivityObserver(context)
        fakeConnectivity.flow.tryEmit(ConnectionStatus.Disconnected)

        val viewModel = NetworkUsageViewModel(
            repository = fakeRepo,
            dateRangePreferences = dateRangePreferences,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = fakeConnectivity
        )

        advanceUntilIdle()
        assertEquals(0, fakeRepo.getUsageForRangeCallCount)
        assertFalse(viewModel.uiState.value.isPermissionGranted)

        // User grants permission in system settings and returns
        fakeRepo.hasPermission = true
        viewModel.onLifecycleResume()
        advanceUntilIdle()

        assertEquals("Load after granting usage permission must occur", 1, fakeRepo.getUsageForRangeCallCount)
        assertTrue(viewModel.uiState.value.isPermissionGranted)
        assertNotNull(viewModel.uiState.value.summary)
    }
}
