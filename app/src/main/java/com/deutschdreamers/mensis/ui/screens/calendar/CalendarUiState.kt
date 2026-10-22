package com.deutschdreamers.mensis.ui.screens.calendar

import com.deutschdreamers.mensis.ui.screens.today.ValueChipUi
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarCellKind { NONE, PERIOD, SPOTTING, FERTILE, FERTILE_ESTIMATED, PREDICTED }

enum class OvulationMark { NONE, CONFIRMED, ESTIMATED }

data class DayCellUi(
    val date: LocalDate,
    val kind: CalendarCellKind,
    val ovulation: OvulationMark,
    val isToday: Boolean,
    val isLogged: Boolean,
    val isSelected: Boolean,
    /** Edit mode: the day is currently marked as bleeding (after pending toggles). */
    val editMarked: Boolean,
    val description: String,
)

data class DayCardUi(
    val title: String,
    val chips: List<ValueChipUi>,
    val footer: String,
)

/** What the user has chosen on the Calendar tab; the rest is derived from the data. */
data class CalendarSelection(
    val month: YearMonth,
    val selected: LocalDate,
    val editMode: Boolean = false,
    val toggles: Set<LocalDate> = emptySet(),
)

data class CalendarUiState(
    val month: YearMonth,
    val monthTitle: String,
    /** Six rows of seven cells, Monday first; null for days outside the month. */
    val weeks: List<List<DayCellUi?>>,
    val selected: LocalDate,
    val dayCard: DayCardUi,
    val editMode: Boolean,
    val pendingToggles: Set<LocalDate>,
)
