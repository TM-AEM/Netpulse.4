package com.example.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {
    @Volatile
    private var overrideClock: Clock? = null

    fun setClockForTesting(clock: Clock) {
        overrideClock = clock
    }

    fun resetClockForTesting() {
        overrideClock = null
    }

    fun getClock(): Clock {
        return overrideClock ?: Clock.systemDefaultZone()
    }

    fun getLocalZoneId(): ZoneId {
        return overrideClock?.zone ?: ZoneId.systemDefault()
    }

    fun now(): Instant {
        return getClock().instant()
    }

    fun today(zoneId: ZoneId = getLocalZoneId()): LocalDate {
        return LocalDate.now(getClock().withZone(zoneId))
    }

    fun getStartOfDayEpochMs(date: LocalDate, zoneId: ZoneId = getLocalZoneId()): Long {
        val currentDay = today(zoneId)
        val boundedDate = if (date.isAfter(currentDay)) currentDay else date
        return boundedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun getEndOfDayEpochMs(date: LocalDate, zoneId: ZoneId = getLocalZoneId()): Long {
        val currentDay = today(zoneId)
        val nowMs = now().toEpochMilli()
        val startOfTodayMs = currentDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
        return when {
            date >= currentDay -> maxOf(startOfTodayMs, nowMs)
            else -> date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        }
    }

    fun getDayRange(date: LocalDate, zoneId: ZoneId = getLocalZoneId()): Pair<Long, Long> {
        val startMs = getStartOfDayEpochMs(date, zoneId)
        val endMs = getEndOfDayEpochMs(date, zoneId)
        return Pair(startMs, endMs)
    }

    fun generateDaysBetween(startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        require(!startDate.isAfter(endDate)) { "startDate ($startDate) cannot be after endDate ($endDate)" }
        val days = mutableListOf<LocalDate>()
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            days.add(curr)
            curr = curr.plusDays(1)
        }
        return days
    }

    private val arabicDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ar"))
    private val englishDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)

    fun formatDateArabic(date: LocalDate): String = date.format(arabicDateFormatter)
    fun formatDateEnglish(date: LocalDate): String = date.format(englishDateFormatter)

    fun formatEpochTime(epochMs: Long, zoneId: ZoneId = getLocalZoneId()): String {
        return Instant.ofEpochMilli(epochMs).atZone(zoneId).format(timeFormatter)
    }

    fun formatDayNameArabic(date: LocalDate): String {
        val arabicDayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("ar"))
        return date.format(arabicDayFormatter)
    }
}
