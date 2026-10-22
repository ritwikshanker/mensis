package com.deutschdreamers.mensis.ui.screens.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.backup.BackupConfig
import com.deutschdreamers.mensis.backup.BackupController
import com.deutschdreamers.mensis.backup.BackupFrequency
import com.deutschdreamers.mensis.backup.BackupRunResult
import com.deutschdreamers.mensis.backup.ManualBackupState
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.backup.auth.GoogleAuthException
import com.deutschdreamers.mensis.backup.drive.ReconnectRequiredException
import com.deutschdreamers.mensis.lock.AppLockManager
import com.deutschdreamers.mensis.ui.screens.export.ExportUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** What the status card shows (design §11: idle, running, done), plus the states the design leaves out. */
enum class BackupCard { UNAVAILABLE, OFF, RUNNING, WAITING, DONE, RECONNECT, FAILING, IDLE, NEVER }

/** The password section, for turning backup on or changing the password. */
enum class PasswordMode { SETUP, CHANGE }

/** Work that keeps the screen busy. */
enum class BackupBusy { SETTING_UP, CHANGING_PASSWORD, DELETING, TURNING_OFF }

/** One-off notes under the status card. */
enum class BackupNote { NO_ACCOUNT, CONNECT_FAILED, DELETED, DELETE_FAILED, CHANGE_FAILED }

/** The screen's own state; [BackupUiState] combines it with the stored config and the manual run. */
data class BackupDraft(
    /** Connected while setting up, before the password is chosen. */
    val setupEmail: String? = null,
    val passwordMode: PasswordMode? = null,
    val password: String = "",
    val repeat: String = "",
    val busy: BackupBusy? = null,
    val note: BackupNote? = null,
    /** A manual run was seen starting while this screen was open, so its end reads "just now". */
    val sawManualRun: Boolean = false,
)

data class BackupUiState(
    val availability: BackupAvailability,
    val config: BackupConfig,
    val manual: ManualBackupState,
    val draft: BackupDraft,
    val today: LocalDate,
    val zone: ZoneId,
) {
    val passwordsMatch: Boolean get() = draft.password == draft.repeat
    val passwordValid: Boolean
        get() = draft.password.length >= ExportUiState.MIN_PASSWORD_LENGTH && passwordsMatch

    val card: BackupCard
        get() =
            when {
                availability != BackupAvailability.AVAILABLE -> BackupCard.UNAVAILABLE
                !config.enabled -> BackupCard.OFF
                manual is ManualBackupState.Running -> BackupCard.RUNNING
                manual == ManualBackupState.Waiting -> BackupCard.WAITING
                config.needsReconnect -> BackupCard.RECONNECT
                config.lastError != null -> BackupCard.FAILING
                draft.sawManualRun && manual == ManualBackupState.Succeeded -> BackupCard.DONE
                config.lastSuccessAt != null -> BackupCard.IDLE
                else -> BackupCard.NEVER
            }
}

/** Callbacks of the Backup screen, bundled so the stateless content stays readable. */
data class BackupActions(
    val onBack: () -> Unit = {},
    val onConnect: (email: String?) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onRepeat: (String) -> Unit = {},
    val onConfirmPassword: () -> Unit = {},
    val onCancelPassword: () -> Unit = {},
    val onChangePassword: () -> Unit = {},
    val onBackUpNow: () -> Unit = {},
    val onFrequency: (BackupFrequency) -> Unit = {},
    val onWifiOnly: (Boolean) -> Unit = {},
    val onRestore: () -> Unit = {},
    val onTurnOff: () -> Unit = {},
    val onDeleteDrive: () -> Unit = {},
)

@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val controller: BackupController,
        val accounts: GoogleAccountGateway,
        val lock: AppLockManager,
        private val clock: Clock,
    ) : ViewModel() {
        private val draft = MutableStateFlow(BackupDraft())

        val uiState: StateFlow<BackupUiState?> =
            combine(controller.config, controller.manualRuns(), draft) { config, manual, local ->
                BackupUiState(
                    availability = controller.availability(),
                    config = config,
                    manual = manual,
                    draft = local,
                    today = LocalDate.now(clock),
                    zone = clock.zone,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        init {
            viewModelScope.launch {
                controller.manualRuns().collect { manual ->
                    if (manual == ManualBackupState.Waiting || manual is ManualBackupState.Running) {
                        draft.update { it.copy(sawManualRun = true) }
                    }
                }
            }
        }

        fun onConnectResult(result: ConnectResult) {
            when (result) {
                is ConnectResult.Connected ->
                    if (uiState.value?.config?.enabled == true) {
                        viewModelScope.launch { controller.reconnected(result.email) }
                    } else {
                        draft.update {
                            it.copy(setupEmail = result.email, passwordMode = PasswordMode.SETUP, note = null)
                        }
                    }
                ConnectResult.Cancelled -> Unit
                ConnectResult.NoAccount -> draft.update { it.copy(note = BackupNote.NO_ACCOUNT) }
                ConnectResult.Failed -> draft.update { it.copy(note = BackupNote.CONNECT_FAILED) }
            }
        }

        fun setPassword(value: String) = draft.update { it.copy(password = value) }

        fun setRepeat(value: String) = draft.update { it.copy(repeat = value) }

        fun startPasswordChange() = draft.update { it.copy(passwordMode = PasswordMode.CHANGE, note = null) }

        fun cancelPassword() =
            draft.update { it.copy(passwordMode = null, setupEmail = null, password = "", repeat = "") }

        /** "Turn on" or "Change password", depending on the section shown. */
        fun confirmPassword() {
            val state = uiState.value ?: return
            if (!state.passwordValid || state.draft.busy != null) return
            val mode = state.draft.passwordMode ?: return
            val password = state.draft.password.toCharArray()
            draft.update {
                it.copy(
                    busy = if (mode == PasswordMode.SETUP) BackupBusy.SETTING_UP else BackupBusy.CHANGING_PASSWORD,
                    note = null,
                )
            }
            viewModelScope.launch {
                try {
                    val failed =
                        when (mode) {
                            PasswordMode.SETUP -> {
                                controller.turnOn(requireNotNull(state.draft.setupEmail), password)
                                false
                            }
                            PasswordMode.CHANGE -> controller.changePassword(password) !is BackupRunResult.Uploaded
                        }
                    draft.update {
                        BackupDraft(
                            sawManualRun = mode == PasswordMode.SETUP || it.sawManualRun,
                            note = if (failed) BackupNote.CHANGE_FAILED else null,
                        )
                    }
                } finally {
                    password.fill('\u0000')
                }
            }
        }

        fun backUpNow() {
            draft.update { it.copy(sawManualRun = true, note = null) }
            controller.backUpNow()
        }

        fun setFrequency(frequency: BackupFrequency) {
            viewModelScope.launch { controller.setFrequency(frequency) }
        }

        fun setWifiOnly(wifiOnly: Boolean) {
            viewModelScope.launch { controller.setWifiOnly(wifiOnly) }
        }

        fun turnOff() {
            draft.update { it.copy(busy = BackupBusy.TURNING_OFF) }
            viewModelScope.launch {
                controller.turnOff()
                draft.value = BackupDraft()
            }
        }

        fun deleteDriveBackups() {
            draft.update { it.copy(busy = BackupBusy.DELETING) }
            viewModelScope.launch {
                val note =
                    try {
                        controller.deleteDriveBackups()
                        BackupNote.DELETED
                    } catch (expected: IOException) {
                        BackupNote.DELETE_FAILED
                    } catch (expected: ReconnectRequiredException) {
                        BackupNote.DELETE_FAILED
                    } catch (expected: GoogleAuthException) {
                        BackupNote.DELETE_FAILED
                    }
                draft.update { it.copy(busy = null, note = note) }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
