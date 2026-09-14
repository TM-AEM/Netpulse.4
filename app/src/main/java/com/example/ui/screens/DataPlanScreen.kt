package com.example.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.AppLanguage
import com.example.model.BillingCyclePeriod
import com.example.model.ByteUnit
import com.example.model.DataPlanConfig
import com.example.model.DataPlanStatus
import com.example.ui.theme.DesignTokens
import com.example.ui.viewmodel.DataPlanUiState
import com.example.ui.viewmodel.DataPlanViewModel
import com.example.util.ByteFormatter
import com.example.util.DateTimeUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPlanScreen(
    viewModel: DataPlanViewModel,
    uiState: DataPlanUiState,
    language: AppLanguage = AppLanguage.AR,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val localizedContext = remember(context, language) {
        val conf = Configuration(context.resources.configuration)
        conf.setLocale(Locale(language.code))
        context.createConfigurationContext(conf)
    }

    // Local form state initialized from config
    var limitInputText by remember(uiState.config.limitBytes, uiState.config.unit) {
        val valueInUnit = uiState.config.valueInUnit
        val text = if (valueInUnit <= 0.0) {
            ""
        } else if (valueInUnit % 1.0 == 0.0) {
            valueInUnit.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", valueInUnit)
        }
        mutableStateOf(text)
    }
    var selectedUnit by remember(uiState.config.unit) { mutableStateOf(uiState.config.unit) }
    var billingDayInputText by remember(uiState.config.billingCycleStartDay) {
        mutableStateOf(uiState.config.billingCycleStartDay.toString())
    }
    var isInputError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("data_plan_screen"),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.data_plan),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("data_plan_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.testTag("data_plan_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
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
                // 1. Plan Master Switch & Status Card
                PlanStatusHeaderCard(
                    enabled = uiState.config.enabled && uiState.config.limitBytes > 0L,
                    onToggleEnabled = { enabled ->
                        if (enabled && uiState.config.limitBytes <= 0L) {
                            // If limit not set yet, parse from input or set 1 GB default
                            val parsedValue = limitInputText.toDoubleOrNull() ?: 1.0
                            val config = DataPlanConfig.fromUnitValue(
                                enabled = true,
                                value = parsedValue,
                                unit = selectedUnit,
                                billingCycleStartDay = billingDayInputText.toIntOrNull() ?: DataPlanConfig.DEFAULT_BILLING_CYCLE_START_DAY
                            )
                            viewModel.updateConfig(config)
                        } else {
                            viewModel.setPlanEnabled(enabled)
                        }
                    }
                )

                // 2. Permission Missing Warning Banner
                if (!uiState.isPermissionGranted) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("data_plan_permission_card"),
                        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(DesignTokens.SpacingMedium),
                            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(R.string.data_plan_permission_required_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = stringResource(R.string.data_plan_permission_required_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    } catch (e: Exception) {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    }
                                },
                                modifier = Modifier.testTag("data_plan_grant_permission_button")
                            ) {
                                Text(stringResource(R.string.data_plan_grant_permission_btn))
                            }
                        }
                    }
                }

                // 3. Query / NetworkStats Error Banner
                if (uiState.errorMessage != null && uiState.isPermissionGranted) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("data_plan_error_card"),
                        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(DesignTokens.SpacingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.data_plan_error_load_failed),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = uiState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.refresh() },
                                modifier = Modifier.testTag("data_plan_retry_button")
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                // 4. Active Usage Card or Disabled State Card
                when (val status = uiState.status) {
                    is DataPlanStatus.Active -> {
                        ActiveUsageCard(
                            status = status,
                            language = language,
                            isLoading = uiState.isLoading
                        )
                    }
                    is DataPlanStatus.Disabled -> {
                        DisabledPlanCard()
                    }
                }

                // 5. Plan Configuration Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("data_plan_config_card"),
                    shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DesignTokens.SpacingMedium),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.data_plan_settings_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Data Limit input and Unit Selector
                        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingTiny)) {
                            Text(
                                text = stringResource(R.string.data_plan_monthly_limit_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = limitInputText,
                                    onValueChange = {
                                        limitInputText = it
                                        isInputError = false
                                    },
                                    placeholder = { Text("10.0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    isError = isInputError,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("data_plan_limit_input")
                                )

                                // Unit Selection Chips (MB / GB)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = selectedUnit == ByteUnit.GB,
                                        onClick = { selectedUnit = ByteUnit.GB },
                                        label = { Text(stringResource(R.string.data_plan_unit_gb)) },
                                        modifier = Modifier.testTag("data_plan_unit_gb"),
                                        colors = FilterChipDefaults.filterChipColors()
                                    )
                                    FilterChip(
                                        selected = selectedUnit == ByteUnit.MB,
                                        onClick = { selectedUnit = ByteUnit.MB },
                                        label = { Text(stringResource(R.string.data_plan_unit_mb)) },
                                        modifier = Modifier.testTag("data_plan_unit_mb"),
                                        colors = FilterChipDefaults.filterChipColors()
                                    )
                                }
                            }
                        }

                        // Billing Cycle Start Day (1..31)
                        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingTiny)) {
                            Text(
                                text = stringResource(R.string.data_plan_billing_cycle_start_day_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = billingDayInputText,
                                onValueChange = {
                                    val filtered = it.filter { ch -> ch.isDigit() }
                                    billingDayInputText = filtered
                                },
                                placeholder = { Text("1") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("data_plan_billing_day_input")
                            )
                            Text(
                                text = stringResource(R.string.data_plan_short_month_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Save / Apply Button
                        Button(
                            onClick = {
                                val parsedValue = limitInputText.toDoubleOrNull()
                                if (parsedValue == null || parsedValue <= 0.0) {
                                    isInputError = true
                                    return@Button
                                }
                                val parsedDay = (billingDayInputText.toIntOrNull() ?: DataPlanConfig.DEFAULT_BILLING_CYCLE_START_DAY)
                                    .coerceIn(DataPlanConfig.MIN_BILLING_CYCLE_START_DAY, DataPlanConfig.MAX_BILLING_CYCLE_START_DAY)

                                val newConfig = DataPlanConfig.fromUnitValue(
                                    enabled = true,
                                    value = parsedValue,
                                    unit = selectedUnit,
                                    billingCycleStartDay = parsedDay
                                )
                                viewModel.updateConfig(newConfig)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("data_plan_save_button")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))
                            Text(stringResource(R.string.data_plan_save_config))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStatusHeaderCard(
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("data_plan_status_card"),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.data_plan_enable),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            if (enabled) R.string.data_plan_status_active_desc
                            else R.string.data_plan_status_disabled_desc
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.testTag("data_plan_enable_switch")
            )
        }
    }
}

@Composable
private fun ActiveUsageCard(
    status: DataPlanStatus.Active,
    language: AppLanguage,
    isLoading: Boolean
) {
    val isOverLimit = status.isOverLimit
    val cyclePeriod = status.cyclePeriod
    val cycleFormatted = "${DateTimeUtils.formatDate(cyclePeriod.startDate, language)} – ${DateTimeUtils.formatDate(cyclePeriod.endDate, language)}"
    val renewsFormatted = DateTimeUtils.formatDate(cyclePeriod.nextCycleStartDate, language)

    val usedFormatted = ByteFormatter.format(status.usedBytes).getDisplay(language)
    val limitFormatted = ByteFormatter.format(status.limitBytes).getDisplay(language)
    val remainingFormatted = ByteFormatter.format(status.remainingBytes).getDisplay(language)

    val percentage = if (status.limitBytes > 0L) {
        (status.usedBytes.toDouble() / status.limitBytes.toDouble()) * 100.0
    } else {
        0.0
    }
    val percentageText = String.format(Locale.US, "%.1f%%", percentage)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("data_plan_active_card"),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverLimit) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
        ) {
            // Header: Cycle Dates & Renewal
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.data_plan_current_billing_cycle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = cycleFormatted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("data_plan_cycle_dates")
                        )
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("data_plan_loading_indicator"),
                        strokeWidth = 2.dp
                    )
                }
            }

            Text(
                text = stringResource(R.string.data_plan_renews_on_format, renewsFormatted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Over-Limit Banner (if applicable)
            if (isOverLimit) {
                val excessFormatted = ByteFormatter.format(status.excessBytes).getDisplay(language)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("data_plan_over_limit_banner"),
                    shape = RoundedCornerShape(DesignTokens.ChipCornerRadius),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(DesignTokens.SpacingSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.data_plan_limit_exceeded_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.data_plan_excess_format, excessFormatted),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.testTag("data_plan_excess_text")
                            )
                        }
                    }
                }
            }

            // Progress Bar & Percentage
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingTiny)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.data_plan_usage_ratio),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = percentageText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("data_plan_percentage_text")
                    )
                }

                LinearProgressIndicator(
                    progress = { status.usageFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .testTag("data_plan_progress_bar"),
                    color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Metrics Grid: Used | Remaining | Limit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    label = stringResource(R.string.data_plan_used),
                    value = usedFormatted,
                    testTag = "data_plan_used_text",
                    isWarning = isOverLimit
                )
                MetricColumn(
                    label = stringResource(R.string.data_plan_remaining),
                    value = remainingFormatted,
                    testTag = "data_plan_remaining_text",
                    isWarning = false
                )
                MetricColumn(
                    label = stringResource(R.string.data_plan_limit_header),
                    value = limitFormatted,
                    testTag = "data_plan_limit_text",
                    isWarning = false
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    testTag: String,
    isWarning: Boolean
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun DisabledPlanCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("data_plan_disabled_card"),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = stringResource(R.string.data_plan_disabled_card_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

