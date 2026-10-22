package com.deutschdreamers.mensis.ui.format

import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.model.daysUntil
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date and range wording used across Today, Calendar and the log. Pure Kotlin so it is unit-tested on
 * the JVM; English only, matching the design copy ("Wed 21 Oct", "2–5 Nov", "30 Oct – 4 Nov").
 */
object Formats {
    private val locale = Locale.ENGLISH
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", locale)
    private val fullMonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val weekdayFull = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)

    const val EN_DASH: String = "–"
    const val MIDDLE_DOT: String = "·"

    /** "Wed 21 Oct" */
    fun weekdayDayMonth(date: LocalDate): String = date.format(weekdayDayMonth)

    /** "21 Oct" */
    fun dayMonth(date: LocalDate): String = date.format(dayMonth)

    /** "21 Oct 2026" */
    fun dayMonthYear(date: LocalDate): String = date.format(dayMonthYear)

    /** "Mar 2031" */
    fun monthYear(date: LocalDate): String = date.format(monthYear)

    /** "October 2026" */
    fun fullMonthYear(date: LocalDate): String = date.format(fullMonthYear)

    /** "Wednesday 21 October" (accessibility). */
    fun spokenDate(date: LocalDate): String = date.format(weekdayFull)

    /** "06:40" */
    fun time(value: LocalTime): String = value.format(time)

    /** "6 Mar – 21 Oct 2026", "6 Mar 2025 – 21 Oct 2026", "21 Oct 2026" for a single day. */
    fun rangeWithYear(
        start: LocalDate,
        end: LocalDate,
    ): String =
        when {
            start == end -> dayMonthYear(end)
            start.year == end.year -> "${dayMonth(start)} $EN_DASH ${dayMonthYear(end)}"
            else -> "${dayMonthYear(start)} $EN_DASH ${dayMonthYear(end)}"
        }

    /** "2–5 Nov", "30 Oct – 4 Nov", "21 Oct" for a single day. */
    fun range(range: DateRange): String = range(range.start, range.end)

    fun range(
        start: LocalDate,
        end: LocalDate,
    ): String =
        when {
            start == end -> dayMonth(start)
            start.month == end.month && start.year == end.year -> "${start.dayOfMonth}$EN_DASH${dayMonth(end)}"
            else -> "${dayMonth(start)} $EN_DASH ${dayMonth(end)}"
        }

    /** "between 15 and 18 Oct", "between 30 Oct and 2 Nov". */
    fun between(range: DateRange): String =
        if (range.start.month == range.end.month && range.start.year == range.end.year) {
            "between ${range.start.dayOfMonth} and ${dayMonth(range.end)}"
        } else {
            "between ${dayMonth(range.start)} and ${dayMonth(range.end)}"
        }

    /** "in 12–15 days", "in 3 days", "tomorrow", "today", "expected now" once the range has started. */
    fun inDays(
        today: LocalDate,
        range: DateRange,
    ): String {
        val a = today.daysUntil(range.start)
        val b = today.daysUntil(range.end)
        return when {
            a > 1 && b > a -> "in $a$EN_DASH$b days"
            a > 1 -> "in $a days"
            a == 1 && b == 1 -> "tomorrow"
            a == 1 -> "from tomorrow"
            a == 0 && b == 0 -> "today"
            a == 0 -> "from today"
            b >= 0 -> "expected now"
            else -> "passed"
        }
    }

    /** "7 h 20 min" */
    fun sleep(minutes: Int): String {
        val h = minutes / MINUTES_PER_HOUR
        val m = minutes % MINUTES_PER_HOUR
        return if (h == 0) "$m min" else "$h h ${m.toString().padStart(2, '0')} min"
    }

    /** "N days" with the singular at 1. */
    fun days(count: Int): String = if (count == 1) "1 day" else "$count days"

    fun cycles(count: Int): String = if (count == 1) "1 cycle" else "$count cycles"

    fun packs(count: Int): String = if (count == 1) "1 pack" else "$count packs"

    /** "36.71" with two decimals in the English locale (dot separator, as the design shows). */
    fun temperature(value: Double): String = String.format(locale, "%.2f", value)

    /** "61.8" (a weight), "1.6" (a standard deviation): one decimal in the English locale. */
    fun oneDecimal(value: Double): String = String.format(locale, "%.1f", value)

    /** Ordinal "1st", "2nd", "3rd". */
    fun ordinal(n: Int): String = ORDINALS.getOrNull(n - 1) ?: "${n}th"

    private val ORDINALS = listOf("1st", "2nd", "3rd")

    private const val MINUTES_PER_HOUR = 60
}
