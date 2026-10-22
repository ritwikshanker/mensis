package com.deutschdreamers.mensis.ui.screens.today

import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.data.backup.BackupStatus
import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.domain.contraception.MethodSchedule
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.prediction.Confidence
import com.deutschdreamers.mensis.domain.prediction.CycleStatistics
import com.deutschdreamers.mensis.domain.prediction.FertileWindow
import com.deutschdreamers.mensis.domain.prediction.OvulationSource
import com.deutschdreamers.mensis.domain.prediction.PeriodPrediction
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.domain.pregnancy.PregnancyStatus
import com.deutschdreamers.mensis.domain.today.CyclePhase
import com.deutschdreamers.mensis.domain.today.StripCellKind
import com.deutschdreamers.mensis.domain.today.StripLaneKind
import com.deutschdreamers.mensis.domain.today.TodayModel
import com.deutschdreamers.mensis.domain.today.TodayState
import com.deutschdreamers.mensis.domain.today.TodayStateResolver
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.EN_DASH
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.format.LoggedChips
import com.deutschdreamers.mensis.ui.format.MethodLabels
import com.deutschdreamers.mensis.ui.screens.backup.BackupTexts
import java.time.LocalDate

/**
 * Words the Today model (design §3). Every sentence here is either design copy or built from the
 * domain facts; nothing claims more certainty than the engine has.
 */
object TodayUiBuilder {
    fun build(
        data: TrackerData,
        today: LocalDate,
        backup: BackupStatus,
    ): TodayUiState {
        val model = TodayStateResolver.resolve(data.inputFor(today))
        val todayEntry = data.entries.firstOrNull { it.date == today }
        val base =
            TodayUiState(
                today = today,
                state = model.state,
                dateLabel = Formats.weekdayDayMonth(today),
                banner = banner(backup),
                numeral = model.numeral?.toString() ?: NO_VALUE,
                numeralLabel = "Cycle day",
                phase = "",
                supporting = "",
                strip = model.strip,
                stripHelper = null,
                laneLabels = laneLabels(model),
                cards = emptyList(),
                lateCard = null,
                firstLaunchCard = false,
                methodCard = null,
                pregnancyCard = null,
                logged = LoggedChips.of(todayEntry, data.settings),
            )
        return when (model.state) {
            TodayState.PREGNANCY -> pregnancy(base, model)
            TodayState.FIRST_LAUNCH -> firstLaunch(base)
            TodayState.SUPPRESSED -> suppressed(base, model, data.entries)
            TodayState.CYCLIC -> cyclic(base, model)
            else -> natural(base, model, data.settings)
        }
    }

    private fun banner(status: BackupStatus): BannerUi? =
        when (status) {
            BackupStatus.Off ->
                BannerUi(
                    BannerKind.OFF,
                    R.drawable.ms_cloud_off,
                    "Backup is off. Your data exists only on this phone.",
                    "Turn on",
                )
            is BackupStatus.Failing ->
                BannerUi(
                    BannerKind.FAILING,
                    R.drawable.ms_sync_problem,
                    "Last backup failed: ${BackupTexts.reason(status.error)}. " +
                        if (status.wifiOnly) "It will retry on Wi-Fi." else "It will retry automatically.",
                    "Retry",
                )
            is BackupStatus.Overdue ->
                BannerUi(
                    BannerKind.OVERDUE,
                    R.drawable.ms_history,
                    "Last backup was ${Formats.days(status.daysSinceSuccess)} ago.",
                    "Back up now",
                )
            BackupStatus.NeedsReconnect ->
                BannerUi(
                    BannerKind.RECONNECT,
                    R.drawable.ms_manage_accounts,
                    "Google account needs reconnecting before the next backup.",
                    "Reconnect",
                )
            is BackupStatus.Idle, BackupStatus.Unavailable -> null
        }

    private fun laneLabels(model: TodayModel): Map<StripLaneKind, String> =
        StripLaneKind.entries.associateWith { kind ->
            when (kind) {
                StripLaneKind.PERIOD -> "Period"
                StripLaneKind.FERTILE -> "Fertile"
                StripLaneKind.FERTILE_ESTIMATED -> "Fertile (est.)"
                StripLaneKind.PREDICTED -> "Predicted"
                StripLaneKind.EXPECTED -> "Expected"
                StripLaneKind.ACTIVE -> "Active"
                StripLaneKind.BREAK -> "Break"
                StripLaneKind.LAST_DAYS -> "Last ${model.strip.lengthDays} days"
                StripLaneKind.SPOTTING -> "Spotting"
            }
        }

    private fun pregnancy(
        base: TodayUiState,
        model: TodayModel,
    ): TodayUiState {
        val status = requireNotNull(model.pregnancy)
        return base.copy(
            numeral = status.week.toString(),
            numeralLabel = "Weeks pregnant",
            phase = "Week ${status.week}, day ${status.dayOfWeek}",
            supporting = "Counted from ${Formats.dayMonth(status.lastPeriodStart)}, first day of last period",
            strip = null,
            pregnancyCard =
                PregnancyCardUi(
                    dueDate = Formats.dayMonthYear(status.dueDate),
                    trimester = Formats.ordinal(status.trimester),
                    progress = status.progress.toFloat(),
                    caption =
                        "Week ${status.week} of ${TOTAL_PREGNANCY_WEEKS}. Due dates are estimates; " +
                            "most births happen within two weeks either side.",
                ),
        )
    }

    private fun firstLaunch(base: TodayUiState): TodayUiState =
        base.copy(
            numeral = NO_VALUE,
            phase = "No cycle data yet",
            supporting = "Predictions appear after your first complete cycle",
            stripHelper = "The strip fills in as you log. Predictions start after your first complete cycle.",
            firstLaunchCard = true,
        )

    private fun suppressed(
        base: TodayUiState,
        model: TodayModel,
        entries: List<DayEntry>,
    ): TodayUiState {
        val method = requireNotNull(model.contraception)
        val summary = requireNotNull(model.suppressed)
        val spotting =
            when {
                summary.bleedingDaysInWindow == 0 -> "no bleeding"
                summary.fullPeriodsInWindow == 0 -> "${Formats.days(
                    summary.bleedingDaysInWindow,
                )} of spotting, no full period"
                else ->
                    "${Formats.days(summary.bleedingDaysInWindow)} of bleeding, " +
                        "${summary.fullPeriodsInWindow} full period${if (summary.fullPeriodsInWindow == 1) "" else "s"}"
            }
        val (nextLabel, next) = methodNext(method, entries)
        return base.copy(
            numeral = summary.daysSinceLastBleed?.toString() ?: NO_VALUE,
            numeralLabel = "Days since last bleed",
            phase = "Predictions paused",
            supporting =
                "Bleeding usually becomes light, irregular or stops on this method. " +
                    "That is expected; anything you log is kept.",
            methodCard =
                MethodCardUi(
                    title = MethodLabels.name(method.method),
                    sinceLabel = "Since",
                    since = Formats.dayMonthYear(method.startDate),
                    nextLabel = nextLabel,
                    next = next,
                    body = "Bleeding you log stays on the strip. Last ${summary.windowDays} days: $spotting.",
                ),
        )
    }

    private fun methodNext(
        method: ContraceptionPeriod,
        entries: List<DayEntry>,
    ): Pair<String, String> =
        when {
            method.method == ContraceptionMethod.INJECTION ->
                "Next injection" to
                    (MethodSchedule.nextInjectionDate(method, entries)?.let(Formats::dayMonthYear) ?: NO_VALUE)
            method.method.isLongActing ->
                "Replace by" to (MethodSchedule.replaceByDate(method)?.let(Formats::monthYear) ?: NO_VALUE)
            method.dailyTime != null -> "Reminder" to "Daily, ${Formats.time(requireNotNull(method.dailyTime))}"
            else -> "Regimen" to "Continuous"
        }

    private fun cyclic(
        base: TodayUiState,
        model: TodayModel,
    ): TodayUiState {
        val method = requireNotNull(model.contraception)
        val position = requireNotNull(model.packPosition)
        val bleed = requireNotNull(model.withdrawalBleed)
        val activeDays = PredictionConstants.PACK_ACTIVE_DAYS
        val breakDays = PredictionConstants.PACK_LENGTH_DAYS - activeDays
        val nextPack = Formats.dayMonth(position.nextPackStart)
        val suppressedNote = "Ovulation is suppressed, so no fertile window is shown."
        val phase: String
        val supporting: String
        if (position.isBreak) {
            phase = "Break week, day ${position.packDay - activeDays} of $breakDays"
            supporting = "${MethodLabels.nextCycleTitle(method.method)} starts $nextPack. $suppressedNote"
        } else {
            phase = "Active phase, day ${position.packDay} of $activeDays"
            supporting = "Break week from ${Formats.dayMonth(position.breakStart)}. $suppressedNote"
        }
        val packsBasis =
            if (bleed.packsUsed >= PredictionConstants.WITHDRAWAL_MIN_PACKS_FOR_HISTORY) {
                "${Formats.packs(bleed.packsUsed)} $MIDDLE_DOT ±${bleed.spreadDays} d"
            } else {
                "set by the regimen $MIDDLE_DOT ±${bleed.spreadDays} d"
            }
        val cards =
            listOf(
                RangeCardUi(
                    icon = R.drawable.ms_water_drop,
                    title = "Withdrawal bleed",
                    range = Formats.range(bleed.range),
                    supporting = Formats.inDays(model.today, bleed.range),
                    pips = bleed.confidence.pips,
                    basis = packsBasis,
                    accent = CardAccent.PERIOD,
                ),
                RangeCardUi(
                    icon = R.drawable.ms_event_repeat,
                    title = MethodLabels.nextCycleTitle(method.method),
                    range = nextPack,
                    supporting = "after the ${MethodLabels.breakName(method.method)}",
                    pips = Confidence.HIGH.pips,
                    basis = "set by the regimen",
                    accent = CardAccent.NEUTRAL,
                ),
            )
        return base.copy(numeralLabel = "Pack day", phase = phase, supporting = supporting, cards = cards)
    }

    private fun natural(
        base: TodayUiState,
        model: TodayModel,
        settings: TrackerSettings,
    ): TodayUiState {
        val prediction = requireNotNull(model.prediction)
        val phase: String
        val supporting: String
        when (model.state) {
            TodayState.LATE -> {
                phase = "${Formats.days(model.lateByDays)} later than expected"
                supporting = lateSupporting(prediction.nextPeriod, prediction.statistics?.max)
            }
            TodayState.ON_PERIOD -> {
                phase = "Period, day ${model.numeral}"
                supporting = periodLengthSentence(model.periodLengthRange)
            }
            else -> {
                phase =
                    when (model.phase) {
                        CyclePhase.FERTILE -> "Fertile window"
                        CyclePhase.FOLLICULAR -> "Follicular phase"
                        else -> "Luteal phase"
                    }
                supporting = ovulationSentence(prediction.fertileWindow, settings.trackingLevel, model.state)
            }
        }
        return base.copy(
            phase = phase,
            supporting = supporting,
            cards = naturalCards(model),
            lateCard =
                if (model.state ==
                    TodayState.LATE
                ) {
                    LateCardUi(lateBody(prediction.nextPeriod, prediction.statistics))
                } else {
                    null
                },
        )
    }

    private fun lateSupporting(
        next: PeriodPrediction?,
        longest: Int?,
    ): String {
        val expected = next?.let { "Expected ${Formats.range(it.range)}." } ?: ""
        val longestText = longest?.let { " Longest cycle so far: ${Formats.days(it)}." } ?: ""
        return (expected + longestText).trim()
    }

    private fun lateBody(
        next: PeriodPrediction?,
        statistics: CycleStatistics?,
    ): String {
        val expected = next?.let { "Your period was expected ${Formats.between(it.range)}. " } ?: ""
        val normal =
            statistics?.takeIf { it.count >= PredictionConstants.MIN_CYCLES_FOR_HISTORY }?.let {
                val span = if (it.min == it.max) "${it.min} days" else "${it.min} to ${it.max} days"
                "Cycles of $span have been normal for you, so a few days of variation is common. "
            } ?: ""
        return expected + normal + "If you had unprotected sex this cycle, a pregnancy test is reliable from today."
    }

    private fun periodLengthSentence(range: IntRange?): String =
        when {
            range == null -> "Log the days you bleed; the period length is counted from them"
            range.first == range.last -> "Your periods usually last ${Formats.days(range.first)}"
            else -> "Your periods usually last ${range.first}$EN_DASH${range.last} days"
        }

    private fun ovulationSentence(
        window: FertileWindow?,
        level: TrackingLevel,
        state: TodayState,
    ): String {
        if (window == null) return "Ovulation is estimated once your next period can be predicted"
        val date = Formats.dayMonth(window.ovulation.date)
        return when (window.ovulation.source) {
            OvulationSource.TEMPERATURE_CONFIRMED -> "Ovulation $date, confirmed by temperature shift"
            OvulationSource.LH_TEST -> "Ovulation estimated $date from your LH test"
            OvulationSource.CALENDAR ->
                if (level == TrackingLevel.FULL && state == TodayState.FERTILE) {
                    "Ovulation estimated $date. An LH test can confirm it."
                } else {
                    "Ovulation estimated $date from your cycle lengths"
                }
        }
    }

    private fun naturalCards(model: TodayModel): List<RangeCardUi> {
        val next = model.nextPeriod ?: return emptyList()
        val cards = mutableListOf<RangeCardUi>()
        cards +=
            RangeCardUi(
                icon = R.drawable.ms_water_drop,
                title = "Next period",
                range = Formats.range(next.range),
                supporting = Formats.inDays(model.today, next.range),
                pips = next.confidence.pips,
                basis = periodBasis(next),
                accent = CardAccent.PERIOD,
            )
        val fertile = model.fertileWindowCard
        if (fertile != null) cards += fertileCard(model, fertile, next)
        return cards
    }

    private fun periodBasis(next: PeriodPrediction): String =
        if (next.usesTypicalLength) {
            "typical length $MIDDLE_DOT ±${next.spreadDays} d"
        } else {
            "${Formats.cycles(next.basisCycles)} $MIDDLE_DOT ±${next.spreadDays} d"
        }

    private fun fertileCard(
        model: TodayModel,
        window: FertileWindow,
        next: PeriodPrediction,
    ): RangeCardUi {
        val today = model.today
        val range = window.range
        val estimated = window.isEstimated
        val supporting =
            when {
                model.fertileWindowIsNextCycle -> "next cycle, estimated"
                today in range ->
                    "day ${today.toEpochDay() - range.start.toEpochDay() + 1} of ${range.lengthDays}" +
                        if (estimated) ", estimated" else ""
                estimated -> "estimated"
                else -> "confirmed by temperature"
            }
        val spread = (window.ovulation.range.lengthDays - 1) / 2
        val pips: Int
        val basis: String
        when (window.ovulation.source) {
            OvulationSource.TEMPERATURE_CONFIRMED -> {
                pips = Confidence.HIGH.pips
                basis = "temperature shift"
            }
            OvulationSource.LH_TEST -> {
                pips = Confidence.MEDIUM.pips
                basis = "LH test $MIDDLE_DOT ±$spread d"
            }
            OvulationSource.CALENDAR -> {
                pips = if (next.usesTypicalLength) Confidence.NONE.pips else Confidence.LOW.pips
                basis = "±$spread d"
            }
        }
        return RangeCardUi(
            icon = R.drawable.ms_waves,
            title = "Fertile window",
            range = Formats.range(range),
            supporting = supporting,
            pips = pips,
            basis = basis,
            accent = CardAccent.FERTILE,
        )
    }

    /** Builds the accessibility sentence for one strip cell. */
    fun cellDescription(
        day: Int,
        kind: StripCellKind,
        isToday: Boolean,
        numeralLabel: String,
    ): String {
        val what =
            when (kind) {
                StripCellKind.PERIOD -> "period"
                StripCellKind.PERIOD_PREDICTED -> "predicted period"
                StripCellKind.FERTILE -> "fertile window"
                StripCellKind.FERTILE_PREDICTED -> "fertile window, estimated"
                StripCellKind.OVULATION -> "ovulation, confirmed"
                StripCellKind.OVULATION_PREDICTED -> "ovulation, estimated"
                StripCellKind.SPOTTING -> "spotting"
                StripCellKind.BREAK -> "break week"
                StripCellKind.NONE -> "nothing"
            }
        return listOfNotNull(
            "${numeralLabel.lowercase()} $day",
            if (isToday) "today" else null,
            what,
        ).joinToString(", ")
    }

    private const val NO_VALUE = "–"
    private val TOTAL_PREGNANCY_WEEKS = PregnancyStatus.TOTAL_WEEKS
}
