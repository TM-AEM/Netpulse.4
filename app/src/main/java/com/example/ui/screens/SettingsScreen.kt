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
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showUnitDialog by remember { mutableStateOf(false) }
    var showRefreshDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        OptionSelectionDialog(
            title = when (language) {
                AppLanguage.AR -> "اختر مظهر التطبيق"
                AppLanguage.FR -> "Choisir le thème"
                AppLanguage.EN -> "Choose App Theme"
            },
            options = AppThemeMode.entries.toList(),
            selectedOption = themeMode,
            optionLabel = { it.getLabel(language) },
            onSelect = {
                appSettingsPreferences.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        OptionSelectionDialog(
            title = when (language) {
                AppLanguage.AR -> "اختر لغة التطبيق"
                AppLanguage.FR -> "Choisir la langue"
                AppLanguage.EN -> "Choose App Language"
            },
            options = AppLanguage.entries.toList(),
            selectedOption = language,
            optionLabel = { it.getLabel(language) },
            onSelect = {
                appSettingsPreferences.setLanguage(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showUnitDialog) {
        OptionSelectionDialog(
            title = when (language) {
                AppLanguage.AR -> "وحدة عرض البيانات"
                AppLanguage.FR -> "Unité d'affichage des données"
                AppLanguage.EN -> "Data Display Unit"
            },
            options = AppDataUnit.entries.toList(),
            selectedOption = dataUnit,
            optionLabel = { it.getLabel(language) },
            onSelect = {
                appSettingsPreferences.setDataUnit(it)
                showUnitDialog = false
            },
            onDismiss = { showUnitDialog = false }
        )
    }

    if (showRefreshDialog) {
        OptionSelectionDialog(
            title = when (language) {
                AppLanguage.AR -> "طريقة تحديث البيانات"
                AppLanguage.FR -> "Mode d'actualisation"
                AppLanguage.EN -> "Refresh Mode"
            },
            options = AppRefreshMode.entries.toList(),
            selectedOption = refreshMode,
            optionLabel = { it.getLabel(language) },
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
                        text = when (language) {
                            AppLanguage.AR -> "الإعدادات"
                            AppLanguage.FR -> "Paramètres"
                            AppLanguage.EN -> "Settings"
                        },
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
                            contentDescription = when (language) {
                                AppLanguage.AR -> "رجوع"
                                AppLanguage.FR -> "Retour"
                                AppLanguage.EN -> "Back"
                            }
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
            SettingsCategory(
                title = when (language) {
                    AppLanguage.AR -> "المظهر واللغة"
                    AppLanguage.FR -> "Apparence et langue"
                    AppLanguage.EN -> "Appearance"
                }
            ) {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = when (language) {
                        AppLanguage.AR -> "المظهر"
                        AppLanguage.FR -> "Thème"
                        AppLanguage.EN -> "Theme"
                    },
                    subtitle = themeMode.getLabel(language),
                    onClick = { showThemeDialog = true },
                    testTag = "theme_settings_item"
                )
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = when (language) {
                        AppLanguage.AR -> "اللغة"
                        AppLanguage.FR -> "Langue"
                        AppLanguage.EN -> "Language"
                    },
                    subtitle = language.getLabel(language),
                    onClick = { showLanguageDialog = true },
                    testTag = "language_settings_item"
                )
                SettingsItem(
                    icon = Icons.Default.Straighten,
                    title = when (language) {
                        AppLanguage.AR -> "وحدة البيانات"
                        AppLanguage.FR -> "Unité de données"
                        AppLanguage.EN -> "Data Unit"
                    },
                    subtitle = dataUnit.getLabel(language),
                    onClick = { showUnitDialog = true },
                    testTag = "unit_settings_item"
                )
            }

            SettingsCategory(
                title = when (language) {
                    AppLanguage.AR -> "السلوك والتحديث"
                    AppLanguage.FR -> "Comportement et actualisation"
                    AppLanguage.EN -> "Behavior & Refresh"
                }
            ) {
                SettingsItem(
                    icon = Icons.Default.Sync,
                    title = when (language) {
                        AppLanguage.AR -> "تحديث البيانات"
                        AppLanguage.FR -> "Actualisation des données"
                        AppLanguage.EN -> "Data Refresh"
                    },
                    subtitle = refreshMode.getLabel(language),
                    onClick = { showRefreshDialog = true },
                    testTag = "refresh_settings_item"
                )
            }

            SettingsCategory(
                title = when (language) {
                    AppLanguage.AR -> "خطة البيانات"
                    AppLanguage.FR -> "Forfait de données"
                    AppLanguage.EN -> "Data Plan"
                }
            ) {
                val dataPlanConfig by appSettingsPreferences.dataPlanConfig.collectAsState()
                val planSubtitle = if (dataPlanConfig.enabled && dataPlanConfig.limitBytes > 0L) {
                    val formatted = ByteFormatter.format(dataPlanConfig.limitBytes).getDisplay(language)
                    when (language) {
                        AppLanguage.AR -> "مفعّلة ($formatted)"
                        AppLanguage.FR -> "Actif ($formatted)"
                        AppLanguage.EN -> "Active ($formatted)"
                    }
                } else {
                    when (language) {
                        AppLanguage.AR -> "معطلة"
                        AppLanguage.FR -> "Désactivé"
                        AppLanguage.EN -> "Disabled"
                    }
                }
                SettingsItem(
                    icon = Icons.Default.PieChart,
                    title = when (language) {
                        AppLanguage.AR -> "خطة واستهلاك البيانات"
                        AppLanguage.FR -> "Forfait et limites de données"
                        AppLanguage.EN -> "Data Plan & Limits"
                    },
                    subtitle = planSubtitle,
                    onClick = onNavigateToDataPlan,
                    testTag = "data_plan_settings_item"
                )
            }

            SettingsCategory(
                title = when (language) {
                    AppLanguage.AR -> "رؤى الاستهلاك"
                    AppLanguage.FR -> "Aperçu de la consommation"
                    AppLanguage.EN -> "Usage Insights"
                }
            ) {
                SettingsItem(
                    icon = Icons.Default.TrendingUp,
                    title = when (language) {
                        AppLanguage.AR -> "رؤى واتجاهات الاستهلاك"
                        AppLanguage.FR -> "Tendances de consommation"
                        AppLanguage.EN -> "Usage Insights & Trends"
                    },
                    subtitle = when (language) {
                        AppLanguage.AR -> "مقارنة الفترات والمتوسط اليومي للاستهلاك"
                        AppLanguage.FR -> "Comparer les périodes et voir les moyennes"
                        AppLanguage.EN -> "Compare periods and view daily averages"
                    },
                    onClick = onNavigateToUsageInsights,
                    testTag = "insights_settings_item"
                )
            }

            SettingsCategory(
                title = when (language) {
                    AppLanguage.AR -> "المطور والتشخيص"
                    AppLanguage.FR -> "Développeur et diagnostics"
                    AppLanguage.EN -> "Developer & Diagnostics"
                }
            ) {
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
                                    text = when (language) {
                                        AppLanguage.AR -> "وضع المطور (تشخيص NetworkStats)"
                                        AppLanguage.FR -> "Mode développeur (Diagnostics)"
                                        AppLanguage.EN -> "Developer Mode (Diagnostics)"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (language) {
                                        AppLanguage.AR -> "إظهار عينة الحزم وتحليل الفروقات"
                                        AppLanguage.FR -> "Afficher l'échantillon brut et les écarts"
                                        AppLanguage.EN -> "Show raw buckets and discrepancy breakdown"
                                    },
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

            SettingsCategory(
                title = when (language) {
                    AppLanguage.AR -> "حول والشفافية"
                    AppLanguage.FR -> "À propos et transparence"
                    AppLanguage.EN -> "About & Transparency"
                }
            ) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = when (language) {
                        AppLanguage.AR -> "سياسة الخصوصية"
                        AppLanguage.FR -> "Politique de confidentialité"
                        AppLanguage.EN -> "Privacy Policy"
                    },
                    subtitle = when (language) {
                        AppLanguage.AR -> "محلي 100% بدون خوادم تتبع"
                        AppLanguage.FR -> "100% local, sans aucun serveur ni télémétrie"
                        AppLanguage.EN -> "100% local, no telemetry or servers"
                    },
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
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = DesignTokens.SpacingSmall, start = 4.dp)
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
