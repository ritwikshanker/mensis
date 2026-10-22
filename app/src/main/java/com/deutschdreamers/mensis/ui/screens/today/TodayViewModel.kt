package com.deutschdreamers.mensis.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.backup.BackupStatusSource
import com.deutschdreamers.mensis.data.backup.BackupTrigger
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
class TodayViewModel
    @Inject
    constructor(
        trackerData: TrackerDataRepository,
        backup: BackupStatusSource,
        private val backupTrigger: BackupTrigger,
        private val clock: Clock,
    ) : ViewModel() {
        private val today = MutableStateFlow(LocalDate.now(clock))

        val uiState: StateFlow<TodayUiState?> =
            combine(trackerData.observe(), today, backup.status) { data, date, status ->
                TodayUiBuilder.build(data, date, status)
            }.flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Banner "Retry" / "Back up now". */
        fun backUpNow() = backupTrigger.backUpNow()

        /** Called when the screen resumes so a day change while in the background is picked up. */
        fun refreshToday() {
            today.value = LocalDate.now(clock)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
