package com.example.util

import com.example.data.preferences.AppDataUnit
import com.example.data.preferences.AppLanguage
import com.example.model.ByteUnit
import java.util.Locale

data class FormattedByteResult(
    val valueString: String,
    val unitSymbolEn: String,
    val unitSymbolAr: String
) {
    val displayEn: String get() = "$valueString $unitSymbolEn"
    val displayAr: String get() = "$valueString $unitSymbolAr"
    val displayDefault: String get() = displayEn

    fun getDisplay(language: AppLanguage): String = if (language == AppLanguage.AR) displayAr else displayEn
}

object ByteFormatter {
    fun format(bytes: Long, forcedUnit: AppDataUnit = AppDataUnit.AUTO): FormattedByteResult {
        val safeBytes = maxOf(0L, bytes)
        val targetUnit: ByteUnit = when (forcedUnit) {
            AppDataUnit.MB -> ByteUnit.MB
            AppDataUnit.GB -> ByteUnit.GB
            AppDataUnit.AUTO -> chooseAutoUnit(safeBytes)
        }
        val convertedValue = safeBytes.toDouble() / targetUnit.factor
        val formattedNumber = when {
            targetUnit == ByteUnit.B -> String.format(Locale.US, "%,d", safeBytes)
            targetUnit == ByteUnit.KB -> String.format(Locale.US, "%.1f", convertedValue)
            convertedValue >= 100.0 -> String.format(Locale.US, "%.1f", convertedValue)
            convertedValue >= 10.0 -> String.format(Locale.US, "%.2f", convertedValue)
            else -> String.format(Locale.US, "%.2f", convertedValue)
        }
        return FormattedByteResult(
            valueString = formattedNumber,
            unitSymbolEn = targetUnit.symbolEn,
            unitSymbolAr = targetUnit.symbolAr
        )
    }

    private fun chooseAutoUnit(bytes: Long): ByteUnit {
        val absBytes = Math.abs(bytes)
        return when {
            absBytes >= ByteUnit.TB.factor * 0.9 -> ByteUnit.TB
            absBytes >= ByteUnit.GB.factor * 0.9 -> ByteUnit.GB
            absBytes >= ByteUnit.MB.factor * 0.9 -> ByteUnit.MB
            absBytes >= ByteUnit.KB.factor * 0.9 -> ByteUnit.KB
            else -> ByteUnit.B
        }
    }
}
