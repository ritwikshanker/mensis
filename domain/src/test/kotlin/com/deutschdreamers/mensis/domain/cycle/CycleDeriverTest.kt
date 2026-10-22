package com.deutschdreamers.mensis.domain.cycle

import com.deutschdreamers.mensis.domain.Synthetic
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CycleDeriverTest {
    private val start = Synthetic.day1

    @ParameterizedTest(name = "{0}-day cycles → {1} complete cycles of {0}")
    @CsvSource("28, 6", "21, 6", "35, 6")
    fun `regular cycles derive their length`(
        length: Int,
        complete: Int,
    ) {
        val cycles = CycleDeriver.derive(Synthetic.regularCycles(cycleLength = length, count = 7))
        assertEquals(complete + 1, cycles.size)
        assertEquals(List(complete) { length }, cycles.filter { it.isComplete }.map { it.lengthDays })
        assertNull(cycles.last().lengthDays)
        assertEquals(5, cycles.first().periodLengthDays)
    }

    @Test
    fun `spotting before a period does not start it`() {
        val spotting =
            listOf(
                DayEntry(start.minusDays(2), flow = Flow.SPOTTING),
                DayEntry(start.minusDays(1), flow = Flow.SPOTTING),
            )
        val starts = CycleDeriver.periodStarts(spotting + Synthetic.period(start))
        assertEquals(listOf(start), starts)
    }

    @Test
    fun `spotting after the last flow day extends the period length`() {
        val entries = Synthetic.period(start) + DayEntry(start.plusDays(5), flow = Flow.SPOTTING)
        assertEquals(6, CycleDeriver.derive(entries).single().periodLengthDays)
    }

    @Test
    fun `one forgotten day inside a period is bridged`() {
        val entries =
            listOf(
                DayEntry(start, flow = Flow.MEDIUM),
                DayEntry(start.plusDays(1), flow = Flow.HEAVY),
                DayEntry(start.plusDays(3), flow = Flow.LIGHT),
            )
        assertEquals(4, CycleDeriver.derive(entries).single().periodLengthDays)
    }

    @Test
    fun `bleeding within the gap belongs to the same period`() {
        val entries = Synthetic.period(start) + DayEntry(start.plusDays(8), flow = Flow.LIGHT)
        assertEquals(listOf(start), CycleDeriver.periodStarts(entries))
    }

    @Test
    fun `bleeding after the gap starts a new period`() {
        val entries = Synthetic.period(start) + DayEntry(start.plusDays(10), flow = Flow.LIGHT)
        assertEquals(listOf(start, start.plusDays(10)), CycleDeriver.periodStarts(entries))
    }

    @Test
    fun `explicit none flow is not bleeding`() {
        val entries = listOf(DayEntry(start, flow = Flow.NONE))
        assertEquals(emptyList<Cycle>(), CycleDeriver.derive(entries))
    }
}
