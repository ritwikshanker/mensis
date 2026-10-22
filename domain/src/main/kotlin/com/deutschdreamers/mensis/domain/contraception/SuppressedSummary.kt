package com.deutschdreamers.mensis.domain.contraception

import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate

/** What Today shows for a suppressing method: predictions are paused, bleeding is kept as spotting. */
data class SuppressedSummary(
    val lastBleedDate: LocalDate?,
    val daysSinceLastBleed: Int?,
    val bleedingDaysInWindow: Int,
    val fullPeriodsInWindow: Int,
    val windowDays: Int,
)

object SuppressedSummaryCalculator {
    fun summarize(
        entries: List<DayEntry>,
        today: LocalDate,
    ): SuppressedSummary {
        val window = PredictionConstants.SUPPRESSED_SPOTTING_WINDOW_DAYS
        val windowStart = today.minusDays((window - 1).toLong())
        val bleeding = entries.filter { it.isBleedingDay && !it.date.isAfter(today) }
        val lastBleed = bleeding.maxOfOrNull { it.date }
        val inWindow = bleeding.filter { !it.date.isBefore(windowStart) }
        val periodsInWindow = CycleDeriver.periodStarts(inWindow).size
        return SuppressedSummary(
            lastBleedDate = lastBleed,
            daysSinceLastBleed = lastBleed?.daysUntil(today),
            bleedingDaysInWindow = inWindow.size,
            fullPeriodsInWindow = periodsInWindow,
            windowDays = window,
        )
    }
}
