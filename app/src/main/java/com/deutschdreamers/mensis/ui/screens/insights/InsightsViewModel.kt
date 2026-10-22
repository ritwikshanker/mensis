package com.deutschdreamers.mensis.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        trackerData: TrackerDataRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val today = MutableStateFlow(LocalDate.now(clock))

        val uiState: StateFlow<InsightsUiState?> =
            combine(trackerData.observe(), today) { data, date -> InsightsUiBuilder.build(data, date) }
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Called when the screen resumes so a day change while in the background is picked up. */
        fun refreshToday() {
            today.value = LocalDate.now(clock)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
