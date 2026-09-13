package com.example.model

/**
 * Domain model representing user-configured data plan.
 * Only configuration parameters are persisted, never usage totals.
 */
data class DataPlanConfig(
    val enabled: Boolean = false,
    val limitBytes: Long = 0L,
    val billingCycleStartDay: Int = DEFAULT_BILLING_CYCLE_START_DAY,
    val unit: ByteUnit = ByteUnit.GB
) {
    companion object {
        const val DEFAULT_BILLING_CYCLE_START_DAY = 1
        const val MIN_BILLING_CYCLE_START_DAY = 1
        const val MAX_BILLING_CYCLE_START_DAY = 31

        fun disabled(): DataPlanConfig = DataPlanConfig(
            enabled = false,
            limitBytes = 0L,
            billingCycleStartDay = DEFAULT_BILLING_CYCLE_START_DAY,
            unit = ByteUnit.GB
        )

        fun fromUnitValue(
            enabled: Boolean,
            value: Double,
            unit: ByteUnit,
            billingCycleStartDay: Int = DEFAULT_BILLING_CYCLE_START_DAY
        ): DataPlanConfig {
            val safeValue = if (value.isNaN() || value < 0.0) 0.0 else value
            val bytes = (safeValue * unit.factor).toLong()
            return DataPlanConfig(
                enabled = enabled,
                limitBytes = bytes,
                billingCycleStartDay = billingCycleStartDay,
                unit = unit
            ).normalized()
        }
    }

    /**
     * Value converted to the configured unit for presentation or editing.
     */
    val valueInUnit: Double
        get() = if (limitBytes > 0L) limitBytes.toDouble() / unit.factor else 0.0

    /**
     * Produces a safely normalized instance:
     * - billingCycleStartDay coerced to 1..31
     * - limitBytes >= 0L
     * - enabled is true ONLY if limitBytes > 0L
     */
    fun normalized(): DataPlanConfig {
        val safeDay = billingCycleStartDay.coerceIn(MIN_BILLING_CYCLE_START_DAY, MAX_BILLING_CYCLE_START_DAY)
        val safeLimit = maxOf(0L, limitBytes)
        val safeEnabled = enabled && safeLimit > 0L
        return copy(
            enabled = safeEnabled,
            limitBytes = safeLimit,
            billingCycleStartDay = safeDay,
            unit = unit
        )
    }
}
