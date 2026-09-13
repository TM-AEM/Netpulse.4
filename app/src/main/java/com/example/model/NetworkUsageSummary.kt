package com.example.model

data class NetworkUsageSummary(
    val wifi: NetworkUsage,
    val mobile: NetworkUsage,
    val total: NetworkUsage,
    val dateRange: DateRange
) {
    val wifiShareRatio: Float
        get() = if (total.totalBytes > 0) wifi.totalBytes.toFloat() / total.totalBytes.toFloat() else 0f

    val mobileShareRatio: Float
        get() = if (total.totalBytes > 0) mobile.totalBytes.toFloat() / total.totalBytes.toFloat() else 0f
}
