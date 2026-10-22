package com.deutschdreamers.mensis.domain.insights

import com.deutschdreamers.mensis.domain.Synthetic
import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.prediction.ConfirmedOvulation
import com.deutschdreamers.mensis.domain.prediction.OvulationSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime

class InsightsCalculatorTest {
    private val lengths = listOf(29, 31, 28, 30, 27, 32, 29, 29)
    private val entries = Synthetic.cyclesWithLengths(lengths = lengths)
    private val cycles = CycleDeriver.derive(entries)

    @Test
    fun `cycle length stats use the last eight complete cycles`() {
        val stats = checkNotNull(InsightsCalculator.cycleLengths(cycles))
        assertEquals(lengths, stats.lengths)
        assertEquals(29.375, stats.mean, 1e-9)
        assertEquals(27, stats.min)
        assertEquals(32, stats.max)
        assertEquals(1.6, stats.standardDeviation, 0.05)
    }

    @Test
    fun `the window drops older cycles`() {
        val many = Synthetic.cyclesWithLengths(lengths = List(12) { 28 } + listOf(30))
        val stats = checkNotNull(InsightsCalculator.cycleLengths(CycleDeriver.derive(many)))
        assertEquals(8, stats.count)
        assertEquals(30, stats.lengths.last())
    }

    @Test
    fun `period stats include the heaviest day`() {
        val stats = checkNotNull(InsightsCalculator.periodLengths(cycles, entries))
        assertEquals(5.0, stats.mean, 1e-9)
        assertEquals(5, stats.min)
        assertEquals(5, stats.max)
        assertEquals(2, stats.peakFlowDay)
    }

    @Test
    fun `periods without one heaviest day give no peak`() {
        val even = Synthetic.cyclesWithLengths(lengths = listOf(29, 29, 29)).map { it.copy(flow = Flow.MEDIUM) }
        val tied = Synthetic.cyclesWithLengths(lengths = listOf(29, 29, 29)).map { it.copy(flow = Flow.HEAVY) }
        for (history in listOf(even, tied)) {
            val stats = checkNotNull(InsightsCalculator.periodLengths(CycleDeriver.derive(history), history))
            assertNull(stats.peakFlowDay)
            assertEquals(5.0, stats.mean, 1e-9)
        }
    }

    @Test
    fun `this cycle's temperatures carry the shift and how long it held`() {
        val history = Synthetic.cyclesWithLengths(lengths = listOf(29))
        val start = Synthetic.lastStart(history)
        val temps = Synthetic.temperatures(start, lowDays = 14, highDays = 4)
        val late = DayEntry(start.plusDays(18), temperatureC = 36.30, temperatureTime = LocalTime.of(10, 0))
        val all = Synthetic.merge(history, temps, listOf(late))
        val current = CycleDeriver.derive(all).last()
        val result = InsightsCalculator.temperatures(current, all)
        assertEquals(19, result.readings.size)
        val shift = checkNotNull(result.shift)
        assertEquals(start.plusDays(14), shift.firstElevatedDate)
        assertEquals(36.40, shift.coverlineC, 1e-9)
        assertEquals(4, result.heldDays)
    }

    @Test
    fun `a flat temperature curve has no shift`() {
        val history = Synthetic.cyclesWithLengths(lengths = listOf(29))
        val start = Synthetic.lastStart(history)
        val all = Synthetic.merge(history, Synthetic.temperatures(start, lowDays = 12, highDays = 0))
        val result = InsightsCalculator.temperatures(CycleDeriver.derive(all).last(), all)
        assertEquals(12, result.readings.size)
        assertNull(result.shift)
        assertEquals(0, result.heldDays)
    }

    @Test
    fun `window cycles are the last complete ones`() {
        val many = CycleDeriver.derive(Synthetic.cyclesWithLengths(lengths = List(10) { 28 }))
        val window = InsightsCalculator.windowCycles(many)
        assertEquals(8, window.size)
        assertTrue(window.all { it.isComplete })
        assertEquals(many[many.size - 2], window.last())
    }

    @Test
    fun `no complete cycle gives no stats`() {
        val single = CycleDeriver.derive(Synthetic.period(Synthetic.day1))
        assertNull(InsightsCalculator.cycleLengths(single))
        assertNull(InsightsCalculator.periodLengths(single, entries))
        assertNull(InsightsCalculator.luteal(emptyList()))
    }

    @Test
    fun `luteal stats summarise confirmed ovulations`() {
        val confirmed =
            listOf(13, 14, 15).map {
                ConfirmedOvulation(Synthetic.day1, Synthetic.day1, OvulationSource.TEMPERATURE_CONFIRMED, it)
            }
        val stats = checkNotNull(InsightsCalculator.luteal(confirmed))
        assertEquals(14.0, stats.mean, 1e-9)
        assertEquals(13, stats.min)
        assertEquals(15, stats.max)
        assertEquals(3, stats.confirmedCount)
    }

    @Test
    fun `recurring symptoms are grouped by phase and ranked`() {
        val starts = CycleDeriver.periodStarts(entries)
        val symptoms =
            starts.dropLast(1).flatMapIndexed { index, start ->
                val next = starts[index + 1]
                buildList {
                    add(DayEntry(start, symptoms = setOf(Symptom.CRAMPS)))
                    if (index % 4 != 0) add(DayEntry(next.minusDays(2), symptoms = setOf(Symptom.HEADACHE)))
                    if (index % 2 == 0) add(DayEntry(next.minusDays(6), symptoms = setOf(Symptom.FATIGUE)))
                }
            }
        val patterns = InsightsCalculator.symptomPatterns(cycles, Synthetic.merge(entries, symptoms))
        assertEquals(3, patterns.size)
        assertEquals(Symptom.CRAMPS, patterns[0].symptom)
        assertEquals(CyclePhaseBucket.PERIOD_START, patterns[0].phase)
        assertEquals(8, patterns[0].cyclesWith)
        assertEquals(Symptom.HEADACHE, patterns[1].symptom)
        assertEquals(CyclePhaseBucket.PRE_PERIOD, patterns[1].phase)
        assertEquals(6, patterns[1].cyclesWith)
        assertEquals(Symptom.FATIGUE, patterns[2].symptom)
        assertEquals(CyclePhaseBucket.LATE_LUTEAL, patterns[2].phase)
        assertTrue(patterns.all { it.fraction >= 0.5 })
    }
}
