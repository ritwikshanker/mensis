package com.deutschdreamers.mensis.ui.screens.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.backup.BackupFileInfo
import com.deutschdreamers.mensis.backup.RestoreError
import com.deutschdreamers.mensis.backup.RestoreException
import com.deutschdreamers.mensis.backup.RestoreSource
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.importer.RestoreResult
import com.deutschdreamers.mensis.lock.AppLockManager
import com.deutschdreamers.mensis.ui.screens.backup.ConnectProblem
import com.deutschdreamers.mensis.ui.screens.backup.ConnectResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

/** Design §12: sign in, backup found (or none), password, restoring, restored. */
enum class RestoreStep { SIGN_IN, FOUND, NONE_FOUND, PASSWORD, RESTORING, DONE }

data class RestoreUiState(
    val availability: BackupAvailability,
    val step: RestoreStep = RestoreStep.SIGN_IN,
    val email: String? = null,
    /** Looking for backups in the account's Drive. */
    val checking: Boolean = false,
    val connectProblem: ConnectProblem? = null,
    val file: BackupFileInfo? = null,
    /** What the phone holds now; null until read. */
    val local: ExportSummary? = null,
    val replaceConfirmed: Boolean = false,
    val password: String = "",
    val decrypting: Boolean = false,
    val wrongPassword: Boolean = false,
    val error: RestoreError? = null,
    val cycles: Int = 0,
    val result: RestoreResult? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val localHasData: Boolean get() = (local?.entries ?: 0) > 0

    /** Step 2: a phone that already holds data needs the replace box ticked first. */
    val canRestore: Boolean get() = file != null && local != null && (!localHasData || replaceConfirmed)

    val canDecrypt: Boolean get() = password.isNotEmpty() && !decrypting
}

/** Callbacks of the restore flow, bundled so the stateless content stays readable. */
data class RestoreActions(
    val onBack: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onReplaceConfirmed: (Boolean) -> Unit = {},
    val onChoose: () -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onDecrypt: () -> Unit = {},
    val onOpen: () -> Unit = {},
    /** Onboarding only: leave the restore and continue setting up a new history. */
    val onStartFresh: (() -> Unit)? = null,
)

@HiltViewModel
class RestoreViewModel
    @Inject
    constructor(
        private val source: RestoreSource,
        val accounts: GoogleAccountGateway,
        val lock: AppLockManager,
        clock: Clock,
    ) : ViewModel() {
        private val state = MutableStateFlow(RestoreUiState(availability = accounts.availability(), zone = clock.zone))
        val uiState: StateFlow<RestoreUiState> = state

        private var download: Deferred<ByteArray>? = null

        init {
            viewModelScope.launch { state.update { it.copy(local = source.localData()) } }
        }

        fun onConnectResult(result: ConnectResult) {
            when (result) {
                is ConnectResult.Connected -> findBackup(result.email)
                ConnectResult.Cancelled -> Unit
                ConnectResult.NoAccount -> state.update { it.copy(connectProblem = ConnectProblem.NO_ACCOUNT) }
                ConnectResult.Failed -> state.update { it.copy(connectProblem = ConnectProblem.FAILED) }
            }
        }

        /** Looks again after a network error on step 1. */
        fun retry() {
            val email = state.value.email ?: return
            findBackup(email)
        }

        private fun findBackup(email: String) {
            state.update { it.copy(email = email, checking = true, connectProblem = null, error = null) }
            viewModelScope.launch {
                try {
                    val file = source.latestBackup(email)
                    state.update {
                        it.copy(
                            checking = false,
                            file = file,
                            step = if (file == null) RestoreStep.NONE_FOUND else RestoreStep.FOUND,
                        )
                    }
                } catch (e: RestoreException) {
                    state.update { it.copy(checking = false, error = e.error) }
                }
            }
        }

        fun setReplaceConfirmed(confirmed: Boolean) = state.update { it.copy(replaceConfirmed = confirmed) }

        /** Step 2 → 3; the download starts while the password is typed. */
        fun chooseBackup() {
            val current = state.value
            if (!current.canRestore) return
            startDownload()
            state.update { it.copy(step = RestoreStep.PASSWORD, error = null) }
        }

        fun setPassword(value: String) = state.update { it.copy(password = value, wrongPassword = false) }

        /** Step 3: download (if not done yet), decrypt, then step 4 replaces everything and step 5 follows. */
        fun decryptAndRestore() {
            val current = state.value
            val email = current.email ?: return
            val file = current.file ?: return
            if (!current.canDecrypt) return
            val password = current.password.toCharArray()
            state.update { it.copy(decrypting = true, wrongPassword = false, error = null) }
            viewModelScope.launch {
                try {
                    val bytes = (download ?: startDownload()).await()
                    val opened = source.open(file, bytes, password)
                    state.update {
                        it.copy(
                            step = RestoreStep.RESTORING,
                            decrypting = false,
                            password = "",
                            cycles = opened.summary.cycles,
                        )
                    }
                    val result = source.restore(email, opened)
                    state.update { it.copy(step = RestoreStep.DONE, result = result) }
                } catch (e: RestoreException) {
                    if (e.error != RestoreError.WRONG_PASSWORD) download = null
                    state.update {
                        it.copy(
                            step = if (e.error == RestoreError.RECONNECT) RestoreStep.SIGN_IN else RestoreStep.PASSWORD,
                            decrypting = false,
                            wrongPassword = e.error == RestoreError.WRONG_PASSWORD,
                            error = e.error.takeIf { error -> error != RestoreError.WRONG_PASSWORD },
                        )
                    }
                } finally {
                    password.fill('\u0000')
                }
            }
        }

        /** The back arrow: one step back, false when the flow should close. */
        fun back(): Boolean {
            val step = state.value.step
            val previous =
                when (step) {
                    RestoreStep.PASSWORD -> RestoreStep.FOUND
                    RestoreStep.FOUND, RestoreStep.NONE_FOUND -> RestoreStep.SIGN_IN
                    else -> return false
                }
            state.update { it.copy(step = previous, password = "", wrongPassword = false, error = null) }
            return true
        }

        private fun startDownload(): Deferred<ByteArray> {
            val email = requireNotNull(state.value.email)
            val file = requireNotNull(state.value.file)
            // A failed download surfaces at await(), when the password is submitted.
            return viewModelScope.async { source.download(email, file) }.also { download = it }
        }
    }
