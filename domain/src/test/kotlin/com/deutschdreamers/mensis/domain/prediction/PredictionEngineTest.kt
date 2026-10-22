package com.deutschdreamers.mensis.domain.prediction

import com.deutschdreamers.mensis.domain.Synthetic
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class PredictionEngineTest {
    private val settings = TrackerSettings()

    private fun predict(
        entries: List<DayEntry>,
        today: LocalDate,
    ): CyclePrediction = PredictionEngine.predict(PredictionInput(entries, settings, today))

    @ParameterizedTest(name = "{0}-day cycles predict day {0} with ±1 and three pips")
    @CsvSource("28", "21", "35")
    fun `regular cycles predict the next start exactly`(length: Int) {
        val entries = Synthetic.regularCycles(cycleLength = length, count = 7)
        val last = Synthetic.lastStart(entries)
        val prediction = predict(entries, last.plusDays(10))
        val next = checkNotNull(prediction.nextPeriod)
        assertEquals(last.plusDays(length.toLong()), next.mostLikely)
        assertEquals(1, next.spreadDays)
        assertEquals(Confidence.HIGH, next.confidence)
        assertEquals(6, next.basisCycles)
        assertFalse(next.usesTypicalLength)
        assertEquals(11, prediction.currentCycleDay)
        assertFalse(prediction.isLate)
    }

    @Test
    fun `irregular cycles widen the range and are flagged`() {
        val entries = Synthetic.cyclesWithLengths(lengths = listOf(24, 33, 26, 35, 25, 34))
        val prediction = predict(entries, Synthetic.lastStart(entries).plusDays(3))
        val stats = checkNotNull(prediction.statistics)
        assertTrue(stats.isIrregular)
        val next = checkNotNull(prediction.nextPeriod)
        assertTrue(next.spreadDays >= 6, "spread was ${next.spreadDays}")
        assertEquals(Confidence.HIGH, next.confidence)
    }

    @Test
    fun `one anomalous 60-day cycle is ignored`() {
        val entries = Synthetic.cyclesWithLengths(lengths = listOf(28, 29, 60, 28, 30, 29))
        val last = Synthetic.lastStart(entries)
        val prediction = predict(entries, last.plusDays(5))
        val stats = checkNotNull(prediction.statistics)
        assertEquals(listOf(60), stats.discardedLengths)
        assertEquals(5, stats.count)
        val next = checkNotNull(prediction.nextPeriod)
        assertTrue(next.mostLikely in last.plusDays(28)..last.plusDays(30))
        assertEquals(Confidence.MEDIUM, next.confidence)
    }

    @Test
    fun `a forgotten period shows up as a long cycle and is discarded`() {
        // Period 3 was never logged: the gap between start 2 and start 4 is 56 days.
        val entries = Synthetic.cyclesWithLengths(lengths = listOf(28, 56, 28, 28))
        val prediction = predict(entries, Synthetic.lastStart(entries).plusDays(2))
        assertEquals(listOf(56), prediction.statistics?.discardedLengths)
        assertEquals(listOf(28, 28, 28), prediction.statistics?.usedLengths)
    }

    @Test
    fun `without history the typical length from onboarding is used with no pips`() {
        val entries = Synthetic.period(Synthetic.day1)
        val prediction = predict(entries, Synthetic.day1.plusDays(3))
        val next = checkNotNull(prediction.nextPeriod)
        assertTrue(next.usesTypicalLength)
        assertEquals(Synthetic.day1.plusDays(29), next.mostLikely)
        assertEquals(PredictionConstants.DEFAULT_SPREAD_DAYS, next.spreadDays)
        assertEquals(Confidence.NONE, next.confidence)
    }

    @Test
    fun `with one complete cycle the typical length is still used but with one pip`() {
        val entries = Synthetic.regularCycles(cycleLength = 31, count = 2)
        val prediction = predict(entries, Synthetic.lastStart(entries).plusDays(3))
        val next = checkNotNull(prediction.nextPeriod)
        assertTrue(next.usesTypicalLength)
        assertEquals(Confidence.LOW, next.confidence)
        assertEquals(1, next.basisCycles)
    }

    @Test
    fun `no period logged at all gives no prediction`() {
        val prediction = predict(emptyList(), Synthetic.day1)
        assertNull(prediction.currentCycle)
        assertNull(prediction.nextPeriod)
        assertNull(prediction.currentCycleDay)
    }

    @Test
    fun `late period is reported from the day after latest plus grace`() {
        val entries = Synthetic.regularCycles(cycleLength = 28, count = 7)
        val last = Synthetic.lastStart(entries)
        val latest = last.plusDays(29)
        assertFalse(predict(entries, latest.plusDays(2)).isLate)
        val late = predict(entries, latest.plusDays(3))
        assertTrue(late.isLate)
        assertEquals(3, late.lateByDays)
        // Predictions do not roll forward: the next start stays anchored on the last logged period.
        assertEquals(last.plusDays(28), late.nextPeriod?.mostLikely)
    }

    @Test
    fun `a gap of several months counts as late and is discarded once a period is logged`() {
        val history = Synthetic.regularCycles(cycleLength = 28, count = 5)
        val last = Synthetic.lastStart(history)
        val duringGap = predict(history, last.plusDays(100))
        assertTrue(duringGap.isLate)
        assertEquals(71, duringGap.lateByDays)
        val resumed = history + Synthetic.period(last.plusDays(100))
        val after = predict(resumed, last.plusDays(103))
        assertEquals(listOf(100), after.statistics?.discardedLengths)
        assertFalse(after.isLate)
    }

    @Test
    fun `emergency contraception widens the range`() {
        val entries = Synthetic.regularCycles(cycleLength = 28, count = 7)
        val last = Synthetic.lastStart(entries)
        val withEc = entries + DayEntry(last.plusDays(8), emergencyContraception = true)
        assertEquals(
            1 + PredictionConstants.EMERGENCY_CONTRACEPTION_WIDEN_DAYS,
            predict(withEc, last.plusDays(10)).nextPeriod?.spreadDays,
        )
    }

    @Test
    fun `calendar ovulation is predicted next start minus the luteal length`() {
        val entries = Synthetic.regularCycles(cycleLength = 28, count = 7)
        val last = Synthetic.lastStart(entries)
        val prediction = predict(entries, last.plusDays(5))
        val ovulation = checkNotNull(prediction.ovulation)
        assertEquals(OvulationSource.CALENDAR, ovulation.source)
        assertEquals(last.plusDays(14), ovulation.date)
        val window = checkNotNull(prediction.fertileWindow)
        assertEquals(last.plusDays(9), window.range.start)
        assertEquals(last.plusDays(14), window.range.end)
        assertTrue(window.isEstimated)
    }

    @Test
    fun `a positive LH test sets ovulation one to two days later`() {
        val entries = Synthetic.regularCycles(cycleLength = 28, count = 7)
        val last = Synthetic.lastStart(entries)
        val withLh =
            entries + DayEntry(last.plusDays(12), lhTest = TestResult.POSITIVE) +
                DayEntry(last.plusDays(13), lhTest = TestResult.POSITIVE)
        val ovulation = checkNotNull(predict(withLh, last.plusDays(14)).ovulation)
        assertEquals(OvulationSource.LH_TEST, ovulation.source)
        assertEquals(last.plusDays(13), ovulation.date)
        assertEquals(last.plusDays(14), ovulation.range.end)
    }

    @Test
    fun `a temperature shift confirms ovulation and teaches the luteal length`() {
        // Two complete 28-day cycles, shift from day 14 → ovulation day 13 → luteal 16 (next start − ovulation).
        val cycles = Synthetic.regularCycles(cycleLength = 28, count = 3)
        val starts =
            com.deutschdreamers.mensis.domain.cycle.CycleDeriver
                .periodStarts(cycles)
        val temps = starts.take(2).flatMap { Synthetic.temperatures(it.plusDays(6), lowDays = 7, highDays = 10) }
        val entries = Synthetic.merge(cycles, temps)
        val prediction = predict(entries, starts.last().plusDays(3))
        assertEquals(2, prediction.confirmedOvulations.size)
        assertTrue(prediction.confirmedOvulations.all { it.source == OvulationSource.TEMPERATURE_CONFIRMED })
        assertEquals(16, prediction.confirmedOvulations.first().lutealDays)
        assertEquals(16, prediction.lutealLengthDays)
        assertEquals(2, prediction.lutealLearnedFrom)
    }

    @Test
    fun `a single confirmation keeps the default luteal length`() {
        val (luteal, from) =
            PredictionEngine.lutealLength(
                listOf(ConfirmedOvulation(Synthetic.day1, Synthetic.day1, OvulationSource.LH_TEST, 12)),
            )
        assertEquals(PredictionConstants.DEFAULT_LUTEAL_DAYS, luteal)
        assertEquals(1, from)
    }

    @Test
    fun `spotting days do not create cycles`() {
        val entries =
            Synthetic.regularCycles(cycleLength = 28, count = 3) +
                DayEntry(Synthetic.day1.plusDays(40), flow = Flow.SPOTTING)
        assertEquals(3, predict(entries, Synthetic.day1.plusDays(60)).cycles.size)
    }
}
