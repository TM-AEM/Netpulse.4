package com.example.model

import java.time.LocalDate

data class DailyNetworkUsage(
    val date: LocalDate,
    val wifi: NetworkUsage,
    val mobile: NetworkUsage,
    val total: NetworkUsage
)
