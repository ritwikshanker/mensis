package com.deutschdreamers.mensis.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.backup.BackupController
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.domain.model.Goal
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.lock.AppLockManager
import com.deutschdreamers.mensis.ui.screens.backup.ConnectProblem
import com.deutschdreamers.mensis.ui.screens.backup.ConnectResult
import com.deutschdreamers.mensis.ui.screens.export.ExportUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    /** Days already on the phone: during onboarding only an import from step 1 puts them there. */
    val hasHistory: Boolean = false,
    /** Settings already stored: only a Mensis export imported from step 1 brings them. */
    val hasSettings: Boolean = false,
    val finishing: Boolean = false,
    val finished: Boolean = false,
) {
    val isFirst: Boolean get() = step == 0
    val isLast: Boolean get() = step == LAST_STEP

    /**
     * A step an import already answered: imported settings answer steps 2–5 (with them come the user's
     * own cycles), imported days answer "When did your last period start?".
     */
    fun isAnswered(step: Int): Boolean =
        when {
            hasSettings -> step in HOW_IT_WORKS_STEP..GOAL_STEP
            hasHistory -> step == LAST_PERIOD_STEP
            else -> false
        }

    fun nextStep(): Int = (step + 1..LAST_STEP).first { !isAnswered(it) }

    fun previousStep(): Int = (step - 1 downTo 0).firstOrNull { !isAnswered(it) } ?: step

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
        const val HOW_IT_WORKS_STEP: Int = 1
        const val LAST_PERIOD_STEP: Int = 2
        const val GOAL_STEP: Int = 4
        const val LOCK_STEP: Int = 5
        const val BACKUP_STEP: Int = 6
        const val PASSWORD_STEP: Int = 7
    }
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        dayEntries: DayEntryRepository,
        private val answers: OnboardingAnswers,
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

        /** Set when step 1 opens the import; coming back with imported data moves past what it answered. */
        private var importOpened = false

        init {
            viewModelScope.launch {
                combine(
                    settings.observeStored(),
                    dayEntries.observeAll(),
                ) { stored, days -> stored to days.isNotEmpty() }
                    .collect { (stored, days) -> state.update { it.copy(hasSettings = stored, hasHistory = days) } }
            }
        }

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
                else -> state.update { it.copy(step = it.nextStep()) }
            }
        }

        fun onImportOpened() {
            importOpened = true
        }

        /** Onboarding is on screen again; after an import that brought data, step 1 moves on by itself. */
        fun onShown() {
            if (!importOpened) return
            importOpened = false
            val current = state.value
            if (current.isFirst && (current.hasHistory || current.hasSettings)) next()
        }

        fun back() =
            state.update {
                when {
                    it.step == OnboardingDraft.LAST_STEP && !it.backupEnabled ->
                        it.copy(
                            step = OnboardingDraft.BACKUP_STEP,
                        )
                    it.step > 0 -> it.copy(step = it.previousStep())
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
         * Writes the answers ([OnboardingAnswers]), turns backup on when it was set up, marks onboarding
         * complete and reports back through [OnboardingDraft.finished].
         */
        fun finish(remindersAllowed: Boolean) {
            val current = state.value
            if (current.finishing) return
            state.update { it.copy(finishing = true) }
            viewModelScope.launch {
                answers.save(current, remindersAllowed)
                val email = current.backupEmail
                if (current.backupReady && email != null) {
                    val password = current.backupPassword.toCharArray()
                    try {
                        backup.turnOn(email, password)
                    } finally {
                        password.fill('\u0000')
                    }
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
