package com.deutschdreamers.mensis.domain.pregnancy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class PregnancyCalculatorTest {
    private val lmp = LocalDate.of(2026, 8, 17)

    @Test
    fun `due date is the last period start plus 280 days`() {
        val status = PregnancyCalculator.calculate(lmp, LocalDate.of(2026, 10, 21))
        assertEquals(LocalDate.of(2027, 5, 24), status.dueDate)
        assertEquals(40, PregnancyStatus.TOTAL_WEEKS)
    }

    @ParameterizedTest(name = "{0} days after LMP → week {1}, day {2}, trimester {3}")
    @CsvSource(
        "0, 1, 1, 1",
        "6, 1, 7, 1",
        "7, 2, 1, 1",
        "57, 9, 2, 1",
        "90, 13, 7, 1",
        "91, 14, 1, 2",
        "189, 28, 1, 3",
        "279, 40, 7, 3",
    )
    fun `gestational week and trimester are counted from the last period`(
        days: Int,
        week: Int,
        dayOfWeek: Int,
        trimester: Int,
    ) {
        val status = PregnancyCalculator.calculate(lmp, lmp.plusDays(days.toLong()))
        assertEquals(week, status.week)
        assertEquals(dayOfWeek, status.dayOfWeek)
        assertEquals(trimester, status.trimester)
        assertEquals(days / 280.0, status.progress, 1e-9)
    }

    @Test
    fun `dates before the last period count as day zero`() {
        assertEquals(0, PregnancyCalculator.calculate(lmp, lmp.minusDays(5)).daysSinceLmp)
    }
}
