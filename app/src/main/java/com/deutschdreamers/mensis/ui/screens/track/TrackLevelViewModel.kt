package com.deutschdreamers.mensis.ui.screens.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackLevelViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val level: StateFlow<TrackingLevel?> =
            settings
                .observeTracker()
                .map {
                    it.trackingLevel
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun setLevel(level: TrackingLevel) {
            viewModelScope.launch { settings.updateTracker { it.copy(trackingLevel = level) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
