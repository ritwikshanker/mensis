package com.deutschdreamers.mensis.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Goal
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Everything onboarding collects. Nothing is written until the last step; Skip keeps the defaults. */
data class OnboardingDraft(
    val step: Int = 0,
    val lastPeriodStart: LocalDate? = null,
    val lastPeriodUnknown: Boolean = false,
    val cycleLength: Int = TrackerSettings.DEFAULT_TYPICAL_CYCLE_LENGTH,
    val periodLength: Int = TrackerSettings.DEFAULT_TYPICAL_PERIOD_LENGTH,
    val goal: Goal = Goal.TRACK_CYCLE,
    val lockEnabled: Boolean = true,
    val hideInRecents: Boolean = true,
    /** Steps 7–8 are UI only until Drive backup lands; nothing is stored from them. */
    val backupEnabled: Boolean = true,
    val backupPassword: String = "",
    val finishing: Boolean = false,
    val finished: Boolean = false,
) {
    val isFirst: Boolean get() = step == 0
    val isLast: Boolean get() = step == LAST_STEP

    /** Design: Skip appears from step 3 (0-based 2) on; on the last step it reads "Not now". */
    val skippable: Boolean get() = step >= FIRST_SKIPPABLE_STEP

    companion object {
        const val STEP_COUNT: Int = 9
        const val LAST_STEP: Int = STEP_COUNT - 1
        const val FIRST_SKIPPABLE_STEP: Int = 2
    }
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val dayEntries: DayEntryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val state = MutableStateFlow(OnboardingDraft())
        val draft: StateFlow<OnboardingDraft> = state

        val today: LocalDate get() = LocalDate.now(clock)

        fun update(transform: (OnboardingDraft) -> OnboardingDraft) = state.update(transform)

        fun next() {
            if (state.value.isLast) finish() else state.update { it.copy(step = it.step + 1) }
        }

        fun back() = state.update { if (it.step > 0) it.copy(step = it.step - 1) else it }

        fun skip() = next()

        /** Writes the draft, marks onboarding complete and reports back through [OnboardingDraft.finished]. */
        fun finish() {
            val current = state.value
            if (current.finishing) return
            state.update { it.copy(finishing = true) }
            viewModelScope.launch {
                current.lastPeriodStart?.let { dayEntries.setFlow(it, Flow.MEDIUM) }
                settings.updateTracker {
                    it.copy(
                        typicalCycleLength = current.cycleLength,
                        typicalPeriodLength = current.periodLength,
                        goal = current.goal,
                    )
                }
                settings.updateUiPrefs {
                    it.copy(
                        lockEnabled = current.lockEnabled,
                        hideInRecents = current.hideInRecents,
                        onboardingCompleted = true,
                    )
                }
                state.update { it.copy(finished = true) }
            }
        }
    }
