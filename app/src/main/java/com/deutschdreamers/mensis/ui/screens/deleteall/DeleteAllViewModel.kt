package com.deutschdreamers.mensis.ui.screens.deleteall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.backup.BackupController
import com.deutschdreamers.mensis.wipe.DeleteAllData
import com.deutschdreamers.mensis.wipe.DeleteAllResult
import com.deutschdreamers.mensis.wipe.DriveDeleteFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeleteAllUiState(
    /** Backup is set up with an account, so its Drive files can go too (the checkbox is shown). */
    val driveBackupOn: Boolean = false,
    val deleteDrive: Boolean = false,
    val busy: Boolean = false,
    val failure: DriveDeleteFailure? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class DeleteAllViewModel
    @Inject
    constructor(
        private val deleteAllData: DeleteAllData,
        backup: BackupController,
    ) : ViewModel() {
        private val draft = MutableStateFlow(DeleteAllUiState())

        // While deleting, the backup settings disappear underneath; the dialog keeps what it showed.
        val uiState: StateFlow<DeleteAllUiState> =
            combine(draft, backup.config) { state, config ->
                if (state.busy || state.deleted) {
                    state
                } else {
                    state.copy(driveBackupOn = config.enabled && config.accountEmail != null)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DeleteAllUiState())

        fun setDeleteDrive(delete: Boolean) = draft.update { it.copy(deleteDrive = delete, failure = null) }

        /** The dialog was closed: the next one starts unticked. */
        fun reset() = draft.update { DeleteAllUiState() }

        fun delete() {
            val current = uiState.value
            if (current.busy || current.deleted) return
            draft.update { it.copy(busy = true, failure = null, driveBackupOn = current.driveBackupOn) }
            viewModelScope.launch {
                when (val result = deleteAllData.deleteAll(current.driveBackupOn && current.deleteDrive)) {
                    DeleteAllResult.Deleted -> draft.update { it.copy(busy = false, deleted = true) }
                    is DeleteAllResult.DriveFailed -> draft.update { it.copy(busy = false, failure = result.failure) }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
