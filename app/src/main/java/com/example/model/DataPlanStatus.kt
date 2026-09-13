package com.example.model

/**
 * Domain status representing the state of the data plan calculation.
 */
sealed class DataPlanStatus {
    /**
     * Plan is either disabled or limit is not set (> 0).
     * No artificial limit is fabricated.
     */
    object Disabled : DataPlanStatus()

    /**
     * Active plan with verified limit and calculated consumption.
     */
    data class Active(
        val config: DataPlanConfig,
        val cyclePeriod: BillingCyclePeriod,
        val usedBytes: Long,
        val limitBytes: Long,
        val remainingBytes: Long,
        val usageFraction: Float, // 0.0f..1.0f capped for UI progress display
        val isOverLimit: Boolean,
        val excessBytes: Long = 0L
    ) : DataPlanStatus()
}

/**
 * Pure calculation logic for deriving data plan allowance, remaining bytes, and usage percentage.
 */
object DataPlanCalculator {

    /**
     * Calculates data plan status given [config], [cyclePeriod], and [usedBytes].
     *
     * Invariants:
     * - Disabled plan produces [DataPlanStatus.Disabled] without fabricating values.
     * - [usedBytes] is preserved accurately without mutation.
     * - [remainingBytes] reaches 0L and never goes negative.
     * - [usageFraction] is capped to [0.0f, 1.0f] for UI visual safety.
     * - Calculations are safe against Long overflow.
     */
    fun calculate(
        config: DataPlanConfig,
        cyclePeriod: BillingCyclePeriod,
        usedBytes: Long
    ): DataPlanStatus {
        val normalized = config.normalized()
        if (!normalized.enabled || normalized.limitBytes <= 0L) {
            return DataPlanStatus.Disabled
        }

        val safeUsedBytes = maxOf(0L, usedBytes)
        val limitBytes = normalized.limitBytes
        val remainingBytes = if (safeUsedBytes >= limitBytes) 0L else limitBytes - safeUsedBytes
        val isOverLimit = safeUsedBytes > limitBytes
        val excessBytes = if (isOverLimit) safeUsedBytes - limitBytes else 0L

        val rawFraction = if (limitBytes > 0L) {
            safeUsedBytes.toDouble() / limitBytes.toDouble()
        } else {
            0.0
        }
        val usageFraction = rawFraction.toFloat().coerceIn(0.0f, 1.0f)

        return DataPlanStatus.Active(
            config = normalized,
            cyclePeriod = cyclePeriod,
            usedBytes = safeUsedBytes,
            limitBytes = limitBytes,
            remainingBytes = remainingBytes,
            usageFraction = usageFraction,
            isOverLimit = isOverLimit,
            excessBytes = excessBytes
        )
    }
}
