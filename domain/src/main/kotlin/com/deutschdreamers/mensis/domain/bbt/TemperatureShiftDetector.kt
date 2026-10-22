package com.deutschdreamers.mensis.domain.bbt

import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate
import java.time.LocalTime

/** One waking temperature. [isLate] readings are flagged in the chart and skipped by the shift rule. */
data class TemperatureReading(
    val date: LocalDate,
    val temperatureC: Double,
    val time: LocalTime? = null,
) {
    val isLate: Boolean get() = time != null && !time.isBefore(PredictionConstants.BBT_LATE_CUTOFF)

    companion object {
        fun from(entry: DayEntry): TemperatureReading? =
            entry.temperatureC?.let { TemperatureReading(entry.date, it, entry.temperatureTime) }
    }
}

/** A detected temperature shift. Ovulation is taken as the day before the first elevated reading. */
data class TemperatureShift(
    val firstElevatedDate: LocalDate,
    val confirmedDate: LocalDate,
    val coverlineC: Double,
    val riseC: Double,
) {
    val ovulationDate: LocalDate get() = firstElevatedDate.minusDays(1)
}

/**
 * The 3-over-6 rule: three consecutive valid readings each above the highest of the previous six
 * valid readings by [PredictionConstants.BBT_SHIFT_MARGIN_C], the third at least
 * [PredictionConstants.BBT_THIRD_READING_MIN_RISE_C] above that coverline, all within
 * [PredictionConstants.BBT_ELEVATED_MAX_SPAN_DAYS] days.
 */
object TemperatureShiftDetector {
    fun detect(readings: List<TemperatureReading>): TemperatureShift? {
        val valid = readings.filterNot { it.isLate }.sortedBy { it.date }
        val baseline = PredictionConstants.BBT_BASELINE_READINGS
        val elevated = PredictionConstants.BBT_ELEVATED_READINGS
        for (index in baseline..valid.size - elevated) {
            val coverline = valid.subList(index - baseline, index).maxOf { it.temperatureC }
            val candidates = valid.subList(index, index + elevated)
            if (isShift(candidates, coverline)) {
                return TemperatureShift(
                    firstElevatedDate = candidates.first().date,
                    confirmedDate = candidates.last().date,
                    coverlineC = coverline,
                    riseC = candidates.last().temperatureC - coverline,
                )
            }
        }
        return null
    }

    private fun isShift(
        candidates: List<TemperatureReading>,
        coverline: Double,
    ): Boolean {
        val allElevated =
            candidates.all {
                it.temperatureC >
                    coverline + PredictionConstants.BBT_SHIFT_MARGIN_C - EPSILON
            }
        val thirdHighEnough =
            candidates.last().temperatureC >= coverline + PredictionConstants.BBT_THIRD_READING_MIN_RISE_C - EPSILON
        val span = candidates.first().date.daysUntil(candidates.last().date) + 1
        return allElevated && thirdHighEnough && span <= PredictionConstants.BBT_ELEVATED_MAX_SPAN_DAYS
    }

    /** Guards against binary floating-point noise when comparing hundredths of a degree. */
    private const val EPSILON = 1e-9
}
