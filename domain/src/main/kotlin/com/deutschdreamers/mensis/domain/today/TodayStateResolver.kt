package com.deutschdreamers.mensis.domain.today

import com.deutschdreamers.mensis.domain.contraception.PackCalendar
import com.deutschdreamers.mensis.domain.contraception.SuppressedSummaryCalculator
import com.deutschdreamers.mensis.domain.contraception.WithdrawalBleedPredictor
import com.deutschdreamers.mensis.domain.cycle.Cycle
import com.deutschdreamers.mensis.domain.insights.InsightsCalculator
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.CyclePrediction
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.domain.prediction.PredictionEngine
import com.deutschdreamers.mensis.domain.prediction.PredictionInput
import com.deutschdreamers.mensis.domain.pregnancy.PregnancyCalculator
import java.time.LocalDate

/**
 * Turns raw entries and settings into the Today model. Precedence: pregnancy → suppressing method, or a
 * hormonal IUD with no period to predict from → cyclic method → first launch → late → on period →
 * fertile → normal.
 */
object TodayStateResolver {
    fun resolve(input: PredictionInput): TodayModel {
        val entries = input.entries.sortedBy { it.date }
        val method = input.contraception.activeOn(input.today)
        val kind = method?.kind ?: MethodKind.NONE
        val prediction = PredictionEngine.predict(input.copy(entries = entries))
        val lmp = input.settings.pregnancyLmp
        val noPeriodToPredict = prediction.currentCycle == null || prediction.isLate
        return when {
            input.settings.mode == TrackerMode.PREGNANCY && lmp != null -> pregnancy(input.today, lmp, method, kind)
            kind == MethodKind.SUPPRESS || (kind == MethodKind.LOCAL_HORMONAL && noPeriodToPredict) ->
                suppressed(input.today, entries, prediction, requireNotNull(method))
            kind == MethodKind.CYCLIC -> cyclic(input.today, entries, prediction, requireNotNull(method))
            prediction.currentCycle == null -> firstLaunch(input.today, prediction, method, kind)
            else -> natural(input, entries, prediction, method, kind)
        }
    }

    private fun pregnancy(
        today: LocalDate,
        lmp: LocalDate,
        method: ContraceptionPeriod?,
        kind: MethodKind,
    ): TodayModel {
        val status = PregnancyCalculator.calculate(lmp, today)
        return empty(TodayState.PREGNANCY, today, method, kind).copy(numeral = status.week, pregnancy = status)
    }

    private fun firstLaunch(
        today: LocalDate,
        prediction: CyclePrediction,
        method: ContraceptionPeriod?,
        kind: MethodKind,
    ): TodayModel = empty(TodayState.FIRST_LAUNCH, today, method, kind).copy(prediction = prediction)

    private fun empty(
        state: TodayState,
        today: LocalDate,
        method: ContraceptionPeriod?,
        kind: MethodKind,
    ): TodayModel {
        val n = PredictionConstants.STRIP_MIN_DAYS
        return TodayModel(
            state = state,
            today = today,
            methodKind = kind,
            contraception = method,
            numeral = null,
            phase = null,
            strip = CycleStrip(n, null, (1..n).map { StripCell(it, StripCellKind.NONE) }, emptyList()),
            prediction = null,
            nextPeriod = null,
            fertileWindowCard = null,
            fertileWindowIsNextCycle = false,
            withdrawalBleed = null,
            packPosition = null,
            suppressed = null,
            pregnancy = null,
            lateByDays = 0,
            periodLengthRange = null,
            isOnPeriod = false,
        )
    }

    private fun suppressed(
        today: LocalDate,
        entries: List<DayEntry>,
        prediction: CyclePrediction,
        method: ContraceptionPeriod,
    ): TodayModel {
        val summary = SuppressedSummaryCalculator.summarize(entries, today)
        val n = PredictionConstants.STRIP_MIN_DAYS
        val byDate = entries.associateBy { it.date }
        val firstDay = today.minusDays((n - 1).toLong())
        // A hormonal IUD often keeps the cycle going, so a logged period stays a period on its strip.
        val periodsKept = method.kind == MethodKind.LOCAL_HORMONAL
        val cells =
            (1..n).map { day ->
                val flow = byDate[firstDay.plusDays((day - 1).toLong())]?.flow
                val kind =
                    when {
                        periodsKept && flow?.isMenstrual == true -> StripCellKind.PERIOD
                        flow?.isBleeding == true -> StripCellKind.SPOTTING
                        else -> StripCellKind.NONE
                    }
                StripCell(day, kind)
            }
        val lanes =
            if (periodsKept) {
                listOf(StripLane(StripLaneKind.LAST_DAYS, 1, n))
            } else {
                listOf(
                    StripLane(StripLaneKind.LAST_DAYS, 1, SUPPRESSED_LANE_SPLIT_DAY),
                    StripLane(StripLaneKind.SPOTTING, SUPPRESSED_LANE_SPLIT_DAY + 1, n),
                )
            }
        return empty(TodayState.SUPPRESSED, today, method, method.kind).copy(
            numeral = summary.daysSinceLastBleed,
            strip = CycleStrip(n, n, cells, lanes),
            prediction = prediction,
            suppressed = summary,
        )
    }

    private fun cyclic(
        today: LocalDate,
        entries: List<DayEntry>,
        prediction: CyclePrediction,
        method: ContraceptionPeriod,
    ): TodayModel {
        val position = PackCalendar.position(method.startDate, today)
        val bleed = WithdrawalBleedPredictor.predict(method, entries, today)
        val n = PredictionConstants.PACK_LENGTH_DAYS
        val byDate = entries.associateBy { it.date }
        val cells =
            (1..n).map { day ->
                val date = position.packStart.plusDays((day - 1).toLong())
                val flow = byDate[date]?.flow
                val kind =
                    when {
                        flow?.isMenstrual == true -> StripCellKind.PERIOD
                        flow?.isBleeding == true -> StripCellKind.SPOTTING
                        date in bleed.range && bleed.position.packStart == position.packStart ->
                            StripCellKind.PERIOD_PREDICTED
                        day > PredictionConstants.PACK_ACTIVE_DAYS -> StripCellKind.BREAK
                        else -> StripCellKind.NONE
                    }
                StripCell(day, kind)
            }
        val lanes =
            listOf(
                StripLane(StripLaneKind.ACTIVE, 1, PredictionConstants.PACK_ACTIVE_DAYS),
                StripLane(StripLaneKind.BREAK, PredictionConstants.PACK_ACTIVE_DAYS + 1, n),
            )
        return empty(TodayState.CYCLIC, today, method, MethodKind.CYCLIC).copy(
            numeral = position.packDay,
            strip = CycleStrip(n, position.packDay, cells, lanes),
            prediction = prediction,
            withdrawalBleed = bleed,
            packPosition = position,
            isOnPeriod = byDate[today]?.isBleedingDay == true,
        )
    }

    /** Facts about the current natural cycle that both the state and the strip depend on. */
    private data class NaturalFacts(
        val cycle: Cycle,
        val cycleDay: Int,
        val expectedPeriodLength: Int,
        val onPeriod: Boolean,
        val state: TodayState,
        val phase: CyclePhase,
        val periodLengthRange: IntRange,
    )

    private fun natural(
        input: PredictionInput,
        entries: List<DayEntry>,
        prediction: CyclePrediction,
        method: ContraceptionPeriod?,
        kind: MethodKind,
    ): TodayModel {
        val facts = naturalFacts(input, entries, prediction)
        val fertile = prediction.fertileWindow
        val currentWindowUpcoming = fertile != null && !fertile.range.end.isBefore(input.today)
        val isLate = facts.state == TodayState.LATE
        val fertileCard =
            when {
                isLate -> null
                currentWindowUpcoming -> fertile
                else -> prediction.nextCycleFertileWindow
            }
        return TodayModel(
            state = facts.state,
            today = input.today,
            methodKind = kind,
            contraception = method,
            numeral = facts.cycleDay,
            phase = facts.phase,
            strip = naturalStrip(facts, entries, prediction),
            prediction = prediction,
            nextPeriod = if (isLate) null else prediction.nextPeriod,
            fertileWindowCard = fertileCard,
            fertileWindowIsNextCycle = !currentWindowUpcoming,
            withdrawalBleed = null,
            packPosition = null,
            suppressed = null,
            pregnancy = null,
            lateByDays = prediction.lateByDays,
            periodLengthRange = facts.periodLengthRange,
            isOnPeriod = facts.onPeriod,
        )
    }

    private fun naturalFacts(
        input: PredictionInput,
        entries: List<DayEntry>,
        prediction: CyclePrediction,
    ): NaturalFacts {
        val today = input.today
        val cycle = requireNotNull(prediction.currentCycle)
        val cycleDay = cycle.dayOf(today)
        val periodStats = InsightsCalculator.periodLengths(prediction.cycles, entries)
        val typical = input.settings.typicalPeriodLength
        val meanPeriod = periodStats?.mean?.let { Math.round(it).toInt() } ?: typical
        val expectedPeriodLength = maxOf(cycle.periodLengthDays, meanPeriod)
        val todayFlow = entries.firstOrNull { it.date == today }?.flow
        val onPeriod = cycleDay <= expectedPeriodLength && todayFlow != Flow.NONE
        val (state, phase) = stateAndPhase(today, prediction, onPeriod)
        val periodRange = periodStats?.let { it.min..it.max } ?: (typical..typical)
        return NaturalFacts(cycle, cycleDay, expectedPeriodLength, onPeriod, state, phase, periodRange)
    }

    private fun stateAndPhase(
        today: LocalDate,
        prediction: CyclePrediction,
        onPeriod: Boolean,
    ): Pair<TodayState, CyclePhase> {
        val fertile = prediction.fertileWindow
        val inWindow = fertile != null && today in fertile.range
        val state =
            when {
                prediction.isLate -> TodayState.LATE
                onPeriod -> TodayState.ON_PERIOD
                inWindow -> TodayState.FERTILE
                else -> TodayState.NORMAL
            }
        val phase =
            when {
                onPeriod -> CyclePhase.PERIOD
                inWindow -> CyclePhase.FERTILE
                fertile != null && today.isBefore(fertile.range.start) -> CyclePhase.FOLLICULAR
                else -> CyclePhase.LUTEAL
            }
        return state to phase
    }

    private fun naturalStrip(
        facts: NaturalFacts,
        entries: List<DayEntry>,
        prediction: CyclePrediction,
    ): CycleStrip {
        val cycle = facts.cycle
        val expectedLength = prediction.nextPeriod?.let { cycle.startDate.daysUntil(it.mostLikely) } ?: 0
        val lateExtra = if (prediction.isLate) facts.cycleDay + 1 else 0
        val n = maxOf(PredictionConstants.STRIP_MIN_DAYS, expectedLength, lateExtra)
        val byDate = entries.associateBy { it.date }
        val lastLoggedBleed =
            entries.filter { it.isBleedingDay && it.date in cycle.periodRange }.maxOfOrNull { it.date }
                ?: cycle.startDate
        val cells =
            (1..n).map { day ->
                val date = cycle.startDate.plusDays((day - 1).toLong())
                val flow = byDate[date]?.flow
                val kind =
                    when {
                        flow?.isMenstrual == true -> StripCellKind.PERIOD
                        flow?.isBleeding == true -> StripCellKind.SPOTTING
                        facts.onPeriod && date.isAfter(lastLoggedBleed) && day <= facts.expectedPeriodLength ->
                            StripCellKind.PERIOD_PREDICTED
                        else -> predictedKind(date, prediction)
                    }
                StripCell(day, kind)
            }
        return CycleStrip(n, facts.cycleDay.takeIf { it <= n }, cells, naturalLanes(facts, prediction, n))
    }

    private fun predictedKind(
        date: LocalDate,
        prediction: CyclePrediction,
    ): StripCellKind {
        val next = prediction.nextPeriod
        val ovulation = prediction.ovulation
        val fertile = prediction.fertileWindow
        return when {
            next != null && date in next.range -> StripCellKind.PERIOD_PREDICTED
            ovulation != null && date == ovulation.date ->
                if (ovulation.isConfirmed) StripCellKind.OVULATION else StripCellKind.OVULATION_PREDICTED
            fertile != null && date in fertile.range ->
                if (fertile.isEstimated) StripCellKind.FERTILE_PREDICTED else StripCellKind.FERTILE
            else -> StripCellKind.NONE
        }
    }

    private fun naturalLanes(
        facts: NaturalFacts,
        prediction: CyclePrediction,
        n: Int,
    ): List<StripLane> {
        val cycle = facts.cycle
        val lanes = mutableListOf<StripLane>()
        val periodEnd = if (facts.onPeriod) facts.expectedPeriodLength else cycle.periodLengthDays
        lanes += StripLane(StripLaneKind.PERIOD, 1, periodEnd.coerceAtMost(n))
        prediction.fertileWindow?.let { window ->
            val from = cycle.dayOf(window.range.start)
            val to = cycle.dayOf(window.range.end)
            if (to >= 1 && from <= n) {
                val laneKind = if (window.isEstimated) StripLaneKind.FERTILE_ESTIMATED else StripLaneKind.FERTILE
                lanes += StripLane(laneKind, from.coerceAtLeast(1), to.coerceAtMost(n))
            }
        }
        prediction.nextPeriod?.let { next ->
            val from = cycle.dayOf(next.earliest)
            val to = cycle.dayOf(next.latest)
            if (from <= n) {
                val laneKind = if (prediction.isLate) StripLaneKind.EXPECTED else StripLaneKind.PREDICTED
                lanes += StripLane(laneKind, from.coerceAtLeast(1), to.coerceAtMost(n))
            }
        }
        return lanes
    }

    /** Suppressed strip lanes: "Last 32 days" on the left, "Spotting" on the right. */
    private const val SUPPRESSED_LANE_SPLIT_DAY = 12
}
