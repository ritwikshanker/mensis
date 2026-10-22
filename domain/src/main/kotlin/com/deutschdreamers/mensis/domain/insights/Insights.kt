package com.deutschdreamers.mensis.domain.insights

import com.deutschdreamers.mensis.domain.bbt.TemperatureReading
import com.deutschdreamers.mensis.domain.bbt.TemperatureShift
import com.deutschdreamers.mensis.domain.bbt.TemperatureShiftDetector
import com.deutschdreamers.mensis.domain.cycle.Cycle
import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.ConfirmedOvulation
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Cycle lengths of the last complete cycles (oldest first), as shown on Insights.
 * No outlier filtering: this is descriptive.
 */
data class CycleLengthStats(
    val startDates: List<LocalDate>,
    val lengths: List<Int>,
    val mean: Double,
    val min: Int,
    val max: Int,
    val standardDeviation: Double,
) {
    val count: Int get() = lengths.size
}

data class PeriodLengthStats(
    val mean: Double,
    val min: Int,
    val max: Int,
    /**
     * 1-based period day on which flow was heaviest most often, or null without flow detail. A period
     * counts only when one day's flow is heavier than every other day's.
     */
    val peakFlowDay: Int?,
)

/**
 * Waking temperatures of one cycle (oldest first) and the shift the 3-over-6 rule finds in them.
 * [heldDays] counts the days from the first elevated reading to the last valid reading of the run
 * that stays above the coverline.
 */
data class CycleTemperatures(
    val cycle: Cycle,
    val readings: List<TemperatureReading>,
    val shift: TemperatureShift?,
    val heldDays: Int,
)

data class LutealStats(
    val mean: Double,
    val min: Int,
    val max: Int,
    val confirmedCount: Int,
)

enum class CyclePhaseBucket { PERIOD_START, PRE_PERIOD, LATE_LUTEAL, MID_CYCLE }

data class SymptomPattern(
    val symptom: Symptom,
    val phase: CyclePhaseBucket,
    val cyclesWith: Int,
    val cyclesConsidered: Int,
) {
    val fraction: Double get() = cyclesWith.toDouble() / cyclesConsidered
}

object InsightsCalculator {
    /** The complete cycles Insights describes: the last [window], oldest first. */
    fun windowCycles(
        cycles: List<Cycle>,
        window: Int = PredictionConstants.INSIGHTS_WINDOW_CYCLES,
    ): List<Cycle> = cycles.filter { it.isComplete }.takeLast(window)

    fun cycleLengths(
        cycles: List<Cycle>,
        window: Int = PredictionConstants.INSIGHTS_WINDOW_CYCLES,
    ): CycleLengthStats? {
        val complete = windowCycles(cycles, window)
        val lengths = complete.mapNotNull { it.lengthDays }
        if (lengths.isEmpty()) return null
        val mean = lengths.average()
        val sd = if (lengths.size < 2) 0.0 else sqrt(lengths.sumOf { (it - mean).pow(2) } / (lengths.size - 1))
        return CycleLengthStats(complete.map { it.startDate }, lengths, mean, lengths.min(), lengths.max(), sd)
    }

    fun periodLengths(
        cycles: List<Cycle>,
        entries: List<DayEntry>,
        window: Int = PredictionConstants.INSIGHTS_WINDOW_CYCLES,
    ): PeriodLengthStats? {
        val complete = windowCycles(cycles, window)
        if (complete.isEmpty()) return null
        val lengths = complete.map { it.periodLengthDays }
        val peakDays =
            complete.mapNotNull { cycle ->
                val bleeding = entries.filter { it.date in cycle.periodRange && it.isBleedingDay }
                val heaviest = bleeding.maxOfOrNull { it.flow?.ordinal ?: 0 }
                bleeding
                    .filter { (it.flow?.ordinal ?: 0) == heaviest }
                    .singleOrNull()
                    ?.let { cycle.dayOf(it.date) }
            }
        val peak =
            peakDays
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        return PeriodLengthStats(lengths.average(), lengths.min(), lengths.max(), peak)
    }

    fun luteal(confirmed: List<ConfirmedOvulation>): LutealStats? {
        if (confirmed.isEmpty()) return null
        val days = confirmed.map { it.lutealDays }
        return LutealStats(days.average(), days.min(), days.max(), days.size)
    }

    /** Symptoms that recur in the same phase in at least half of the considered cycles, strongest first. */
    fun symptomPatterns(
        cycles: List<Cycle>,
        entries: List<DayEntry>,
        window: Int = PredictionConstants.INSIGHTS_WINDOW_CYCLES,
        limit: Int = DEFAULT_PATTERN_LIMIT,
    ): List<SymptomPattern> {
        val complete = windowCycles(cycles, window)
        if (complete.size < PredictionConstants.SYMPTOM_PATTERN_MIN_CYCLES) return emptyList()
        val byDate = entries.associateBy { it.date }
        val patterns =
            Symptom.entries.flatMap { symptom ->
                CyclePhaseBucket.entries.map { phase ->
                    val hits =
                        complete.count { cycle ->
                            bucketRange(cycle, phase).any {
                                byDate[it]?.symptoms?.contains(symptom) ==
                                    true
                            }
                        }
                    SymptomPattern(symptom, phase, hits, complete.size)
                }
            }
        return patterns
            .filter { it.cyclesWith > 0 && it.fraction >= PredictionConstants.SYMPTOM_PATTERN_MIN_FRACTION }
            .sortedWith(compareByDescending<SymptomPattern> { it.fraction }.thenBy { it.phase }.thenBy { it.symptom })
            .take(limit)
    }

    /** The readings of [cycle] and its shift, found the same way the prediction engine finds it. */
    fun temperatures(
        cycle: Cycle,
        entries: List<DayEntry>,
    ): CycleTemperatures {
        val inCycle = entries.filter { it.date in cycle }
        val readings = inCycle.mapNotNull { TemperatureReading.from(it) }.sortedBy { it.date }
        val shift = TemperatureShiftDetector.detect(readings)
        val held =
            shift?.let { found ->
                val run =
                    readings
                        .filterNot { it.isLate }
                        .dropWhile { it.date.isBefore(found.firstElevatedDate) }
                        .takeWhile { it.temperatureC > found.coverlineC }
                found.firstElevatedDate.daysUntil(run.last().date) + 1
            } ?: 0
        return CycleTemperatures(cycle, readings, shift, held)
    }

    private fun bucketRange(
        cycle: Cycle,
        phase: CyclePhaseBucket,
    ): Iterable<LocalDate> {
        val nextStart = requireNotNull(cycle.nextStartDate)
        val range =
            when (phase) {
                CyclePhaseBucket.PERIOD_START -> DateRange(cycle.startDate, cycle.startDate.plusDays(1))
                CyclePhaseBucket.PRE_PERIOD ->
                    DateRange(
                        nextStart.minusDays(PredictionConstants.SYMPTOM_PRE_PERIOD_DAYS.toLong()),
                        nextStart.minusDays(1),
                    )
                CyclePhaseBucket.LATE_LUTEAL ->
                    DateRange(
                        nextStart.minusDays(PredictionConstants.SYMPTOM_LATE_LUTEAL_DAYS.toLong()),
                        nextStart.minusDays((PredictionConstants.SYMPTOM_PRE_PERIOD_DAYS + 1).toLong()),
                    )
                CyclePhaseBucket.MID_CYCLE -> {
                    val ovulation = nextStart.minusDays(PredictionConstants.DEFAULT_LUTEAL_DAYS.toLong())
                    DateRange(
                        ovulation.minusDays(PredictionConstants.FERTILE_WINDOW_DAYS_BEFORE_OVULATION.toLong()),
                        ovulation,
                    )
                }
            }
        return generateSequence(range.start) { it.plusDays(1) }.takeWhile { !it.isAfter(range.end) }.asIterable()
    }

    private const val DEFAULT_PATTERN_LIMIT = 3
}
