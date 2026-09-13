package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.AppSettingsPreferences
import com.example.domain.repository.NetworkStatsRepository
import com.example.model.BillingCycleCalculator
import com.example.model.BillingCyclePeriod
import com.example.model.ByteUnit
import com.example.model.DailyNetworkUsage
import com.example.model.DataPlanCalculator
import com.example.model.DataPlanConfig
import com.example.model.DataPlanStatus
import com.example.model.DateRange
import com.example.model.NetworkStatsDebugInfo
import com.example.model.NetworkType
import com.example.model.NetworkUsage
import com.example.model.NetworkUsageSummary
import com.example.ui.viewmodel.DataPlanViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataPlanTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // ========================================================================
    // A. Billing Cycle Tests
    // ========================================================================

    @Test
    fun billingCycle_normalMonth_onOrAfterBillingDay() {
        // Today is Sept 13, 2026. Billing day is 10.
        // Cycle starts Sept 10, 2026 and ends Oct 9, 2026 (next cycle starts Oct 10, 2026).
        val refDate = LocalDate.of(2026, 9, 13)
        val period = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 10,
            referenceDate = refDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2026, 9, 10), period.startDate)
        assertEquals(LocalDate.of(2026, 10, 9), period.endDate)
        assertEquals(LocalDate.of(2026, 10, 10), period.nextCycleStartDate)
    }

    @Test
    fun billingCycle_crossingMonthBoundary_beforeBillingDay() {
        // Today is Sept 5, 2026. Billing day is 15.
        // Cycle starts Aug 15, 2026 and ends Sept 14, 2026 (next cycle starts Sept 15, 2026).
        val refDate = LocalDate.of(2026, 9, 5)
        val period = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 15,
            referenceDate = refDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2026, 8, 15), period.startDate)
        assertEquals(LocalDate.of(2026, 9, 14), period.endDate)
        assertEquals(LocalDate.of(2026, 9, 15), period.nextCycleStartDate)
    }

    @Test
    fun billingCycle_yearBoundary_januaryBeforeBillingDay() {
        // Today is Jan 5, 2026. Billing day is 15.
        // Cycle started Dec 15, 2025 and ends Jan 14, 2026 (next cycle starts Jan 15, 2026).
        val refDate = LocalDate.of(2026, 1, 5)
        val period = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 15,
            referenceDate = refDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2025, 12, 15), period.startDate)
        assertEquals(LocalDate.of(2026, 1, 14), period.endDate)
        assertEquals(LocalDate.of(2026, 1, 15), period.nextCycleStartDate)
    }

    @Test
    fun billingCycle_yearBoundary_decemberOnOrAfterBillingDay() {
        // Today is Dec 20, 2025. Billing day is 15.
        // Cycle starts Dec 15, 2025 and ends Jan 14, 2026 (next cycle starts Jan 15, 2026).
        val refDate = LocalDate.of(2025, 12, 20)
        val period = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 15,
            referenceDate = refDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2025, 12, 15), period.startDate)
        assertEquals(LocalDate.of(2026, 1, 14), period.endDate)
        assertEquals(LocalDate.of(2026, 1, 15), period.nextCycleStartDate)
    }

    @Test
    fun billingCycle_billingDay1() {
        // Billing day is 1. Today is Sept 13, 2026.
        // Cycle starts Sept 1, 2026 and ends Sept 30, 2026 (next starts Oct 1, 2026).
        val refDate = LocalDate.of(2026, 9, 13)
        val period = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 1,
            referenceDate = refDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2026, 9, 1), period.startDate)
        assertEquals(LocalDate.of(2026, 9, 30), period.endDate)
        assertEquals(LocalDate.of(2026, 10, 1), period.nextCycleStartDate)
    }

    @Test
    fun billingCycle_billingDay31_inMonthWith30Days() {
        // September has 30 days. Billing day is 31.
        // Effective cycle date in September is Sept 30.
        // Today is Sept 13 (< Sept 30).
        // Previous month was August (31 days). So cycle started Aug 31, 2026.
        // Ends Sept 29, 2026. Next starts Sept 30, 2026.
        val refDate = LocalDate.of(2026, 9, 13)
        val period = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 31,
            referenceDate = refDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2026, 8, 31), period.startDate)
        assertEquals(LocalDate.of(2026, 9, 29), period.endDate)
        assertEquals(LocalDate.of(2026, 9, 30), period.nextCycleStartDate)
    }

    @Test
    fun billingCycle_february_nonLeapYear_billingDay31() {
        // 2026 is non-leap (Feb has 28 days). Billing day is 31.
        // Effective cycle date in Feb is Feb 28.

        // Case A: mid-February (Feb 15 < Feb 28).
        // Cycle started Jan 31, 2026. Ends Feb 27, 2026. Next starts Feb 28, 2026.
        val midFeb = LocalDate.of(2026, 2, 15)
        val periodMidFeb = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 31,
            referenceDate = midFeb,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(LocalDate.of(2026, 1, 31), periodMidFeb.startDate)
        assertEquals(LocalDate.of(2026, 2, 27), periodMidFeb.endDate)
        assertEquals(LocalDate.of(2026, 2, 28), periodMidFeb.nextCycleStartDate)

        // Case B: last day of Feb (Feb 28 >= Feb 28).
        // Cycle starts Feb 28, 2026. March has 31 days. Ends March 30, 2026. Next starts March 31, 2026.
        val lastDayFeb = LocalDate.of(2026, 2, 28)
        val periodLastDayFeb = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 31,
            referenceDate = lastDayFeb,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(LocalDate.of(2026, 2, 28), periodLastDayFeb.startDate)
        assertEquals(LocalDate.of(2026, 3, 30), periodLastDayFeb.endDate)
        assertEquals(LocalDate.of(2026, 3, 31), periodLastDayFeb.nextCycleStartDate)
    }

    @Test
    fun billingCycle_shortMonths_leapYearFebruary() {
        // 2024 is leap year (Feb has 29 days). Billing day is 30.
        // Feb effective cycle date is Feb 29.
        val midFeb2024 = LocalDate.of(2024, 2, 10)
        val period = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 30,
            referenceDate = midFeb2024,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(LocalDate.of(2024, 1, 30), period.startDate)
        assertEquals(LocalDate.of(2024, 2, 28), period.endDate)
        assertEquals(LocalDate.of(2024, 2, 29), period.nextCycleStartDate)
    }

    @Test
    fun billingCycle_localTimezoneBehavior() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val fixedInstant = Instant.parse("2026-09-13T20:00:00Z")
        val tokyoDate = LocalDate.ofInstant(fixedInstant, tokyoZone) // 2026-09-14 in Tokyo

        val period = BillingCycleCalculator.calculateCurrentPeriod(
            billingCycleStartDay = 15,
            referenceDate = tokyoDate,
            zoneId = tokyoZone
        )

        // Since tokyoDate is 2026-09-14, which is before the 15th:
        assertEquals(LocalDate.of(2026, 8, 15), period.startDate)
        assertEquals(LocalDate.of(2026, 9, 14), period.endDate)
        assertEquals(LocalDate.of(2026, 9, 15), period.nextCycleStartDate)
        assertEquals(tokyoZone, period.zoneId)
    }

    // ========================================================================
    // B. Data-Plan Calculations Tests
    // ========================================================================

    @Test
    fun calculations_usageBelowLimit() {
        val limit = 100L * 1024 * 1024 // 100 MB
        val used = 40L * 1024 * 1024  // 40 MB
        val config = DataPlanConfig(enabled = true, limitBytes = limit)
        val cycle = BillingCycleCalculator.calculateCurrentPeriod(1, LocalDate.of(2026, 9, 13))

        val status = DataPlanCalculator.calculate(config, cycle, used)
        assertTrue(status is DataPlanStatus.Active)
        val active = status as DataPlanStatus.Active

        assertEquals(used, active.usedBytes)
        assertEquals(limit, active.limitBytes)
        assertEquals(60L * 1024 * 1024, active.remainingBytes)
        assertEquals(0.4f, active.usageFraction, 0.001f)
        assertFalse(active.isOverLimit)
        assertEquals(0L, active.excessBytes)
    }

    @Test
    fun calculations_usageExactlyAtLimit() {
        val limit = 100L * 1024 * 1024
        val config = DataPlanConfig(enabled = true, limitBytes = limit)
        val cycle = BillingCycleCalculator.calculateCurrentPeriod(1, LocalDate.of(2026, 9, 13))

        val status = DataPlanCalculator.calculate(config, cycle, limit)
        assertTrue(status is DataPlanStatus.Active)
        val active = status as DataPlanStatus.Active

        assertEquals(limit, active.usedBytes)
        assertEquals(0L, active.remainingBytes)
        assertEquals(1.0f, active.usageFraction, 0.001f)
        assertFalse(active.isOverLimit)
        assertEquals(0L, active.excessBytes)
    }

    @Test
    fun calculations_usageAboveLimit() {
        val limit = 100L * 1024 * 1024
        val used = 150L * 1024 * 1024
        val config = DataPlanConfig(enabled = true, limitBytes = limit)
        val cycle = BillingCycleCalculator.calculateCurrentPeriod(1, LocalDate.of(2026, 9, 13))

        val status = DataPlanCalculator.calculate(config, cycle, used)
        assertTrue(status is DataPlanStatus.Active)
        val active = status as DataPlanStatus.Active

        assertEquals(used, active.usedBytes) // raw used bytes preserved without mutation
        assertEquals(0L, active.remainingBytes) // remaining capped at zero
        assertEquals(1.0f, active.usageFraction, 0.001f) // usage fraction capped at 1.0f
        assertTrue(active.isOverLimit)
        assertEquals(50L * 1024 * 1024, active.excessBytes)
    }

    @Test
    fun calculations_remainingReachesZeroAndNeverNegative() {
        val limit = 50L
        val used = 200L
        val config = DataPlanConfig(enabled = true, limitBytes = limit)
        val cycle = BillingCycleCalculator.calculateCurrentPeriod(1, LocalDate.of(2026, 9, 13))

        val status = DataPlanCalculator.calculate(config, cycle, used)
        assertTrue(status is DataPlanStatus.Active)
        val active = status as DataPlanStatus.Active

        assertEquals(0L, active.remainingBytes)
    }

    @Test
    fun calculations_fractionCappedAtOne() {
        val limit = 10L * 1024 * 1024
        val used = 100L * 1024 * 1024 // 10x limit
        val config = DataPlanConfig(enabled = true, limitBytes = limit)
        val cycle = BillingCycleCalculator.calculateCurrentPeriod(1, LocalDate.of(2026, 9, 13))

        val status = DataPlanCalculator.calculate(config, cycle, used)
        assertTrue(status is DataPlanStatus.Active)
        val active = status as DataPlanStatus.Active

        assertEquals(1.0f, active.usageFraction, 0.0001f)
    }

    @Test
    fun calculations_longOverflowSafety() {
        val limit = 1000L
        val used = Long.MAX_VALUE
        val config = DataPlanConfig(enabled = true, limitBytes = limit)
        val cycle = BillingCycleCalculator.calculateCurrentPeriod(1, LocalDate.of(2026, 9, 13))

        val status = DataPlanCalculator.calculate(config, cycle, used)
        assertTrue(status is DataPlanStatus.Active)
        val active = status as DataPlanStatus.Active

        assertEquals(0L, active.remainingBytes)
        assertEquals(1.0f, active.usageFraction, 0.0001f)
        assertTrue(active.isOverLimit)
    }

    @Test
    fun calculations_disabledPlan() {
        val config = DataPlanConfig(enabled = false, limitBytes = 1000L)
        val cycle = BillingCycleCalculator.calculateCurrentPeriod(1, LocalDate.of(2026, 9, 13))

        val status = DataPlanCalculator.calculate(config, cycle, 500L)
        assertEquals(DataPlanStatus.Disabled, status)

        // Also if limit is 0, disabled
        val zeroLimitConfig = DataPlanConfig(enabled = true, limitBytes = 0L)
        assertEquals(DataPlanStatus.Disabled, DataPlanCalculator.calculate(zeroLimitConfig, cycle, 500L))
    }

    // ========================================================================
    // C. Preferences Tests
    // ========================================================================

    @Test
    fun preferences_defaultState() {
        val prefs = AppSettingsPreferences(context)
        val config = prefs.getDataPlanConfig()

        assertFalse(config.enabled)
        assertEquals(0L, config.limitBytes)
        assertEquals(1, config.billingCycleStartDay)
        assertEquals(ByteUnit.GB, config.unit)
        assertEquals(config, prefs.dataPlanConfig.value)
    }

    @Test
    fun preferences_saveAndLoad() {
        val prefs = AppSettingsPreferences(context)
        val newConfig = DataPlanConfig(
            enabled = true,
            limitBytes = 5L * 1024 * 1024 * 1024,
            billingCycleStartDay = 15,
            unit = ByteUnit.GB
        )

        prefs.setDataPlanConfig(newConfig)

        val loaded = prefs.getDataPlanConfig()
        assertTrue(loaded.enabled)
        assertEquals(5L * 1024 * 1024 * 1024, loaded.limitBytes)
        assertEquals(15, loaded.billingCycleStartDay)
        assertEquals(ByteUnit.GB, loaded.unit)
        assertEquals(loaded, prefs.dataPlanConfig.value)
    }

    @Test
    fun preferences_enableAndDisable() {
        val prefs = AppSettingsPreferences(context)
        prefs.setDataPlanLimit(10L * 1024 * 1024, ByteUnit.MB)
        prefs.setDataPlanEnabled(true)

        assertTrue(prefs.getDataPlanConfig().enabled)

        prefs.setDataPlanEnabled(false)
        assertFalse(prefs.getDataPlanConfig().enabled)
        assertEquals(10L * 1024 * 1024, prefs.getDataPlanConfig().limitBytes)
    }

    @Test
    fun preferences_invalidValues_failSafelyAndNormalize() {
        val sp = context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE)

        // Persist invalid/out-of-bound values directly
        sp.edit()
            .putBoolean(AppSettingsPreferences.KEY_DATA_PLAN_ENABLED, true)
            .putLong(AppSettingsPreferences.KEY_DATA_PLAN_LIMIT_BYTES, -500L) // negative limit
            .putInt(AppSettingsPreferences.KEY_DATA_PLAN_BILLING_DAY, 99) // invalid day
            .putString(AppSettingsPreferences.KEY_DATA_PLAN_UNIT, "INVALID_UNIT") // bad unit
            .commit()

        val prefs = AppSettingsPreferences(context)
        val config = prefs.getDataPlanConfig()

        // Negative limit normalized to 0L, which disables the plan safely
        assertEquals(0L, config.limitBytes)
        assertFalse(config.enabled)
        // Billing day clamped to 31
        assertEquals(31, config.billingCycleStartDay)
        // Unit safely falls back to GB
        assertEquals(ByteUnit.GB, config.unit)
    }

    @Test
    fun preferences_immediateStateFlowPropagation_acrossMultipleInstances() {
        val instance1 = AppSettingsPreferences(context)
        val instance2 = AppSettingsPreferences(context)

        assertEquals(instance1.getDataPlanConfig(), instance2.getDataPlanConfig())

        val updated = DataPlanConfig(
            enabled = true,
            limitBytes = 20L * 1024 * 1024 * 1024,
            billingCycleStartDay = 7,
            unit = ByteUnit.GB
        )

        instance1.setDataPlanConfig(updated)

        // Verify instance2 immediately gets the updated StateFlow
        assertEquals(updated, instance2.dataPlanConfig.value)
        assertEquals(updated, instance2.getDataPlanConfig())

        // And instance2 updating propagates back to instance1
        instance2.setDataPlanBillingDay(20)
        assertEquals(20, instance1.dataPlanConfig.value.billingCycleStartDay)
        assertEquals(20, instance1.getDataPlanConfig().billingCycleStartDay)
    }

    // ========================================================================
    // D. Integration Tests
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun integration_currentPeriodUsageFromNetworkStatsRepository_andNoPersistedUsageTotal() = runTest {
        var queryReceivedRange: DateRange? = null
        val expectedDownload = 250L * 1024 * 1024
        val expectedUpload = 50L * 1024 * 1024
        val expectedTotal = expectedDownload + expectedUpload

        val fakeRepo = object : NetworkStatsRepository {
            override fun hasUsageStatsPermission(): Boolean = true

            override suspend fun getUsageForRange(dateRange: DateRange): Result<NetworkUsageSummary> {
                queryReceivedRange = dateRange
                return Result.success(
                    NetworkUsageSummary(
                        wifi = NetworkUsage(downloadBytes = 0L, uploadBytes = 0L, startTime = 0L, endTime = 0L, networkType = NetworkType.WIFI),
                        mobile = NetworkUsage(downloadBytes = expectedDownload, uploadBytes = expectedUpload, startTime = 0L, endTime = 0L, networkType = NetworkType.MOBILE),
                        total = NetworkUsage(downloadBytes = expectedDownload, uploadBytes = expectedUpload, startTime = 0L, endTime = 0L, networkType = NetworkType.TOTAL),
                        dateRange = dateRange
                    )
                )
            }

            override suspend fun getDailyUsageBreakdown(startDate: LocalDate, endDate: LocalDate): Result<List<DailyNetworkUsage>> =
                Result.success(emptyList())

            override suspend fun getDebugInfo(networkType: NetworkType, startTimeMs: Long, endTimeMs: Long): Result<NetworkStatsDebugInfo> =
                Result.failure(UnsupportedOperationException())
        }

        val prefs = AppSettingsPreferences(context)
        val fixedToday = LocalDate.of(2026, 9, 13)
        val viewModel = DataPlanViewModel(
            repository = fakeRepo,
            appSettingsPreferences = prefs,
            zoneId = ZoneOffset.UTC
        )

        // Enable plan with 1 GB limit
        val planConfig = DataPlanConfig(
            enabled = true,
            limitBytes = 1024L * 1024 * 1024,
            billingCycleStartDay = 1,
            unit = ByteUnit.GB
        )
        viewModel.updateConfig(planConfig)
        advanceUntilIdle()

        // Verify usage came from repository query
        assertTrue("Repository must have been queried", queryReceivedRange != null)
        val status = viewModel.uiState.value.status
        assertTrue("Status must be Active", status is DataPlanStatus.Active)
        val active = status as DataPlanStatus.Active

        assertEquals(expectedTotal, active.usedBytes)
        assertEquals(planConfig.limitBytes, active.limitBytes)
        assertEquals(planConfig.limitBytes - expectedTotal, active.remainingBytes)
        assertFalse(active.isOverLimit)

        // Verify NO usage totals or counters are stored in SharedPreferences
        val sp = context.getSharedPreferences("netpulse_settings_prefs", Context.MODE_PRIVATE)
        val allPrefs = sp.all
        assertFalse("No used bytes must be persisted", allPrefs.containsKey("used_bytes"))
        assertFalse("No used bytes must be persisted", allPrefs.containsKey("usedBytes"))
        assertFalse("No remaining bytes must be persisted", allPrefs.containsKey("remaining_bytes"))
        assertFalse("No usage snapshot must be persisted", allPrefs.containsKey("monthly_snapshot"))
        assertFalse("No usage counter must be persisted", allPrefs.containsKey("usage_counter"))
    }
}
