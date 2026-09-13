package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppRefreshMode
import com.example.data.preferences.AppSettingsPreferences
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.DeveloperPreferences
import com.example.ui.theme.DesignTokens
import com.example.util.ByteFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appSettingsPreferences: AppSettingsPreferences,
    developerPreferences: DeveloperPreferences,
    themeMode: AppThemeMode,
    language: AppLanguage,
    dataUnit: AppDataUnit,
    refreshMode: AppRefreshMode,
    isDevMode: Boolean,
    onBack: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onNavigateToDataPlan: () -> Unit = {},
    onNavigateToUsageInsights: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isArabic = language == AppLanguage.AR

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showUnitDialog by remember { mutableStateOf(false) }
    var showRefreshDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        OptionSelectionDialog(
            title = if (isArabic) "اختر مظهر التطبيق" else "Choose App Theme",
            options = AppThemeMode.entries.toList(),
            selectedOption = themeMode,
            optionLabel = { if (isArabic) it.labelAr else it.labelEn },
            onSelect = {
                appSettingsPreferences.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        OptionSelectionDialog(
            title = if (isArabic) "اختر لغة التطبيق" else "Choose App Language",
            options = AppLanguage.entries.toList(),
            selectedOption = language,
            optionLabel = { if (isArabic) it.labelAr else it.labelEn },
            onSelect = {
                appSettingsPreferences.setLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showUnitDialog) {
        OptionSelectionDialog(
            title = if (isArabic) "وحدة عرض البيانات" else "Data Display Unit",
            options = AppDataUnit.entries.toList(),
            selectedOption = dataUnit,
            optionLabel = { if (isArabic) it.labelAr else it.labelEn },
            onSelect = {
                appSettingsPreferences.setDataUnit(it)
                showUnitDialog = false
            },
            onDismiss = { showUnitDialog = false }
        )
    }

    if (showRefreshDialog) {
        OptionSelectionDialog(
            title = if (isArabic) "طريقة تحديث البيانات" else "Refresh Mode",
            options = AppRefreshMode.entries.toList(),
            selectedOption = refreshMode,
            optionLabel = { if (isArabic) it.labelAr else it.labelEn },
            onSelect = {
                appSettingsPreferences.setRefreshMode(it)
                showRefreshDialog = false
            },
            onDismiss = { showRefreshDialog = false }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isArabic) "الإعدادات" else "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isArabic) "رجوع" else "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(DesignTokens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
        ) {
            SettingsCategory(title = if (isArabic) "المظهر واللغة" else "Appearance") {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = if (isArabic) "المظهر" else "Theme",
                    subtitle = if (isArabic) themeMode.labelAr else themeMode.labelEn,
                    onClick = { showThemeDialog = true },
                    testTag = "theme_settings_item"
                )
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = if (isArabic) "اللغة" else "Language",
                    subtitle = if (isArabic) language.labelAr else language.labelEn,
                    onClick = { showLanguageDialog = true },
                    testTag = "language_settings_item"
                )
                SettingsItem(
                    icon = Icons.Default.Straighten,
                    title = if (isArabic) "وحدة البيانات" else "Data Unit",
                    subtitle = if (isArabic) dataUnit.labelAr else dataUnit.labelEn,
                    onClick = { showUnitDialog = true },
                    testTag = "unit_settings_item"
                )
            }

            SettingsCategory(title = if (isArabic) "السلوك والتحديث" else "Behavior & Refresh") {
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = if (isArabic) "تحديث البيانات" else "Data Refresh",
                    subtitle = if (isArabic) refreshMode.labelAr else refreshMode.labelEn,
                    onClick = { showRefreshDialog = true },
                    testTag = "refresh_settings_item"
                )
            }

            SettingsCategory(title = if (isArabic) "خطة البيانات" else "Data Plan") {
                val dataPlanConfig by appSettingsPreferences.dataPlanConfig.collectAsState()
                val planSubtitle = if (dataPlanConfig.enabled && dataPlanConfig.limitBytes > 0L) {
                    val formatted = ByteFormatter.format(dataPlanConfig.limitBytes).getDisplay(language)
                    if (isArabic) "مفعّلة ($formatted)" else "Active ($formatted)"
                } else {
                    if (isArabic) "معطلة" else "Disabled"
                }
                SettingsItem(
                    icon = Icons.Default.PieChart,
                    title = if (isArabic) "خطة واستهلاك البيانات" else "Data Plan & Limits",
                    subtitle = planSubtitle,
                    onClick = onNavigateToDataPlan,
                    testTag = "data_plan_settings_item"
                )
            }

            SettingsCategory(title = if (isArabic) "رؤى الاستهلاك" else "Usage Insights") {
                SettingsItem(
                    icon = Icons.Default.TrendingUp,
                    title = if (isArabic) "رؤى واتجاهات الاستهلاك" else "Usage Insights & Trends",
                    subtitle = if (isArabic) "مقارنة الفترات والمتوسط اليومي للاستهلاك" else "Compare periods and view daily averages",
                    onClick = onNavigateToUsageInsights,
                    testTag = "insights_settings_item"
                )
            }

            SettingsCategory(title = if (isArabic) "المطور والتشخيص" else "Developer & Diagnostics") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DesignTokens.SpacingMedium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(DesignTokens.SpacingMedium))
                            Column {
                                Text(
                                    text = if (isArabic) "وضع المطور (تشخيص NetworkStats)" else "Developer Mode (Diagnostics)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (isArabic) "إظهار عينة الحزم وتحليل الفروقات" else "Show raw buckets and discrepancy breakdown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDevMode,
                            onCheckedChange = { developerPreferences.setDeveloperModeEnabled(it) },
                            modifier = Modifier.testTag("dev_mode_switch")
                        )
                    }
                }
            }

            SettingsCategory(title = if (isArabic) "حول والشفافية" else "About & Transparency") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = if (isArabic) "سياسة الخصوصية" else "Privacy Policy",
                    subtitle = if (isArabic) "محلي 100% بدون خوادم تتبع" else "100% local, no telemetry or servers",
                    onClick = onOpenPrivacyPolicy,
                    testTag = "privacy_settings_item"
                )
            }
        }
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(DesignTokens.SpacingMedium))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
}

@Composable
private fun <T> OptionSelectionDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(DesignTokens.DialogCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("option_selection_dialog")
        ) {
            Column(modifier = Modifier.padding(DesignTokens.SpacingLarge)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
                options.forEach { option ->
                    val optionName = (option as? Enum<*>)?.name ?: optionLabel(option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp)
                            .testTag("option_item_$optionName"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onSelect(option) }
                        )
                        Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))
                        Text(text = optionLabel(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
