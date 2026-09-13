package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppLanguage
import com.example.model.DiscrepancyReason
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.RawBucketDetail
import com.example.ui.theme.DesignTokens
import com.example.util.ByteFormatter
import androidx.compose.material3.FilterChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    debugInfo: NetworkStatsDebugInfo?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    language: AppLanguage = AppLanguage.AR,
    onSelectNetworkType: ((NetworkType) -> Unit)? = null
) {
    val isArabic = language == AppLanguage.AR

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("diagnostics_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.SpacingMedium, vertical = DesignTokens.SpacingSmall)
        ) {
            Text(
                text = if (isArabic) "تشخيصات NetworkStatsManager (وضع المطور)" else "NetworkStatsManager Diagnostics (Dev Mode)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))

            if (debugInfo == null) {
                Text(
                    text = if (isArabic) "بيانات التشخيص غير متوفرة أو جاري التحميل (يرجى التحقق من منح إذن الوصول للاستخدام)"
                           else "Diagnostic data unavailable or loading (please verify Usage Access permission)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium)
                ) {
                    if (onSelectNetworkType != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall)
                            ) {
                                val types = listOf(
                                    NetworkType.TOTAL to if (isArabic) "الإجمالي" else "Total",
                                    NetworkType.WIFI to if (isArabic) "واي فاي" else "Wi-Fi",
                                    NetworkType.MOBILE to if (isArabic) "الجوال" else "Mobile"
                                )
                                types.forEach { (type, label) ->
                                    val isSelected = debugInfo.networkType == type
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectNetworkType(type) },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("diag_filter_${type.name}")
                                    )
                                }
                            }
                        }
                    }

                    item {
                        DiagnosticSummaryCard(debugInfo = debugInfo, isArabic = isArabic)
                    }

                    item {
                        Text(
                            text = if (isArabic) "أسباب الفروقات المحتملة:" else "Possible Discrepancy Reasons:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(debugInfo.discrepancyAnalysis) { reason ->
                        DiscrepancyReasonItem(reason = reason)
                    }

                    item {
                        Text(
                            text = if (isArabic) "عينة الحزم الخام (queryDetails):" else "Raw Buckets Sample:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (debugInfo.detailedBuckets.isEmpty()) {
                        item {
                            Text(
                                text = if (isArabic) "لا توجد سجلات حزم تفصيلية." else "No detailed bucket records found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(debugInfo.detailedBuckets.take(15)) { bucket ->
                            RawBucketItem(bucket = bucket)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(DesignTokens.SpacingLarge))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSummaryCard(
    debugInfo: NetworkStatsDebugInfo,
    isArabic: Boolean
) {
    val totalFormatted = ByteFormatter.format(debugInfo.rawTotalBytes).displayDefault
    val rxFormatted = ByteFormatter.format(debugInfo.rawRxBytes).displayDefault
    val txFormatted = ByteFormatter.format(debugInfo.rawTxBytes).displayDefault
    val bootFormatted = ByteFormatter.format(debugInfo.trafficStatsBootTotalBytes).displayDefault

    val networkTypeLabel = when (debugInfo.networkType) {
        NetworkType.WIFI -> if (isArabic) "واي فاي (Wi-Fi)" else "Wi-Fi"
        NetworkType.MOBILE -> if (isArabic) "بيانات الجوال (Mobile Data)" else "Mobile Data"
        NetworkType.TOTAL -> if (isArabic) "الإجمالي (Total: Wi-Fi + Mobile)" else "Total (Wi-Fi + Mobile)"
    }

    Surface(
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(DesignTokens.SpacingMedium)) {
            Text(
                text = if (isArabic) "الهدف: $networkTypeLabel" else "Target: $networkTypeLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall / 2))
            Text(
                text = if (isArabic) {
                    "الفترة المحددة: ${debugInfo.queryStartFormatted} إلى ${debugInfo.queryEndFormatted}"
                } else {
                    "Selected Range: ${debugInfo.queryStartFormatted} to ${debugInfo.queryEndFormatted}"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
            Text(
                text = if (isArabic) {
                    "NetworkStatsManager (الاستهلاك التاريخي للفترة المحددة):"
                } else {
                    "NetworkStatsManager (Historical usage for selected range):"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isArabic) {
                    "الإجمالي: $totalFormatted (تنزيل: $rxFormatted | رفع: $txFormatted)"
                } else {
                    "Total: $totalFormatted (Download: $rxFormatted | Upload: $txFormatted)"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (debugInfo.rawRxPackets >= 0 && debugInfo.rawTxPackets >= 0) {
                Text(
                    text = if (isArabic) {
                        "إجمالي الحزم: تنزيل ${debugInfo.rawRxPackets} حزمة | رفع ${debugInfo.rawTxPackets} حزمة"
                    } else {
                        "Packets: RX ${debugInfo.rawRxPackets} pkts | TX ${debugInfo.rawTxPackets} pkts"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
            Text(
                text = if (isArabic) {
                    "TrafficStats (إجمالي الجهاز منذ الإقلاع — للمقارنة فقط):"
                } else {
                    "TrafficStats (Device total since boot — diagnostic comparison only):"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = bootFormatted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
            Text(
                text = debugInfo.trafficStatsComparisonNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@Composable
private fun DiscrepancyReasonItem(reason: DiscrepancyReason) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            text = reason.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = reason.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RawBucketItem(bucket: RawBucketDetail) {
    val rx = ByteFormatter.format(bucket.rxBytes).displayDefault
    val tx = ByteFormatter.format(bucket.txBytes).displayDefault

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "UID: ${bucket.uid} | State: ${bucket.state}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                val packetsInfo = if (bucket.rxPackets >= 0 && bucket.txPackets >= 0) {
                    " | Pkts: RX ${bucket.rxPackets}, TX ${bucket.txPackets}"
                } else {
                    ""
                }
                Text(
                    text = "RX: $rx | TX: $tx$packetsInfo",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
