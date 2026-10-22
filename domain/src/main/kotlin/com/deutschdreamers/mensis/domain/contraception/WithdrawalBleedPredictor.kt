package com.deutschdreamers.mensis.domain.contraception

import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.Confidence
import com.deutschdreamers.mensis.domain.prediction.OutlierFilter
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate
import kotlin.math.roundToInt

data class WithdrawalBleedPrediction(
    val range: DateRange,
    val mostLikelyStart: LocalDate,
    val packsUsed: Int,
    val spreadDays: Int,
    val confidence: Confidence,
    val position: PackPosition,
) {
    val nextPackStart: LocalDate get() = position.nextPackStart
}

/**
 * Predicts the withdrawal bleed in the break week of a cyclic method. The default range is break days
 * [PredictionConstants.WITHDRAWAL_BLEED_START_BREAK_DAY]..[PredictionConstants.WITHDRAWAL_BLEED_END_BREAK_DAY];
 * once enough previous breaks have logged bleeding, the start shifts to their median offset. The base
 * uncertainty is reported as [WithdrawalBleedPrediction.spreadDays] ("±1 d"); missed pills in the current
 * pack and emergency contraception widen both the spread and the range. Once today is past the current
 * pack's bleed range, the next pack's bleed is predicted.
 */
object WithdrawalBleedPredictor {
    fun predict(
        period: ContraceptionPeriod,
        entries: List<DayEntry>,
        today: LocalDate,
    ): WithdrawalBleedPrediction {
        val current = predictFor(period, entries, PackCalendar.position(period.startDate, today))
        if (!today.isAfter(current.range.end)) return current
        return predictFor(period, entries, PackCalendar.position(period.startDate, current.nextPackStart))
    }

    private fun predictFor(
        period: ContraceptionPeriod,
        entries: List<DayEntry>,
        position: PackPosition,
    ): WithdrawalBleedPrediction {
        val offsets = historicalOffsets(period, position, entries)
        val startOffset =
            if (offsets.size >= PredictionConstants.WITHDRAWAL_MIN_PACKS_FOR_HISTORY) {
                OutlierFilter.median(offsets.map { it.toDouble() }).roundToInt()
            } else {
                PredictionConstants.WITHDRAWAL_BLEED_START_BREAK_DAY
            }
        val duration =
            PredictionConstants.WITHDRAWAL_BLEED_END_BREAK_DAY - PredictionConstants.WITHDRAWAL_BLEED_START_BREAK_DAY
        val extra = widening(position, entries)
        val mostLikely = position.breakStart.plusDays((startOffset - 1).toLong())
        return WithdrawalBleedPrediction(
            range = DateRange(mostLikely.minusDays(extra.toLong()), mostLikely.plusDays((duration + extra).toLong())),
            mostLikelyStart = mostLikely,
            packsUsed = offsets.size,
            spreadDays = PredictionConstants.MIN_SPREAD_DAYS + extra,
            confidence = Confidence.HIGH,
            position = position,
        )
    }

    /** Break-day offsets (1-based) of the first menstrual-flow day in each previous completed break. */
    private fun historicalOffsets(
        period: ContraceptionPeriod,
        position: PackPosition,
        entries: List<DayEntry>,
    ): List<Int> =
        (0 until position.packIndex).mapNotNull { index ->
            val pack =
                PackCalendar.position(
                    period.startDate,
                    period.startDate.plusDays(
                        (
                            index *
                                PredictionConstants.PACK_LENGTH_DAYS
                        ).toLong(),
                    ),
                )
            entries
                .filter { it.isMenstrualDay && it.date in pack.breakRange }
                .minOfOrNull { pack.breakStart.daysUntil(it.date) + 1 }
        }

    private fun widening(
        position: PackPosition,
        entries: List<DayEntry>,
    ): Int {
        val inPack = entries.filter { it.date >= position.packStart && it.date < position.nextPackStart }
        val missed = inPack.count { it.pillState == PillState.MISSED } * PredictionConstants.MISSED_PILL_WIDEN_DAYS
        val emergency =
            if (inPack.any {
                    it.emergencyContraception == true
                }
            ) {
                PredictionConstants.EMERGENCY_CONTRACEPTION_WIDEN_DAYS
            } else {
                0
            }
        return missed + emergency
    }
}
