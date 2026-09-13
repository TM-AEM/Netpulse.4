package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.AppNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.NetworkUsageSummary
import com.example.model.PeriodPreset
import com.example.ui.screens.AppUsageScreen
import com.example.ui.theme.NetPulseTheme
import com.example.ui.viewmodel.AppSortOption
import com.example.ui.viewmodel.AppUsageContentState
import com.example.ui.viewmodel.AppUsageUiState
import com.example.ui.viewmodel.AppUsageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUsageUiAndViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeNetworkStatsRepository(
        var hasPermission: Boolean = true,
        var resultList: List<AppNetworkUsage> = emptyList(),
        var exceptionToThrow: Throwable? = null
    ) : NetworkStatsRepository {
        val queriedRanges = mutableListOf<DateRange>()
        val queriedTypes = mutableListOf<NetworkType>()

        override fun hasUsageStatsPermission(): Boolean = hasPermission

        override suspend fun getAppUsageForRange(
            dateRange: DateRange,
            networkType: NetworkType
        ): Result<List<AppNetworkUsage>> {
            queriedRanges.add(dateRange)
            queriedTypes.add(networkType)
            exceptionToThrow?.let { return Result.failure(it) }
            return Result.success(resultList)
        }

        override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> {
            val zero = NetworkUsage.zero(NetworkType.TOTAL)
            return Result.success(NetworkUsageSummary(zero, zero, zero, dateRange))
        }

        override suspend fun getDailyUsageBreakdown(
            startDate: LocalDate,
            endDate: LocalDate
        ): Result<List<com.example.model.DailyNetworkUsage>> =
            Result.success(emptyList())

        override suspend fun getDebugInfo(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): Result<NetworkStatsDebugInfo> =
            Result.success(
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
                    discrepancyAnalysis = emptyList(),
                    networkType = networkType
                )
            )
    }

    private fun createApp(
        uid: Int,
        name: String,
        packageName: String,
        downloadMb: Long,
        uploadMb: Long
    ): AppNetworkUsage {
        val mb = 1024L * 1024L
        val dlBytes = downloadMb * mb
        val ulBytes = uploadMb * mb
        val totalBytes = dlBytes + ulBytes
        val wifi = NetworkUsage(dlBytes, ulBytes, 0, 1000, NetworkType.WIFI)
        val mobile = NetworkUsage.zero(NetworkType.MOBILE)
        val total = NetworkUsage(dlBytes, ulBytes, 0, 1000, NetworkType.TOTAL)

        return AppNetworkUsage(
            uid = uid,
            packageNames = listOf(packageName),
            displayName = name,
            wifiUsage = wifi,
            mobileUsage = mobile,
            totalUsage = total,
            rxPackets = 100,
            txPackets = 50
        )
    }

    @Test
    fun default_state_loads_apps_with_total_filter_and_total_usage_sort() = runTest(testDispatcher) {
        val app1 = createApp(1001, "Browser", "com.browser", 50, 10)
        val app2 = createApp(1002, "VideoPlayer", "com.video", 200, 20)
        val repository = FakeNetworkStatsRepository(resultList = listOf(app1, app2))

        val initialDateRange = DateRange.today(zoneId)
        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = initialDateRange
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NetworkType.TOTAL, state.selectedFilter)
        assertEquals(AppSortOption.TOTAL_USAGE, state.selectedSort)
        assertTrue(state.contentState is AppUsageContentState.Success)

        val apps = (state.contentState as AppUsageContentState.Success).apps
        assertEquals(2, apps.size)
        // VideoPlayer has 220MB, Browser has 60MB -> VideoPlayer first
        assertEquals(1002, apps[0].uid)
        assertEquals(1001, apps[1].uid)
    }

    @Test
    fun network_filter_switching_queries_correct_network_type() = runTest(testDispatcher) {
        val app = createApp(1001, "TestApp", "com.test", 10, 5)
        val repository = FakeNetworkStatsRepository(resultList = listOf(app))

        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )
        advanceUntilIdle()

        assertEquals(NetworkType.TOTAL, repository.queriedTypes.last())

        viewModel.setNetworkFilter(NetworkType.WIFI)
        advanceUntilIdle()
        assertEquals(NetworkType.WIFI, repository.queriedTypes.last())

        viewModel.setNetworkFilter(NetworkType.MOBILE)
        advanceUntilIdle()
        assertEquals(NetworkType.MOBILE, repository.queriedTypes.last())
    }

    @Test
    fun sorting_determinism_all_options() = runTest(testDispatcher) {
        val appA = createApp(1001, "Alpha", "com.alpha", 100, 10)  // Total: 110, Dl: 100, Ul: 10
        val appB = createApp(1002, "Beta", "com.beta", 20, 200)   // Total: 220, Dl: 20, Ul: 200
        val appC = createApp(1003, "Gamma", "com.gamma", 50, 50)  // Total: 100, Dl: 50, Ul: 50
        val repository = FakeNetworkStatsRepository(resultList = listOf(appA, appB, appC))

        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )
        advanceUntilIdle()

        // 1. Sort by TOTAL_USAGE descending: Beta (220) -> Alpha (110) -> Gamma (100)
        viewModel.setSortOption(AppSortOption.TOTAL_USAGE)
        var apps = (viewModel.uiState.value.contentState as AppUsageContentState.Success).apps
        assertEquals(listOf(1002, 1001, 1003), apps.map { it.uid })

        // 2. Sort by DOWNLOAD descending: Alpha (100) -> Gamma (50) -> Beta (20)
        viewModel.setSortOption(AppSortOption.DOWNLOAD)
        apps = (viewModel.uiState.value.contentState as AppUsageContentState.Success).apps
        assertEquals(listOf(1001, 1003, 1002), apps.map { it.uid })

        // 3. Sort by UPLOAD descending: Beta (200) -> Gamma (50) -> Alpha (10)
        viewModel.setSortOption(AppSortOption.UPLOAD)
        apps = (viewModel.uiState.value.contentState as AppUsageContentState.Success).apps
        assertEquals(listOf(1002, 1003, 1001), apps.map { it.uid })

        // 4. Sort by APP_NAME ascending: Alpha -> Beta -> Gamma
        viewModel.setSortOption(AppSortOption.APP_NAME)
        apps = (viewModel.uiState.value.contentState as AppUsageContentState.Success).apps
        assertEquals(listOf(1001, 1002, 1003), apps.map { it.uid })
    }

    @Test
    fun empty_state_when_no_apps_found() = runTest(testDispatcher) {
        val repository = FakeNetworkStatsRepository(resultList = emptyList())

        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is AppUsageContentState.Empty)
    }

    @Test
    fun permission_error_when_permission_missing() = runTest(testDispatcher) {
        val repository = FakeNetworkStatsRepository(hasPermission = false)

        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is AppUsageContentState.PermissionError)
    }

    @Test
    fun error_state_on_repository_failure() = runTest(testDispatcher) {
        val repository = FakeNetworkStatsRepository(
            exceptionToThrow = RuntimeException("Underlying service error")
        )

        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value.contentState
        assertTrue(state is AppUsageContentState.Error)
        assertEquals("Underlying service error", (state as AppUsageContentState.Error).message)
    }

    @Test
    fun date_navigation_updates_range_and_reloads() = runTest(testDispatcher) {
        val app = createApp(1001, "TestApp", "com.test", 10, 5)
        val repository = FakeNetworkStatsRepository(resultList = listOf(app))

        val fixedToday = LocalDate.of(2026, 9, 13)
        val initialDateRange = DateRange.custom(fixedToday, fixedToday, zoneId)
        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = initialDateRange
        )
        advanceUntilIdle()

        assertEquals(1, repository.queriedRanges.size)
        assertEquals(fixedToday, viewModel.uiState.value.dateRange.startDate)

        // Previous period
        viewModel.navigatePreviousPeriod()
        advanceUntilIdle()

        assertEquals(2, repository.queriedRanges.size)
        assertEquals(fixedToday.minusDays(1), viewModel.uiState.value.dateRange.startDate)

        // Select preset
        viewModel.selectPreset(PeriodPreset.LAST_7_DAYS)
        advanceUntilIdle()

        assertEquals(3, repository.queriedRanges.size)
    }

    @Test
    fun compose_ui_renders_app_usage_screen_success_state() {
        val app1 = createApp(1001, "Chrome", "com.android.chrome", 50, 10)
        val app2 = createApp(1002, "YouTube", "com.google.android.youtube", 120, 15)
        val repository = FakeNetworkStatsRepository(resultList = listOf(app1, app2))

        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )

        val uiState = AppUsageUiState(
            contentState = AppUsageContentState.Success(listOf(app2, app1)),
            dateRange = DateRange.today(zoneId),
            selectedFilter = NetworkType.TOTAL,
            selectedSort = AppSortOption.TOTAL_USAGE,
            language = AppLanguage.EN,
            dataUnit = AppDataUnit.AUTO
        )

        composeTestRule.setContent {
            NetPulseTheme {
                AppUsageScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("app_usage_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_total").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_wifi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_mobile").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sort_selector_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_usage_row_1001").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_usage_row_1002").assertIsDisplayed()
    }

    @Test
    fun compose_ui_renders_empty_state() {
        val repository = FakeNetworkStatsRepository(resultList = emptyList())
        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )

        val uiState = AppUsageUiState(
            contentState = AppUsageContentState.Empty,
            dateRange = DateRange.today(zoneId),
            language = AppLanguage.EN
        )

        composeTestRule.setContent {
            NetPulseTheme {
                AppUsageScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("empty_state").assertIsDisplayed()
    }

    @Test
    fun compose_ui_renders_permission_error_state() {
        val repository = FakeNetworkStatsRepository(hasPermission = false)
        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )

        val uiState = AppUsageUiState(
            contentState = AppUsageContentState.PermissionError(),
            dateRange = DateRange.today(zoneId),
            language = AppLanguage.EN
        )

        composeTestRule.setContent {
            NetPulseTheme {
                AppUsageScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("permission_error_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_settings_button").assertIsDisplayed()
    }

    @Test
    fun compose_ui_renders_arabic_rtl_without_crashing() {
        val app = createApp(1001, "واتساب", "com.whatsapp", 30, 5)
        val repository = FakeNetworkStatsRepository(resultList = listOf(app))
        val viewModel = AppUsageViewModel(
            repository = repository,
            initialDateRange = DateRange.today(zoneId)
        )

        val uiState = AppUsageUiState(
            contentState = AppUsageContentState.Success(listOf(app)),
            dateRange = DateRange.today(zoneId),
            language = AppLanguage.AR
        )

        composeTestRule.setContent {
            NetPulseTheme {
                AppUsageScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("app_usage_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_usage_row_1001").assertIsDisplayed()
    }
}
