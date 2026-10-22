package com.deutschdreamers.mensis.ui.screens.units

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
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.WeightUnit
import com.deutschdreamers.mensis.ui.components.ContentSectionTitle
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.SegmentedChoice
import com.deutschdreamers.mensis.ui.format.Units

/** Settings → Units. Only entry and display change; stored values stay in °C and kg. */
@Composable
fun UnitsScreen(
    onBack: () -> Unit,
    viewModel: UnitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UnitsContent(
        state = state,
        onTemperature = viewModel::setTemperature,
        onWeight = viewModel::setWeight,
        onBack = onBack,
    )
}

@Composable
fun UnitsContent(
    state: UnitsUiState?,
    onTemperature: (TemperatureUnit) -> Unit,
    onWeight: (WeightUnit) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { MensisTopBar(title = stringResource(R.string.settings_units), onBack = onBack) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentSectionTitle(stringResource(R.string.units_temperature))
                    SegmentedChoice(
                        options = TemperatureUnit.entries,
                        selected = state.temperature,
                        label = Units::temperatureSymbol,
                        onSelect = onTemperature,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentSectionTitle(stringResource(R.string.units_weight))
                    SegmentedChoice(
                        options = WeightUnit.entries,
                        selected = state.weight,
                        label = Units::weightSymbol,
                        onSelect = onWeight,
                    )
                }
            }
            Text(
                text = stringResource(R.string.units_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
