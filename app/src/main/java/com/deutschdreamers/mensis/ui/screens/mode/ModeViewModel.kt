package com.deutschdreamers.mensis.ui.screens.mode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.pregnancy.PregnancyCalculator
import com.deutschdreamers.mensis.domain.pregnancy.PregnancyStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ModeUiState(
    val mode: TrackerMode,
    val lmp: LocalDate?,
    val today: LocalDate,
    /** The latest logged period start: what pregnancy mode counts from unless the user picks another day. */
    val suggestedLmp: LocalDate?,
) {
    val pregnancy: PregnancyStatus?
        get() = lmp?.takeIf { mode == TrackerMode.PREGNANCY }?.let { PregnancyCalculator.calculate(it, today) }
}

/** Settings → Mode, and the pregnancy-mode offer after a positive test. Both write the tracker settings. */
@HiltViewModel
class ModeViewModel
    @Inject
    constructor(
        trackerData: TrackerDataRepository,
        private val settings: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<ModeUiState?> =
            trackerData
                .observe()
                .map { data ->
                    val today = LocalDate.now(clock)
                    ModeUiState(
                        mode = data.settings.mode,
                        lmp = data.settings.pregnancyLmp,
                        today = today,
                        suggestedLmp = PregnancyCalculator.lastPeriodStart(data.entries, today),
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Back to cycle tracking; the pregnancy start date is not kept. */
        fun setCycle() {
            viewModelScope.launch {
                settings.updateTracker { it.copy(mode = TrackerMode.CYCLE, pregnancyLmp = null) }
            }
        }

        fun setPregnancy(lmp: LocalDate) {
            viewModelScope.launch {
                settings.updateTracker { it.copy(mode = TrackerMode.PREGNANCY, pregnancyLmp = lmp) }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
