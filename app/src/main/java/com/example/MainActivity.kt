package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.DeveloperPreferences
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.screens.AppUsageScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DataPlanScreen
import com.example.ui.screens.PermissionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UsageInsightsScreen
import com.example.ui.theme.NetPulseTheme
import com.example.ui.viewmodel.AppUsageViewModel
import com.example.ui.viewmodel.DataPlanViewModel
import com.example.ui.viewmodel.NetworkUsageViewModel
import com.example.ui.viewmodel.NetworkUsageViewModelFactory
import com.example.ui.viewmodel.UsageInsightsViewModel

enum class Screen {
    DASHBOARD,
    SETTINGS,
    APP_USAGE,
    DATA_PLAN,
    USAGE_INSIGHTS
}

class MainActivity : ComponentActivity() {

    private val viewModel: NetworkUsageViewModel by viewModels {
        NetworkUsageViewModelFactory(applicationContext)
    }

    private val appUsageViewModel: AppUsageViewModel by viewModels {
        NetworkUsageViewModelFactory(applicationContext)
    }

    private val dataPlanViewModel: DataPlanViewModel by viewModels {
        NetworkUsageViewModelFactory(applicationContext)
    }

    private val usageInsightsViewModel: UsageInsightsViewModel by viewModels {
        NetworkUsageViewModelFactory(applicationContext)
    }

    private lateinit var appSettingsPreferences: AppSettingsPreferences
    private lateinit var developerPreferences: DeveloperPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSettingsPreferences = viewModel.appSettingsPreferences
        developerPreferences = viewModel.developerPreferences

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val appUsageUiState by appUsageViewModel.uiState.collectAsState()
            val dataPlanUiState by dataPlanViewModel.uiState.collectAsState()
            val usageInsightsUiState by usageInsightsViewModel.uiState.collectAsState()
            val layoutDirection = if (uiState.language.isRtl) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
            var showPrivacyPolicyFromPermission by remember { mutableStateOf(false) }

            BackHandler(enabled = currentScreen != Screen.DASHBOARD) {
                currentScreen = if (currentScreen == Screen.DATA_PLAN || currentScreen == Screen.USAGE_INSIGHTS) Screen.SETTINGS else Screen.DASHBOARD
            }

            if (showPrivacyPolicyFromPermission) {
                PrivacyPolicyDialog(
                    onDismiss = { showPrivacyPolicyFromPermission = false },
                    language = uiState.language
                )
            }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                NetPulseTheme(themeMode = uiState.themeMode) {
                    if (!uiState.isPermissionGranted) {
                        PermissionScreen(
                            onCheckPermission = { viewModel.checkPermissionAndLoad(force = true) },
                            onOpenPrivacyPolicy = { showPrivacyPolicyFromPermission = true },
                            language = uiState.language
                        )
                    } else {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "ScreenTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { screen ->
                            when (screen) {
                                Screen.DASHBOARD -> DashboardScreen(
                                    viewModel = viewModel,
                                    uiState = uiState,
                                    onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                    onNavigateToAppUsage = { currentScreen = Screen.APP_USAGE }
                                )
                                Screen.SETTINGS -> SettingsScreen(
                                    appSettingsPreferences = appSettingsPreferences,
                                    developerPreferences = developerPreferences,
                                    themeMode = uiState.themeMode,
                                    language = uiState.language,
                                    dataUnit = uiState.dataUnit,
                                    refreshMode = uiState.refreshMode,
                                    isDevMode = uiState.isDeveloperModeEnabled,
                                    onBack = { currentScreen = Screen.DASHBOARD },
                                    onOpenPrivacyPolicy = { showPrivacyPolicyFromPermission = true },
                                    onNavigateToDataPlan = { currentScreen = Screen.DATA_PLAN },
                                    onNavigateToUsageInsights = { currentScreen = Screen.USAGE_INSIGHTS }
                                )
                                Screen.APP_USAGE -> AppUsageScreen(
                                    viewModel = appUsageViewModel,
                                    uiState = appUsageUiState,
                                    onBack = { currentScreen = Screen.DASHBOARD }
                                )
                                Screen.DATA_PLAN -> DataPlanScreen(
                                    viewModel = dataPlanViewModel,
                                    uiState = dataPlanUiState,
                                    language = uiState.language,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                                Screen.USAGE_INSIGHTS -> UsageInsightsScreen(
                                    viewModel = usageInsightsViewModel,
                                    uiState = usageInsightsUiState,
                                    onBack = { currentScreen = Screen.SETTINGS }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onLifecycleResume()
        appUsageViewModel.refreshData()
        dataPlanViewModel.refresh()
        usageInsightsViewModel.refreshData()
    }
}
