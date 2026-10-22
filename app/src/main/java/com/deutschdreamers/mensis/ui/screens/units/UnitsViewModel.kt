package com.deutschdreamers.mensis.ui.screens.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnitsUiState(
    val temperature: TemperatureUnit,
    val weight: WeightUnit,
)

@HiltViewModel
class UnitsViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<UnitsUiState?> =
            settings
                .observeTracker()
                .map { UnitsUiState(it.temperatureUnit, it.weightUnit) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun setTemperature(unit: TemperatureUnit) {
            viewModelScope.launch { settings.updateTracker { it.copy(temperatureUnit = unit) } }
        }

        fun setWeight(unit: WeightUnit) {
            viewModelScope.launch { settings.updateTracker { it.copy(weightUnit = unit) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
