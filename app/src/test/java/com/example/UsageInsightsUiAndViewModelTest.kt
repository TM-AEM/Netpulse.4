package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppSettingsPreferences
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.AppNetworkUsage
import com.example.model.DailyAverageUsage
import com.example.model.DailyNetworkUsage
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.NetworkUsageComparison
import com.example.model.NetworkUsageSummary
import com.example.model.PercentageChange
import com.example.model.PeriodPreset
import com.example.model.TrendDirection
import com.example.model.UsageInsights
import com.example.model.UsageMetricComparison
import com.example.ui.screens.UsageInsightsScreen
import com.example.ui.theme.NetPulseTheme
import com.example.ui.viewmodel.UsageInsightsContentState
import com.example.ui.viewmodel.UsageInsightsUiState
import com.example.ui.viewmodel.UsageInsightsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class UsageInsightsUiAndViewModelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("UTC")
    private lateinit var context: Context
    private lateinit var appSettingsPreferences: AppSettingsPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        appSettingsPreferences = AppSettingsPreferences(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeUsageInsightsRepository(
        var hasPermission: Boolean = true,
        var insightsResult: Result<UsageInsights>? = null,
        var delayDeferred: CompletableDeferred<Unit>? = null
    ) : NetworkStatsRepository {
        val queriedRanges = mutableListOf<DateRange>()

        override fun hasUsageStatsPermission(): Boolean = hasPermission

        override suspend fun getUsageInsights(dateRange: DateRange): Result<UsageInsights> {
            queriedRanges.add(dateRange)
            delayDeferred?.await()
            return insightsResult ?: Result.success(createSampleInsights(dateRange))
        }

        override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> {
            val zero = NetworkUsage.zero(NetworkType.TOTAL)
            return Result.success(NetworkUsageSummary(zero, zero, zero, dateRange))
        }

        override suspend fun getDailyUsageBreakdown(
            startDate: LocalDate,
            endDate: LocalDate
        ): Result<List<DailyNetworkUsage>> = Result.success(emptyList())

        override suspend fun getAppUsageForRange(
            dateRange: DateRange,
            networkType: NetworkType
        ): Result<List<AppNetworkUsage>> = Result.success(emptyList())

        override suspend fun getDebugInfo(
            networkType: NetworkType,
            startTimeMs: Long,
            endTimeMs: Long
        ): Result<NetworkStatsDebugInfo> = Result.failure(UnsupportedOperationException())
    }

    companion object {
        fun createSampleInsights(
            dateRange: DateRange,
            currentTotal: Long = 1000L,
            currentWifi: Long = 600L,
            currentMobile: Long = 400L,
            prevTotal: Long = 800L,
            prevWifi: Long = 500L,
            prevMobile: Long = 300L
        ): UsageInsights {
            val prevRange = dateRange.previousPeriod()

            val curDownload = currentTotal / 2
            val curUpload = currentTotal - curDownload
            val curTotalUsage = NetworkUsage(curDownload, curUpload, 0L, 0L, NetworkType.TOTAL)

            val curWifiDownload = currentWifi / 2
            val curWifiUpload = currentWifi - curWifiDownload
            val curWifiUsage = NetworkUsage(curWifiDownload, curWifiUpload, 0L, 0L, NetworkType.WIFI)

            val curMobileDownload = currentMobile / 2
            val curMobileUpload = currentMobile - curMobileDownload
            val curMobileUsage = NetworkUsage(curMobileDownload, curMobileUpload, 0L, 0L, NetworkType.MOBILE)

            val prevDownload = prevTotal / 2
            val prevUpload = prevTotal - prevDownload
            val prevTotalUsage = NetworkUsage(prevDownload, prevUpload, 0L, 0L, NetworkType.TOTAL)

            val prevWifiDownload = prevWifi / 2
            val prevWifiUpload = prevWifi - prevWifiDownload
            val prevWifiUsage = NetworkUsage(prevWifiDownload, prevWifiUpload, 0L, 0L, NetworkType.WIFI)

            val prevMobileDownload = prevMobile / 2
            val prevMobileUpload = prevMobile - prevMobileDownload
            val prevMobileUsage = NetworkUsage(prevMobileDownload, prevMobileUpload, 0L, 0L, NetworkType.MOBILE)

            val curSummary = NetworkUsageSummary(curWifiUsage, curMobileUsage, curTotalUsage, dateRange)
            val prevSummary = NetworkUsageSummary(prevWifiUsage, prevMobileUsage, prevTotalUsage, prevRange)

            val days = (java.time.temporal.ChronoUnit.DAYS.between(dateRange.startDate, dateRange.endDate) + 1).toInt()

            val totalComp = NetworkUsageComparison.create(curTotalUsage, prevTotalUsage, days)
            val wifiComp = NetworkUsageComparison.create(curWifiUsage, prevWifiUsage, days)
            val mobileComp = NetworkUsageComparison.create(curMobileUsage, prevMobileUsage, days)

            return UsageInsights(
                dateRange = dateRange,
                previousDateRange = prevRange,
                currentSummary = curSummary,
                previousSummary = prevSummary,
                total = totalComp,
                wifi = wifiComp,
                mobile = mobileComp
            )
        }
    }

    // 1. ViewModel: Successful Total insights
    @Test
    fun `viewModel loads and produces Success state for Total insights`() = runTest(testDispatcher) {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val fakeRepo = FakeUsageInsightsRepository()
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NetworkType.TOTAL, state.selectedFilter)
        assertTrue("State should be Success", state.contentState is UsageInsightsContentState.Success)
        val success = state.contentState as UsageInsightsContentState.Success
        assertEquals(1000L, success.insights.total.currentUsage.totalBytes)
        assertEquals(1, fakeRepo.queriedRanges.size)
    }

    // 2. ViewModel: Wi-Fi filter selection
    @Test
    fun `viewModel setNetworkFilter to WIFI updates selectedFilter and activeComparison`() = runTest(testDispatcher) {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val fakeRepo = FakeUsageInsightsRepository()
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)
        advanceUntilIdle()

        viewModel.setNetworkFilter(NetworkType.WIFI)

        val state = viewModel.uiState.value
        assertEquals(NetworkType.WIFI, state.selectedFilter)
        assertNotNull(state.activeComparison)
        assertEquals(600L, state.activeComparison?.currentUsage?.totalBytes)
    }

    // 3. ViewModel: Mobile filter selection
    @Test
    fun `viewModel setNetworkFilter to MOBILE updates selectedFilter and activeComparison`() = runTest(testDispatcher) {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val fakeRepo = FakeUsageInsightsRepository()
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)
        advanceUntilIdle()

        viewModel.setNetworkFilter(NetworkType.MOBILE)

        val state = viewModel.uiState.value
        assertEquals(NetworkType.MOBILE, state.selectedFilter)
        assertNotNull(state.activeComparison)
        assertEquals(400L, state.activeComparison?.currentUsage?.totalBytes)
    }

    // 4. ViewModel: Loading state
    @Test
    fun `viewModel exhibits Loading state before repository completes`() = runTest(testDispatcher) {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val deferred = CompletableDeferred<Unit>()
        val fakeRepo = FakeUsageInsightsRepository(delayDeferred = deferred)
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)

        assertEquals(UsageInsightsContentState.Loading, viewModel.uiState.value.contentState)

        deferred.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is UsageInsightsContentState.Success)
    }

    // 5. ViewModel: Permission required state
    @Test
    fun `viewModel sets PermissionRequired state when permission is absent`() = runTest(testDispatcher) {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val fakeRepo = FakeUsageInsightsRepository(hasPermission = false)
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is UsageInsightsContentState.PermissionRequired)
    }

    // 6. ViewModel: Error state
    @Test
    fun `viewModel sets Error state when repository returns failure`() = runTest(testDispatcher) {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val fakeRepo = FakeUsageInsightsRepository(insightsResult = Result.failure(IllegalStateException("Network query failed")))
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is UsageInsightsContentState.Error)
        val errorState = viewModel.uiState.value.contentState as UsageInsightsContentState.Error
        assertEquals("Network query failed", errorState.message)
    }

    // 7. ViewModel: Zero-usage state
    @Test
    fun `viewModel sets ZeroUsage state when current total usage is zero`() = runTest(testDispatcher) {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val zeroInsights = createSampleInsights(
            dateRange = range,
            currentTotal = 0L,
            currentWifi = 0L,
            currentMobile = 0L,
            prevTotal = 0L,
            prevWifi = 0L,
            prevMobile = 0L
        )
        val fakeRepo = FakeUsageInsightsRepository(insightsResult = Result.success(zeroInsights))
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is UsageInsightsContentState.ZeroUsage)
        assertTrue(viewModel.uiState.value.isZeroUsage)
    }

    // 8. ViewModel: Date range navigation triggers refresh
    @Test
    fun `viewModel selectPreset or navigate triggers data reload for new range`() = runTest(testDispatcher) {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val fakeRepo = FakeUsageInsightsRepository()
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)
        advanceUntilIdle()

        assertEquals(1, fakeRepo.queriedRanges.size)

        viewModel.navigatePreviousPeriod()
        advanceUntilIdle()

        assertEquals(2, fakeRepo.queriedRanges.size)
        assertEquals(LocalDate.of(2026, 5, 25), fakeRepo.queriedRanges[1].startDate)
        assertEquals(LocalDate.of(2026, 5, 31), fakeRepo.queriedRanges[1].endDate)
    }

    // 9. UI: Compose test displaying Success state cards
    @Test
    fun `usageInsightsScreen displays current period, daily average, and comparison cards`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val insights = createSampleInsights(range, currentTotal = 1024L * 1024L * 100L, prevTotal = 1024L * 1024L * 80L)
        val fakeRepo = FakeUsageInsightsRepository(insightsResult = Result.success(insights))
        val viewModel = UsageInsightsViewModel(fakeRepo, initialDateRange = range, appSettingsPreferences = appSettingsPreferences)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = viewModel,
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        selectedFilter = NetworkType.TOTAL,
                        contentState = UsageInsightsContentState.Success(insights),
                        language = AppLanguage.EN
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("usage_insights_screen").assertExists()
        composeTestRule.onNodeWithTag("current_period_card").assertExists()
        composeTestRule.onNodeWithTag("current_total_metric").assertExists()
        composeTestRule.onNodeWithTag("daily_average_card").assertExists()
        composeTestRule.onNodeWithTag("daily_avg_total_metric").assertExists()
        composeTestRule.onNodeWithTag("comparison_section").assertExists()
        composeTestRule.onNodeWithTag("comparison_card_total").assertExists()
        composeTestRule.onNodeWithTag("total_trend_indicator").assertExists()
    }

    // 10. UI: Percentage defined and increase direction presentation
    @Test
    fun `trend indicator displays increase and positive percentage change`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        // 100MB current vs 80MB prev = +25.0% Increase
        val insights = createSampleInsights(range, currentTotal = 100L, prevTotal = 80L)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = UsageInsightsViewModel(FakeUsageInsightsRepository(), initialDateRange = range),
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        selectedFilter = NetworkType.TOTAL,
                        contentState = UsageInsightsContentState.Success(insights),
                        language = AppLanguage.EN
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("total_trend_indicator").assertExists()
        composeTestRule.onNodeWithTag("total_pct_change", useUnmergedTree = true).assertTextEquals("(+25.0%)")
    }

    // 11. UI: Percentage defined and decrease direction presentation
    @Test
    fun `trend indicator displays decrease and negative percentage change`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        // 50MB current vs 100MB prev = -50.0% Decrease
        val insights = createSampleInsights(range, currentTotal = 50L, prevTotal = 100L)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = UsageInsightsViewModel(FakeUsageInsightsRepository(), initialDateRange = range),
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        selectedFilter = NetworkType.TOTAL,
                        contentState = UsageInsightsContentState.Success(insights),
                        language = AppLanguage.EN
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("total_trend_indicator").assertExists()
        composeTestRule.onNodeWithTag("total_pct_change", useUnmergedTree = true).assertTextEquals("(-50.0%)")
    }

    // 12. UI: Percentage undefined presentation (previous = 0, current > 0)
    @Test
    fun `trend indicator displays NA when percentage is undefined`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        // 100MB current vs 0 prev = Undefined percentage
        val insights = createSampleInsights(range, currentTotal = 100L, prevTotal = 0L)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = UsageInsightsViewModel(FakeUsageInsightsRepository(), initialDateRange = range),
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        selectedFilter = NetworkType.TOTAL,
                        contentState = UsageInsightsContentState.Success(insights),
                        language = AppLanguage.EN
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("total_pct_change", useUnmergedTree = true).assertTextEquals("(N/A)")
    }

    // 13. UI: Loading state displays circular indicator
    @Test
    fun `usageInsightsScreen displays loading indicator when state is Loading`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = UsageInsightsViewModel(FakeUsageInsightsRepository(), initialDateRange = range),
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        contentState = UsageInsightsContentState.Loading,
                        language = AppLanguage.EN
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("insights_loading_indicator").assertExists()
    }

    // 14. UI: Permission required state displays permission cards
    @Test
    fun `usageInsightsScreen displays permission required card when permission is needed`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = UsageInsightsViewModel(FakeUsageInsightsRepository(), initialDateRange = range),
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        contentState = UsageInsightsContentState.PermissionRequired(),
                        language = AppLanguage.EN
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("insights_permission_state").assertExists()
        composeTestRule.onNodeWithTag("open_settings_button").assertExists()
        composeTestRule.onNodeWithTag("retry_permission_button").assertExists()
    }

    // 15. UI: Error state displays error card with retry button
    @Test
    fun `usageInsightsScreen displays error card when state is Error`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = UsageInsightsViewModel(FakeUsageInsightsRepository(), initialDateRange = range),
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        contentState = UsageInsightsContentState.Error("Network error occurred"),
                        language = AppLanguage.EN
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("insights_error_state").assertExists()
        composeTestRule.onNodeWithTag("insights_retry_button").assertExists()
    }

    // 16. UI: Zero usage state displays zero usage card
    @Test
    fun `usageInsightsScreen displays zero usage card when state is ZeroUsage`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val zeroInsights = createSampleInsights(range, currentTotal = 0L, prevTotal = 0L)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = UsageInsightsViewModel(FakeUsageInsightsRepository(), initialDateRange = range),
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        contentState = UsageInsightsContentState.ZeroUsage(zeroInsights),
                        language = AppLanguage.EN
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("insights_zero_usage_card").assertExists()
    }

    // 17. UI: Arabic localization renders Arabic percentage and labels
    @Test
    fun `usageInsightsScreen in Arabic renders without crashing and displays Arabic N-A for undefined pct`() {
        val range = DateRange.custom(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7), zoneId)
        val insights = createSampleInsights(range, currentTotal = 100L, prevTotal = 0L)

        composeTestRule.setContent {
            NetPulseTheme {
                UsageInsightsScreen(
                    viewModel = UsageInsightsViewModel(FakeUsageInsightsRepository(), initialDateRange = range),
                    uiState = UsageInsightsUiState(
                        dateRange = range,
                        selectedFilter = NetworkType.TOTAL,
                        contentState = UsageInsightsContentState.Success(insights),
                        language = AppLanguage.AR
                    ),
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("usage_insights_screen").assertExists()
        composeTestRule.onNodeWithTag("total_pct_change", useUnmergedTree = true).assertTextEquals("(غير متاح)")
    }
}
