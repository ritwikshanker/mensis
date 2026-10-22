package com.deutschdreamers.mensis.ui.screens.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.components.ContentCard
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.ValueChip
import com.deutschdreamers.mensis.ui.components.dashedBorder
import com.deutschdreamers.mensis.ui.components.hatched
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import java.time.LocalDate

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onLog: (LocalDate) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    val current = state
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    CalendarScreenContent(
        state = current,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onDayTap = { date ->
            if (current.editMode) {
                viewModel.toggle(date)
            } else {
                viewModel.select(date)
                onLog(date)
            }
        },
        onStartEdit = viewModel::startEdit,
        onCancelEdit = viewModel::cancelEdit,
        onSaveEdit = viewModel::saveEdit,
        onEditDay = { onLog(current.selected) },
    )
}

/** Stateless Calendar (design §5): month header, weekday row, grid, legend, day card; edit-period mode. */
@Composable
fun CalendarScreenContent(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayTap: (LocalDate) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onEditDay: () -> Unit,
) {
    if (state.editMode) BackHandler(onBack = onCancelEdit)
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.editMode) {
            MensisTopBar(
                title = stringResource(R.string.calendar_edit_title),
                onBack = onCancelEdit,
                backIcon = R.drawable.ms_close,
                backDescription = stringResource(R.string.action_cancel),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Button(
                    onClick = onSaveEdit,
                    modifier = Modifier.padding(end = 12.dp),
                ) { Text(stringResource(R.string.action_save)) }
            }
        } else {
            MensisTopBar(title = stringResource(R.string.tab_calendar)) {
                IconButton(onClick = onStartEdit) {
                    MsIcon(
                        resId = R.drawable.ms_edit_calendar,
                        contentDescription = stringResource(R.string.calendar_edit_period_dates),
                    )
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState(),
                    ).padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonthHeader(state.monthTitle, onPreviousMonth, onNextMonth)
            if (state.editMode) {
                InfoBanner(text = stringResource(R.string.calendar_edit_hint), icon = R.drawable.ms_touch_app)
            }
            WeekdayRow()
            MonthGrid(state, onDayTap)
            Legend()
            if (!state.editMode) {
                DayCard(state.dayCard, onEdit = onEditDay)
            }
        }
    }
}

@Composable
private fun MonthHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            MsIcon(
                resId = R.drawable.ms_chevron_left,
                contentDescription = stringResource(R.string.calendar_previous_month),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            MsIcon(
                resId = R.drawable.ms_chevron_right,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
    }
}

@Composable
private fun WeekdayRow() {
    val letters = stringResource(R.string.calendar_weekday_letters).split(" ")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (letter in letters) {
            Text(
                text = letter,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    state: CalendarUiState,
    onDayTap: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (week in state.weeks) {
            if (week.all { it == null }) continue
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (cell in week) {
                    if (cell == null) {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(cell, state.editMode, onClick = { onDayTap(cell.date) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: DayCellUi,
    editMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MensisTheme.colors
    val scheme = MaterialTheme.colorScheme
    val fill: Modifier
    val textColor: Color
    when (cell.kind) {
        CalendarCellKind.PERIOD -> {
            fill = Modifier.background(colors.period, CircleShape)
            textColor = colors.onPeriod
        }
        CalendarCellKind.SPOTTING -> {
            fill = Modifier.background(colors.periodContainer, CircleShape)
            textColor = colors.onPeriodContainer
        }
        CalendarCellKind.FERTILE -> {
            fill = Modifier.background(colors.fertileContainer, CircleShape)
            textColor = colors.onFertileContainer
        }
        CalendarCellKind.FERTILE_ESTIMATED -> {
            fill = Modifier.background(colors.fertileContainer, CircleShape).dashedBorder(colors.fertile, 1.dp, 999.dp)
            textColor = colors.onFertileContainer
        }
        CalendarCellKind.PREDICTED -> {
            fill =
                Modifier.hatched(colors.predHatch, colors.predTint, 999.dp).dashedBorder(colors.predHatch, 1.dp, 999.dp)
            textColor = scheme.onSurface
        }
        CalendarCellKind.NONE -> {
            fill = Modifier
            textColor = scheme.onSurface
        }
    }
    val ring =
        when {
            cell.isToday -> Modifier.border(2.dp, scheme.onSurface, CircleShape)
            cell.ovulation == OvulationMark.CONFIRMED -> Modifier.border(2.dp, colors.ovulation, CircleShape)
            cell.ovulation == OvulationMark.ESTIMATED -> Modifier.dashedBorder(colors.ovulation, 2.dp, 999.dp)
            editMode && !cell.editMarked -> Modifier.dashedBorder(scheme.outline, 1.dp, 999.dp)
            else -> Modifier
        }
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .then(fill)
                .then(ring)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = cell.description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (cell.isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                color = textColor,
            )
            Box(
                modifier =
                    Modifier
                        .size(4.dp)
                        .background(
                            when {
                                !cell.isLogged -> Color.Transparent
                                cell.kind == CalendarCellKind.PERIOD -> colors.onPeriod
                                else -> scheme.onSurfaceVariant
                            },
                            CircleShape,
                        ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val colors = MensisTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem(stringResource(R.string.legend_period), Modifier.background(colors.period, CircleShape))
        LegendItem(stringResource(R.string.legend_fertile), Modifier.background(colors.fertileContainer, CircleShape))
        LegendItem(stringResource(R.string.legend_ovulation), Modifier.border(2.dp, colors.ovulation, CircleShape))
        LegendItem(
            stringResource(R.string.legend_predicted),
            Modifier.hatched(colors.predHatch, colors.predTint, 999.dp),
        )
        LegendItem(
            stringResource(R.string.legend_logged),
            Modifier.padding(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        )
    }
}

@Composable
private fun LegendItem(
    label: String,
    swatch: Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(14.dp).then(swatch))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(
    card: DayCardUi,
    onEdit: () -> Unit,
) {
    ContentCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = card.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
        }
        if (card.chips.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_nothing_logged),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (chip in card.chips) ValueChip(icon = chip.icon, text = chip.text)
            }
        }
        Text(
            text = card.footer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
