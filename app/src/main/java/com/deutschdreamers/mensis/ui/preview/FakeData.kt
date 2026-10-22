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
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
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

    const val CYCLE_LENGTH: Int = 29
    private const val MINUTES_PER_HOUR = 60
}
