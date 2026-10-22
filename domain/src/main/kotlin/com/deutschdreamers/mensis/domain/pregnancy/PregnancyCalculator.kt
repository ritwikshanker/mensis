package com.deutschdreamers.mensis.domain.pregnancy

import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate

data class PregnancyStatus(
    val lastPeriodStart: LocalDate,
    val dueDate: LocalDate,
    val daysSinceLmp: Int,
    val week: Int,
    val dayOfWeek: Int,
    val trimester: Int,
    val progress: Double,
) {
    companion object {
        private const val DAYS_PER_WEEK = 7
        const val TOTAL_WEEKS: Int = PredictionConstants.PREGNANCY_DURATION_DAYS / DAYS_PER_WEEK
    }
}

/** Due date = LMP + 280 days (Naegele); gestational week counted from the LMP, week 1 = days 0–6. */
object PregnancyCalculator {
    private const val DAYS_PER_WEEK = 7
    private const val THIRD_TRIMESTER = 3

    fun calculate(
        lastPeriodStart: LocalDate,
        today: LocalDate,
    ): PregnancyStatus {
        val days = maxOf(0, lastPeriodStart.daysUntil(today))
        val week = days / DAYS_PER_WEEK + 1
        val trimester =
            when {
                week <= PredictionConstants.TRIMESTER_1_LAST_WEEK -> 1
                week <= PredictionConstants.TRIMESTER_2_LAST_WEEK -> 2
                else -> THIRD_TRIMESTER
            }
        return PregnancyStatus(
            lastPeriodStart = lastPeriodStart,
            dueDate = lastPeriodStart.plusDays(PredictionConstants.PREGNANCY_DURATION_DAYS.toLong()),
            daysSinceLmp = days,
            week = week,
            dayOfWeek = days % DAYS_PER_WEEK + 1,
            trimester = trimester,
            progress = (days.toDouble() / PredictionConstants.PREGNANCY_DURATION_DAYS).coerceIn(0.0, 1.0),
        )
    }

    /**
     * The first day of the last period on or before [date]: what pregnancy mode counts from unless the
     * user picks another day. Null when no period start is logged by then.
     */
    fun lastPeriodStart(
        entries: Collection<DayEntry>,
        date: LocalDate,
    ): LocalDate? = CycleDeriver.periodStarts(entries).lastOrNull { !it.isAfter(date) }
}
