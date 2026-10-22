package com.deutschdreamers.mensis.domain.prediction

import com.deutschdreamers.mensis.domain.bbt.TemperatureReading
import com.deutschdreamers.mensis.domain.bbt.TemperatureShiftDetector
import com.deutschdreamers.mensis.domain.cycle.Cycle
import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.daysUntil
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

data class PredictionInput(
    val entries: List<DayEntry>,
    val settings: TrackerSettings,
    val today: LocalDate,
    val contraception: List<ContraceptionPeriod> = emptyList(),
)

/**
 * Everything the engine derives for the natural (none / copper IUD) case. Hormonal methods are handled
 * by the contraception package; the Today resolver decides which to show.
 */
data class CyclePrediction(
    val today: LocalDate,
    val cycles: List<Cycle>,
    val currentCycle: Cycle?,
    val statistics: CycleStatistics?,
    val lutealLengthDays: Int,
    val lutealLearnedFrom: Int,
    val confirmedOvulations: List<ConfirmedOvulation>,
    val nextPeriod: PeriodPrediction?,
    val ovulation: OvulationEstimate?,
    val fertileWindow: FertileWindow?,
    val nextCycleFertileWindow: FertileWindow?,
    val lateByDays: Int,
) {
    val isLate: Boolean get() = lateByDays > 0

    /** 1-based day of the current cycle on [today], or null before any period was logged. */
    val currentCycleDay: Int? get() = currentCycle?.dayOf(today)
}

object PredictionEngine {
    fun predict(input: PredictionInput): CyclePrediction {
        val entries = input.entries.sortedBy { it.date }
        val cycles = CycleDeriver.derive(entries)
        val currentCycle = cycles.lastOrNull()
        val statistics = CycleStatistics.fromCycles(cycles)
        val confirmed = confirmedOvulations(cycles, entries)
        val (luteal, learnedFrom) = lutealLength(confirmed)
        val nextPeriod = currentCycle?.let { nextPeriod(it, statistics, entries, input.settings) }
        val lateByDays =
            nextPeriod?.let { prediction ->
                val threshold = prediction.latest.plusDays(PredictionConstants.LATE_GRACE_DAYS.toLong())
                if (input.today.isAfter(threshold)) prediction.latest.daysUntil(input.today) else 0
            } ?: 0
        val ovulation = currentCycle?.let { ovulationFor(it, entries, nextPeriod, luteal) }
        val fertileWindow = ovulation?.let { fertileWindowFor(it) }
        val nextCycleFertileWindow = nextPeriod?.let { nextCycleFertileWindow(it, statistics, input.settings, luteal) }
        return CyclePrediction(
            today = input.today,
            cycles = cycles,
            currentCycle = currentCycle,
            statistics = statistics,
            lutealLengthDays = luteal,
            lutealLearnedFrom = learnedFrom,
            confirmedOvulations = confirmed,
            nextPeriod = nextPeriod,
            ovulation = ovulation,
            fertileWindow = fertileWindow,
            nextCycleFertileWindow = nextCycleFertileWindow,
            lateByDays = lateByDays,
        )
    }

    /** Ovulations found inside complete cycles: temperature shift first, LH positive as fallback. */
    fun confirmedOvulations(
        cycles: List<Cycle>,
        entries: List<DayEntry>,
    ): List<ConfirmedOvulation> =
        cycles.filter { it.isComplete }.mapNotNull { cycle ->
            val nextStart = cycle.nextStartDate ?: return@mapNotNull null
            val inCycle = entries.filter { it.date in cycle }
            val (date, source) = detectOvulation(inCycle) ?: return@mapNotNull null
            val luteal = date.daysUntil(nextStart)
            if (luteal <= 0) null else ConfirmedOvulation(cycle.startDate, date, source, luteal)
        }

    private fun detectOvulation(inCycle: List<DayEntry>): Pair<LocalDate, OvulationSource>? {
        val shift = TemperatureShiftDetector.detect(inCycle.mapNotNull { TemperatureReading.from(it) })
        if (shift != null) return shift.ovulationDate to OvulationSource.TEMPERATURE_CONFIRMED
        val firstPositive = inCycle.firstOrNull { it.lhTest == TestResult.POSITIVE }?.date
        if (firstPositive != null) {
            return firstPositive.plusDays(PredictionConstants.LH_OVULATION_OFFSET_MIN_DAYS.toLong()) to
                OvulationSource.LH_TEST
        }
        return null
    }

    /** Personal luteal length once enough confirmations exist, else the default. */
    fun lutealLength(confirmed: List<ConfirmedOvulation>): Pair<Int, Int> {
        val usable = confirmed.map { it.lutealDays.toDouble() }
        if (usable.size <
            PredictionConstants.LUTEAL_MIN_CONFIRMATIONS
        ) {
            return PredictionConstants.DEFAULT_LUTEAL_DAYS to usable.size
        }
        val median = OutlierFilter.median(usable).roundToInt()
        return median.coerceIn(PredictionConstants.LUTEAL_MIN_DAYS, PredictionConstants.LUTEAL_MAX_DAYS) to usable.size
    }

    private fun nextPeriod(
        current: Cycle,
        statistics: CycleStatistics?,
        entries: List<DayEntry>,
        settings: TrackerSettings,
    ): PeriodPrediction {
        val emergencyWiden =
            if (entries.any { it.date in current && it.emergencyContraception == true }) {
                PredictionConstants.EMERGENCY_CONTRACEPTION_WIDEN_DAYS
            } else {
                0
            }
        val history = statistics?.takeIf { it.count >= PredictionConstants.MIN_CYCLES_FOR_HISTORY }
        val useHistory = history != null
        val expectedLength: Int
        val spread: Int
        val confidence: Confidence
        val basis: Int
        if (history != null) {
            expectedLength = history.weightedMean.roundToInt()
            val irregularExtra = if (history.isIrregular) PredictionConstants.IRREGULAR_EXTRA_SPREAD_DAYS else 0
            spread =
                maxOf(
                    PredictionConstants.MIN_SPREAD_DAYS,
                    ceil(history.standardDeviation).toInt(),
                ) + irregularExtra +
                emergencyWiden
            confidence = Confidence.forCycleCount(history.count)
            basis = history.count
        } else {
            expectedLength = settings.typicalCycleLength
            spread = PredictionConstants.DEFAULT_SPREAD_DAYS + emergencyWiden
            confidence = if (statistics == null || statistics.count == 0) Confidence.NONE else Confidence.LOW
            basis = statistics?.count ?: 0
        }
        val mostLikely = current.startDate.plusDays(expectedLength.toLong())
        return PeriodPrediction(
            earliest = mostLikely.minusDays(spread.toLong()),
            mostLikely = mostLikely,
            latest = mostLikely.plusDays(spread.toLong()),
            confidence = confidence,
            basisCycles = basis,
            spreadDays = spread,
            usesTypicalLength = !useHistory,
        )
    }

    private fun ovulationFor(
        current: Cycle,
        entries: List<DayEntry>,
        nextPeriod: PeriodPrediction?,
        luteal: Int,
    ): OvulationEstimate? {
        val inCycle = entries.filter { it.date in current }
        return observedOvulation(inCycle) ?: calendarOvulation(nextPeriod, luteal)
    }

    /** Temperature-confirmed or LH-indicated ovulation inside the given entries, if any. */
    private fun observedOvulation(inCycle: List<DayEntry>): OvulationEstimate? {
        val shift = TemperatureShiftDetector.detect(inCycle.mapNotNull { TemperatureReading.from(it) })
        if (shift != null) {
            val date = shift.ovulationDate
            return OvulationEstimate(date, DateRange.single(date), OvulationSource.TEMPERATURE_CONFIRMED)
        }
        val firstPositive = inCycle.firstOrNull { it.lhTest == TestResult.POSITIVE }?.date ?: return null
        val earliest = firstPositive.plusDays(PredictionConstants.LH_OVULATION_OFFSET_MIN_DAYS.toLong())
        val latest = firstPositive.plusDays(PredictionConstants.LH_OVULATION_OFFSET_MAX_DAYS.toLong())
        return OvulationEstimate(earliest, DateRange(earliest, latest), OvulationSource.LH_TEST)
    }

    private fun calendarOvulation(
        nextPeriod: PeriodPrediction?,
        luteal: Int,
    ): OvulationEstimate? {
        if (nextPeriod == null) return null
        val days = luteal.toLong()
        val range = DateRange(nextPeriod.earliest.minusDays(days), nextPeriod.latest.minusDays(days))
        return OvulationEstimate(nextPeriod.mostLikely.minusDays(days), range, OvulationSource.CALENDAR)
    }

    private fun fertileWindowFor(ovulation: OvulationEstimate): FertileWindow =
        FertileWindow(
            range =
                DateRange(
                    ovulation.date.minusDays(PredictionConstants.FERTILE_WINDOW_DAYS_BEFORE_OVULATION.toLong()),
                    ovulation.date,
                ),
            ovulation = ovulation,
        )

    private fun nextCycleFertileWindow(
        nextPeriod: PeriodPrediction,
        statistics: CycleStatistics?,
        settings: TrackerSettings,
        luteal: Int,
    ): FertileWindow {
        val length =
            if (statistics != null && statistics.count >= PredictionConstants.MIN_CYCLES_FOR_HISTORY) {
                statistics.weightedMean.roundToInt()
            } else {
                settings.typicalCycleLength
            }
        val ovulationDate = nextPeriod.mostLikely.plusDays((length - luteal).toLong())
        val spread = nextPeriod.spreadDays.toLong()
        val ovulation =
            OvulationEstimate(
                date = ovulationDate,
                range = DateRange(ovulationDate.minusDays(spread), ovulationDate.plusDays(spread)),
                source = OvulationSource.CALENDAR,
            )
        return fertileWindowFor(ovulation)
    }
}
