package com.deutschdreamers.mensis.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.backup.BackupController
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Goal
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import com.deutschdreamers.mensis.lock.AppLockManager
import com.deutschdreamers.mensis.ui.screens.backup.ConnectProblem
import com.deutschdreamers.mensis.ui.screens.backup.ConnectResult
import com.deutschdreamers.mensis.ui.screens.export.ExportUiState
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
    val backupAvailability: BackupAvailability = BackupAvailability.AVAILABLE,
    val backupEnabled: Boolean = true,
    /** The Google account connected on step 7; backup is turned on with it when onboarding finishes. */
    val backupEmail: String? = null,
    val backupProblem: ConnectProblem? = null,
    val backupPassword: String = "",
    val backupRepeat: String = "",
    val finishing: Boolean = false,
    val finished: Boolean = false,
) {
    val isFirst: Boolean get() = step == 0
    val isLast: Boolean get() = step == LAST_STEP

    /** Design: Skip appears from step 3 (0-based 2) on; on the last step it reads "Not now". */
    val skippable: Boolean get() = step >= FIRST_SKIPPABLE_STEP

    val backupOffered: Boolean get() = backupAvailability == BackupAvailability.AVAILABLE

    /** Step 7's Continue first connects the Google account. */
    val needsGoogleConnect: Boolean
        get() = step == BACKUP_STEP && backupOffered && backupEnabled && backupEmail == null

    val passwordsMatch: Boolean get() = backupPassword == backupRepeat

    val backupPasswordValid: Boolean
        get() = backupPassword.length >= ExportUiState.MIN_PASSWORD_LENGTH && passwordsMatch

    /** Backup is turned on at the end only with an account and a valid password. */
    val backupReady: Boolean get() = backupOffered && backupEnabled && backupEmail != null && backupPasswordValid

    val canContinue: Boolean get() = !finishing && (step != PASSWORD_STEP || backupPasswordValid)

    companion object {
        const val STEP_COUNT: Int = 9
        const val LAST_STEP: Int = STEP_COUNT - 1
        const val FIRST_SKIPPABLE_STEP: Int = 2
        const val BACKUP_STEP: Int = 6
        const val PASSWORD_STEP: Int = 7
    }
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val dayEntries: DayEntryRepository,
        private val backup: BackupController,
        val accounts: GoogleAccountGateway,
        val lock: AppLockManager,
        private val clock: Clock,
    ) : ViewModel() {
        private val state =
            MutableStateFlow(
                backup.availability().let {
                    OnboardingDraft(
                        backupAvailability = it,
                        backupEnabled =
                            it == BackupAvailability.AVAILABLE,
                    )
                },
            )
        val draft: StateFlow<OnboardingDraft> = state

        val today: LocalDate get() = LocalDate.now(clock)

        fun update(transform: (OnboardingDraft) -> OnboardingDraft) = state.update(transform)

        fun next() {
            val current = state.value
            when {
                // "Not now" on the last step.
                current.isLast -> finish(remindersAllowed = false)
                !current.canContinue -> Unit
                // Without backup there is no password to choose.
                current.step == OnboardingDraft.BACKUP_STEP && !current.backupReadyToAsk() ->
                    state.update { it.copy(step = OnboardingDraft.LAST_STEP, backupEnabled = false) }
                else -> state.update { it.copy(step = it.step + 1) }
            }
        }

        fun back() =
            state.update {
                when {
                    it.step == OnboardingDraft.LAST_STEP && !it.backupEnabled ->
                        it.copy(
                            step = OnboardingDraft.BACKUP_STEP,
                        )
                    it.step > 0 -> it.copy(step = it.step - 1)
                    else -> it
                }
            }

        /** Skipping step 7 or 8 skips backup altogether. */
        fun skip() {
            val step = state.value.step
            if (step == OnboardingDraft.BACKUP_STEP || step == OnboardingDraft.PASSWORD_STEP) {
                state.update { it.copy(step = OnboardingDraft.LAST_STEP, backupEnabled = false) }
            } else {
                next()
            }
        }

        fun onBackupConnect(result: ConnectResult) {
            when (result) {
                is ConnectResult.Connected ->
                    state.update {
                        it.copy(backupEmail = result.email, backupProblem = null, step = OnboardingDraft.PASSWORD_STEP)
                    }
                ConnectResult.Cancelled -> Unit
                ConnectResult.NoAccount -> state.update { it.copy(backupProblem = ConnectProblem.NO_ACCOUNT) }
                ConnectResult.Failed -> state.update { it.copy(backupProblem = ConnectProblem.FAILED) }
            }
        }

        /**
         * Writes the draft, turns backup on when it was set up, marks onboarding complete and reports
         * back through [OnboardingDraft.finished]. When notifications were allowed on the last step, the
         * period reminder is switched on with its defaults (2 days before, 09:00).
         */
        fun finish(remindersAllowed: Boolean) {
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
                val email = current.backupEmail
                if (current.backupReady && email != null) {
                    val password = current.backupPassword.toCharArray()
                    try {
                        backup.turnOn(email, password)
                    } finally {
                        password.fill('\u0000')
                    }
                }
                if (remindersAllowed) {
                    settings.updateReminders { it.update(ReminderType.PERIOD) { s -> s.copy(enabled = true) } }
                }
                settings.updateUiPrefs {
                    it.copy(
                        lockEnabled = current.lockEnabled,
                        hideInRecents = current.hideInRecents,
                        onboardingCompleted = true,
                    )
                }
                state.update { it.copy(finished = true, backupPassword = "", backupRepeat = "") }
            }
        }

        private fun OnboardingDraft.backupReadyToAsk(): Boolean = backupOffered && backupEnabled && backupEmail != null
    }
