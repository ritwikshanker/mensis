package com.deutschdreamers.mensis.ui.screens.calendar

import com.deutschdreamers.mensis.ui.preview.FakeData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthBuilderTest {
    private val today = FakeData.lastStart.plusDays(16)
    private val data = FakeData.data(FakeData.regularHistory() + FakeData.todayEntry(today))

    private fun build(
        month: YearMonth = YearMonth.from(today),
        selected: LocalDate = today,
        editMode: Boolean = false,
        toggles: Set<LocalDate> = emptySet(),
    ) = CalendarMonthBuilder.build(data, today, CalendarSelection(month, selected, editMode, toggles))

    private fun CalendarUiState.cell(date: LocalDate): DayCellUi =
        requireNotNull(weeks.flatten().firstOrNull { it?.date == date })

    @Test
    fun `grid starts on Monday and covers the month`() {
        val state = build()
        assertEquals("September 2026", state.monthTitle)
        val firstRow = state.weeks[0]
        val firstCell = firstRow.first { it != null }
        val leadingBlanks = firstRow.indexOf(firstCell)
        assertEquals(DayOfWeek.MONDAY.plus(leadingBlanks.toLong()), firstCell?.date?.dayOfWeek)
        assertEquals(state.month.lengthOfMonth(), state.weeks.flatten().count { it != null })
    }

    @Test
    fun `period, predicted, today and logged marks`() {
        val state = build()
        val periodMonth = build(month = YearMonth.from(FakeData.lastStart))
        assertEquals(CalendarCellKind.PERIOD, periodMonth.cell(FakeData.lastStart).kind)
        assertTrue(periodMonth.cell(FakeData.lastStart).isLogged)
        assertTrue(state.cell(today).isToday)
        assertTrue(state.cell(today).isLogged)
        val nextStart = FakeData.lastStart.plusDays(FakeData.CYCLE_LENGTH.toLong())
        val nextMonth = build(month = YearMonth.from(nextStart))
        assertEquals(CalendarCellKind.PREDICTED, nextMonth.cell(nextStart).kind)
        assertTrue(nextMonth.cell(nextStart).description.contains("predicted period"))
        val ovulation = nextStart.minusDays(14)
        assertEquals(OvulationMark.ESTIMATED, build(month = YearMonth.from(ovulation)).cell(ovulation).ovulation)
        assertEquals(
            CalendarCellKind.FERTILE_ESTIMATED,
            build(month = YearMonth.from(ovulation)).cell(ovulation.minusDays(2)).kind,
        )
    }

    @Test
    fun `day card names the cycle day and the previous start`() {
        val card = build().dayCard
        assertEquals("Thu 10 Sep · Cycle day 17", card.title)
        assertEquals("Cycle started 25 Aug · 29 d before: 27 Jul", card.footer)
        assertEquals(listOf("36.71 °C · 06:40", "Headache", "Calm", "7 h 20 min"), card.chips.map { it.text })
    }

    @Test
    fun `edit mode flips marks for pending toggles only`() {
        val periodDay = FakeData.lastStart
        val plainDay = today
        val state = build(month = YearMonth.from(periodDay), editMode = true, toggles = setOf(periodDay, plainDay))
        assertEquals(false, state.cell(periodDay).editMarked)
        assertEquals(CalendarCellKind.NONE, state.cell(periodDay).kind)
        val thisMonth = build(editMode = true, toggles = setOf(periodDay, plainDay))
        assertEquals(true, thisMonth.cell(plainDay).editMarked)
        assertEquals(CalendarCellKind.PERIOD, thisMonth.cell(plainDay).kind)
        assertEquals(true, state.cell(periodDay.plusDays(1)).editMarked)
        assertTrue(thisMonth.cell(plainDay).description.endsWith("bleeding, tap to unmark"))
        assertEquals(OvulationMark.NONE, thisMonth.cell(plainDay).ovulation)
    }

    @Test
    fun `empty history has no predictions and says so`() {
        val empty =
            CalendarMonthBuilder.build(
                FakeData.data(emptyList()),
                today,
                CalendarSelection(YearMonth.from(today), today),
            )
        assertTrue(
            empty.weeks
                .flatten()
                .filterNotNull()
                .all { it.kind == CalendarCellKind.NONE },
        )
        assertEquals("No period logged yet", empty.dayCard.footer)
        assertEquals("Thu 10 Sep", empty.dayCard.title)
    }
}
