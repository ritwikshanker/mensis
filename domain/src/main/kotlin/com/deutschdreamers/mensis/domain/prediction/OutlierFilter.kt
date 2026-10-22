package com.deutschdreamers.mensis.domain.prediction

import kotlin.math.abs

/**
 * Removes implausible cycle lengths before they feed a prediction: first absolute bounds
 * ([PredictionConstants.CYCLE_LENGTH_MIN_DAYS]..[PredictionConstants.CYCLE_LENGTH_MAX_DAYS]), then a
 * median-absolute-deviation test. Order is preserved.
 */
object OutlierFilter {
    data class Result(
        val kept: List<Int>,
        val discarded: List<Int>,
    )

    fun filter(lengths: List<Int>): Result {
        val inBounds =
            lengths.filter {
                it in
                    PredictionConstants.CYCLE_LENGTH_MIN_DAYS..PredictionConstants.CYCLE_LENGTH_MAX_DAYS
            }
        val median = median(inBounds.map { it.toDouble() })
        val mad = median(inBounds.map { abs(it - median) })
        val scaledMad = maxOf(PredictionConstants.MAD_FLOOR_DAYS, PredictionConstants.MAD_NORMAL_SCALE * mad)
        val limit = PredictionConstants.MAD_OUTLIER_MULTIPLIER * scaledMad
        val kept = inBounds.filter { abs(it - median) <= limit }
        return Result(kept = kept, discarded = lengths - kept.toSet())
    }

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
}
