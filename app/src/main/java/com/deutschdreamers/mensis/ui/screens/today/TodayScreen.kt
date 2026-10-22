package com.deutschdreamers.mensis.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.ui.components.ContentCard
import com.deutschdreamers.mensis.ui.components.CycleStripView
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.RangeCard
import com.deutschdreamers.mensis.ui.components.ValueChip
import com.deutschdreamers.mensis.ui.screens.log.LogRequest
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import com.deutschdreamers.mensis.ui.theme.SerifStyles

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onLog: (LogRequest) -> Unit,
    onImport: () -> Unit,
    onBackupAction: () -> Unit,
    onMethodSettings: () -> Unit,
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
    TodayScreenContent(
        state = current,
        onLog = onLog,
        onImport = onImport,
        onBackupAction = onBackupAction,
        onMethodSettings = onMethodSettings,
    )
}

/** Stateless Today screen (design §3); also what the previews and UI tests render. */
@Composable
fun TodayScreenContent(
    state: TodayUiState,
    onLog: (LogRequest) -> Unit,
    onImport: () -> Unit,
    onBackupAction: () -> Unit,
    onMethodSettings: () -> Unit,
) {
    Scaffold(
        topBar = { TodayTopBar(state.dateLabel) },
        // The tab host already pads for the navigation bar; the top bar pads for the status bar itself.
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onLog(LogRequest(state.today)) },
                icon = { MsIcon(resId = R.drawable.ms_edit_note, contentDescription = null) },
                text = { Text(stringResource(R.string.today_log_today)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.banner?.let { banner ->
                InfoBanner(
                    text = banner.text,
                    icon = banner.icon,
                    actionLabel = banner.action,
                    onAction = onBackupAction,
                )
            }
            HeaderRow(state)
            if (state.strip != null) {
                StripCard(state)
            }
            state.pregnancyCard?.let { PregnancyCard(it) }
            state.lateCard?.let { card ->
                LateCard(
                    card = card,
                    onPregnancyTest = { onLog(LogRequest(state.today, testsFirst = true)) },
                    onPeriodStarted = { onLog(LogRequest(state.today, presetFlow = Flow.MEDIUM)) },
                )
            }
            if (state.firstLaunchCard) {
                FirstLaunchCard(
                    onPeriodStarted = { onLog(LogRequest(state.today, presetFlow = Flow.MEDIUM)) },
                    onImport = onImport,
                )
            }
            state.methodCard?.let { card ->
                MethodCard(
                    card = card,
                    onLogBleeding = { onLog(LogRequest(state.today)) },
                    onMethodSettings = onMethodSettings,
                )
            }
            if (state.cards.isNotEmpty()) RangeCards(state.cards)
            LoggedToday(state.logged, onEdit = { onLog(LogRequest(state.today)) })
        }
    }
}

@Composable
private fun TodayTopBar(dateLabel: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tab_today),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeaderRow(state: TodayUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = state.numeral,
            style = SerifStyles.numeral,
            modifier = Modifier.semantics { contentDescription = "${state.numeralLabel} ${state.numeral}" },
        )
        Column(modifier = Modifier.weight(1f).padding(bottom = 8.dp)) {
            Text(
                text = state.numeralLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = state.phase, style = MaterialTheme.typography.titleLarge)
            Text(
                text = state.supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StripCard(state: TodayUiState) {
    val strip = requireNotNull(state.strip)
    val labels = state.laneLabels
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 12.dp),
    ) {
        CycleStripView(
            strip = strip,
            laneLabel = { kind -> labels[kind] ?: kind.name },
            cellDescription = {
                day,
                kind,
                isToday,
                ->
                TodayUiBuilder.cellDescription(day, kind, isToday, state.numeralLabel)
            },
        )
        state.stripHelper?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun RangeCards(cards: List<RangeCardUi>) {
    val colors = MensisTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (card in cards) {
            RangeCard(
                icon = card.icon,
                title = card.title,
                range = card.range,
                supporting = card.supporting,
                pips = card.pips,
                basis = card.basis,
                accent =
                    when (card.accent) {
                        CardAccent.PERIOD -> colors.period
                        CardAccent.FERTILE -> colors.fertile
                        CardAccent.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LateCard(
    card: LateCardUi,
    onPregnancyTest: () -> Unit,
    onPeriodStarted: () -> Unit,
) {
    ContentCard {
        Text(text = stringResource(R.string.today_late_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = card.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ButtonPair(
            tonalLabel = stringResource(R.string.today_log_pregnancy_test),
            tonalIcon = R.drawable.ms_science,
            onTonal = onPregnancyTest,
            outlinedLabel = stringResource(R.string.today_period_started),
            onOutlined = onPeriodStarted,
        )
    }
}

@Composable
private fun FirstLaunchCard(
    onPeriodStarted: () -> Unit,
    onImport: () -> Unit,
) {
    ContentCard {
        Text(text = stringResource(R.string.today_first_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.today_first_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ButtonPair(
            tonalLabel = stringResource(R.string.today_period_started_today),
            tonalIcon = R.drawable.ms_water_drop,
            onTonal = onPeriodStarted,
            outlinedLabel = stringResource(R.string.today_import_history),
            onOutlined = onImport,
        )
    }
}

@Composable
private fun MethodCard(
    card: MethodCardUi,
    onLogBleeding: () -> Unit,
    onMethodSettings: () -> Unit,
) {
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MsIcon(
                resId = R.drawable.ms_event_repeat,
                contentDescription = null,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = card.title, style = MaterialTheme.typography.titleMedium)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabelledValue(card.sinceLabel, card.since, Modifier.weight(1f))
            LabelledValue(card.nextLabel, card.next, Modifier.weight(1f))
        }
        Text(
            text = card.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ButtonPair(
            tonalLabel = stringResource(R.string.today_log_bleeding),
            tonalIcon = R.drawable.ms_water_drop,
            onTonal = onLogBleeding,
            outlinedLabel = stringResource(R.string.today_method_settings),
            onOutlined = onMethodSettings,
        )
    }
}

@Composable
private fun PregnancyCard(card: PregnancyCardUi) {
    ContentCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabelledValue(stringResource(R.string.today_due_date), card.dueDate, Modifier.weight(1f))
            LabelledValue(stringResource(R.string.today_trimester), card.trimester, Modifier.weight(1f))
        }
        LinearProgressIndicator(
            progress = { card.progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
            text = card.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    InfoBanner(text = stringResource(R.string.today_pregnancy_banner))
}

@Composable
private fun LabelledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ButtonPair(
    tonalLabel: String,
    tonalIcon: Int,
    onTonal: () -> Unit,
    outlinedLabel: String,
    onOutlined: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onTonal, modifier = Modifier.weight(1f)) {
            MsIcon(resId = tonalIcon, contentDescription = null, size = 18.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(tonalLabel, maxLines = 1)
        }
        OutlinedButton(onClick = onOutlined, modifier = Modifier.weight(1f)) { Text(outlinedLabel, maxLines = 1) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoggedToday(
    chips: List<ValueChipUi>,
    onEdit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.today_logged_today),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            if (chips.isNotEmpty()) TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
        }
        if (chips.isEmpty()) {
            Text(
                text = stringResource(R.string.today_nothing_logged),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (chip in chips) ValueChip(icon = chip.icon, text = chip.text)
            }
        }
    }
}
