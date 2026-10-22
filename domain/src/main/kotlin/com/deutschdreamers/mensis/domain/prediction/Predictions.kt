package com.deutschdreamers.mensis.domain.prediction

import com.deutschdreamers.mensis.domain.model.DateRange
import java.time.LocalDate

/** Where an ovulation estimate comes from, in decreasing order of certainty. */
enum class OvulationSource { TEMPERATURE_CONFIRMED, LH_TEST, CALENDAR }

data class OvulationEstimate(
    val date: LocalDate,
    val range: DateRange,
    val source: OvulationSource,
) {
    val isConfirmed: Boolean get() = source == OvulationSource.TEMPERATURE_CONFIRMED
}

/** The fertile window: five days before estimated ovulation through ovulation day. */
data class FertileWindow(
    val range: DateRange,
    val ovulation: OvulationEstimate,
) {
    val isEstimated: Boolean get() = !ovulation.isConfirmed
}

/** The next period as a date range plus the basis the range rests on. */
data class PeriodPrediction(
    val earliest: LocalDate,
    val mostLikely: LocalDate,
    val latest: LocalDate,
    val confidence: Confidence,
    val basisCycles: Int,
    val spreadDays: Int,
    val usesTypicalLength: Boolean,
) {
    val range: DateRange get() = DateRange(earliest, latest)
}

/** An ovulation confirmed (temperature) or indicated (LH) inside a complete cycle; used to learn the luteal length. */
data class ConfirmedOvulation(
    val cycleStart: LocalDate,
    val date: LocalDate,
    val source: OvulationSource,
    val lutealDays: Int,
)
