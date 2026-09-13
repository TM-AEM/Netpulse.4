package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.AppLanguage
import com.example.model.AppNetworkUsage
import com.example.model.NetworkType
import com.example.ui.components.CustomDateRangeDialog
import com.example.ui.components.DateRangeSelector
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.DownloadAccent
import com.example.ui.theme.TotalAccent
import com.example.ui.theme.UploadAccent
import com.example.ui.viewmodel.AppSortOption
import com.example.ui.viewmodel.AppUsageContentState
import com.example.ui.viewmodel.AppUsageUiState
import com.example.ui.viewmodel.AppUsageViewModel
import com.example.util.ByteFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(
    viewModel: AppUsageViewModel,
    uiState: AppUsageUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language = uiState.language
    val isArabic = language == AppLanguage.AR

    var showCustomRangeDialog by remember { mutableStateOf(false) }

    if (showCustomRangeDialog) {
        CustomDateRangeDialog(
            initialStartDate = uiState.dateRange.startDate,
            initialEndDate = uiState.dateRange.endDate,
            onConfirm = { s, e ->
                viewModel.setCustomRange(s, e)
                showCustomRangeDialog = false
            },
            onDismiss = { showCustomRangeDialog = false },
            language = language
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_usage_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isArabic) "استهلاك التطبيقات" else "App Usage",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (isArabic) "استهلاك بيانات الشبكة لكل تطبيق" else "Network data consumption by application",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("app_usage_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isArabic) "رجوع" else "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshData() },
                        modifier = Modifier.testTag("app_usage_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (isArabic) "تحديث" else "Refresh"
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
        ) {
            // 1. Date Range Selector
            DateRangeSelector(
                selectedRange = uiState.dateRange,
                onPresetSelected = { viewModel.selectPreset(it) },
                onCustomClick = { showCustomRangeDialog = true },
                language = language,
                onNavigatePrevious = { viewModel.navigatePreviousPeriod() },
                onNavigateNext = { viewModel.navigateNextPeriod() }
            )

            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            // 2. Network Filter & Sort Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.SpacingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Network Filters: Total, Wi-Fi, Mobile
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.selectedFilter == NetworkType.TOTAL,
                        onClick = { viewModel.setNetworkFilter(NetworkType.TOTAL) },
                        label = { Text(if (isArabic) "الإجمالي" else "Total") },
                        modifier = Modifier.testTag("filter_chip_total")
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == NetworkType.WIFI,
                        onClick = { viewModel.setNetworkFilter(NetworkType.WIFI) },
                        label = { Text(if (isArabic) "واي فاي" else "Wi-Fi") },
                        modifier = Modifier.testTag("filter_chip_wifi")
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == NetworkType.MOBILE,
                        onClick = { viewModel.setNetworkFilter(NetworkType.MOBILE) },
                        label = { Text(if (isArabic) "الجوال" else "Mobile") },
                        modifier = Modifier.testTag("filter_chip_mobile")
                    )
                }

                // Sorting Menu
                var sortMenuExpanded by remember { mutableStateOf(false) }
                val currentSortLabel = when (uiState.selectedSort) {
                    AppSortOption.TOTAL_USAGE -> if (isArabic) "الإجمالي" else "Total"
                    AppSortOption.DOWNLOAD -> if (isArabic) "التنزيل" else "Download"
                    AppSortOption.UPLOAD -> if (isArabic) "الرفع" else "Upload"
                    AppSortOption.APP_NAME -> if (isArabic) "الاسم" else "Name"
                }

                Box {
                    AssistChip(
                        onClick = { sortMenuExpanded = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(currentSortLabel) },
                        modifier = Modifier.testTag("sort_selector_button")
                    )

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isArabic) "إجمالي الاستهلاك" else "Total Usage") },
                            onClick = {
                                viewModel.setSortOption(AppSortOption.TOTAL_USAGE)
                                sortMenuExpanded = false
                            },
                            modifier = Modifier.testTag("sort_option_total")
                        )
                        DropdownMenuItem(
                            text = { Text(if (isArabic) "التنزيل" else "Download") },
                            onClick = {
                                viewModel.setSortOption(AppSortOption.DOWNLOAD)
                                sortMenuExpanded = false
                            },
                            modifier = Modifier.testTag("sort_option_download")
                        )
                        DropdownMenuItem(
                            text = { Text(if (isArabic) "الرفع" else "Upload") },
                            onClick = {
                                viewModel.setSortOption(AppSortOption.UPLOAD)
                                sortMenuExpanded = false
                            },
                            modifier = Modifier.testTag("sort_option_upload")
                        )
                        DropdownMenuItem(
                            text = { Text(if (isArabic) "اسم التطبيق" else "App Name") },
                            onClick = {
                                viewModel.setSortOption(AppSortOption.APP_NAME)
                                sortMenuExpanded = false
                            },
                            modifier = Modifier.testTag("sort_option_app_name")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))

            // 3. Content State
            when (val state = uiState.contentState) {
                is AppUsageContentState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("loading_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.testTag("loading_indicator")
                        )
                    }
                }
                is AppUsageContentState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(DesignTokens.SpacingLarge)
                            .testTag("empty_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.SpacingLarge),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DataUsage,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
                                Text(
                                    text = if (isArabic) "لا يوجد استهلاك للتطبيقات مسجل في هذه الفترة."
                                    else "No app network activity recorded for this period.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is AppUsageContentState.PermissionError -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(DesignTokens.SpacingMedium)
                            .testTag("permission_error_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.SpacingLarge),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
                                Text(
                                    text = if (isArabic) "مطلوب إذن الوصول لبيانات الاستخدام لعرض استهلاك بيانات التطبيقات."
                                    else "Usage access permission is required to view per-app data consumption.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.SpacingLarge))
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val fallback = Intent(Settings.ACTION_SETTINGS)
                                            context.startActivity(fallback)
                                        }
                                    },
                                    modifier = Modifier.testTag("open_settings_button")
                                ) {
                                    Text(if (isArabic) "فتح الإعدادات" else "Open Settings")
                                }
                                Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
                                OutlinedButton(
                                    onClick = { viewModel.refreshData() },
                                    modifier = Modifier.testTag("retry_permission_button")
                                ) {
                                    Text(if (isArabic) "إعادة المحاولة" else "Retry")
                                }
                            }
                        }
                    }
                }
                is AppUsageContentState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(DesignTokens.SpacingMedium)
                            .testTag("error_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DesignTokens.SpacingLarge),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))
                                Button(
                                    onClick = { viewModel.refreshData() },
                                    modifier = Modifier.testTag("retry_error_button")
                                ) {
                                    Text(if (isArabic) "إعادة المحاولة" else "Retry")
                                }
                            }
                        }
                    }
                }
                is AppUsageContentState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("app_usage_list")
                            .padding(horizontal = DesignTokens.SpacingMedium),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = state.apps,
                            key = { it.uid }
                        ) { app ->
                            AppUsageRowItem(
                                app = app,
                                uiState = uiState
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(DesignTokens.SpacingLarge))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppUsageRowItem(
    app: AppNetworkUsage,
    uiState: AppUsageUiState,
    modifier: Modifier = Modifier
) {
    val language = uiState.language
    val isArabic = language == AppLanguage.AR
    val dataUnit = uiState.dataUnit

    val totalFormatted = ByteFormatter.format(app.totalBytes, dataUnit).getDisplay(language)
    val downloadFormatted = ByteFormatter.format(app.downloadBytes, dataUnit).getDisplay(language)
    val uploadFormatted = ByteFormatter.format(app.uploadBytes, dataUnit).getDisplay(language)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_usage_row_${app.uid}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            SafeAppIcon(
                packageName = app.primaryPackageName,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(DesignTokens.SpacingMedium))

            // App details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val packageName = app.primaryPackageName
                if (!packageName.isNullOrEmpty() && packageName != app.displayName) {
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Download & Upload Breakdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Download
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = if (isArabic) "تنزيل" else "Download",
                            tint = DownloadAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = downloadFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Upload
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = if (isArabic) "رفع" else "Upload",
                            tint = UploadAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = uploadFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(DesignTokens.SpacingSmall))

            // Total Usage
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = totalFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TotalAccent
                )

                if (app.rxPackets >= 0 && app.txPackets >= 0) {
                    val totalPackets = app.rxPackets + app.txPackets
                    Text(
                        text = "${totalPackets}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SafeAppIcon(
    packageName: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        if (packageName.isNullOrEmpty()) null
        else {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap
                } else {
                    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(8.dp))
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
