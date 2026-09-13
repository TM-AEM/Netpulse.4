package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppRefreshMode
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.DeveloperPreferences
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.ByteUnit
import com.example.model.DailyNetworkUsage
import com.example.model.DataPlanConfig
import com.example.model.DataPlanStatus
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.NetworkUsageSummary
import com.example.ui.screens.DataPlanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NetPulseTheme
import com.example.ui.viewmodel.DataPlanViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataPlanUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var appSettingsPreferences: AppSettingsPreferences
    private lateinit var developerPreferences: DeveloperPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("netpulse_developer_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        appSettingsPreferences = AppSettingsPreferences(context)
        developerPreferences = DeveloperPreferences(context)
    }

    private fun createFakeRepository(
        hasPermission: Boolean = true,
        totalBytes: Long = 0L,
        failWith: Exception? = null
    ): NetworkStatsRepository {
        return object : NetworkStatsRepository {
            override fun hasUsageStatsPermission(): Boolean = hasPermission

            override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> {
                if (failWith != null) {
                    return Result.failure(failWith)
                }
                val wifiUsage = NetworkUsage(
                    downloadBytes = totalBytes / 2,
                    uploadBytes = 0L,
                    startTime = 0L,
                    endTime = 0L,
                    networkType = NetworkType.WIFI
                )
                val mobileUsage = NetworkUsage(
                    downloadBytes = totalBytes - (totalBytes / 2),
                    uploadBytes = 0L,
                    startTime = 0L,
                    endTime = 0L,
                    networkType = NetworkType.MOBILE
                )
                val totalUsage = NetworkUsage(
                    downloadBytes = totalBytes,
                    uploadBytes = 0L,
                    startTime = 0L,
                    endTime = 0L,
                    networkType = NetworkType.TOTAL
                )
                return Result.success(
                    NetworkUsageSummary(
                        wifi = wifiUsage,
                        mobile = mobileUsage,
                        total = totalUsage,
                        dateRange = dateRange
                    )
                )
            }

            override suspend fun getDailyUsageBreakdown(startDate: LocalDate, endDate: LocalDate): Result<List<DailyNetworkUsage>> =
                Result.success(emptyList())

            override suspend fun getDebugInfo(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): Result<NetworkStatsDebugInfo> =
                Result.failure(UnsupportedOperationException())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun disabled_plan_state_displays_disabled_card_and_switch_off() = runTest {
        val repo = createFakeRepository(totalBytes = 0L)
        val viewModel = DataPlanViewModel(repo, appSettingsPreferences, ZoneOffset.UTC)

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            NetPulseTheme {
                DataPlanScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    language = AppLanguage.EN,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("data_plan_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_enable_switch").assertIsOff()
        composeTestRule.onNodeWithTag("data_plan_disabled_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_config_card").assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun enabled_plan_displays_current_cycle_usage_limit_and_remaining() = runTest {
        val limitBytes = 10L * 1024 * 1024 * 1024 // 10 GB
        val usedBytes = 4L * 1024 * 1024 * 1024  // 4 GB
        appSettingsPreferences.setDataPlanConfig(
            DataPlanConfig(
                enabled = true,
                limitBytes = limitBytes,
                billingCycleStartDay = 1,
                unit = ByteUnit.GB
            )
        )

        val repo = createFakeRepository(totalBytes = usedBytes)
        val viewModel = DataPlanViewModel(repo, appSettingsPreferences, ZoneOffset.UTC)

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            NetPulseTheme {
                DataPlanScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    language = AppLanguage.EN,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("data_plan_active_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_cycle_dates").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_used_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_remaining_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_limit_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_progress_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_enable_switch").assertIsOn()

        val activeStatus = viewModel.uiState.value.status as DataPlanStatus.Active
        assertEquals(usedBytes, activeStatus.usedBytes)
        assertEquals(limitBytes, activeStatus.limitBytes)
        assertEquals(limitBytes - usedBytes, activeStatus.remainingBytes)
        assertFalse(activeStatus.isOverLimit)
        assertEquals(0.4f, activeStatus.usageFraction, 0.01f)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun usage_progress_capped_at_100_percent_and_shows_over_limit_banner() = runTest {
        val limitBytes = 10L * 1024 * 1024 * 1024 // 10 GB
        val usedBytes = 15L * 1024 * 1024 * 1024  // 15 GB (5 GB over)
        appSettingsPreferences.setDataPlanConfig(
            DataPlanConfig(
                enabled = true,
                limitBytes = limitBytes,
                billingCycleStartDay = 1,
                unit = ByteUnit.GB
            )
        )

        val repo = createFakeRepository(totalBytes = usedBytes)
        val viewModel = DataPlanViewModel(repo, appSettingsPreferences, ZoneOffset.UTC)

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            NetPulseTheme {
                DataPlanScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    language = AppLanguage.EN,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val activeStatus = viewModel.uiState.value.status as DataPlanStatus.Active
        assertTrue(activeStatus.isOverLimit)
        assertEquals(1.0f, activeStatus.usageFraction, 0.001f) // Capped at 1.0f
        assertEquals(0L, activeStatus.remainingBytes)
        assertEquals(5L * 1024 * 1024 * 1024, activeStatus.excessBytes)

        composeTestRule.onNodeWithTag("data_plan_over_limit_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_excess_text").assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun preference_changes_propagate_to_UI_state() = runTest {
        val repo = createFakeRepository(totalBytes = 2L * 1024 * 1024 * 1024)
        val viewModel = DataPlanViewModel(repo, appSettingsPreferences, ZoneOffset.UTC)

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            NetPulseTheme {
                DataPlanScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    language = AppLanguage.EN,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("data_plan_disabled_card").assertIsDisplayed()

        // Enable plan with 8 GB limit
        appSettingsPreferences.setDataPlanConfig(
            DataPlanConfig(
                enabled = true,
                limitBytes = 8L * 1024 * 1024 * 1024,
                billingCycleStartDay = 1,
                unit = ByteUnit.GB
            )
        )

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("data_plan_active_card").assertIsDisplayed()
        val status = viewModel.uiState.value.status as DataPlanStatus.Active
        assertEquals(8L * 1024 * 1024 * 1024, status.limitBytes)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun permission_unavailable_shows_permission_card() = runTest {
        appSettingsPreferences.setDataPlanConfig(
            DataPlanConfig(
                enabled = true,
                limitBytes = 10L * 1024 * 1024 * 1024,
                billingCycleStartDay = 1,
                unit = ByteUnit.GB
            )
        )

        val repo = createFakeRepository(hasPermission = false)
        val viewModel = DataPlanViewModel(repo, appSettingsPreferences, ZoneOffset.UTC)

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            NetPulseTheme {
                DataPlanScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    language = AppLanguage.EN,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("data_plan_permission_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_grant_permission_button").assertIsDisplayed()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun repository_query_error_shows_error_card_with_retry() = runTest {
        appSettingsPreferences.setDataPlanConfig(
            DataPlanConfig(
                enabled = true,
                limitBytes = 10L * 1024 * 1024 * 1024,
                billingCycleStartDay = 1,
                unit = ByteUnit.GB
            )
        )

        val repo = createFakeRepository(failWith = RuntimeException("Database query failed"))
        val viewModel = DataPlanViewModel(repo, appSettingsPreferences, ZoneOffset.UTC)

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            NetPulseTheme {
                DataPlanScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    language = AppLanguage.EN,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("data_plan_error_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_retry_button").assertIsDisplayed()
    }

    @Test
    fun settings_screen_navigates_to_data_plan() {
        var navigatedToDataPlan = false
        composeTestRule.setContent {
            NetPulseTheme {
                SettingsScreen(
                    appSettingsPreferences = appSettingsPreferences,
                    developerPreferences = developerPreferences,
                    themeMode = AppThemeMode.SYSTEM,
                    language = AppLanguage.EN,
                    dataUnit = AppDataUnit.AUTO,
                    refreshMode = AppRefreshMode.SMART,
                    isDevMode = false,
                    onBack = {},
                    onOpenPrivacyPolicy = {},
                    onNavigateToDataPlan = { navigatedToDataPlan = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("data_plan_settings_item").performScrollTo().performClick()
        assertTrue(navigatedToDataPlan)
    }

    @Test
    fun no_persisted_usage_total_in_shared_preferences() {
        val sp = context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE)

        appSettingsPreferences.setDataPlanConfig(
            DataPlanConfig(
                enabled = true,
                limitBytes = 12L * 1024 * 1024 * 1024,
                billingCycleStartDay = 5,
                unit = ByteUnit.GB
            )
        )

        val allPrefs = sp.all
        assertFalse("No used bytes must be persisted", allPrefs.containsKey("used_bytes"))
        assertFalse("No used bytes must be persisted", allPrefs.containsKey("usedBytes"))
        assertFalse("No remaining bytes must be persisted", allPrefs.containsKey("remaining_bytes"))
        assertFalse("No usage snapshot must be persisted", allPrefs.containsKey("monthly_snapshot"))
        assertFalse("No usage counter must be persisted", allPrefs.containsKey("usage_counter"))
        assertFalse("No total bytes must be persisted", allPrefs.containsKey("total_bytes"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun arabic_rtl_formatting_and_labels() = runTest {
        val limitBytes = 10L * 1024 * 1024 * 1024
        val usedBytes = 3L * 1024 * 1024 * 1024
        appSettingsPreferences.setDataPlanConfig(
            DataPlanConfig(
                enabled = true,
                limitBytes = limitBytes,
                billingCycleStartDay = 1,
                unit = ByteUnit.GB
            )
        )

        val repo = createFakeRepository(totalBytes = usedBytes)
        val viewModel = DataPlanViewModel(repo, appSettingsPreferences, ZoneOffset.UTC)

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            NetPulseTheme {
                DataPlanScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    language = AppLanguage.AR,
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("data_plan_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_active_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_used_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_remaining_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("data_plan_limit_text").assertIsDisplayed()
    }
}
