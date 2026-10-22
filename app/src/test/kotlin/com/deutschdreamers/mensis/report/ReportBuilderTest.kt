package com.deutschdreamers.mensis.report

import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.ui.preview.FakeData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class ReportBuilderTest {
    private val today = FakeData.insightsToday
    private val allSections = ReportSection.entries.toSet()

    private fun build(
        entries: List<DayEntry> = FakeData.insightsHistory(),
        range: ReportRange = ReportRange.TWELVE_MONTHS,
        sections: Set<ReportSection> = ReportSection.DEFAULTS,
        contraception: List<ContraceptionPeriod> = emptyList(),
        temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    ): ReportModel =
        ReportBuilder.build(
            FakeData.data(
                entries,
                settings = FakeData.settings.copy(temperatureUnit = temperatureUnit),
                contraception = contraception,
            ),
            ReportOptions(range, sections),
            today,
            appVersion = "1.0.0",
        )

    private fun ReportModel.summaryValue(label: String): String? = summary?.firstOrNull { it.label == label }?.value

    @Test
    fun `header names the range, the version and that it is not a diagnosis`() {
        val model = build(range = ReportRange.LAST_6_CYCLES)
        assertEquals("Cycle report", model.title)
        assertEquals("Prepared 21 Oct 2026", model.prepared)
        assertEquals("Apr – Oct 2026 · Logged with Mensis 1.0.0 · Not a medical diagnosis", model.subtitle)
        assertEquals("mensis-report-2026-10-21.pdf", model.fileName)
    }

    @ParameterizedTest(name = "{0}: from {1}, {2} cycles, cycle {3}, period {4}")
    @CsvSource(
        delimiter = '|',
        value = [
            "TWELVE_MONTHS | Feb – Oct 2026 | 8 | 29.4 d (27–32) | 4.9 d (4–6)",
            "ALL           | Feb – Oct 2026 | 8 | 29.4 d (27–32) | 4.9 d (4–6)",
            "LAST_6_CYCLES | Apr – Oct 2026 | 6 | 29.2 d (27–32) | 5.0 d (4–6)",
        ],
    )
    fun `the summary grid describes the complete cycles of the chosen range`(
        range: ReportRange,
        months: String,
        cycles: String,
        cycleLength: String,
        periodLength: String,
    ) {
        val model = build(range = range)
        assertTrue(model.subtitle.startsWith("$months ·"))
        assertEquals(cycles, model.summaryValue("Cycles logged"))
        assertEquals(cycleLength, model.summaryValue("Cycle length, mean (range)"))
        assertEquals(periodLength, model.summaryValue("Period length, mean (range)"))
        assertEquals("13.8 d", model.summaryValue("Luteal phase, mean"))
        // Six of the eight cycles carry a temperature shift; all six fall in the last six.
        assertEquals("6 of $cycles cycles", model.summaryValue("Ovulation confirmed by BBT"))
        assertEquals("None recorded", model.summaryValue("Contraception"))
    }

    @Test
    fun `twelve months never start before the first entry`() {
        val model = build(entries = FakeData.insightsHistory(cycles = 2))
        assertTrue(model.subtitle.startsWith("Aug – Oct 2026 ·"))
        assertEquals("2", model.summaryValue("Cycles logged"))
    }

    @Test
    fun `only ticked sections are built and notes stay out by default`() {
        val defaults = build()
        assertNotNull(defaults.summary)
        assertNotNull(defaults.lengths)
        assertNotNull(defaults.symptoms)
        assertNotNull(defaults.temperatures)
        assertNull(defaults.medication)
        assertNull(defaults.notes)

        val onlyNotes = build(sections = setOf(ReportSection.NOTES))
        assertNull(onlyNotes.summary)
        assertNull(onlyNotes.lengths)
        assertNull(onlyNotes.symptoms)
        assertNull(onlyNotes.temperatures)
        assertNotNull(onlyNotes.notes)
    }

    @Test
    fun `notes appear only when ticked and only from the chosen range`() {
        val history = FakeData.insightsHistory()
        val february = history.first().date.plusDays(1)
        val october = today.minusDays(2)
        val entries =
            history.map {
                when (it.date) {
                    february -> it.copy(notes = "Started a new job")
                    october -> it.copy(notes = "  Dentist  ")
                    else -> it
                }
            }
        assertNull(build(entries).notes)
        val all = requireNotNull(build(entries, sections = setOf(ReportSection.NOTES)).notes)
        assertEquals(
            listOf(ReportRow("13 Feb 2026", "Started a new job"), ReportRow("19 Oct 2026", "Dentist")),
            all.rows,
        )
        val recent = build(entries, range = ReportRange.LAST_6_CYCLES, sections = setOf(ReportSection.NOTES))
        assertEquals(listOf("Dentist"), requireNotNull(recent.notes).rows.map { it.value })
        assertEquals("No notes in this period.", build(sections = setOf(ReportSection.NOTES)).notes?.emptyText)
    }

    @Test
    fun `cycle lengths give bars on the Insights scale and one table row per cycle`() {
        val lengths = requireNotNull(build().lengths)
        val chart = requireNotNull(lengths.chart)
        assertEquals(listOf(29, 31, 28, 30, 27, 32, 29, 29), chart.lengths)
        assertEquals(24, chart.axisMin)
        assertEquals(34, chart.axisMax)
        assertEquals("mean 29.4 d", chart.meanLabel)
        assertEquals("Feb 2026", chart.startLabel)
        assertEquals("Oct 2026", chart.endLabel)
        assertEquals(8, lengths.rows.size)
        assertEquals(ReportLengthRow("12 Feb 2026", "29 days", "5 days"), lengths.rows.first())
        assertEquals(ReportLengthRow("6 Sep 2026", "29 days", "5 days"), lengths.rows.last())
    }

    @Test
    fun `symptoms are listed by frequency with the phase they usually fall in`() {
        val symptoms = requireNotNull(build().symptoms)
        assertEquals(
            listOf(
                ReportRow("Cramps (days 1–2)", "8/8"),
                ReportRow("Headache (pre-menstrual)", "6/8"),
                ReportRow("Fatigue (late luteal)", "5/8"),
            ),
            symptoms.rows,
        )
    }

    @Test
    fun `without a complete cycle the sections say so instead of showing numbers`() {
        val model = build(entries = FakeData.insightsHistory(cycles = 0), sections = allSections)
        assertEquals("0", model.summaryValue("Cycles logged"))
        assertNull(model.summaryValue("Cycle length, mean (range)"))
        assertNull(model.summaryValue("Ovulation confirmed by BBT"))
        assertNull(model.lengths?.chart)
        assertEquals("No complete cycle in this period yet.", model.lengths?.emptyText)
        assertTrue(requireNotNull(model.symptoms).rows.isEmpty())
        assertEquals("Symptom frequencies appear after the first complete cycle.", model.symptoms?.emptyText)
        // The open cycle's readings still get their chart.
        assertEquals(1, model.temperatures?.charts?.size)
    }

    @Test
    fun `one temperature chart per cycle with readings, the open one last`() {
        val temperatures = requireNotNull(build().temperatures)
        assertEquals(7, temperatures.charts.size)
        val first = temperatures.charts.first()
        assertEquals("Cycle from 13 Apr 2026 · 28 days", first.title)
        assertEquals(28, first.chart.dayCount)
        assertNotNull(first.chart.coverlineC)
        assertTrue(first.caption.startsWith("Rise of "))
        val open = temperatures.charts.last()
        assertEquals("Current cycle from 5 Oct 2026 · day 17", open.title)
        assertEquals(29, open.chart.dayCount)
        assertTrue(temperatures.note.startsWith("Waking temperature in °C, one chart per cycle."))
    }

    @Test
    fun `temperature axis labels follow the unit setting`() {
        val celsius = requireNotNull(build().temperatures).charts.first()
        val fahrenheit = requireNotNull(build(temperatureUnit = TemperatureUnit.FAHRENHEIT).temperatures).charts.first()
        assertEquals("36.2", celsius.axisLow)
        assertEquals("97.2", fahrenheit.axisLow)
        assertEquals(
            celsius.chart,
            fahrenheit.chart.copy(
                coverlineLabel = celsius.chart.coverlineLabel,
                description = celsius.chart.description,
            ),
        )
    }

    @Test
    fun `a hormonal method keeps readings but no shift, and drops the ovulation rows`() {
        val pill =
            ContraceptionPeriod(
                ContraceptionMethod.COMBINED_PILL,
                startDate = LocalDate.of(2025, 12, 1),
                regimen = Regimen.CYCLIC,
            )
        val model = build(contraception = listOf(pill), sections = allSections)
        assertEquals("Combined pill, cyclic, since 1 Dec 2025", model.summaryValue("Contraception"))
        assertNull(model.summaryValue("Luteal phase, mean"))
        assertNull(model.summaryValue("Ovulation confirmed by BBT"))
        val charts = requireNotNull(model.temperatures).charts
        assertTrue(charts.all { it.chart.coverlineC == null && it.chart.shiftDays == null })
        assertEquals("Combined pill in use: readings shown without looking for a shift.", charts.first().caption)
    }

    @Test
    fun `medication lists the method history and what the log recorded`() {
        val switch = LocalDate.of(2026, 6, 1)
        val methods =
            listOf(
                ContraceptionPeriod(ContraceptionMethod.NONE, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31)),
                ContraceptionPeriod(
                    ContraceptionMethod.COMBINED_PILL,
                    LocalDate.of(2026, 1, 1),
                    switch.minusDays(1),
                    regimen = Regimen.CONTINUOUS,
                ),
                ContraceptionPeriod(ContraceptionMethod.HORMONAL_IUD, switch, replaceByDate = LocalDate.of(2031, 6, 1)),
            )
        val pills = listOf(PillState.TAKEN, PillState.TAKEN, PillState.LATE, PillState.MISSED, PillState.TAKEN)
        val logged =
            pills.mapIndexed {
                index,
                state,
                ->
                DayEntry(LocalDate.of(2026, 3, 1).plusDays(index.toLong()), pillState = state)
            } +
                DayEntry(LocalDate.of(2026, 4, 20), emergencyContraception = true)
        val medication =
            requireNotNull(
                build(
                    entries = FakeData.insightsHistory().filterNot { e -> logged.any { it.date == e.date } } + logged,
                    sections = setOf(ReportSection.MEDICATION),
                    contraception = methods,
                ).medication,
            )
        assertEquals(
            listOf(
                ReportRow("Combined pill, continuous", "1 Jan – 31 May 2026"),
                ReportRow("Hormonal IUD", "since 1 Jun 2026 · replace by 1 Jun 2031"),
                ReportRow("Pill days logged", "3 taken · 1 late · 1 missed"),
                ReportRow("Emergency contraception", "20 Apr 2026"),
            ),
            medication.rows,
        )
        val none = requireNotNull(build(sections = setOf(ReportSection.MEDICATION)).medication)
        assertTrue(none.rows.isEmpty())
        assertEquals("No contraception recorded for this period.", none.emptyText)
    }
}
