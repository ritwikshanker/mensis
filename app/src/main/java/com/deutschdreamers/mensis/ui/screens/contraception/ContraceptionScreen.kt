package com.deutschdreamers.mensis.ui.screens.contraception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.ui.components.ContentCard
import com.deutschdreamers.mensis.ui.components.FieldLabel
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisDatePickerDialog
import com.deutschdreamers.mensis.ui.components.MensisTimePickerDialog
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.RadioRow
import com.deutschdreamers.mensis.ui.components.SectionHeader
import com.deutschdreamers.mensis.ui.components.SegmentedChoice
import com.deutschdreamers.mensis.ui.components.SettingsRow
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.MethodLabels
import java.time.LocalDate
import java.time.LocalTime

private enum class PickerTarget { START, NEXT, CHECKUP }

@Composable
fun ContraceptionScreen(
    onBack: () -> Unit,
    onReminders: () -> Unit,
    viewModel: ContraceptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    ContraceptionContent(
        state = current,
        onBack = onBack,
        onReminders = onReminders,
        onSelectMethod = viewModel::selectMethod,
        onRegimen = viewModel::setRegimen,
        onStartDate = viewModel::setStartDate,
        onDailyTime = viewModel::setDailyTime,
        onNextDate = viewModel::setReplaceByDate,
        onCheckupDate = viewModel::setCheckupDate,
    )
}

/** Design §9: method list, regimen, start date (+ time for daily pills), LARC card, effect banner, reminder row. */
@Composable
fun ContraceptionContent(
    state: ContraceptionUiState,
    onBack: () -> Unit,
    onReminders: () -> Unit,
    onSelectMethod: (ContraceptionMethod) -> Unit,
    onRegimen: (Regimen) -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onDailyTime: (LocalTime?) -> Unit,
    onNextDate: (LocalDate?) -> Unit,
    onCheckupDate: (LocalDate?) -> Unit,
) {
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        MensisTopBar(title = stringResource(R.string.settings_contraception), onBack = onBack)
    }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        padding,
                    ).verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                SectionHeader(stringResource(R.string.contraception_method))
                for (method in ContraceptionMethod.entries) {
                    RadioRow(
                        title = MethodLabels.name(method),
                        supporting = MethodLabels.description(method),
                        selected = method == state.method,
                        onSelect = { onSelectMethod(method) },
                    )
                }
            }
            if (state.isCyclic) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldLabel(stringResource(R.string.contraception_regimen))
                    SegmentedChoice(
                        options = Regimen.entries,
                        selected = state.regimen,
                        label = { if (it == Regimen.CYCLIC) "21 + 7 break" else "Continuous" },
                        onSelect = onRegimen,
                    )
                    Text(
                        text = MethodLabels.regimenHelp(state.regimen),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.method != ContraceptionMethod.NONE) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DateField(
                        label = MethodLabels.startLabel(state.method),
                        value = Formats.dayMonthYear(state.startDate),
                        onPick = { picker = PickerTarget.START },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isDailyPill) {
                        OutlinedTextField(
                            value = state.dailyTime?.let(Formats::time) ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.log_time)) },
                            trailingIcon = {
                                IconButton(onClick = { showTimePicker = true }) {
                                    MsIcon(
                                        resId = R.drawable.ms_schedule,
                                        contentDescription = stringResource(R.string.log_pick_time),
                                    )
                                }
                            },
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }
            if (state.isLongActing) {
                LarcCard(state, onEditNext = { picker = PickerTarget.NEXT }, onEditCheckup = {
                    picker =
                        PickerTarget.CHECKUP
                })
            }
            InfoBanner(
                title = stringResource(R.string.contraception_effect_title),
                text = state.effect,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsRow(
                icon = R.drawable.ms_notifications,
                title = stringResource(R.string.contraception_reminder),
                supporting = state.reminderSummary,
                onClick = onReminders,
            )
            Text(
                text = stringResource(R.string.contraception_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
    picker?.let { target ->
        MensisDatePickerDialog(
            initial =
                when (target) {
                    PickerTarget.START -> state.startDate
                    PickerTarget.NEXT -> state.nextDate
                    PickerTarget.CHECKUP -> state.checkupDate
                },
            onDismiss = { picker = null },
            onConfirm = { date ->
                picker = null
                when (target) {
                    PickerTarget.START -> onStartDate(date)
                    PickerTarget.NEXT -> onNextDate(date)
                    PickerTarget.CHECKUP -> onCheckupDate(date)
                }
            },
        )
    }
    if (showTimePicker) {
        MensisTimePickerDialog(
            initial = state.dailyTime,
            title = stringResource(R.string.log_time),
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                showTimePicker = false
                onDailyTime(time)
            },
        )
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = onPick) {
                MsIcon(
                    resId = R.drawable.ms_calendar_month,
                    contentDescription = stringResource(R.string.action_pick_date),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun LarcCard(
    state: ContraceptionUiState,
    onEditNext: () -> Unit,
    onEditCheckup: () -> Unit,
) {
    ContentCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MsIcon(
                resId = R.drawable.ms_event_repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.nextLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.nextDate?.let(Formats::dayMonthYear) ?: "–",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(onClick = onEditNext) { Text(stringResource(R.string.action_edit)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MsIcon(
                resId = R.drawable.ms_event_available,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.contraception_checkup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        state.checkupDate?.let { "Done ${Formats.dayMonthYear(it)}" }
                            ?: stringResource(R.string.contraception_checkup_none),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(onClick = onEditCheckup) { Text(stringResource(R.string.action_edit)) }
        }
        Text(
            text = stringResource(R.string.contraception_larc_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
