package com.deutschdreamers.mensis.ui.screens.insights

import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.ui.preview.FakeData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalTime

class InsightsUiBuilderTest {
    @ParameterizedTest(name = "{0} cycles: {1}, mean {2}, {3}, {4}")
    @CsvSource(
        delimiter = '|',
        nullValues = ["-"],
        value = [
            "8 | 8 cycles | 29 | Range 27–32 | SD 1.6 d",
            "3 | 3 cycles | 30 | Range 29–32 | SD 1.7 d",
            "1 | 1 cycle  | 29 | Range 29    | -",
            "0 | -        | -  | -           | -",
        ],
    )
    fun `cycle length table`(
        cycles: Int,
        cyclesLabel: String?,
        figure: String?,
        range: String?,
        sd: String?,
    ) {
        val state = FakeData.insights(cycles = cycles)
        assertEquals(cyclesLabel, state.cyclesLabel)
        assertEquals(figure, state.cycleLength?.figure)
        assertEquals(range, state.cycleLength?.range)
        assertEquals(sd, state.cycleLength?.standardDeviation)
    }

    @ParameterizedTest(name = "{0} cycles: period {1} ({2}), luteal {3} ({4})")
    @CsvSource(
        delimiter = '|',
        nullValues = ["-"],
        value = [
            "8 | 5 | Range 4–6 · heaviest on day 2 | 14 | Range 13–15 · 6 cycles with a confirmed shift",
            "3 | 5 | Range 4–5 · heaviest on day 2 | 14 | Range 13–14 · 3 cycles with a confirmed shift",
            "1 | 5 | Range 5 · heaviest on day 2   | 13 | Range 13 · 1 cycle with a confirmed shift",
            "0 | -  | -                            | -  | -",
        ],
    )
    fun `period and luteal table`(
        cycles: Int,
        periodFigure: String?,
        periodLine: String?,
        lutealFigure: String?,
        lutealLine: String?,
    ) {
        val state = FakeData.insights(cycles = cycles)
        assertEquals(periodFigure, state.periodLength?.figure)
        assertEquals(periodLine, state.periodLength?.supporting)
        assertEquals(lutealFigure, state.luteal?.figure)
        assertEquals(lutealLine, state.luteal?.supporting)
    }

    @Test
    fun `eight cycles give eight bars, the latest highlighted, on the design's 24 to 34 day axis`() {
        val chart = requireNotNull(FakeData.insights().cycleLength).chart
        assertEquals(listOf(29, 31, 28, 30, 27, 32, 29, 29), chart.bars.map { it.days })
        assertEquals(listOf("Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep"), chart.bars.map { it.label })
        assertEquals(listOf(false, false, false, false, false, false, false, true), chart.bars.map { it.latest })
        assertEquals(8, chart.slots)
        assertEquals(24, chart.axisMin)
        assertEquals(34, chart.axisMax)
        assertEquals(29.375, requireNotNull(chart.mean), 1e-9)
    }

    @Test
    fun `a single cycle keeps eight slots and a ten-day axis`() {
        val chart = requireNotNull(FakeData.insights(cycles = 1).cycleLength).chart
        assertEquals(1, chart.bars.size)
        assertTrue(chart.bars.single().latest)
        assertEquals(8, chart.slots)
        assertEquals(26, chart.axisMin)
        assertEquals(36, chart.axisMax)
    }

    @Test
    fun `chart summaries are spoken text`() {
        assertEquals(
            "Cycle lengths, oldest first: 12 Feb, 29 days; 13 Mar, 31 days; 13 Apr, 28 days; 11 May, 30 days; " +
                "10 Jun, 27 days; 7 Jul, 32 days; 8 Aug, 29 days; 6 Sep, 29 days. " +
                "Mean 29.4 days, shown as a dashed line.",
            requireNotNull(FakeData.insights().cycleLength).chart.description,
        )
        assertEquals(
            "Cycle lengths, oldest first: 6 Sep, 29 days. Mean 29.0 days, shown as a dashed line.",
            requireNotNull(FakeData.insights(cycles = 1).cycleLength).chart.description,
        )
        assertEquals("Cycle length chart, no complete cycles yet.", InsightsUiBuilder.cycleChartDescription(null))
        assertEquals(
            "Waking temperature, day 1 to day 32: 17 readings between 36.35 and 36.71 °C. " +
                "Coverline 36.45 °C. Shift from day 15 to day 17.",
            requireNotNull(FakeData.insights().temperature?.chart).description,
        )
    }

    @Test
    fun `temperature card shows this cycle's shift as in the design`() {
        val card = requireNotNull(FakeData.insights().temperature)
        assertEquals("°C", card.unit)
        assertEquals("Rise of 0.3 °C held for 3 days from day 15. Ovulation estimated day 14 (18 Oct).", card.caption)
        val chart = requireNotNull(card.chart)
        assertEquals(17, chart.points.size)
        assertEquals(32, chart.dayCount)
        assertEquals(36.2, chart.minC, 1e-9)
        assertEquals(36.9, chart.maxC, 1e-9)
        assertEquals(36.45, requireNotNull(chart.coverlineC), 1e-9)
        assertEquals("coverline 36.45", chart.coverlineLabel)
        assertEquals(15..17, chart.shiftDays)
        assertEquals("day 1", chart.startLabel)
        assertEquals("day 32", chart.endLabel)
    }

    @Test
    fun `fahrenheit converts the labels and the rise but not the plotted values`() {
        val card = requireNotNull(FakeData.insights(temperatureUnit = TemperatureUnit.FAHRENHEIT).temperature)
        assertEquals("°F", card.unit)
        assertTrue(card.caption.startsWith("Rise of 0.5 °F held for 3 days"), card.caption)
        val chart = requireNotNull(card.chart)
        assertEquals("coverline 97.61", chart.coverlineLabel)
        assertEquals(36.45, requireNotNull(chart.coverlineC), 1e-9)
        assertTrue(chart.description.contains("between 97.43 and 98.08 °F"), chart.description)
    }

    @Test
    fun `late readings are flagged, kept off the shift and explained`() {
        val history = FakeData.insightsHistory()
        val lateDay = FakeData.insightsToday.minusDays(10)
        val entries =
            history.map {
                if (it.date == lateDay) it.copy(temperatureC = 36.90, temperatureTime = LocalTime.of(9, 30)) else it
            }
        val state = InsightsUiBuilder.build(FakeData.data(entries), FakeData.insightsToday)
        val card = requireNotNull(state.temperature)
        val chart = requireNotNull(card.chart)
        assertEquals(listOf(7), chart.points.filter { it.late }.map { it.day })
        assertTrue(card.caption.startsWith("Rise of 0.3 °C"), card.caption)
        assertTrue(
            card.caption.endsWith("Readings in grey were taken at 09:00 or later and are not used."),
            card.caption,
        )
        assertTrue(chart.description.endsWith("1 reading taken late is not used."), chart.description)
    }

    @Test
    fun `no shift and no readings have their own captions`() {
        val flat =
            FakeData.insightsHistory().map {
                if (it.date.isAfter(FakeData.insightsToday.minusDays(3))) it.copy(temperatureC = 36.40) else it
            }
        val noShift = requireNotNull(InsightsUiBuilder.build(FakeData.data(flat), FakeData.insightsToday).temperature)
        assertEquals("No temperature shift found this cycle yet.", noShift.caption)
        assertNull(noShift.chart?.coverlineC)
        assertNull(noShift.chart?.shiftDays)
        assertTrue(requireNotNull(noShift.chart).description.endsWith("No shift found."))

        val none = FakeData.insightsHistory().map { it.copy(temperatureC = null, temperatureTime = null) }
        val empty = requireNotNull(InsightsUiBuilder.build(FakeData.data(none), FakeData.insightsToday).temperature)
        assertNull(empty.chart)
        assertEquals("No waking temperature logged this cycle yet.", empty.caption)
    }

    @Test
    fun `the temperature card needs tracking level Everything`() {
        assertNotNull(FakeData.insights(trackingLevel = TrackingLevel.FULL).temperature)
        assertNull(FakeData.insights(trackingLevel = TrackingLevel.SYMPTOMS).temperature)
        assertNull(FakeData.insights(trackingLevel = TrackingLevel.PERIOD).temperature)
    }

    @Test
    fun `existing temperatures still count for the luteal card at a lower tracking level`() {
        val state = FakeData.insights(trackingLevel = TrackingLevel.SYMPTOMS)
        assertEquals("Range 13–15 · 6 cycles with a confirmed shift", state.luteal?.supporting)
    }

    @Test
    fun `without temperatures the luteal phase is the estimate`() {
        val none = FakeData.insightsHistory().map { it.copy(temperatureC = null, temperatureTime = null) }
        val state = InsightsUiBuilder.build(FakeData.data(none), FakeData.insightsToday)
        assertEquals("14", state.luteal?.figure)
        assertEquals("Estimated from cycle lengths · no temperature data", state.luteal?.supporting)
    }

    @Test
    fun `flat temperatures in every cycle are not called missing`() {
        val flat = FakeData.insightsHistory().map { if (it.temperatureC != null) it.copy(temperatureC = 36.40) else it }
        val state = InsightsUiBuilder.build(FakeData.data(flat), FakeData.insightsToday)
        assertEquals("Estimated from cycle lengths · no confirmed shift yet", state.luteal?.supporting)
    }

    @Test
    fun `positive LH tests stand in when no shift was found`() {
        val history = FakeData.regularHistory()
        val lh =
            (0 until 8).map { index ->
                DayEntry(FakeData.firstStart.plusDays(index * 29L + 14), lhTest = TestResult.POSITIVE)
            }
        val state = InsightsUiBuilder.build(FakeData.data(history + lh), FakeData.lastStart.plusDays(3))
        assertEquals("14", state.luteal?.figure)
        assertEquals("Range 14 · 8 cycles with a positive LH test", state.luteal?.supporting)
    }

    @Test
    fun `a hormonal method today hides the luteal and temperature cards`() {
        val pill =
            ContraceptionPeriod(
                ContraceptionMethod.COMBINED_PILL,
                FakeData.insightsToday.minusDays(3),
                regimen = Regimen.CYCLIC,
            )
        val state = FakeData.insights(contraception = listOf(pill))
        assertNull(state.luteal)
        assertNull(state.temperature)
        assertNotNull(state.periodLength)
        val copper = ContraceptionPeriod(ContraceptionMethod.COPPER_IUD, FakeData.insightsToday.minusDays(3))
        assertNotNull(FakeData.insights(contraception = listOf(copper)).luteal)
    }

    @Test
    fun `a hormonal IUD keeps the luteal and temperature cards`() {
        val iud = ContraceptionPeriod(ContraceptionMethod.HORMONAL_IUD, FakeData.firstStart.minusDays(30))
        val state = FakeData.insights(contraception = listOf(iud))
        assertNotNull(state.luteal)
        assertNotNull(state.temperature)
        assertEquals(FakeData.insights().luteal, state.luteal)
    }

    @Test
    fun `pregnancy mode hides this cycle's temperature chart`() {
        val settings = FakeData.settings.copy(mode = TrackerMode.PREGNANCY, pregnancyLmp = FakeData.insightsToday)
        val state = InsightsUiBuilder.build(FakeData.data(FakeData.insightsHistory(), settings), FakeData.insightsToday)
        assertNull(state.temperature)
    }

    @Test
    fun `recurring symptoms read like the design`() {
        val card = requireNotNull(FakeData.insights().symptoms)
        assertNull(card.emptyText)
        assertEquals(
            listOf(
                "Cramps on period days 1–2" to "8 of 8 cycles",
                "Headache in the 3 days before a period" to "6 of 8 cycles",
                "Fatigue in the late luteal phase" to "5 of 8 cycles",
            ),
            card.rows.map { it.title to it.supporting },
        )
    }

    @Test
    fun `symptom card empty texts and visibility`() {
        assertEquals(
            "Patterns appear once 2 cycles are complete.",
            FakeData.insights(cycles = 1).symptoms?.emptyText,
        )
        val plain = InsightsUiBuilder.build(FakeData.data(FakeData.regularHistory()), FakeData.lastStart.plusDays(3))
        assertEquals(
            "No symptom came back in the same phase in at least half of your cycles.",
            plain.symptoms?.emptyText,
        )
        val periodOnly =
            InsightsUiBuilder.build(
                FakeData.data(FakeData.regularHistory(), FakeData.settings.copy(trackingLevel = TrackingLevel.PERIOD)),
                FakeData.lastStart.plusDays(3),
            )
        assertNull(periodOnly.symptoms)
        assertNotNull(FakeData.insights(trackingLevel = TrackingLevel.PERIOD).symptoms)
    }

    @Test
    fun `uniform flow does not claim a heaviest day`() {
        val even = FakeData.regularHistory().map { it.copy(flow = Flow.MEDIUM) }
        val state = InsightsUiBuilder.build(FakeData.data(even), FakeData.lastStart.plusDays(3))
        assertEquals("Range 5", state.periodLength?.supporting)
        assertFalse(requireNotNull(state.periodLength).supporting.contains("heaviest"))
    }

    @Test
    fun `nothing logged leaves only the empty cycle card and the symptom hint`() {
        val state = InsightsUiBuilder.build(FakeData.data(emptyList()), FakeData.insightsToday)
        assertNull(state.cyclesLabel)
        assertNull(state.cycleLength)
        assertNull(state.periodLength)
        assertNull(state.luteal)
        assertNull(state.temperature)
        assertEquals("Patterns appear once 2 cycles are complete.", state.symptoms?.emptyText)
    }
}
