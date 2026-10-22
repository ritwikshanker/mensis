package com.deutschdreamers.mensis.ui.screens.importer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.data.importer.ConflictStrategy
import com.deutschdreamers.mensis.data.importer.CsvField
import com.deutschdreamers.mensis.data.importer.DateOrder
import com.deutschdreamers.mensis.data.importer.ImportPlanner
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.ui.components.DoneHeader
import com.deutschdreamers.mensis.ui.components.FieldLabel
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.PasswordField
import com.deutschdreamers.mensis.ui.components.RadioRow
import com.deutschdreamers.mensis.ui.components.SegmentedChoice
import com.deutschdreamers.mensis.ui.components.SettingsRow
import com.deutschdreamers.mensis.ui.components.WorkingIndicator
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.LoggedChips
import com.deutschdreamers.mensis.ui.format.Units
import com.deutschdreamers.mensis.ui.theme.SerifStyles
import java.time.ZoneId

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> viewModel.onFileChosen(uri) }
    ImportContent(
        state = state,
        onBack = { if (!viewModel.back()) onBack() },
        onClose = onBack,
        onSource = viewModel::setSource,
        onChooseFile = {
            viewModel.onPickerOpening()
            // Every type: spreadsheets and other apps' exports carry all kinds of MIME types; the content decides.
            openLauncher.launch(arrayOf("*/*"))
        },
        onPassword = viewModel::setPassword,
        onDecrypt = viewModel::decrypt,
        onField = viewModel::setField,
        onDateOrder = viewModel::setDateOrder,
        onConfirmMapping = viewModel::confirmMapping,
        onStrategy = viewModel::setStrategy,
        onImport = viewModel::import,
        onUndo = viewModel::undo,
    )
}

/** Design §14, "Step n of 4" in the top bar. Stateless for tests and previews. */
@Composable
fun ImportContent(
    state: ImportUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSource: (ImportSource) -> Unit,
    onChooseFile: () -> Unit,
    onPassword: (String) -> Unit,
    onDecrypt: () -> Unit,
    onField: (Int, CsvField) -> Unit,
    onDateOrder: (DateOrder) -> Unit,
    onConfirmMapping: () -> Unit,
    onStrategy: (ConflictStrategy) -> Unit,
    onImport: () -> Unit,
    onUndo: () -> Unit,
) {
    val busy = state.step == ImportStep.READING || state.step == ImportStep.APPLYING
    BackHandler(enabled = busy) {}
    BackHandler(
        enabled = state.step in setOf(ImportStep.PASSWORD, ImportStep.MAPPING, ImportStep.PREVIEW),
        onBack = onBack,
    )
    Scaffold(
        topBar = {
            MensisTopBar(title = stringResource(R.string.settings_import), onBack = if (busy) null else onBack) {
                Text(
                    text = stringResource(R.string.import_step, state.step.number),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state.step) {
                ImportStep.SOURCE -> SourceStep(state, onSource, onChooseFile, onUndo)
                ImportStep.READING -> {
                    val decrypting = state.password.isNotEmpty()
                    WorkingIndicator(
                        text = stringResource(if (decrypting) R.string.import_decrypting else R.string.import_reading),
                        modifier = Modifier.padding(top = 120.dp),
                    )
                }
                ImportStep.PASSWORD -> PasswordStep(state, onPassword, onDecrypt)
                ImportStep.MAPPING -> MappingStep(state, onField, onDateOrder, onConfirmMapping)
                ImportStep.PREVIEW -> PreviewStep(state, onStrategy, onImport)
                ImportStep.APPLYING ->
                    WorkingIndicator(
                        text = stringResource(R.string.import_applying),
                        modifier = Modifier.padding(top = 120.dp),
                    )
                ImportStep.DONE -> DoneStep(state, onUndo, onClose)
                ImportStep.UNDONE -> {
                    DoneHeader(
                        title = stringResource(R.string.import_undone),
                        body = stringResource(R.string.import_undone_body),
                        icon = R.drawable.ms_history,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(
    title: String,
    subtitle: String? = null,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorBanner(text: String) {
    InfoBanner(
        text = text,
        icon = R.drawable.ms_error,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        iconTint = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (icon != null) {
            MsIcon(resId = icon, contentDescription = null, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text)
    }
}

val ImportSource.labelRes: Int
    get() =
        when (this) {
            ImportSource.CSV -> R.string.import_source_csv
            ImportSource.MENSIS -> R.string.import_source_mensis
            ImportSource.DRIP -> R.string.import_source_drip
            ImportSource.CLUE -> R.string.import_source_clue
        }

private val ImportSource.bodyRes: Int
    get() =
        when (this) {
            ImportSource.CSV -> R.string.import_source_csv_body
            ImportSource.MENSIS -> R.string.import_source_mensis_body
            ImportSource.DRIP -> R.string.import_source_drip_body
            ImportSource.CLUE -> R.string.import_source_clue_body
        }

@Composable
private fun SourceStep(
    state: ImportUiState,
    onSource: (ImportSource) -> Unit,
    onChooseFile: () -> Unit,
    onUndo: () -> Unit,
) {
    var confirmUndo by remember { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.import_source),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (source in ImportSource.entries) {
            RadioRow(
                title = stringResource(source.labelRes),
                supporting = stringResource(source.bodyRes),
                selected = source == state.source,
                onSelect = { onSource(source) },
                horizontalPadding = 12.dp,
            )
        }
    }
    Text(
        text = stringResource(R.string.import_local_only),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.problem?.let { ErrorBanner(problemText(it, state.source)) }
    state.lastImport?.let { last ->
        SettingsRow(
            icon = R.drawable.ms_history,
            title = stringResource(R.string.import_undo_last),
            supporting = ImportTexts.lastImport(stringResource(last.source.labelRes), last, ZoneId.systemDefault()),
            showChevron = false,
            onClick = { confirmUndo = true },
        )
    }
    PrimaryButton(stringResource(R.string.import_choose_file), onChooseFile, icon = R.drawable.ms_folder_open)
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(stringResource(R.string.import_undo_confirm_title)) },
            text = { Text(stringResource(R.string.import_undo_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    onUndo()
                }) { Text(stringResource(R.string.import_undo)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmUndo = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun problemText(
    problem: ImportProblem,
    source: ImportSource,
): String =
    when (problem) {
        ImportProblem.NOT_THIS_FORMAT -> stringResource(R.string.import_error_format, stringResource(source.labelRes))
        ImportProblem.NEWER_VERSION -> stringResource(R.string.import_error_newer)
        ImportProblem.DAMAGED -> stringResource(R.string.import_error_damaged)
        ImportProblem.TOO_LARGE -> stringResource(R.string.import_error_too_large)
        ImportProblem.UNREADABLE -> stringResource(R.string.import_error_unreadable)
    }

@Composable
private fun PasswordStep(
    state: ImportUiState,
    onPassword: (String) -> Unit,
    onDecrypt: () -> Unit,
) {
    Text(text = stringResource(R.string.import_password_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        text = stringResource(R.string.import_password_body, state.fileName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PasswordField(
        value = state.password,
        onValueChange = onPassword,
        label = stringResource(R.string.import_password_label),
        isError = state.wrongPassword,
        supporting = stringResource(R.string.import_error_wrong_password).takeIf { state.wrongPassword },
    )
    PrimaryButton(stringResource(R.string.import_decrypt), onDecrypt, enabled = state.password.isNotEmpty())
}

@Composable
private fun MappingStep(
    state: ImportUiState,
    onField: (Int, CsvField) -> Unit,
    onDateOrder: (DateOrder) -> Unit,
    onConfirm: () -> Unit,
) {
    val mapping = state.mapping ?: return
    Title(
        stringResource(R.string.import_map_title),
        ImportTexts.mapSubtitle(state.fileName, mapping.columns.size, mapping.rows),
    )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        mapping.columns.forEachIndexed { index, column ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = column.header.ifBlank { "—" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = column.sample.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MsIcon(
                    resId = R.drawable.ms_arrow_forward,
                    contentDescription = null,
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldDropdown(
                    header = column.header,
                    selected = mapping.mapping.fields[index],
                    onSelect = { onField(index, it) },
                )
            }
        }
    }
    mapping.ambiguousDateSample?.let { sample ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.import_map_date_order, sample))
            SegmentedChoice(
                options = DateOrder.entries,
                selected = mapping.mapping.dateOrder,
                label = { ImportTexts.dateReading(sample, it) },
                onSelect = onDateOrder,
            )
        }
    }
    Text(
        text = stringResource(R.string.import_map_helper),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!mapping.mapping.hasDate) ErrorBanner(stringResource(R.string.import_map_no_date))
    PrimaryButton(stringResource(R.string.import_preview), onConfirm, enabled = mapping.mapping.hasDate)
}

/** The design's 36 dp target box with the field name and a drop-down arrow. */
@Composable
private fun FieldDropdown(
    header: String,
    selected: CsvField,
    onSelect: (CsvField) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minWidth = 124.dp, minHeight = 36.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(start = 12.dp, end = 8.dp)
                    .semantics { contentDescription = "$header: ${ImportTexts.fieldLabel(selected)}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = ImportTexts.fieldLabel(selected),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (selected ==
                        CsvField.SKIP
                    ) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            MsIcon(resId = R.drawable.ms_arrow_drop_down, contentDescription = null, size = 18.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (field in CsvField.entries) {
                DropdownMenuItem(
                    text = { Text(ImportTexts.fieldLabel(field)) },
                    onClick = {
                        open = false
                        onSelect(field)
                    },
                    leadingIcon =
                        if (field == selected) {
                            { MsIcon(resId = R.drawable.ms_check, contentDescription = null, size = 18.dp) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun PreviewStep(
    state: ImportUiState,
    onStrategy: (ConflictStrategy) -> Unit,
    onImport: () -> Unit,
) {
    val parsed = state.parsed ?: return
    Title(stringResource(R.string.import_preview), ImportTexts.previewCounts(parsed))
    if (parsed.days.isNotEmpty()) PreviewTable(parsed.days.take(PREVIEW_ROWS), state.temperatureUnit)
    ImportTexts.issues(parsed.issues)?.let { InfoBanner(text = it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringResource(R.string.import_conflict_title))
        val labels = ConflictStrategy.entries.associateWith { stringResource(it.labelRes) }
        SegmentedChoice(
            options = ConflictStrategy.entries,
            selected = state.strategy,
            label = { labels.getValue(it) },
            onSelect = onStrategy,
        )
        Text(
            text = stringResource(state.strategy.helpRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val preview = state.preview
        if (preview != null) {
            val lines =
                listOfNotNull(
                    ImportTexts.conflicts(preview.conflicts),
                    ImportTexts.extras(
                        ImportPlanner.historyOutcome(parsed, state.strategy, preview.localHasHistory),
                        ImportPlanner.settingsOutcome(parsed, state.strategy, preview.localHasSettings),
                    ),
                )
            for (line in lines) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    PrimaryButton(ImportTexts.importButton(parsed.days.size), onImport, enabled = parsed.days.isNotEmpty())
}

@Composable
private fun PreviewTable(
    days: List<DayEntry>,
    unit: TemperatureUnit,
) {
    val weights = listOf(1.2f, 1f, 1f, 1f)

    @Composable
    fun Cells(
        values: List<String>,
        header: Boolean,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { i, value ->
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
                    color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    modifier = Modifier.weight(weights[i]),
                )
            }
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Cells(
            listOf(
                stringResource(R.string.import_preview_date),
                stringResource(R.string.import_preview_flow),
                stringResource(R.string.import_preview_temp),
                stringResource(R.string.import_preview_mood),
            ),
            header = true,
        )
        for (day in days) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Cells(
                listOf(
                    Formats.dayMonthYear(day.date),
                    day.flow?.let(LoggedChips::flowOption) ?: DASH,
                    day.temperatureC?.let { Formats.temperature(Units.toDisplayTemperature(it, unit)) } ?: DASH,
                    Mood.entries.firstOrNull { it in day.moods }?.let(LoggedChips::moodLabel) ?: DASH,
                ),
                header = false,
            )
        }
    }
}

@Composable
private fun DoneStep(
    state: ImportUiState,
    onUndo: () -> Unit,
    onDone: () -> Unit,
) {
    val result = state.result ?: return
    DoneHeader(title = stringResource(R.string.import_done), body = ImportTexts.summary(result))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Stat(result.entries, stringResource(R.string.import_entries))
        Stat(result.cycles, stringResource(R.string.import_cycles))
        Stat(result.skipped, stringResource(R.string.import_skipped))
    }
    val skipped = state.parsed?.skipped.orEmpty()
    if (skipped.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.import_skipped_rows))
            for (row in skipped.take(MAX_SKIPPED_LISTED)) {
                Text(
                    text = ImportTexts.skippedRow(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (skipped.size > MAX_SKIPPED_LISTED) {
                Text(
                    text = "+ ${skipped.size - MAX_SKIPPED_LISTED}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (result.undoable) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.import_undo))
            }
        }
        PrimaryButton(stringResource(R.string.action_done), onDone)
    }
}

@Composable
private fun RowScope.Stat(
    value: Int,
    label: String,
) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value.toString(), style = SerifStyles.statSmall, textAlign = TextAlign.Center)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ConflictStrategy.labelRes: Int
    get() =
        when (this) {
            ConflictStrategy.MERGE -> R.string.import_conflict_merge
            ConflictStrategy.REPLACE -> R.string.import_conflict_replace
            ConflictStrategy.SKIP -> R.string.import_conflict_skip
        }

private val ConflictStrategy.helpRes: Int
    get() =
        when (this) {
            ConflictStrategy.MERGE -> R.string.import_conflict_merge_help
            ConflictStrategy.REPLACE -> R.string.import_conflict_replace_help
            ConflictStrategy.SKIP -> R.string.import_conflict_skip_help
        }

private const val PREVIEW_ROWS = 4
private const val MAX_SKIPPED_LISTED = 30
private const val DASH = "—"
