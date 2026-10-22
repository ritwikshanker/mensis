package com.deutschdreamers.mensis.domain.today

import com.deutschdreamers.mensis.domain.contraception.PackPosition
import com.deutschdreamers.mensis.domain.contraception.SuppressedSummary
import com.deutschdreamers.mensis.domain.contraception.WithdrawalBleedPrediction
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.prediction.CyclePrediction
import com.deutschdreamers.mensis.domain.prediction.FertileWindow
import com.deutschdreamers.mensis.domain.prediction.PeriodPrediction
import com.deutschdreamers.mensis.domain.pregnancy.PregnancyStatus
import java.time.LocalDate

/** The Today screen's state, in the order the resolver checks them. */
enum class TodayState { PREGNANCY, FIRST_LAUNCH, SUPPRESSED, CYCLIC, LATE, ON_PERIOD, FERTILE, NORMAL }

enum class CyclePhase { PERIOD, FOLLICULAR, FERTILE, LUTEAL }

/** Fill of one strip cell; one per visual treatment. */
enum class StripCellKind {
    PERIOD,
    PERIOD_PREDICTED,
    FERTILE,
    FERTILE_PREDICTED,
    OVULATION,
    OVULATION_PREDICTED,
    SPOTTING,
    BREAK,
    NONE,
}

data class StripCell(
    val day: Int,
    val kind: StripCellKind,
)

enum class StripLaneKind { PERIOD, FERTILE, FERTILE_ESTIMATED, PREDICTED, EXPECTED, ACTIVE, BREAK, LAST_DAYS, SPOTTING }

/** A labelled span of strip days (1-based, inclusive). */
data class StripLane(
    val kind: StripLaneKind,
    val fromDay: Int,
    val toDay: Int,
)

data class CycleStrip(
    val lengthDays: Int,
    val todayDay: Int?,
    val cells: List<StripCell>,
    val lanes: List<StripLane>,
) {
    fun kindOf(day: Int): StripCellKind = cells.getOrNull(day - 1)?.kind ?: StripCellKind.NONE
}

/** Everything the Today screen renders. Strings are built by the UI from these facts. */
data class TodayModel(
    val state: TodayState,
    val today: LocalDate,
    val methodKind: MethodKind,
    val contraception: ContraceptionPeriod?,
    /** The big numeral: cycle day, pack day, days since the last bleed, or pregnancy week. */
    val numeral: Int?,
    val phase: CyclePhase?,
    val strip: CycleStrip,
    val prediction: CyclePrediction?,
    val nextPeriod: PeriodPrediction?,
    val fertileWindowCard: FertileWindow?,
    val fertileWindowIsNextCycle: Boolean,
    val withdrawalBleed: WithdrawalBleedPrediction?,
    val packPosition: PackPosition?,
    val suppressed: SuppressedSummary?,
    val pregnancy: PregnancyStatus?,
    val lateByDays: Int,
    val periodLengthRange: IntRange?,
    val isOnPeriod: Boolean,
) {
    val showsRangeCards: Boolean get() = nextPeriod != null
}
