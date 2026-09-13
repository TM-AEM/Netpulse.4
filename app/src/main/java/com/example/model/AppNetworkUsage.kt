package com.example.model

/**
 * Minimal domain model representing authoritative measured network usage for an application/UID.
 * Does not contain projections, trends, averages, or persistence state.
 */
data class AppNetworkUsage(
    val uid: Int,
    val packageNames: List<String> = emptyList(),
    val displayName: String,
    val wifiUsage: NetworkUsage,
    val mobileUsage: NetworkUsage,
    val totalUsage: NetworkUsage,
    val rxPackets: Long = -1L,
    val txPackets: Long = -1L
) {
    val downloadBytes: Long get() = totalUsage.downloadBytes
    val uploadBytes: Long get() = totalUsage.uploadBytes
    val totalBytes: Long get() = totalUsage.totalBytes
    val primaryPackageName: String? get() = packageNames.firstOrNull()
}
