package com.example

import com.example.data.preferences.AppLanguage
import com.example.model.DateRange
import com.example.model.PeriodPreset
import com.example.util.DateTimeUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DateRangeAndDateTimeTest {

    private val fixedZone = ZoneId.of("UTC")
    // Fixed instant: 2024-06-15 12:00:00 UTC
    private val fixedInstant = Instant.parse("2024-06-15T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, fixedZone)

    @Before
    fun setUp() {
        DateTimeUtils.setClockForTesting(fixedClock)
    }

    @After
    fun tearDown() {
        DateTimeUtils.resetClockForTesting()
    }

    @Test
    fun `DateRange fromPreset generates accurate boundaries for Today`() {
        val range = DateRange.fromPreset(PeriodPreset.TODAY, fixedZone)
        val expectedDate = LocalDate.of(2024, 6, 15)

        assertEquals(expectedDate, range.startDate)
        assertEquals(expectedDate, range.endDate)
        assertEquals(PeriodPreset.TODAY, range.preset)
        assertTrue(range.isSingleDay)
        assertTrue(range.isCurrentDay)

        // Start is at 00:00:00 UTC
        val expectedStartMs = Instant.parse("2024-06-15T00:00:00Z").toEpochMilli()
        assertEquals(expectedStartMs, range.getStartEpochMs())

        // End of today is capped at current instant (nowMs)
        assertEquals(fixedInstant.toEpochMilli(), range.getEndEpochMs())
    }

    @Test
    fun `DateRange fromPreset generates accurate boundaries for Yesterday`() {
        val range = DateRange.fromPreset(PeriodPreset.YESTERDAY, fixedZone)
        val expectedDate = LocalDate.of(2024, 6, 14)

        assertEquals(expectedDate, range.startDate)
        assertEquals(expectedDate, range.endDate)
        assertEquals(PeriodPreset.YESTERDAY, range.preset)
        assertTrue(range.isSingleDay)
        assertFalse(range.isCurrentDay)

        val expectedStartMs = Instant.parse("2024-06-14T00:00:00Z").toEpochMilli()
        val expectedEndMs = Instant.parse("2024-06-14T23:59:59.999Z").toEpochMilli()
        assertEquals(expectedStartMs, range.getStartEpochMs())
        assertEquals(expectedEndMs, range.getEndEpochMs())
    }

    @Test
    fun `DateRange fromPreset generates accurate multi-day ranges`() {
        val last7Days = DateRange.fromPreset(PeriodPreset.LAST_7_DAYS, fixedZone)
        assertEquals(LocalDate.of(2024, 6, 9), last7Days.startDate)
        assertEquals(LocalDate.of(2024, 6, 15), last7Days.endDate)
        assertFalse(last7Days.isSingleDay)

        val last30Days = DateRange.fromPreset(PeriodPreset.LAST_30_DAYS, fixedZone)
        assertEquals(LocalDate.of(2024, 5, 17), last30Days.startDate)
        assertEquals(LocalDate.of(2024, 6, 15), last30Days.endDate)

        val thisMonth = DateRange.fromPreset(PeriodPreset.THIS_MONTH, fixedZone)
        assertEquals(LocalDate.of(2024, 6, 1), thisMonth.startDate)
        assertEquals(LocalDate.of(2024, 6, 15), thisMonth.endDate)
    }

    @Test
    fun `DateRange custom automatically normalizes reversed start and end dates`() {
        val laterDate = LocalDate.of(2024, 6, 10)
        val earlierDate = LocalDate.of(2024, 6, 1)

        val range = DateRange.custom(startDate = laterDate, endDate = earlierDate, zoneId = fixedZone)

        assertEquals(earlierDate, range.startDate)
        assertEquals(laterDate, range.endDate)
        assertEquals(PeriodPreset.CUSTOM, range.preset)
    }

    @Test
    fun `DateRange custom clamps future dates to today`() {
        val today = LocalDate.of(2024, 6, 15)
        val futureStart = LocalDate.of(2024, 7, 1)
        val futureEnd = LocalDate.of(2024, 7, 10)

        val range = DateRange.custom(futureStart, futureEnd, fixedZone)

        assertEquals(today, range.startDate)
        assertEquals(today, range.endDate)
    }

    @Test
    fun `DateRange direct constructor throws on reversed or future dates`() {
        val today = LocalDate.of(2024, 6, 15)

        // Reversed
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(
                startDate = LocalDate.of(2024, 6, 10),
                endDate = LocalDate.of(2024, 6, 5),
                zoneId = fixedZone
            )
        }

        // Future start
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(
                startDate = today.plusDays(1),
                endDate = today.plusDays(2),
                zoneId = fixedZone
            )
        }

        // Future end
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(
                startDate = today.minusDays(2),
                endDate = today.plusDays(1),
                zoneId = fixedZone
            )
        }
    }

    @Test
    fun `DateRange previousPeriod and nextPeriod navigate accurately`() {
        val range = DateRange(
            startDate = LocalDate.of(2024, 6, 1),
            endDate = LocalDate.of(2024, 6, 7),
            preset = PeriodPreset.CUSTOM,
            zoneId = fixedZone
        )

        val prev = range.previousPeriod()
        assertEquals(LocalDate.of(2024, 5, 25), prev.startDate)
        assertEquals(LocalDate.of(2024, 5, 31), prev.endDate)

        val next = prev.nextPeriod()
        assertEquals(LocalDate.of(2024, 6, 1), next.startDate)
        assertEquals(LocalDate.of(2024, 6, 7), next.endDate)

        // Next period cannot advance past today (2024-06-15)
        val nearTodayRange = DateRange(
            startDate = LocalDate.of(2024, 6, 10),
            endDate = LocalDate.of(2024, 6, 15),
            preset = PeriodPreset.CUSTOM,
            zoneId = fixedZone
        )
        val attemptAdvance = nearTodayRange.nextPeriod()
        assertEquals(nearTodayRange, attemptAdvance)
    }

    @Test
    fun `Timezone support handles non-UTC local zones correctly`() {
        val riyadhZone = ZoneId.of("Asia/Riyadh") // UTC+3
        val testDate = LocalDate.of(2024, 6, 1)

        val startMs = DateTimeUtils.getStartOfDayEpochMs(testDate, riyadhZone)
        val endMs = DateTimeUtils.getEndOfDayEpochMs(testDate, riyadhZone)

        // 2024-06-01 00:00:00 Asia/Riyadh is 2024-05-31 21:00:00 UTC
        val expectedStartInstant = Instant.parse("2024-05-31T21:00:00Z")
        val expectedEndInstant = Instant.parse("2024-06-01T20:59:59.999Z")

        assertEquals(expectedStartInstant.toEpochMilli(), startMs)
        assertEquals(expectedEndInstant.toEpochMilli(), endMs)
    }

    @Test
    fun `Daylight Saving Time spring transition 23-hour day is handled properly`() {
        val nyZone = ZoneId.of("America/New_York")
        // 2024-03-10 is the Spring DST transition day in US/Eastern (clocks jump 2:00 -> 3:00, total 23 hours)
        val springDstDay = LocalDate.of(2024, 3, 10)

        val startMs = DateTimeUtils.getStartOfDayEpochMs(springDstDay, nyZone)
        val endMs = DateTimeUtils.getEndOfDayEpochMs(springDstDay, nyZone)

        // Total duration should be exactly 23 hours minus 1 ms
        val durationMs = (endMs - startMs) + 1
        val twentyThreeHoursMs = 23L * 60 * 60 * 1000

        assertEquals(twentyThreeHoursMs, durationMs)
    }

    @Test
    fun `Daylight Saving Time autumn transition 25-hour day is handled properly`() {
        val nyZone = ZoneId.of("America/New_York")
        // 2023-11-05 is the Autumn DST transition day in US/Eastern (clocks fall back 2:00 -> 1:00, total 25 hours)
        val autumnDstDay = LocalDate.of(2023, 11, 5)

        val startMs = DateTimeUtils.getStartOfDayEpochMs(autumnDstDay, nyZone)
        val endMs = DateTimeUtils.getEndOfDayEpochMs(autumnDstDay, nyZone)

        // Total duration should be exactly 25 hours minus 1 ms
        val durationMs = (endMs - startMs) + 1
        val twentyFiveHoursMs = 25L * 60 * 60 * 1000

        assertEquals(twentyFiveHoursMs, durationMs)
    }

    @Test
    fun `generateDaysBetween correctly outputs dates sequence across DST transitions`() {
        val nyZone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2024, 3, 9)
        val end = LocalDate.of(2024, 3, 11)

        val days = DateTimeUtils.generateDaysBetween(start, end)

        assertEquals(3, days.size)
        assertEquals(LocalDate.of(2024, 3, 9), days[0])
        assertEquals(LocalDate.of(2024, 3, 10), days[1])
        assertEquals(LocalDate.of(2024, 3, 11), days[2])
    }

    @Test
    fun `formatDisplay formats both Arabic and English accurately`() {
        val singleDay = DateRange(
            startDate = LocalDate.of(2024, 5, 20),
            endDate = LocalDate.of(2024, 5, 20),
            preset = PeriodPreset.CUSTOM,
            zoneId = fixedZone
        )
        val multiDay = DateRange(
            startDate = LocalDate.of(2024, 5, 1),
            endDate = LocalDate.of(2024, 5, 10),
            preset = PeriodPreset.CUSTOM,
            zoneId = fixedZone
        )

        val enSingle = singleDay.formatDisplay(AppLanguage.EN)
        assertTrue(enSingle.contains("May 20, 2024"))

        val arSingle = singleDay.formatDisplay(AppLanguage.AR)
        assertTrue(arSingle.contains("20") && arSingle.contains("2024"))

        val enMulti = multiDay.formatDisplay(AppLanguage.EN)
        assertTrue(enMulti.contains("May 1, 2024") && enMulti.contains("May 10, 2024"))
    }
}
