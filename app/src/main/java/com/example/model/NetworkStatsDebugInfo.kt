package com.example.model

data class RawBucketDetail(
    val uid: Int,
    val state: String,
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long,
    val txPackets: Long,
    val startTime: Long,
    val endTime: Long
)

data class DiscrepancyReason(
    val title: String,
    val description: String
)

data class NetworkStatsDebugInfo(
    val queryStartMs: Long,
    val queryEndMs: Long,
    val queryStartFormatted: String,
    val queryEndFormatted: String,
    val rawRxBytes: Long,
    val rawTxBytes: Long,
    val rawTotalBytes: Long,
    val rawRxPackets: Long,
    val rawTxPackets: Long,
    val trafficStatsBootTotalBytes: Long,
    val trafficStatsComparisonNote: String,
    val detailedBuckets: List<RawBucketDetail>,
    val discrepancyAnalysis: List<DiscrepancyReason>,
    val networkType: NetworkType = NetworkType.TOTAL
)
