package com.example.data.source

import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.RawUidUsageBucket

interface NetworkStatsDataSource {
    fun hasUsageStatsPermission(): Boolean
    suspend fun getUsageForNetwork(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): NetworkUsage
    suspend fun getUidUsage(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): List<RawUidUsageBucket> = emptyList()
    suspend fun getDebugInfo(
        networkType: NetworkType,
        startTimeMs: Long,
        endTimeMs: Long
    ): NetworkStatsDebugInfo
}
