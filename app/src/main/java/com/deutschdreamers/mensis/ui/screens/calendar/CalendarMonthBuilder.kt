package com.deutschdreamers.mensis.ui.screens.calendar

import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.domain.contraception.WithdrawalBleedPredictor
import com.deutschdreamers.mensis.domain.cycle.Cycle
import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.domain.prediction.CyclePrediction
import com.deutschdreamers.mensis.domain.prediction.OvulationSource
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.domain.prediction.PredictionEngine
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.format.LoggedChips
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Turns raw entries and the prediction into the month grid and day card (design §5). */
object CalendarMonthBuilder {
    private const val DAYS_PER_WEEK = 7
    private const val ROWS = 6

    fun build(
        data: TrackerData,
        today: LocalDate,
        selection: CalendarSelection,
    ): CalendarUiState {
        val prediction = PredictionEngine.predict(data.inputFor(today))
        val method = data.contraception.activeOn(today)
        val kind = method?.kind ?: MethodKind.NONE
        val byDate = data.entries.associateBy { it.date }
        val bleed = method?.let { WithdrawalBleedPredictor.predict(it, data.entries, today) }?.range
        val context = CellContext(Marks.from(prediction, kind, bleed), today, selection)
        val first = selection.month.atDay(1)
        val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks =
            (0 until ROWS).map { row ->
                (0 until DAYS_PER_WEEK).map { column ->
                    val date = gridStart.plusDays((row * DAYS_PER_WEEK + column).toLong())
                    if (YearMonth.from(date) != selection.month) null else cell(date, byDate[date], context)
                }
            }
        return CalendarUiState(
            month = selection.month,
            monthTitle = Formats.fullMonthYear(first),
            weeks = weeks,
            selected = selection.selected,
            dayCard = dayCard(selection.selected, byDate[selection.selected], prediction.cycles, data, kind),
            editMode = selection.editMode,
            pendingToggles = selection.toggles,
        )
    }

    private class CellContext(
        val marks: Marks,
        val today: LocalDate,
        val selection: CalendarSelection,
    )

    private fun cell(
        date: LocalDate,
        entry: DayEntry?,
        context: CellContext,
    ): DayCellUi {
        val editMode = context.selection.editMode
        val editMarked = (entry?.isMenstrualDay == true) != (date in context.selection.toggles)
        val cell =
            DayCellUi(
                date = date,
                kind = cellKind(date, entry, context.marks, editMode, editMarked),
                ovulation = if (editMode) OvulationMark.NONE else ovulationMark(date, context.marks),
                isToday = date == context.today,
                isLogged = entry != null && !entry.isEmpty,
                isSelected = date == context.selection.selected,
                editMarked = editMarked,
                description = "",
            )
        return cell.copy(description = describe(cell, editMode))
    }

    private fun cellKind(
        date: LocalDate,
        entry: DayEntry?,
        marks: Marks,
        editMode: Boolean,
        editMarked: Boolean,
    ): CalendarCellKind =
        when {
            editMode -> if (editMarked) CalendarCellKind.PERIOD else CalendarCellKind.NONE
            entry?.isMenstrualDay == true -> CalendarCellKind.PERIOD
            entry?.isBleedingDay == true -> CalendarCellKind.SPOTTING
            marks.predicted.any { date in it } -> CalendarCellKind.PREDICTED
            marks.fertile.any { date in it } -> CalendarCellKind.FERTILE
            marks.fertileEstimated.any { date in it } -> CalendarCellKind.FERTILE_ESTIMATED
            else -> CalendarCellKind.NONE
        }

    private fun ovulationMark(
        date: LocalDate,
        marks: Marks,
    ): OvulationMark =
        when (date) {
            in marks.ovulationConfirmed -> OvulationMark.CONFIRMED
            in marks.ovulationEstimated -> OvulationMark.ESTIMATED
            else -> OvulationMark.NONE
        }

    private fun describe(
        cell: DayCellUi,
        editMode: Boolean,
    ): String {
        val parts = mutableListOf(Formats.spokenDate(cell.date))
        if (cell.isToday) parts += "today"
        if (editMode) {
            parts += if (cell.editMarked) "bleeding, tap to unmark" else "not bleeding, tap to mark"
            return parts.joinToString(", ")
        }
        kindWords(cell.kind)?.let { parts += it }
        when (cell.ovulation) {
            OvulationMark.CONFIRMED -> parts += "ovulation, confirmed"
            OvulationMark.ESTIMATED -> parts += "ovulation, estimated"
            OvulationMark.NONE -> Unit
        }
        if (cell.isLogged) parts += "logged"
        return parts.joinToString(", ")
    }

    private fun kindWords(kind: CalendarCellKind): String? =
        when (kind) {
            CalendarCellKind.PERIOD -> "period"
            CalendarCellKind.SPOTTING -> "spotting"
            CalendarCellKind.FERTILE -> "fertile window"
            CalendarCellKind.FERTILE_ESTIMATED -> "fertile window, estimated"
            CalendarCellKind.PREDICTED -> "predicted period"
            CalendarCellKind.NONE -> null
        }

    private fun dayCard(
        date: LocalDate,
        entry: DayEntry?,
        cycles: List<Cycle>,
        data: TrackerData,
        kind: MethodKind,
    ): DayCardUi {
        val cycle = cycles.firstOrNull { date in it }
        val index = cycles.indexOf(cycle)
        val previous = if (index > 0) cycles[index - 1] else null
        val dayLabel =
            when {
                cycle == null -> ""
                kind == MethodKind.SUPPRESS -> ""
                else -> " $MIDDLE_DOT Cycle day ${cycle.dayOf(date)}"
            }
        val footer =
            when {
                cycles.isEmpty() -> "No period logged yet"
                cycle == null -> "Before your first logged period"
                previous?.lengthDays != null ->
                    "Cycle started ${Formats.dayMonth(cycle.startDate)} $MIDDLE_DOT " +
                        "${previous.lengthDays} d before: ${Formats.dayMonth(previous.startDate)}"
                else -> "Cycle started ${Formats.dayMonth(cycle.startDate)}"
            }
        return DayCardUi(
            title = Formats.weekdayDayMonth(date) + dayLabel,
            chips = LoggedChips.of(entry, data.settings),
            footer = footer,
        )
    }

    /** Date sets the grid colours come from. */
    private data class Marks(
        val predicted: List<DateRange>,
        val fertile: List<DateRange>,
        val fertileEstimated: List<DateRange>,
        val ovulationConfirmed: Set<LocalDate>,
        val ovulationEstimated: Set<LocalDate>,
    ) {
        companion object {
            fun from(
                prediction: CyclePrediction,
                kind: MethodKind,
                withdrawalBleed: DateRange?,
            ): Marks {
                val predicted = mutableListOf<DateRange>()
                val fertile = mutableListOf<DateRange>()
                val fertileEstimated = mutableListOf<DateRange>()
                val confirmed = mutableSetOf<LocalDate>()
                val estimated = mutableSetOf<LocalDate>()
                when (kind) {
                    MethodKind.CYCLIC -> withdrawalBleed?.let { predicted += it }
                    MethodKind.SUPPRESS -> Unit
                    MethodKind.NONE, MethodKind.COPPER -> {
                        if (!prediction.isLate) prediction.nextPeriod?.let { predicted += it.range }
                        for (past in prediction.confirmedOvulations) {
                            val window =
                                DateRange(
                                    past.date.minusDays(
                                        PredictionConstants.FERTILE_WINDOW_DAYS_BEFORE_OVULATION.toLong(),
                                    ),
                                    past.date,
                                )
                            if (past.source == OvulationSource.TEMPERATURE_CONFIRMED) {
                                fertile += window
                                confirmed += past.date
                            } else {
                                fertileEstimated += window
                                estimated += past.date
                            }
                        }
                        prediction.fertileWindow?.let { window ->
                            if (window.isEstimated) {
                                fertileEstimated += window.range
                                estimated += window.ovulation.date
                            } else {
                                fertile += window.range
                                confirmed += window.ovulation.date
                            }
                        }
                        if (!prediction.isLate) {
                            prediction.nextCycleFertileWindow?.let { window ->
                                fertileEstimated += window.range
                                estimated += window.ovulation.date
                            }
                        }
                    }
                }
                return Marks(predicted, fertile, fertileEstimated, confirmed, estimated)
            }
        }
    }
}
