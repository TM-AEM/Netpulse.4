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
import androidx.compose.material3.FilterChip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    debugInfo: NetworkStatsDebugInfo?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    language: AppLanguage = AppLanguage.AR,
    onSelectNetworkType: ((NetworkType) -> Unit)? = null
) {
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
                text = when (language) {
                    AppLanguage.AR -> "تشخيصات NetworkStatsManager (وضع المطور)"
                    AppLanguage.FR -> "Diagnostics NetworkStatsManager (Mode dev)"
                    AppLanguage.EN -> "NetworkStatsManager Diagnostics (Dev Mode)"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(DesignTokens.SpacingMedium))

            if (debugInfo == null) {
                Text(
                    text = when (language) {
                        AppLanguage.AR -> "بيانات التشخيص غير متوفرة أو جاري التحميل (يرجى التحقق من منح إذن الوصول للاستخدام)"
                        AppLanguage.FR -> "Données de diagnostic indisponibles ou en cours de chargement (vérifiez l'autorisation d'accès)"
                        AppLanguage.EN -> "Diagnostic data unavailable or loading (please verify Usage Access permission)"
                    },
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
                                    NetworkType.TOTAL to when (language) {
                                        AppLanguage.AR -> "الإجمالي"
                                        AppLanguage.FR -> "Total"
                                        AppLanguage.EN -> "Total"
                                    },
                                    NetworkType.WIFI to when (language) {
                                        AppLanguage.AR -> "واي فاي"
                                        AppLanguage.FR -> "Wi-Fi"
                                        AppLanguage.EN -> "Wi-Fi"
                                    },
                                    NetworkType.MOBILE to when (language) {
                                        AppLanguage.AR -> "الجوال"
                                        AppLanguage.FR -> "Mobile"
                                        AppLanguage.EN -> "Mobile"
                                    }
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
                        DiagnosticSummaryCard(debugInfo = debugInfo, language = language)
                    }

                    item {
                        Text(
                            text = when (language) {
                                AppLanguage.AR -> "أسباب الفروقات المحتملة:"
                                AppLanguage.FR -> "Raisons possibles des écarts :"
                                AppLanguage.EN -> "Possible Discrepancy Reasons:"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(debugInfo.discrepancyAnalysis) { reason ->
                        DiscrepancyReasonItem(reason = reason)
                    }

                    item {
                        Text(
                            text = when (language) {
                                AppLanguage.AR -> "عينة الحزم الخام (queryDetails):"
                                AppLanguage.FR -> "Échantillon brut de paquets (queryDetails) :"
                                AppLanguage.EN -> "Raw Buckets Sample:"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (debugInfo.detailedBuckets.isEmpty()) {
                        item {
                            Text(
                                text = when (language) {
                                    AppLanguage.AR -> "لا توجد سجلات حزم تفصيلية."
                                    AppLanguage.FR -> "Aucun enregistrement détaillé trouvé."
                                    AppLanguage.EN -> "No detailed bucket records found."
                                },
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
fun DiagnosticSummaryCard(
    debugInfo: NetworkStatsDebugInfo,
    language: AppLanguage = AppLanguage.AR
) {
    val totalFormatted = ByteFormatter.format(debugInfo.rawTotalBytes).displayDefault
    val rxFormatted = ByteFormatter.format(debugInfo.rawRxBytes).displayDefault
    val txFormatted = ByteFormatter.format(debugInfo.rawTxBytes).displayDefault
    val bootFormatted = ByteFormatter.format(debugInfo.trafficStatsBootTotalBytes).displayDefault

    val networkTypeLabel = when (debugInfo.networkType) {
        NetworkType.WIFI -> when (language) {
            AppLanguage.AR -> "واي فاي (Wi-Fi)"
            AppLanguage.FR -> "Wi-Fi"
            AppLanguage.EN -> "Wi-Fi"
        }
        NetworkType.MOBILE -> when (language) {
            AppLanguage.AR -> "بيانات الجوال (Mobile Data)"
            AppLanguage.FR -> "Données mobiles"
            AppLanguage.EN -> "Mobile Data"
        }
        NetworkType.TOTAL -> when (language) {
            AppLanguage.AR -> "الإجمالي (Total: Wi-Fi + Mobile)"
            AppLanguage.FR -> "Total (Wi-Fi + Mobile)"
            AppLanguage.EN -> "Total (Wi-Fi + Mobile)"
        }
    }

    Surface(
        shape = RoundedCornerShape(DesignTokens.CardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(DesignTokens.SpacingMedium)) {
            Text(
                text = when (language) {
                    AppLanguage.AR -> "الهدف: $networkTypeLabel"
                    AppLanguage.FR -> "Cible : $networkTypeLabel"
                    AppLanguage.EN -> "Target: $networkTypeLabel"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall / 2))
            Text(
                text = when (language) {
                    AppLanguage.AR -> "الفترة المحددة: ${debugInfo.queryStartFormatted} إلى ${debugInfo.queryEndFormatted}"
                    AppLanguage.FR -> "Période sélectionnée : ${debugInfo.queryStartFormatted} à ${debugInfo.queryEndFormatted}"
                    AppLanguage.EN -> "Selected Range: ${debugInfo.queryStartFormatted} to ${debugInfo.queryEndFormatted}"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
            Text(
                text = when (language) {
                    AppLanguage.AR -> "NetworkStatsManager (الاستهلاك التاريخي للفترة المحددة):"
                    AppLanguage.FR -> "NetworkStatsManager (Consommation historique pour la période) :"
                    AppLanguage.EN -> "NetworkStatsManager (Historical usage for selected range):"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (language) {
                    AppLanguage.AR -> "الإجمالي: $totalFormatted (تنزيل: $rxFormatted | رفع: $txFormatted)"
                    AppLanguage.FR -> "Total : $totalFormatted (Téléchargement : $rxFormatted | Téléversement : $txFormatted)"
                    AppLanguage.EN -> "Total: $totalFormatted (Download: $rxFormatted | Upload: $txFormatted)"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (debugInfo.rawRxPackets >= 0 && debugInfo.rawTxPackets >= 0) {
                Text(
                    text = when (language) {
                        AppLanguage.AR -> "إجمالي الحزم: تنزيل ${debugInfo.rawRxPackets} حزمة | رفع ${debugInfo.rawTxPackets} حزمة"
                        AppLanguage.FR -> "Paquets : RX ${debugInfo.rawRxPackets} paquets | TX ${debugInfo.rawTxPackets} paquets"
                        AppLanguage.EN -> "Packets: RX ${debugInfo.rawRxPackets} pkts | TX ${debugInfo.rawTxPackets} pkts"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(DesignTokens.SpacingSmall))
            Text(
                text = when (language) {
                    AppLanguage.AR -> "TrafficStats (إجمالي الجهاز منذ الإقلاع — للمقارنة فقط):"
                    AppLanguage.FR -> "TrafficStats (Total de l'appareil depuis le démarrage — comparaison) :"
                    AppLanguage.EN -> "TrafficStats (Device total since boot — diagnostic comparison only):"
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
