package com.deutschdreamers.mensis.domain.cycle

import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate

/**
 * Derives period starts and cycles from day entries.
 *
 * A period starts on the first day with flow of at least "light" that comes more than
 * [PredictionConstants.PERIOD_GAP_DAYS] days after the previous such day. Spotting never starts a
 * period but does count towards period length, where bleeding days separated by at most
 * [PredictionConstants.PERIOD_RUN_MAX_GAP_DAYS] missing days form one run.
 */
object CycleDeriver {
    fun periodStarts(entries: Collection<DayEntry>): List<LocalDate> {
        val menstrualDays = entries.filter { it.isMenstrualDay }.map { it.date }.sorted()
        val starts = mutableListOf<LocalDate>()
        var previous: LocalDate? = null
        for (day in menstrualDays) {
            if (previous == null || previous.daysUntil(day) > PredictionConstants.PERIOD_GAP_DAYS) starts += day
            previous = day
        }
        return starts
    }

    fun derive(entries: Collection<DayEntry>): List<Cycle> {
        val starts = periodStarts(entries)
        val bleedingDays = entries.filter { it.isBleedingDay }.map { it.date }.sorted()
        return starts.mapIndexed { index, start ->
            val nextStart = starts.getOrNull(index + 1)
            Cycle(
                startDate = start,
                nextStartDate = nextStart,
                periodLengthDays = periodLength(start, nextStart, bleedingDays),
            )
        }
    }

    private fun periodLength(
        start: LocalDate,
        nextStart: LocalDate?,
        bleedingDays: List<LocalDate>,
    ): Int {
        val candidates = bleedingDays.filter { it.isAfter(start) && (nextStart == null || it.isBefore(nextStart)) }
        var last = start
        for (day in candidates) {
            if (last.daysUntil(day) > PredictionConstants.PERIOD_RUN_MAX_GAP_DAYS + 1) break
            last = day
        }
        return start.daysUntil(last) + 1
    }
}
