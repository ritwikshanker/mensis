package com.deutschdreamers.mensis.ui.preview

import com.deutschdreamers.mensis.data.backup.BackupStatus
import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.ui.screens.insights.InsightsUiBuilder
import com.deutschdreamers.mensis.ui.screens.insights.InsightsUiState
import com.deutschdreamers.mensis.ui.screens.today.TodayUiBuilder
import com.deutschdreamers.mensis.ui.screens.today.TodayUiState
import java.time.LocalDate
import java.time.LocalTime

/**
 * Synthetic data for previews and UI tests. Nothing here comes from a real person: the dates are
 * generated from a fixed first period and regular cycle lengths.
 */
@Suppress("MagicNumber") // the numbers are the synthetic fixture itself
object FakeData {
    val firstStart: LocalDate = LocalDate.of(2026, 1, 5)
    private val periodFlows = listOf(Flow.MEDIUM, Flow.HEAVY, Flow.MEDIUM, Flow.LIGHT, Flow.LIGHT)

    /** Eight complete 29-day cycles plus the open one; the last period started on [lastStart]. */
    val lastStart: LocalDate = firstStart.plusDays(8L * CYCLE_LENGTH)

    fun regularHistory(
        cycleLength: Int = CYCLE_LENGTH,
        cycles: Int = 8,
        lastPeriodDays: Int = periodFlows.size,
    ): List<DayEntry> =
        (0..cycles).flatMap { index ->
            val start = firstStart.plusDays(index.toLong() * cycleLength)
            val flows = if (index == cycles) periodFlows.take(lastPeriodDays) else periodFlows
            flows.mapIndexed { day, flow -> DayEntry(start.plusDays(day.toLong()), flow = flow) }
        }

    fun todayEntry(date: LocalDate): DayEntry =
        DayEntry(
            date = date,
            temperatureC = 36.71,
            temperatureTime = LocalTime.of(6, 40),
            symptoms = setOf(Symptom.HEADACHE),
            moods = setOf(Mood.CALM),
            sleepMinutes = 7 * MINUTES_PER_HOUR + 20,
        )

    val settings: TrackerSettings = TrackerSettings(trackingLevel = TrackingLevel.FULL)

    fun data(
        entries: List<DayEntry>,
        settings: TrackerSettings = this.settings,
        contraception: List<ContraceptionPeriod> = emptyList(),
    ): TrackerData = TrackerData(entries, settings, contraception)

    fun today(
        data: TrackerData,
        today: LocalDate,
        backup: BackupStatus = BackupStatus.Off,
    ): TodayUiState = TodayUiBuilder.build(data, today, backup)

    /** Day 17 of the open cycle: luteal phase with both range cards. */
    fun normal(): TodayUiState =
        today(data(regularHistory() + todayEntry(lastStart.plusDays(16))), lastStart.plusDays(16))

    /** Day 2 with only days 1–2 logged, so days 3–5 are predicted. */
    fun onPeriod(): TodayUiState = today(data(regularHistory(lastPeriodDays = 2)), lastStart.plusDays(1))

    fun fertile(): TodayUiState = today(data(regularHistory()), lastStart.plusDays(12))

    /** Day 34: three days past the latest predicted date plus the grace period. */
    fun late(): TodayUiState = today(data(regularHistory()), lastStart.plusDays(33))

    fun firstLaunch(): TodayUiState = today(data(emptyList()), firstStart)

    fun cyclic(): TodayUiState {
        val start = LocalDate.of(2026, 1, 3)
        val pill =
            ContraceptionPeriod(
                ContraceptionMethod.COMBINED_PILL,
                start,
                regimen = Regimen.CYCLIC,
                dailyTime = LocalTime.of(21, 0),
            )
        val today = start.plusDays(28L * 10 + 16)
        return today(data(emptyList(), contraception = listOf(pill)), today)
    }

    fun suppressed(): TodayUiState {
        val inserted = LocalDate.of(2026, 3, 12)
        val iud = ContraceptionPeriod(ContraceptionMethod.HORMONAL_IUD, inserted)
        val today = inserted.plusDays(200)
        val spotting = listOf(47L, 48L, 60L).map { DayEntry(today.minusDays(it), flow = Flow.SPOTTING) }
        return today(data(spotting, contraception = listOf(iud)), today)
    }

    fun pregnancy(): TodayUiState {
        val lmp = LocalDate.of(2026, 8, 17)
        val settings = settings.copy(mode = TrackerMode.PREGNANCY, pregnancyLmp = lmp)
        return today(data(emptyList(), settings = settings), lmp.plusDays(7L * 8 + 1))
    }

    /** Insights fixture "today": day 17 of a cycle that started on 5 Oct 2026, as in the design. */
    val insightsToday: LocalDate = LocalDate.of(2026, 10, 21)

    private val insightLengths = listOf(29, 31, 28, 30, 27, 32, 29, 29)
    private val insightPeriodDays = listOf(5, 4, 5, 6, 5, 5, 4, 5)
    private val insightLuteal = listOf(null, null, 14, 13, 15, 14, 14, 13)
    private val insightHeadache = setOf(1, 2, 3, 5, 6, 7)
    private val insightFatigue = setOf(0, 1, 3, 5, 6)
    private val insightFlows = listOf(Flow.MEDIUM, Flow.HEAVY, Flow.MEDIUM, Flow.LIGHT, Flow.LIGHT, Flow.LIGHT)
    private val lowTemps =
        listOf(36.42, 36.38, 36.45, 36.40, 36.36, 36.44, 36.41, 36.39, 36.43, 36.45, 36.40, 36.38, 36.39, 36.35)
    private val highTemps = listOf(36.61, 36.68, 36.71, 36.66, 36.70, 36.64, 36.69)
    private val wakingTime = LocalTime.of(6, 40)

    /**
     * The design's Insights numbers: the last [cycles] of eight complete cycles (29, 31, 28, 30, 27, 32,
     * 29, 29 days; periods 4–6 days, heaviest on day 2; six with a temperature shift and a 13–15 day
     * luteal phase; cramps, headache and fatigue in 8, 6 and 5 of 8 cycles) plus the open cycle on day 17
     * of [insightsToday], whose temperatures rise from day 15.
     */
    fun insightsHistory(cycles: Int = insightLengths.size): List<DayEntry> {
        val first = insightLengths.size - cycles
        val currentStart = insightsToday.minusDays(16)
        val starts =
            insightLengths
                .drop(
                    first,
                ).reversed()
                .runningFold(currentStart) { d, l -> d.minusDays(l.toLong()) }
                .reversed()
        val days = mutableMapOf<LocalDate, DayEntry>()

        fun add(
            date: LocalDate,
            change: (DayEntry) -> DayEntry,
        ) {
            days[date] = change(days[date] ?: DayEntry(date))
        }
        for (index in first until insightLengths.size) {
            val start = starts[index - first]
            val length = insightLengths[index]
            insightFlows.take(insightPeriodDays[index]).forEachIndexed { day, flow ->
                add(start.plusDays(day.toLong())) { it.copy(flow = flow) }
            }
            add(start) { it.copy(symptoms = it.symptoms + Symptom.CRAMPS) }
            if (index in
                insightHeadache
            ) {
                add(start.plusDays(length - 2L)) { it.copy(symptoms = it.symptoms + Symptom.HEADACHE) }
            }
            if (index in
                insightFatigue
            ) {
                add(start.plusDays(length - 5L)) { it.copy(symptoms = it.symptoms + Symptom.FATIGUE) }
            }
            insightLuteal[index]?.let { luteal -> addTemperatures(start, length, length - luteal + 2, ::add) }
        }
        insightFlows.take(periodFlows.size).forEachIndexed { day, flow ->
            add(currentStart.plusDays(day.toLong())) { it.copy(flow = flow) }
        }
        add(currentStart) { it.copy(symptoms = it.symptoms + Symptom.CRAMPS) }
        addTemperatures(currentStart, 17, 15, ::add)
        return days.values.sortedBy { it.date }
    }

    /** Readings on days 1..[through]: low before [firstHighDay], high from it. */
    private fun addTemperatures(
        start: LocalDate,
        through: Int,
        firstHighDay: Int,
        add: (LocalDate, (DayEntry) -> DayEntry) -> Unit,
    ) {
        for (day in 1..through) {
            val value =
                if (day <
                    firstHighDay
                ) {
                    lowTemps[(day - 1) % lowTemps.size]
                } else {
                    highTemps[(day - firstHighDay) % highTemps.size]
                }
            add(start.plusDays(day - 1L)) { it.copy(temperatureC = value, temperatureTime = wakingTime) }
        }
    }

    fun insights(
        cycles: Int = insightLengths.size,
        trackingLevel: TrackingLevel = TrackingLevel.FULL,
        temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
        contraception: List<ContraceptionPeriod> = emptyList(),
    ): InsightsUiState =
        InsightsUiBuilder.build(
            data(
                insightsHistory(cycles),
                settings = settings.copy(trackingLevel = trackingLevel, temperatureUnit = temperatureUnit),
                contraception = contraception,
            ),
            insightsToday,
        )

    const val CYCLE_LENGTH: Int = 29
    private const val MINUTES_PER_HOUR = 60
}
