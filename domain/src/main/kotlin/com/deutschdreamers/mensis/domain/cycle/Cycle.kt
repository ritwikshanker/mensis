package com.deutschdreamers.mensis.domain.cycle

import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.model.daysUntil
import java.time.LocalDate

/**
 * A menstrual cycle derived from logged flow: it starts on a period's first day and ends the day
 * before the next period starts. The last cycle is open ([nextStartDate] is null) and is never used
 * for statistics. Cycles are never stored; they are recomputed from day entries.
 */
data class Cycle(
    val startDate: LocalDate,
    val nextStartDate: LocalDate?,
    val periodLengthDays: Int,
) {
    val isComplete: Boolean get() = nextStartDate != null

    /** Days from this start to the next start; null for the open cycle. */
    val lengthDays: Int? get() = nextStartDate?.let { startDate.daysUntil(it) }

    val endDate: LocalDate? get() = nextStartDate?.minusDays(1)

    val periodRange: DateRange get() = DateRange(startDate, startDate.plusDays((periodLengthDays - 1).toLong()))

    /** 1-based cycle day of [date] (may exceed the length for dates after the cycle). */
    fun dayOf(date: LocalDate): Int = startDate.daysUntil(date) + 1

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && (nextStartDate == null || date.isBefore(nextStartDate))
}
