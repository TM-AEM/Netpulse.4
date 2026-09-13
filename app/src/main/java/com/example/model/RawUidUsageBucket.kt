package com.example.model

/**
 * Minimal raw model representing UID-level network usage returned directly
 * from [android.app.usage.NetworkStatsManager].
 * Contains only metrics provided by the authoritative system source.
 */
data class RawUidUsageBucket(
    val uid: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val rxPackets: Long = -1L,
    val txPackets: Long = -1L
)
