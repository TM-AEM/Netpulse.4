package com.example.util

class SmartRefreshManager(
    private val debounceIntervalMs: Long = 1000L,
    private val staleIntervalMs: Long = 60_000L
) {
    @Volatile
    private var lastRefreshEpochMs: Long = 0L

    @Volatile
    private var isRefreshing: Boolean = false

    fun canTriggerRefresh(force: Boolean = false): Boolean {
        if (isRefreshing) return false
        val now = System.currentTimeMillis()
        if (force) return true
        return (now - lastRefreshEpochMs) >= debounceIntervalMs
    }

    fun isDataStale(): Boolean {
        val now = System.currentTimeMillis()
        return (now - lastRefreshEpochMs) >= staleIntervalMs
    }

    fun recordRefresh() {
        lastRefreshEpochMs = System.currentTimeMillis()
    }

    fun setRefreshing(refreshing: Boolean) {
        isRefreshing = refreshing
    }

    fun formatTimeSinceLastRefresh(): String {
        if (lastRefreshEpochMs == 0L) return ""
        val diffSec = (System.currentTimeMillis() - lastRefreshEpochMs) / 1000L
        return when {
            diffSec < 10 -> "الآن"
            diffSec < 60 -> "منذ $diffSec ثانية"
            else -> "منذ ${diffSec / 60} دقيقة"
        }
    }
}
