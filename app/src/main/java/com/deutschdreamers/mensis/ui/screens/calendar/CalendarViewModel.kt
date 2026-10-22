package com.deutschdreamers.mensis.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.domain.model.Flow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel
    @Inject
    constructor(
        trackerData: TrackerDataRepository,
        private val dayEntries: DayEntryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val today = MutableStateFlow(LocalDate.now(clock))
        private val selection = MutableStateFlow(today.value.let { CalendarSelection(YearMonth.from(it), it) })

        val uiState: StateFlow<CalendarUiState?> =
            combine(trackerData.observe(), today, selection) { data, date, sel ->
                CalendarMonthBuilder.build(data, date, sel)
            }.flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun refreshToday() {
            today.value = LocalDate.now(clock)
        }

        fun previousMonth() = selection.update { it.copy(month = it.month.minusMonths(1)) }

        fun nextMonth() = selection.update { it.copy(month = it.month.plusMonths(1)) }

        fun select(date: LocalDate) = selection.update { it.copy(selected = date) }

        fun startEdit() = selection.update { it.copy(editMode = true, toggles = emptySet()) }

        fun cancelEdit() = selection.update { it.copy(editMode = false, toggles = emptySet()) }

        /** Edit mode: tapping a day flips its bleeding mark; nothing is written until [saveEdit]. */
        fun toggle(date: LocalDate) =
            selection.update { current ->
                val toggles = if (date in current.toggles) current.toggles - date else current.toggles + date
                current.copy(toggles = toggles)
            }

        /** Marked days get medium flow unless already light or more; unmarked days lose their flow. */
        fun saveEdit() {
            val toggles = selection.value.toggles
            viewModelScope.launch {
                for (date in toggles) {
                    val existing = dayEntries.get(date)
                    val newFlow = if (existing?.isMenstrualDay == true) null else Flow.MEDIUM
                    dayEntries.setFlow(date, newFlow)
                }
                selection.update { it.copy(editMode = false, toggles = emptySet()) }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
