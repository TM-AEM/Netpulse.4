package com.example.model

enum class ByteUnit(
    val factor: Double,
    val symbolEn: String,
    val symbolAr: String
) {
    B(1.0, "B", "بايت"),
    KB(1024.0, "KB", "ك.ب"),
    MB(1024.0 * 1024.0, "MB", "م.ب"),
    GB(1024.0 * 1024.0 * 1024.0, "GB", "ج.ب"),
    TB(1024.0 * 1024.0 * 1024.0 * 1024.0, "TB", "ت.ب")
}
