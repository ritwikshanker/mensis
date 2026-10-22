package com.deutschdreamers.mensis.domain.today

import com.deutschdreamers.mensis.domain.Synthetic
import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.prediction.PredictionInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TodayStateResolverTest {
    private val history = Synthetic.regularCycles(cycleLength = 28, count = 7)
    private val last = Synthetic.lastStart(history)
    private val settings = TrackerSettings()

    private fun resolve(
        entries: List<DayEntry>,
        today: LocalDate,
        settings: TrackerSettings = this.settings,
        contraception: List<ContraceptionPeriod> = emptyList(),
    ) = TodayStateResolver.resolve(PredictionInput(entries, settings, today, contraception))

    @Test
    fun `first launch has no numeral and an empty strip`() {
        val model = resolve(emptyList(), Synthetic.day1)
        assertEquals(TodayState.FIRST_LAUNCH, model.state)
        assertNull(model.numeral)
        assertNull(model.nextPeriod)
        assertEquals(32, model.strip.lengthDays)
        assertTrue(model.strip.cells.all { it.kind == StripCellKind.NONE })
        assertNull(model.strip.todayDay)
    }

    @Test
    fun `normal luteal day`() {
        val model = resolve(history, last.plusDays(16))
        assertEquals(TodayState.NORMAL, model.state)
        assertEquals(17, model.numeral)
        assertEquals(CyclePhase.LUTEAL, model.phase)
        assertEquals(32, model.strip.lengthDays)
        assertEquals(17, model.strip.todayDay)
        assertEquals(StripCellKind.PERIOD, model.strip.kindOf(1))
        assertEquals(StripCellKind.PERIOD, model.strip.kindOf(5))
        assertEquals(StripCellKind.NONE, model.strip.kindOf(6))
        assertEquals(StripCellKind.FERTILE_PREDICTED, model.strip.kindOf(10))
        assertEquals(StripCellKind.OVULATION_PREDICTED, model.strip.kindOf(15))
        assertEquals(StripCellKind.PERIOD_PREDICTED, model.strip.kindOf(28))
        assertEquals(StripCellKind.PERIOD_PREDICTED, model.strip.kindOf(30))
        assertEquals(StripCellKind.NONE, model.strip.kindOf(31))
        assertEquals(
            listOf(
                StripLaneKind.PERIOD,
                StripLaneKind.FERTILE_ESTIMATED,
                StripLaneKind.PREDICTED,
            ),
            model.strip.lanes.map {
                it.kind
            },
        )
        assertNotNull(model.nextPeriod)
        assertTrue(model.fertileWindowIsNextCycle)
        assertEquals(last.plusDays(28 + 9), model.fertileWindowCard?.range?.start)
    }

    @Test
    fun `on period day 2 predicts the remaining period days`() {
        val entries =
            history +
                listOf(DayEntry(last.plusDays(28), flow = Flow.MEDIUM), DayEntry(last.plusDays(29), flow = Flow.HEAVY))
        val model = resolve(entries, last.plusDays(29))
        assertEquals(TodayState.ON_PERIOD, model.state)
        assertEquals(2, model.numeral)
        assertEquals(CyclePhase.PERIOD, model.phase)
        assertTrue(model.isOnPeriod)
        assertEquals(StripCellKind.PERIOD, model.strip.kindOf(2))
        assertEquals(StripCellKind.PERIOD_PREDICTED, model.strip.kindOf(3))
        assertEquals(StripCellKind.PERIOD_PREDICTED, model.strip.kindOf(5))
        assertEquals(StripCellKind.NONE, model.strip.kindOf(6))
        assertEquals(5..5, model.periodLengthRange)
        assertFalse(model.fertileWindowIsNextCycle)
    }

    @Test
    fun `fertile window day`() {
        val model = resolve(history, last.plusDays(12))
        assertEquals(TodayState.FERTILE, model.state)
        assertEquals(CyclePhase.FERTILE, model.phase)
        assertFalse(model.fertileWindowIsNextCycle)
        assertEquals(last.plusDays(9), model.fertileWindowCard?.range?.start)
    }

    @Test
    fun `follicular day before the window`() {
        assertEquals(CyclePhase.FOLLICULAR, resolve(history, last.plusDays(7)).phase)
    }

    @Test
    fun `late period hides the range cards and relabels the lane`() {
        val model = resolve(history, last.plusDays(34))
        assertEquals(TodayState.LATE, model.state)
        assertEquals(35, model.numeral)
        // 28-day history with ±1: latest expected day 30, today is day 35.
        assertEquals(5, model.lateByDays)
        assertNull(model.nextPeriod)
        assertNull(model.fertileWindowCard)
        assertEquals(36, model.strip.lengthDays)
        assertTrue(model.strip.lanes.any { it.kind == StripLaneKind.EXPECTED })
    }

    @Test
    fun `pregnancy mode shows the week`() {
        val lmp = LocalDate.of(2026, 8, 17)
        val model =
            resolve(
                history,
                LocalDate.of(2026, 10, 13),
                settings.copy(mode = TrackerMode.PREGNANCY, pregnancyLmp = lmp),
            )
        assertEquals(TodayState.PREGNANCY, model.state)
        assertEquals(9, model.numeral)
        assertEquals(2, model.pregnancy?.dayOfWeek)
        assertNull(model.nextPeriod)
    }

    @Test
    fun `cyclic method shows pack day, break cells and the predicted bleed`() {
        val methodStart = LocalDate.of(2026, 1, 3)
        val pill = ContraceptionPeriod(ContraceptionMethod.COMBINED_PILL, methodStart, regimen = Regimen.CYCLIC)
        val today = methodStart.plusDays(28 * 7 + 16L)
        val model = resolve(emptyList(), today, contraception = listOf(pill))
        assertEquals(TodayState.CYCLIC, model.state)
        assertEquals(17, model.numeral)
        assertEquals(28, model.strip.lengthDays)
        assertEquals(StripCellKind.NONE, model.strip.kindOf(21))
        assertEquals(StripCellKind.BREAK, model.strip.kindOf(22))
        assertEquals(StripCellKind.PERIOD_PREDICTED, model.strip.kindOf(23))
        assertEquals(StripCellKind.PERIOD_PREDICTED, model.strip.kindOf(26))
        assertEquals(StripCellKind.BREAK, model.strip.kindOf(28))
        assertNotNull(model.withdrawalBleed)
        assertNull(model.nextPeriod)
        assertEquals(listOf(StripLaneKind.ACTIVE, StripLaneKind.BREAK), model.strip.lanes.map { it.kind })
    }

    @Test
    fun `continuous regimen behaves as suppressing`() {
        val pill =
            ContraceptionPeriod(
                ContraceptionMethod.COMBINED_PILL,
                LocalDate.of(2026, 1, 3),
                regimen = Regimen.CONTINUOUS,
            )
        assertEquals(
            TodayState.SUPPRESSED,
            resolve(emptyList(), LocalDate.of(2026, 10, 21), contraception = listOf(pill)).state,
        )
    }

    @Test
    fun `suppressing method shows days since the last bleed and spotting cells`() {
        val today = LocalDate.of(2026, 10, 21)
        val iud = ContraceptionPeriod(ContraceptionMethod.HORMONAL_IUD, LocalDate.of(2026, 3, 12))
        val entries =
            listOf(DayEntry(today.minusDays(5), flow = Flow.SPOTTING), DayEntry(today.minusDays(47), flow = Flow.LIGHT))
        val model = resolve(entries, today, contraception = listOf(iud))
        assertEquals(TodayState.SUPPRESSED, model.state)
        assertEquals(5, model.numeral)
        assertEquals(32, model.strip.lengthDays)
        assertEquals(32, model.strip.todayDay)
        assertEquals(StripCellKind.SPOTTING, model.strip.kindOf(27))
        assertEquals(StripCellKind.NONE, model.strip.kindOf(32))
        assertEquals(2, model.suppressed?.bleedingDaysInWindow)
        assertNull(model.nextPeriod)
    }

    @Test
    fun `copper IUD behaves like no method`() {
        val iud = ContraceptionPeriod(ContraceptionMethod.COPPER_IUD, LocalDate.of(2025, 3, 12))
        val model = resolve(history, last.plusDays(16), contraception = listOf(iud))
        assertEquals(TodayState.NORMAL, model.state)
        assertNotNull(model.nextPeriod)
    }

    @Test
    fun `a method that ended no longer applies`() {
        val ended =
            ContraceptionPeriod(
                ContraceptionMethod.HORMONAL_IUD,
                LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2025, 12, 31),
            )
        val model = resolve(history, last.plusDays(16), contraception = listOf(ended))
        assertEquals(TodayState.NORMAL, model.state)
        assertEquals(CycleDeriver.derive(history).size, model.prediction?.cycles?.size)
    }
}
