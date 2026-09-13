package com.example.model

enum class PeriodPreset(val titleEn: String, val titleAr: String) {
    TODAY("Today", "اليوم"),
    YESTERDAY("Yesterday", "أمس"),
    LAST_7_DAYS("Last 7 Days", "آخر 7 أيام"),
    LAST_30_DAYS("Last 30 Days", "آخر 30 يوم"),
    THIS_MONTH("This Month", "هذا الشهر"),
    CUSTOM("Custom", "مخصص")
}
