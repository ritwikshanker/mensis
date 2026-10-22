package com.deutschdreamers.mensis.ui.screens.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.RingState
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.ui.components.FieldLabel
import com.deutschdreamers.mensis.ui.components.MensisTimePickerDialog
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.MultiChoiceChips
import com.deutschdreamers.mensis.ui.components.SegmentedChoice
import com.deutschdreamers.mensis.ui.components.SingleChoiceChips
import com.deutschdreamers.mensis.ui.components.SwitchRow
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.LoggedChips
import com.deutschdreamers.mensis.ui.format.Units
import kotlinx.coroutines.launch

/**
 * Design §4: a modal bottom sheet that expands to full screen. Partially expanded shows Flow,
 * temperature, symptoms and mood plus an "expand" button; fully expanded shows every section with
 * the full-screen top bar. Save persists only non-empty fields; Cancel discards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogHost(
    request: LogRequest,
    viewModel: DailyLogViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(request) { viewModel.open(request) }
    val saved = state?.saved == true
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.close()
            onDismiss()
        }
    }
    // The sheet is composed only once the day is loaded: measured against a small placeholder it would
    // decide the content "fits" and skip the partially expanded state for good.
    val current = state ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = request.testsFirst)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.close()
            onDismiss()
        },
        sheetState = sheetState,
        shape =
            MaterialTheme.shapes.extraLarge.copy(
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            ),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val expanded = sheetState.currentValue == SheetValue.Expanded || request.testsFirst
        DailyLogContent(
            state = current,
            expanded = expanded,
            onExpand = { scope.launch { sheetState.expand() } },
            onCollapse = { scope.launch { sheetState.partialExpand() } },
            onUpdate = viewModel::update,
            onCancel = {
                viewModel.close()
                onDismiss()
            },
            onSave = viewModel::save,
        )
    }
}

/** The sheet body; stateless so previews and tests can drive it. */
@Composable
fun DailyLogContent(
    state: LogUiState,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onUpdate: ((LogDraft) -> LogDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = state.draft
    val level = state.trackingLevel
    Column(modifier = Modifier.fillMaxWidth().imePadding()) {
        LogHeader(state, expanded, onExpand, onCollapse, onCancel)
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .then(if (expanded) Modifier else Modifier.heightIn(max = collapsedContentHeight()))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.log_nothing_required),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.testsFirst) {
                PregnancyTestSection(draft, onUpdate, highlighted = true)
            }
            Section(stringResource(R.string.log_flow)) {
                SingleChoiceChips(
                    options = Flow.entries,
                    selected = draft.flow,
                    label = LoggedChips::flowOption,
                    onSelect = { flow -> onUpdate { it.copy(flow = flow) } },
                )
            }
            if (level.showsTemperature) TemperatureSection(state, onUpdate)
            if (level.showsSymptoms) {
                Section(stringResource(R.string.log_symptoms)) {
                    MultiChoiceChips(
                        options = Symptom.entries,
                        selected = draft.symptoms,
                        label = LoggedChips::symptomLabel,
                        onToggle = { s ->
                            onUpdate {
                                it.copy(
                                    symptoms =
                                        if (s in
                                            it.symptoms
                                        ) {
                                            it.symptoms - s
                                        } else {
                                            it.symptoms + s
                                        },
                                )
                            }
                        },
                    )
                }
                Section(stringResource(R.string.log_mood)) {
                    MultiChoiceChips(
                        options = Mood.entries,
                        selected = draft.moods,
                        label = LoggedChips::moodLabel,
                        onToggle = { m ->
                            onUpdate {
                                it.copy(
                                    moods =
                                        if (m in
                                            it.moods
                                        ) {
                                            it.moods - m
                                        } else {
                                            it.moods + m
                                        },
                                )
                            }
                        },
                    )
                }
            }
            if (!expanded) {
                TextButton(onClick = onExpand) {
                    MsIcon(resId = R.drawable.ms_expand_more, contentDescription = null, size = 18.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(moreLabel(level, state.method.item))
                }
            } else {
                ExpandedSections(state, onUpdate)
            }
        }
        LogFooter(onCancel, onSave)
    }
}

@Composable
private fun LogHeader(
    state: LogUiState,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onCancel: () -> Unit,
) {
    if (expanded) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                MsIcon(resId = R.drawable.ms_close, contentDescription = stringResource(R.string.action_cancel))
            }
            Text(
                text = state.dateLabel,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onCollapse) {
                MsIcon(
                    resId = R.drawable.ms_close_fullscreen,
                    contentDescription = stringResource(R.string.log_collapse),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = state.dateLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onExpand) {
                MsIcon(resId = R.drawable.ms_open_in_full, contentDescription = stringResource(R.string.log_expand))
            }
        }
    }
}

@Composable
private fun LogFooter(
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        Button(onClick = onSave) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(title)
        content()
    }
}

@Composable
private fun TemperatureSection(
    state: LogUiState,
    onUpdate: ((LogDraft) -> LogDraft) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val draft = state.draft
    Section(stringResource(R.string.log_temperature)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft.temperatureText,
                onValueChange = { text -> onUpdate { it.copy(temperatureText = text) } },
                label = { Text(Units.temperatureSymbol(state.temperatureUnit)) },
                isError = state.temperatureError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.temperatureTime?.let(Formats::time) ?: "",
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
        Text(
            text = stringResource(R.string.log_temperature_helper),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showTimePicker) {
        MensisTimePickerDialog(
            initial = draft.temperatureTime,
            title = stringResource(R.string.log_time),
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                showTimePicker = false
                onUpdate { it.copy(temperatureTime = time) }
            },
        )
    }
}

@Composable
private fun PregnancyTestSection(
    draft: LogDraft,
    onUpdate: ((LogDraft) -> LogDraft) -> Unit,
    highlighted: Boolean,
) {
    val container = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        modifier =
            if (highlighted) {
                Modifier.fillMaxWidth().background(container, MaterialTheme.shapes.medium).padding(12.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FieldLabel(stringResource(R.string.log_pregnancy_test))
        TestChoice(draft.pregnancyTest) { result -> onUpdate { it.copy(pregnancyTest = result) } }
        if (highlighted) {
            Text(
                text = stringResource(R.string.log_pregnancy_test_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun TestChoice(
    selected: TestResult?,
    onSelect: (TestResult?) -> Unit,
) {
    val options = listOf<TestResult?>(null, TestResult.NEGATIVE, TestResult.POSITIVE)
    SegmentedChoice(options = options, selected = selected, label = LoggedChips::testOption, onSelect = onSelect)
}

@Composable
private fun ExpandedSections(
    state: LogUiState,
    onUpdate: ((LogDraft) -> LogDraft) -> Unit,
) {
    val draft = state.draft
    val level = state.trackingLevel
    if (level.showsTemperature) {
        Section(stringResource(R.string.log_cervical_mucus)) {
            SingleChoiceChips(
                options = CervicalMucus.entries,
                selected = draft.cervicalMucus,
                label = LoggedChips::mucusLabel,
                onSelect = { m -> onUpdate { it.copy(cervicalMucus = m) } },
            )
        }
        Section(stringResource(R.string.log_lh_test)) {
            TestChoice(draft.lhTest) { result -> onUpdate { it.copy(lhTest = result) } }
        }
    }
    if (!state.testsFirst) PregnancyTestSection(draft, onUpdate, highlighted = false)
    Section(stringResource(R.string.log_sex)) {
        SegmentedChoice(
            options = SexType.entries,
            selected = draft.sex ?: SexType.NONE,
            label = LoggedChips::sexOption,
            onSelect = { sex -> onUpdate { it.copy(sex = sex.takeIf { s -> s != SexType.NONE }) } },
        )
    }
    MethodSection(state, onUpdate)
    if (state.method.showEmergencyContraception) {
        SwitchRow(
            icon = R.drawable.ms_emergency,
            title = stringResource(R.string.log_emergency_contraception),
            supporting = stringResource(R.string.log_emergency_contraception_helper),
            checked = draft.emergencyContraception,
            onCheckedChange = { checked -> onUpdate { it.copy(emergencyContraception = checked) } },
            horizontalPadding = 0.dp,
        )
    }
    if (level.showsSymptoms) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.sleepText,
                onValueChange = { text -> onUpdate { it.copy(sleepText = text) } },
                label = { Text(stringResource(R.string.log_sleep)) },
                placeholder = { Text(stringResource(R.string.log_sleep_placeholder)) },
                isError = state.sleepError,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = draft.weightText,
                onValueChange = { text -> onUpdate { it.copy(weightText = text) } },
                label = { Text(stringResource(R.string.log_weight)) },
                suffix = { Text(Units.weightSymbol(state.weightUnit)) },
                isError = state.weightError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
    }
    OutlinedTextField(
        value = draft.notes,
        onValueChange = { text -> onUpdate { it.copy(notes = text) } },
        label = { Text(stringResource(R.string.log_notes)) },
        placeholder = { Text(stringResource(R.string.log_notes_placeholder)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    )
}

@Composable
private fun MethodSection(
    state: LogUiState,
    onUpdate: ((LogDraft) -> LogDraft) -> Unit,
) {
    val method = state.method
    val draft = state.draft
    when (method.item) {
        MethodItem.NONE -> Unit
        MethodItem.PILL ->
            Section(method.title) {
                SingleChoiceChips(
                    options = PillState.entries,
                    selected = draft.pillState,
                    label = LoggedChips::pillOption,
                    onSelect = { s -> onUpdate { it.copy(pillState = s) } },
                )
                Text(
                    text = method.helper,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        MethodItem.PATCH ->
            SwitchRow(
                icon = R.drawable.ms_healing,
                title = stringResource(R.string.log_patch_changed),
                supporting = method.helper,
                checked = draft.patchChanged,
                onCheckedChange = { checked -> onUpdate { it.copy(patchChanged = checked) } },
                horizontalPadding = 0.dp,
            )
        MethodItem.RING ->
            Section(method.title) {
                SingleChoiceChips(
                    options = RingState.entries,
                    selected = draft.ringState,
                    label = LoggedChips::ringOption,
                    onSelect = { s -> onUpdate { it.copy(ringState = s) } },
                )
                Text(
                    text = method.helper,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        MethodItem.INJECTION ->
            SwitchRow(
                icon = R.drawable.ms_vaccines,
                title = stringResource(R.string.log_injection_given),
                supporting = method.helper,
                checked = draft.injectionGiven,
                onCheckedChange = { checked -> onUpdate { it.copy(injectionGiven = checked) } },
                horizontalPadding = 0.dp,
            )
        MethodItem.LARC ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MsIcon(
                    resId = R.drawable.ms_event_repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    Text(text = method.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = method.helper,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

/** "Mucus, tests, sex, method, sleep, notes" minus the items the tracking level or method hides. */
@Composable
private fun moreLabel(
    level: com.deutschdreamers.mensis.domain.model.TrackingLevel,
    item: MethodItem,
): String {
    val parts = mutableListOf<String>()
    if (level.showsTemperature) parts += stringResource(R.string.log_more_mucus)
    parts += stringResource(R.string.log_more_tests)
    parts += stringResource(R.string.log_more_sex)
    if (item != MethodItem.NONE) parts += stringResource(R.string.log_more_method)
    if (level.showsSymptoms) parts += stringResource(R.string.log_more_sleep)
    parts += stringResource(R.string.log_more_notes)
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
}

/**
 * Content height while the sheet is partially expanded: Material's partial sheet is half the screen,
 * and the header and footer must stay visible inside it, so the scrolling part gets what is left.
 */
@Composable
private fun collapsedContentHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeight / 2 - COLLAPSED_CHROME_HEIGHT).coerceAtLeast(COLLAPSED_MIN_CONTENT_HEIGHT)
}

/** Header, hint line and footer of the partially expanded sheet, roughly. */
private val COLLAPSED_CHROME_HEIGHT = 200.dp
private val COLLAPSED_MIN_CONTENT_HEIGHT = 160.dp
