package com.example

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.NetworkUsageViewModel
import com.example.util.ConnectivityObserver
import com.example.util.ConnectionStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsInteractionRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var appSettingsPreferences: AppSettingsPreferences
    private lateinit var developerPreferences: DeveloperPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("netpulse_developer_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        appSettingsPreferences = AppSettingsPreferences(context)
        developerPreferences = DeveloperPreferences(context)
    }

    @Test
    fun theme_item_opens_its_selection_dialog() {
        composeTestRule.setContent {
            val themeMode by appSettingsPreferences.themeMode.collectAsState()
            val language by appSettingsPreferences.language.collectAsState()
            val dataUnit by appSettingsPreferences.dataUnit.collectAsState()
            val refreshMode by appSettingsPreferences.refreshMode.collectAsState()
            val isDevMode by developerPreferences.isDeveloperModeEnabled.collectAsState()

            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = themeMode,
                language = language,
                dataUnit = dataUnit,
                refreshMode = refreshMode,
                isDevMode = isDevMode,
                onBack = {},
                onOpenPrivacyPolicy = {}
            )
        }

        composeTestRule.onNodeWithTag("theme_settings_item").performClick()
        composeTestRule.onNodeWithTag("option_selection_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_item_DARK").assertIsDisplayed()
    }

    @Test
    fun selecting_a_different_theme_updates_the_displayed_selected_option_and_state() {
        composeTestRule.setContent {
            val themeMode by appSettingsPreferences.themeMode.collectAsState()
            val language by appSettingsPreferences.language.collectAsState()
            val dataUnit by appSettingsPreferences.dataUnit.collectAsState()
            val refreshMode by appSettingsPreferences.refreshMode.collectAsState()
            val isDevMode by developerPreferences.isDeveloperModeEnabled.collectAsState()

            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = themeMode,
                language = language,
                dataUnit = dataUnit,
                refreshMode = refreshMode,
                isDevMode = isDevMode,
                onBack = {},
                onOpenPrivacyPolicy = {}
            )
        }

        assertEquals(AppThemeMode.SYSTEM, appSettingsPreferences.getThemeMode())
        composeTestRule.onNodeWithTag("theme_settings_item").performClick()
        composeTestRule.onNodeWithTag("option_item_DARK").performClick()

        assertEquals(AppThemeMode.DARK, appSettingsPreferences.getThemeMode())
    }

    @Test
    fun language_item_opens_its_selection_dialog() {
        composeTestRule.setContent {
            val themeMode by appSettingsPreferences.themeMode.collectAsState()
            val language by appSettingsPreferences.language.collectAsState()
            val dataUnit by appSettingsPreferences.dataUnit.collectAsState()
            val refreshMode by appSettingsPreferences.refreshMode.collectAsState()
            val isDevMode by developerPreferences.isDeveloperModeEnabled.collectAsState()

            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = themeMode,
                language = language,
                dataUnit = dataUnit,
                refreshMode = refreshMode,
                isDevMode = isDevMode,
                onBack = {},
                onOpenPrivacyPolicy = {}
            )
        }

        composeTestRule.onNodeWithTag("language_settings_item").performClick()
        composeTestRule.onNodeWithTag("option_selection_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_item_EN").assertIsDisplayed()
    }

    @Test
    fun selecting_English_updates_the_setting_state() {
        composeTestRule.setContent {
            val themeMode by appSettingsPreferences.themeMode.collectAsState()
            val language by appSettingsPreferences.language.collectAsState()
            val dataUnit by appSettingsPreferences.dataUnit.collectAsState()
            val refreshMode by appSettingsPreferences.refreshMode.collectAsState()
            val isDevMode by developerPreferences.isDeveloperModeEnabled.collectAsState()

            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = themeMode,
                language = language,
                dataUnit = dataUnit,
                refreshMode = refreshMode,
                isDevMode = isDevMode,
                onBack = {},
                onOpenPrivacyPolicy = {}
            )
        }

        assertEquals(AppLanguage.AR, appSettingsPreferences.getLanguage())
        composeTestRule.onNodeWithTag("language_settings_item").performClick()
        composeTestRule.onNodeWithTag("option_item_EN").performClick()

        assertEquals(AppLanguage.EN, appSettingsPreferences.getLanguage())
    }

    @Test
    fun data_unit_item_opens_its_selection_dialog_and_selection_updates_state() {
        composeTestRule.setContent {
            val themeMode by appSettingsPreferences.themeMode.collectAsState()
            val language by appSettingsPreferences.language.collectAsState()
            val dataUnit by appSettingsPreferences.dataUnit.collectAsState()
            val refreshMode by appSettingsPreferences.refreshMode.collectAsState()
            val isDevMode by developerPreferences.isDeveloperModeEnabled.collectAsState()

            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = themeMode,
                language = language,
                dataUnit = dataUnit,
                refreshMode = refreshMode,
                isDevMode = isDevMode,
                onBack = {},
                onOpenPrivacyPolicy = {}
            )
        }

        assertEquals(AppDataUnit.AUTO, appSettingsPreferences.getDataUnit())
        composeTestRule.onNodeWithTag("unit_settings_item").performClick()
        composeTestRule.onNodeWithTag("option_selection_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_item_MB").performClick()

        assertEquals(AppDataUnit.MB, appSettingsPreferences.getDataUnit())
    }

    @Test
    fun refresh_mode_item_opens_its_selection_dialog_and_selection_updates_state() {
        composeTestRule.setContent {
            val themeMode by appSettingsPreferences.themeMode.collectAsState()
            val language by appSettingsPreferences.language.collectAsState()
            val dataUnit by appSettingsPreferences.dataUnit.collectAsState()
            val refreshMode by appSettingsPreferences.refreshMode.collectAsState()
            val isDevMode by developerPreferences.isDeveloperModeEnabled.collectAsState()

            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = themeMode,
                language = language,
                dataUnit = dataUnit,
                refreshMode = refreshMode,
                isDevMode = isDevMode,
                onBack = {},
                onOpenPrivacyPolicy = {}
            )
        }

        assertEquals(AppRefreshMode.SMART, appSettingsPreferences.getRefreshMode())
        composeTestRule.onNodeWithTag("refresh_settings_item").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("option_selection_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_item_MANUAL").performClick()

        assertEquals(AppRefreshMode.MANUAL, appSettingsPreferences.getRefreshMode())
    }

    @Test
    fun developer_mode_switch_toggles_and_persists_through_DeveloperPreferences() {
        composeTestRule.setContent {
            val themeMode by appSettingsPreferences.themeMode.collectAsState()
            val language by appSettingsPreferences.language.collectAsState()
            val dataUnit by appSettingsPreferences.dataUnit.collectAsState()
            val refreshMode by appSettingsPreferences.refreshMode.collectAsState()
            val isDevMode by developerPreferences.isDeveloperModeEnabled.collectAsState()

            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = themeMode,
                language = language,
                dataUnit = dataUnit,
                refreshMode = refreshMode,
                isDevMode = isDevMode,
                onBack = {},
                onOpenPrivacyPolicy = {}
            )
        }

        assertEquals(false, developerPreferences.isDeveloperModeEnabled())
        composeTestRule.onNodeWithTag("dev_mode_switch").performScrollTo().performClick()
        assertEquals(true, developerPreferences.isDeveloperModeEnabled())

        // Create a new instance from storage to verify persistence
        val reloadedPrefs = DeveloperPreferences(context)
        assertEquals(true, reloadedPrefs.isDeveloperModeEnabled())
    }

    @Test
    fun back_button_invokes_navigation_callback() {
        var backInvoked = false
        composeTestRule.setContent {
            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = AppThemeMode.SYSTEM,
                language = AppLanguage.AR,
                dataUnit = AppDataUnit.AUTO,
                refreshMode = AppRefreshMode.SMART,
                isDevMode = false,
                onBack = { backInvoked = true },
                onOpenPrivacyPolicy = {}
            )
        }

        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        assertTrue(backInvoked)
    }

    @Test
    fun privacy_policy_callback_remains_wired() {
        var privacyInvoked = false
        composeTestRule.setContent {
            SettingsScreen(
                appSettingsPreferences = appSettingsPreferences,
                developerPreferences = developerPreferences,
                themeMode = AppThemeMode.SYSTEM,
                language = AppLanguage.AR,
                dataUnit = AppDataUnit.AUTO,
                refreshMode = AppRefreshMode.SMART,
                isDevMode = false,
                onBack = {},
                onOpenPrivacyPolicy = { privacyInvoked = true }
            )
        }

        composeTestRule.onNodeWithTag("privacy_settings_item").performScrollTo().performClick()
        assertTrue(privacyInvoked)
    }

    @Test
    fun preferences_sync_across_multiple_instances_via_shared_preferences_listener() {
        val instance1 = AppSettingsPreferences(context)
        val instance2 = AppSettingsPreferences(context)

        assertEquals(instance1.getThemeMode(), instance2.getThemeMode())

        instance1.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, instance2.themeMode.value)
        assertEquals(AppThemeMode.DARK, instance2.getThemeMode())

        instance2.setLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, instance1.language.value)

        val dev1 = DeveloperPreferences(context)
        val dev2 = DeveloperPreferences(context)
        dev1.setDeveloperModeEnabled(true)
        assertEquals(true, dev2.isDeveloperModeEnabled.value)
    }

    @Test
    fun live_state_updates_in_viewmodel_without_restarting() {
        val fakeRepo = object : NetworkStatsRepository {
            override fun hasUsageStatsPermission(): Boolean = true
            override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> =
                Result.success(
                    NetworkUsageSummary(
                        wifi = NetworkUsage.zero(NetworkType.WIFI),
                        mobile = NetworkUsage.zero(NetworkType.MOBILE),
                        total = NetworkUsage.zero(NetworkType.TOTAL),
                        dateRange = dateRange
                    )
                )
            override suspend fun getDailyUsageBreakdown(startDate: LocalDate, endDate: LocalDate): Result<List<DailyNetworkUsage>> =
                Result.success(emptyList())
            override suspend fun getDebugInfo(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): Result<NetworkStatsDebugInfo> =
                Result.failure(UnsupportedOperationException())
        }

        val dateRangePrefs = DateRangePreferences(context)
        val vm = NetworkUsageViewModel(
            repository = fakeRepo,
            dateRangePreferences = dateRangePrefs,
            appSettingsPreferences = appSettingsPreferences,
            developerPreferences = developerPreferences,
            connectivityObserver = ConnectivityObserver(context)
        )

        // 1. Theme live update
        assertEquals(AppThemeMode.SYSTEM, vm.uiState.value.themeMode)
        appSettingsPreferences.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, vm.uiState.value.themeMode)

        // 2. Language live update
        assertEquals(AppLanguage.AR, vm.uiState.value.language)
        appSettingsPreferences.setLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, vm.uiState.value.language)

        // 3. Data Unit live update
        assertEquals(AppDataUnit.AUTO, vm.uiState.value.dataUnit)
        appSettingsPreferences.setDataUnit(AppDataUnit.GB)
        assertEquals(AppDataUnit.GB, vm.uiState.value.dataUnit)

        // 4. Refresh Mode live update
        assertEquals(AppRefreshMode.SMART, vm.uiState.value.refreshMode)
        appSettingsPreferences.setRefreshMode(AppRefreshMode.MANUAL)
        assertEquals(AppRefreshMode.MANUAL, vm.uiState.value.refreshMode)

        // 5. Developer Mode live update
        assertEquals(false, vm.uiState.value.isDeveloperModeEnabled)
        developerPreferences.setDeveloperModeEnabled(true)
        assertEquals(true, vm.uiState.value.isDeveloperModeEnabled)

        // 6. Persistence across new instance / relaunch
        val reloadedSettings = AppSettingsPreferences(context)
        val reloadedDev = DeveloperPreferences(context)
        assertEquals(AppThemeMode.DARK, reloadedSettings.getThemeMode())
        assertEquals(AppLanguage.EN, reloadedSettings.getLanguage())
        assertEquals(AppDataUnit.GB, reloadedSettings.getDataUnit())
        assertEquals(AppRefreshMode.MANUAL, reloadedSettings.getRefreshMode())
        assertEquals(true, reloadedDev.isDeveloperModeEnabled())
    }
}
