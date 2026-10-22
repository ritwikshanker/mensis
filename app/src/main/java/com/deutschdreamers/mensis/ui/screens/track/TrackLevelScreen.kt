package com.deutschdreamers.mensis.ui.screens.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.ui.components.InfoBanner
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.RadioRow

/** Design §8 "What to track". Hidden fields keep their data; nothing is ever required. */
@Composable
fun TrackLevelScreen(
    onBack: () -> Unit,
    viewModel: TrackLevelViewModel = hiltViewModel(),
) {
    val level by viewModel.level.collectAsStateWithLifecycle()
    TrackLevelContent(level = level, onSelect = viewModel::setLevel, onBack = onBack)
}

@Composable
fun TrackLevelContent(
    level: TrackingLevel?,
    onSelect: (TrackingLevel) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { MensisTopBar(title = stringResource(R.string.settings_track), onBack = onBack) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        padding,
                    ).verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.track_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column {
                for (option in TrackingLevel.entries) {
                    RadioRow(
                        title = stringResource(option.titleRes),
                        supporting = stringResource(option.descriptionRes),
                        selected = option == level,
                        onSelect = { onSelect(option) },
                    )
                }
            }
            InfoBanner(text = stringResource(R.string.track_info), modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                text = stringResource(R.string.track_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private val TrackingLevel.titleRes: Int
    get() =
        when (this) {
            TrackingLevel.PERIOD -> R.string.track_period
            TrackingLevel.SYMPTOMS -> R.string.track_symptoms
            TrackingLevel.FULL -> R.string.track_full
        }

private val TrackingLevel.descriptionRes: Int
    get() =
        when (this) {
            TrackingLevel.PERIOD -> R.string.track_period_desc
            TrackingLevel.SYMPTOMS -> R.string.track_symptoms_desc
            TrackingLevel.FULL -> R.string.track_full_desc
        }
