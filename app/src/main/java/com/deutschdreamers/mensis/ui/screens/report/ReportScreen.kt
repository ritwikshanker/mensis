package com.deutschdreamers.mensis.ui.screens.report

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.report.ReportRange
import com.deutschdreamers.mensis.report.ReportSection
import com.deutschdreamers.mensis.report.ReportTexts
import com.deutschdreamers.mensis.ui.components.DoneHeader
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.SegmentedChoice
import com.deutschdreamers.mensis.ui.components.WorkingIndicator

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ReportTexts.MIME_TYPE)) { uri ->
            viewModel.onLocationChosen(uri)
        }
    val shareLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { viewModel.onShareClosed() }
    LaunchedEffect(viewModel) {
        viewModel.shareRequests.collect { uri ->
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType(ReportTexts.MIME_TYPE)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareLauncher.launch(Intent.createChooser(send, null))
        }
    }
    ReportContent(
        state = state,
        actions =
            ReportActions(
                onBack = onBack,
                onRange = viewModel::setRange,
                onToggle = viewModel::toggleSection,
                onPreview = viewModel::preview,
                onEdit = viewModel::edit,
                onCreate = viewModel::create,
                onShare = viewModel::share,
                onSave = {
                    viewModel.onPickerOpening()
                    saveLauncher.launch(viewModel.suggestedFileName)
                },
                onDiscard = {
                    viewModel.discard()
                    onBack()
                },
            ),
    )
}

class ReportActions(
    val onBack: () -> Unit,
    val onRange: (ReportRange) -> Unit,
    val onToggle: (ReportSection) -> Unit,
    val onPreview: () -> Unit,
    val onEdit: () -> Unit,
    val onCreate: () -> Unit,
    val onShare: () -> Unit,
    val onSave: () -> Unit,
    val onDiscard: () -> Unit,
)

/**
 * Design §15: set up, preview, then "PDF ready". The preview shows the PDF's own pages rather than the
 * design's page-1 card; a tapped page opens full screen. Stateless for tests and previews.
 */
@Composable
fun ReportContent(
    state: ReportUiState,
    actions: ReportActions,
) {
    // Back steps through the flow: preview → set up; leaving "PDF ready" discards the PDF.
    BackHandler(enabled = state.step != ReportStep.SETUP) {
        if (state.step == ReportStep.READY) actions.onDiscard() else actions.onEdit()
    }
    val pages = state.preview?.pages?.takeIf { state.step == ReportStep.PREVIEW }
    var opened by remember(pages) { mutableStateOf<Int?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        // While a page is open full screen, the screen underneath is hidden from accessibility services.
        val behind = if (opened != null) Modifier.clearAndSetSemantics {} else Modifier
        ReportScaffold(state, actions, behind) { opened = it }
        val index = opened
        if (pages != null && index != null) PdfPageViewer(pages, index, onClose = { opened = null })
    }
}

@Composable
private fun ReportScaffold(
    state: ReportUiState,
    actions: ReportActions,
    modifier: Modifier,
    onOpenPage: (Int) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MensisTopBar(
                title = stringResource(R.string.settings_report),
                onBack =
                    when (state.step) {
                        ReportStep.SETUP -> actions.onBack
                        ReportStep.PREVIEW -> actions.onEdit
                        ReportStep.READY -> actions.onDiscard
                    },
            )
        },
        bottomBar = { if (state.step == ReportStep.PREVIEW && state.preview != null) PreviewButtons(actions) },
    ) { padding ->
        if (state.step == ReportStep.PREVIEW) {
            PreviewStep(state, Modifier.padding(padding), onOpenPage)
            return@Scaffold
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.step == ReportStep.SETUP) SetupStep(state, actions) else ReadyStep(state, actions)
        }
    }
}

@Composable
private fun ColumnScope.SetupStep(
    state: ReportUiState,
    actions: ReportActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(R.string.report_range))
        val ranges =
            mapOf(
                ReportRange.LAST_6_CYCLES to stringResource(R.string.report_range_6_cycles),
                ReportRange.TWELVE_MONTHS to stringResource(R.string.report_range_12_months),
                ReportRange.ALL to stringResource(R.string.report_range_all),
            )
        SegmentedChoice(
            options = ReportRange.entries,
            selected = state.options.range,
            label = { ranges.getValue(it) },
            onSelect = actions.onRange,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(stringResource(R.string.report_sections), modifier = Modifier.padding(bottom = 4.dp))
        for (section in ReportSection.entries) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = section in state.options.sections,
                            role = Role.Checkbox,
                            onValueChange = { actions.onToggle(section) },
                        ).defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Checkbox(checked = section in state.options.sections, onCheckedChange = null)
                Text(text = stringResource(section.labelRes), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    InfoBanner(text = stringResource(R.string.report_info))
    if (state.hasEntries == false) {
        Text(
            text = stringResource(R.string.report_nothing_logged),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(
        onClick = actions.onPreview,
        enabled = state.canPreview,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(stringResource(R.string.report_preview))
    }
}

/** The PDF's pages, drawn only while they are on screen so a long report does not fill the memory. */
@Composable
private fun PreviewStep(
    state: ReportUiState,
    modifier: Modifier,
    onOpenPage: (Int) -> Unit,
) {
    val preview = state.preview
    if (preview == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            WorkingIndicator(text = stringResource(R.string.report_building), modifier = Modifier.padding(top = 120.dp))
        }
        return
    }
    val pages = preview.pages
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (pages == null) {
            item { InfoBanner(text = stringResource(R.string.report_pages_failed)) }
        } else {
            item {
                Text(
                    text = stringResource(R.string.report_pages_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(pages.pageCount) { index -> PdfPageCard(pages, index, onOpen = { onOpenPage(index) }) }
        }
    }
}

/** Edit and Create PDF stay in reach however long the report is. */
@Composable
private fun PreviewButtons(actions: ReportActions) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = actions.onEdit, modifier = Modifier.weight(1f).height(48.dp)) {
                Text(stringResource(R.string.action_edit))
            }
            Button(onClick = actions.onCreate, modifier = Modifier.weight(1f).height(48.dp)) {
                MsIcon(resId = R.drawable.ms_picture_as_pdf, contentDescription = null, size = 20.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.report_create))
            }
        }
    }
}

@Composable
private fun ColumnScope.ReadyStep(
    state: ReportUiState,
    actions: ReportActions,
) {
    val preview = state.preview ?: return
    DoneHeader(
        title = stringResource(R.string.report_ready),
        body = ReportTexts.ready(preview.model.fileName, preview.pageCount, preview.bytes),
        icon = R.drawable.ms_picture_as_pdf,
    )
    state.savedName?.let { name ->
        Text(
            text = stringResource(R.string.report_saved, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    if (state.failed) {
        InfoBanner(
            text = stringResource(R.string.export_failed),
            icon = R.drawable.ms_error,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = actions.onShare, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            MsIcon(resId = R.drawable.ms_share, contentDescription = null, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.report_share))
        }
        OutlinedButton(onClick = actions.onSave, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            MsIcon(resId = R.drawable.ms_download, contentDescription = null, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.report_save))
        }
        TextButton(onClick = actions.onDiscard, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text(stringResource(R.string.report_discard))
        }
    }
}

@Composable
private fun Label(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val ReportSection.labelRes: Int
    get() =
        when (this) {
            ReportSection.SUMMARY -> R.string.report_section_summary
            ReportSection.LENGTHS -> R.string.report_section_lengths
            ReportSection.SYMPTOMS -> R.string.report_section_symptoms
            ReportSection.TEMPERATURE -> R.string.report_section_bbt
            ReportSection.MEDICATION -> R.string.report_section_medication
            ReportSection.NOTES -> R.string.report_section_notes
        }
