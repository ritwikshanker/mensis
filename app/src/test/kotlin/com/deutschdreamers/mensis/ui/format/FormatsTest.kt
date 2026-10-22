package com.deutschdreamers.mensis.ui.format

import com.deutschdreamers.mensis.domain.model.DateRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalTime

class FormatsTest {
    @Test
    fun `dates follow the design wording`() {
        val date = LocalDate.of(2026, 10, 21)
        assertEquals("Wed 21 Oct", Formats.weekdayDayMonth(date))
        assertEquals("21 Oct", Formats.dayMonth(date))
        assertEquals("21 Oct 2026", Formats.dayMonthYear(date))
        assertEquals("Oct 2026", Formats.monthYear(date))
        assertEquals("1.6", Formats.oneDecimal(1.598))
        assertEquals("29.0", Formats.oneDecimal(29.0))
        assertEquals("October 2026", Formats.fullMonthYear(date))
        assertEquals("Wednesday 21 October", Formats.spokenDate(date))
        assertEquals("06:40", Formats.time(LocalTime.of(6, 40)))
        assertEquals("Sep", Formats.dayMonth(LocalDate.of(2026, 9, 6)).substringAfter(' '))
    }

    @ParameterizedTest(name = "{0} to {1} → {2}")
    @CsvSource(
        "2026-11-02, 2026-11-05, 2–5 Nov",
        "2026-10-30, 2026-11-04, 30 Oct – 4 Nov",
        "2026-10-21, 2026-10-21, 21 Oct",
        "2026-12-30, 2027-01-02, 30 Dec – 2 Jan",
    )
    fun `ranges collapse the month when both ends share it`(
        start: LocalDate,
        end: LocalDate,
        expected: String,
    ) {
        assertEquals(expected, Formats.range(DateRange(start, end)))
    }

    @ParameterizedTest(name = "today {0}, range {1}..{2} → {3}")
    @CsvSource(
        "2026-10-21, 2026-11-02, 2026-11-05, in 12–15 days",
        "2026-10-21, 2026-10-24, 2026-10-24, in 3 days",
        "2026-10-21, 2026-10-22, 2026-10-22, tomorrow",
        "2026-10-21, 2026-10-22, 2026-10-25, from tomorrow",
        "2026-10-21, 2026-10-21, 2026-10-24, from today",
        "2026-10-21, 2026-10-19, 2026-10-23, expected now",
        "2026-10-21, 2026-10-10, 2026-10-13, passed",
    )
    fun `in-days wording`(
        today: LocalDate,
        start: LocalDate,
        end: LocalDate,
        expected: String,
    ) {
        assertEquals(expected, Formats.inDays(today, DateRange(start, end)))
    }

    @Test
    fun `between and counts`() {
        assertEquals(
            "between 15 and 18 Oct",
            Formats.between(DateRange(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 18))),
        )
        assertEquals(
            "between 30 Oct and 2 Nov",
            Formats.between(DateRange(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2))),
        )
        assertEquals("7 h 20 min", Formats.sleep(440))
        assertEquals("45 min", Formats.sleep(45))
        assertEquals("1 day", Formats.days(1))
        assertEquals("3 days", Formats.days(3))
        assertEquals("1 cycle", Formats.cycles(1))
        assertEquals("8 cycles", Formats.cycles(8))
        assertEquals("36.71", Formats.temperature(36.71))
        assertEquals("61.8", Formats.oneDecimal(61.8))
        assertEquals("1st", Formats.ordinal(1))
        assertEquals("3rd", Formats.ordinal(3))
    }
}
