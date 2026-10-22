package com.deutschdreamers.mensis.domain.prediction

import com.deutschdreamers.mensis.domain.cycle.Cycle
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Statistics over the kept lengths of the most recent complete cycles (oldest first in [usedLengths]).
 * [weightedMean] weights recent cycles more ([PredictionConstants.RECENCY_DECAY]); [standardDeviation]
 * is the plain sample deviation used for the ± spread.
 */
data class CycleStatistics(
    val usedLengths: List<Int>,
    val discardedLengths: List<Int>,
    val weightedMean: Double,
    val mean: Double,
    val standardDeviation: Double,
    val min: Int,
    val max: Int,
) {
    val count: Int get() = usedLengths.size

    val isIrregular: Boolean get() = count >= 2 && max - min > PredictionConstants.IRREGULAR_SPAN_DAYS

    companion object {
        /** Null when no complete cycle survives the outlier filter. */
        fun fromCycles(
            cycles: List<Cycle>,
            window: Int = PredictionConstants.PREDICTION_WINDOW_CYCLES,
        ): CycleStatistics? {
            val lengths = cycles.filter { it.isComplete }.mapNotNull { it.lengthDays }.takeLast(window)
            return fromLengths(lengths)
        }

        fun fromLengths(lengths: List<Int>): CycleStatistics? {
            val filtered = OutlierFilter.filter(lengths)
            val kept = filtered.kept
            if (kept.isEmpty()) return null
            val weights = kept.indices.map { index -> PredictionConstants.RECENCY_DECAY.pow(kept.size - 1 - index) }
            val weightedMean = kept.zip(weights).sumOf { (length, weight) -> length * weight } / weights.sum()
            val mean = kept.average()
            val sd = if (kept.size < 2) 0.0 else sqrt(kept.sumOf { (it - mean).pow(2) } / (kept.size - 1))
            return CycleStatistics(
                usedLengths = kept,
                discardedLengths = filtered.discarded,
                weightedMean = weightedMean,
                mean = mean,
                standardDeviation = sd,
                min = kept.min(),
                max = kept.max(),
            )
        }
    }
}
