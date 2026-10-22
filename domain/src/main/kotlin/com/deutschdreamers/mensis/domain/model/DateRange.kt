package com.deutschdreamers.mensis.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** An inclusive range of calendar days. */
data class DateRange(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        require(!end.isBefore(start)) { "end $end is before start $start" }
    }

    val lengthDays: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun shift(days: Long): DateRange = DateRange(start.plusDays(days), end.plusDays(days))

    companion object {
        fun single(date: LocalDate): DateRange = DateRange(date, date)
    }
}

/** Whole days from [this] to [other]; positive when [other] is later. */
fun LocalDate.daysUntil(other: LocalDate): Int = ChronoUnit.DAYS.between(this, other).toInt()
