package com.deutschdreamers.mensis.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.theme.SerifStyles

@Composable
fun InsightsScreen(viewModel: InsightsViewModel) {
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
    InsightsScreenContent(current)
}

/** Stateless Insights screen (design §6); also what the previews and UI tests render. */
@Composable
fun InsightsScreenContent(state: InsightsUiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        MensisTopBar(title = stringResource(R.string.tab_insights)) {
            state.cyclesLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CycleLengthCard(state.cycleLength)
            if (state.periodLength != null || state.luteal != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.periodLength?.let {
                        StatCard(stringResource(R.string.insights_period_length), it, Modifier.weight(1f))
                    }
                    state.luteal?.let {
                        StatCard(stringResource(R.string.insights_luteal_phase), it, Modifier.weight(1f))
                    }
                }
            }
            state.temperature?.let { TemperatureCard(it) }
            state.symptoms?.let { SymptomsCard(it) }
            Text(
                text = stringResource(R.string.insights_footer, PredictionConstants.INSIGHTS_WINDOW_CYCLES),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun InsightCard(
    modifier: Modifier = Modifier,
    spacing: Int = 12,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
    ) {
        content()
    }
}

@Composable
private fun CardTitle(
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

/** Serif figure with "days, mean" on its baseline. */
@Composable
private fun MeanFigure(figure: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = figure, style = SerifStyles.stat, modifier = Modifier.alignByBaseline())
        Text(
            text = stringResource(R.string.insights_days_mean),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun CycleLengthCard(card: CycleLengthCardUi?) {
    if (card == null) {
        InsightCard {
            CardTitle(stringResource(R.string.insights_cycle_length))
            Text(
                text = stringResource(R.string.insights_no_cycles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    InsightCard {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CardTitle(stringResource(R.string.insights_cycle_length))
                MeanFigure(card.figure)
            }
            Text(
                text = listOfNotNull(card.range, card.standardDeviation).joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        CycleLengthChart(card.chart)
    }
}

@Composable
private fun StatCard(
    title: String,
    card: StatCardUi,
    modifier: Modifier = Modifier,
) {
    InsightCard(modifier = modifier.fillMaxHeight().semantics(mergeDescendants = true) {}, spacing = 0) {
        CardTitle(title)
        MeanFigure(card.figure)
        Text(
            text = card.supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemperatureCard(card: TemperatureCardUi) {
    InsightCard(spacing = 8) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle(stringResource(R.string.insights_temperature_title), Modifier.weight(1f))
            Text(
                text = card.unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        card.chart?.let { TemperatureChart(it) }
        Text(
            text = card.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SymptomsCard(card: SymptomsCardUi) {
    InsightCard {
        CardTitle(stringResource(R.string.insights_recurring_symptoms))
        card.emptyText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (card.rows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in card.rows) SymptomRow(row)
            }
        }
    }
}

@Composable
private fun SymptomRow(row: SymptomRowUi) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MsIcon(
            resId = row.icon,
            contentDescription = null,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = row.supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
