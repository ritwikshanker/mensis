package com.deutschdreamers.mensis.ui.screens.mode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.pregnancy.PregnancyStatus
import com.deutschdreamers.mensis.ui.components.ContentCard
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisDatePickerDialog
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.RadioRow
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import java.time.LocalDate

/** Settings → Mode: cycle tracking, or pregnancy counted from the first day of the last period. */
@Composable
fun ModeScreen(
    onBack: () -> Unit,
    viewModel: ModeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    ModeContent(state = current, onCycle = viewModel::setCycle, onPregnancy = viewModel::setPregnancy, onBack = onBack)
}

@Composable
fun ModeContent(
    state: ModeUiState,
    onCycle: () -> Unit,
    onPregnancy: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val pregnant = state.mode == TrackerMode.PREGNANCY
    Scaffold(topBar = { MensisTopBar(title = stringResource(R.string.settings_mode), onBack = onBack) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.mode_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column {
                RadioRow(
                    title = stringResource(R.string.mode_cycle),
                    supporting = stringResource(R.string.mode_cycle_desc),
                    selected = !pregnant,
                    onSelect = { if (pregnant) onCycle() },
                )
                RadioRow(
                    title = stringResource(R.string.mode_pregnancy),
                    supporting = stringResource(R.string.mode_pregnancy_desc),
                    selected = pregnant,
                    onSelect = {
                        // Without a logged period there is nothing to count from until the user picks a day.
                        val start = state.suggestedLmp
                        when {
                            pregnant -> Unit
                            start != null -> onPregnancy(start)
                            else -> picking = true
                        }
                    },
                )
            }
            val status = state.pregnancy
            if (pregnant && status != null) {
                ContentCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LmpRow(lmp = status.lastPeriodStart, onClick = { picking = true })
                    Text(
                        text = ModeTexts.dueLine(status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            InfoBanner(text = stringResource(R.string.mode_info), modifier = Modifier.padding(horizontal = 16.dp))
            if (pregnant) {
                Text(
                    text = stringResource(R.string.mode_footer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
    if (picking) {
        MensisDatePickerDialog(
            initial = state.lmp ?: state.suggestedLmp ?: state.today,
            latest = state.today,
            onDismiss = { picking = false },
            onConfirm = { date ->
                picking = false
                onPregnancy(date)
            },
        )
    }
}

/** "First day of last period" with the chosen date, or a prompt to choose one; tapping opens the picker. */
@Composable
internal fun LmpRow(
    lmp: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MsIcon(
            resId = R.drawable.ms_edit_calendar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val colors = MaterialTheme.colorScheme
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.mode_lmp), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = lmp?.let(Formats::dayMonthYear) ?: stringResource(R.string.mode_lmp_choose),
                style = MaterialTheme.typography.bodyMedium,
                color = if (lmp == null) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

object ModeTexts {
    /** "Estimated due date 24 May 2027 · week 9, day 2". */
    fun dueLine(status: PregnancyStatus): String =
        "Estimated due date ${Formats.dayMonthYear(status.dueDate)} $MIDDLE_DOT " +
            "week ${status.week}, day ${status.dayOfWeek}"
}
