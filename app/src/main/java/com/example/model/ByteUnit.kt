package com.example.model

import com.example.data.preferences.AppLanguage

enum class ByteUnit(
    val factor: Double,
    val symbolEn: String,
    val symbolAr: String,
    val symbolFr: String = symbolEn
) {
    B(1.0, "B", "بايت", "o"),
    KB(1024.0, "KB", "ك.ب", "Ko"),
    MB(1024.0 * 1024.0, "MB", "م.ب", "Mo"),
    GB(1024.0 * 1024.0 * 1024.0, "GB", "ج.ب", "Go"),
    TB(1024.0 * 1024.0 * 1024.0 * 1024.0, "TB", "ت.ب", "To");

    fun getSymbol(language: AppLanguage): String = when (language) {
        AppLanguage.AR -> symbolAr
        AppLanguage.FR -> symbolFr
        AppLanguage.EN -> symbolEn
    }
}
