package com.deutschdreamers.mensis.ui.screens.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.BuildConfig
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.export.DataExporter
import com.deutschdreamers.mensis.data.export.ExportFiles
import com.deutschdreamers.mensis.data.export.ExportFormat
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.data.export.MensisExport
import com.deutschdreamers.mensis.files.DocumentFiles
import com.deutschdreamers.mensis.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

enum class ExportStep { OPTIONS, SAVING, SAVED }

data class ExportUiState(
    val step: ExportStep = ExportStep.OPTIONS,
    val format: ExportFormat = ExportFormat.JSON,
    val encrypt: Boolean = false,
    val password: String = "",
    val repeat: String = "",
    /** Null until the snapshot is read. */
    val summary: ExportSummary? = null,
    /** Expected file size for the current format and encryption choice. */
    val estimatedBytes: Long? = null,
    val savedName: String? = null,
    val savedBytes: Long = 0,
    val savedEncrypted: Boolean = false,
    val failed: Boolean = false,
) {
    val passwordsMatch: Boolean get() = password == repeat

    /** Design §13 step 1 → the system "Save to" picker; with encryption the password must be set twice. */
    val canChooseLocation: Boolean
        get() = summary != null && (!encrypt || (password.length >= MIN_PASSWORD_LENGTH && passwordsMatch))

    companion object {
        /** Matches the backup password hint ("At least 12 characters; a sentence works well."). */
        const val MIN_PASSWORD_LENGTH: Int = 12
    }
}

@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val exporter: DataExporter,
        private val files: DocumentFiles,
        private val lock: AppLockManager,
        private val clock: Clock,
    ) : ViewModel() {
        private val state = MutableStateFlow(ExportUiState())
        val uiState: StateFlow<ExportUiState> = state

        private val shareChannel = Channel<Uri>(Channel.BUFFERED)

        /** Content URIs for the share sheet, one per "Share file" tap. */
        val shareRequests: Flow<Uri> = shareChannel.receiveAsFlow()

        private var sizes: Map<Pair<ExportFormat, Boolean>, Long> = emptyMap()
        private var savedBytes: ByteArray? = null

        private val app = ExportedApp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        init {
            viewModelScope.launch {
                val snapshot = exporter.snapshot(app)
                sizes = withContext(Dispatchers.Default) { estimateSizes(snapshot) }
                state.update {
                    it.copy(summary = ExportFiles.summary(snapshot), estimatedBytes = sizes[it.format to it.encrypt])
                }
            }
        }

        val suggestedFileName: String
            get() = ExportFiles.fileName(state.value.format, LocalDate.now(clock), state.value.encrypt)

        val mimeType: String get() = ExportFiles.mimeType(state.value.format, state.value.encrypt)

        fun setFormat(format: ExportFormat) =
            state.update {
                it.copy(
                    format = format,
                    estimatedBytes =
                        sizes[
                            format to
                                it.encrypt,
                        ],
                )
            }

        fun setEncrypt(encrypt: Boolean) =
            state.update {
                it.copy(
                    encrypt = encrypt,
                    estimatedBytes =
                        sizes[
                            it.format to
                                encrypt,
                        ],
                )
            }

        fun setPassword(value: String) = state.update { it.copy(password = value) }

        fun setRepeat(value: String) = state.update { it.copy(repeat = value) }

        /** The system picker is about to open; returning from it must not trigger the app lock. */
        fun onPickerOpening() = lock.allowExternalTrip()

        /** [uri] from ACTION_CREATE_DOCUMENT, or null when the user backed out of the picker. */
        fun onLocationChosen(uri: Uri?) {
            lock.endExternalTrip()
            val current = state.value
            if (uri == null || !current.canChooseLocation) return
            state.update { it.copy(step = ExportStep.SAVING, failed = false) }
            viewModelScope.launch {
                try {
                    val password = current.password.takeIf { current.encrypt }?.toCharArray()
                    val bytes =
                        try {
                            val snapshot = exporter.snapshot(app)
                            withContext(Dispatchers.Default) { ExportFiles.encode(snapshot, current.format, password) }
                        } finally {
                            password?.fill('\u0000')
                        }
                    files.write(uri, bytes)
                    savedBytes = bytes
                    val name = files.displayName(uri) ?: suggestedFileName
                    state.update {
                        it.copy(
                            step = ExportStep.SAVED,
                            savedName = name,
                            savedBytes = bytes.size.toLong(),
                            savedEncrypted = current.encrypt,
                            password = "",
                            repeat = "",
                        )
                    }
                } catch (expected: IOException) {
                    state.update { it.copy(step = ExportStep.OPTIONS, failed = true) }
                } catch (expected: SecurityException) {
                    state.update { it.copy(step = ExportStep.OPTIONS, failed = true) }
                }
            }
        }

        fun share() {
            val bytes = savedBytes ?: return
            val name = state.value.savedName ?: suggestedFileName
            viewModelScope.launch {
                lock.allowExternalTrip()
                shareChannel.send(files.shareableCopy(name, bytes))
            }
        }

        fun onShareClosed() = lock.endExternalTrip()

        override fun onCleared() {
            savedBytes = null
        }

        /** Plain sizes are exact; encrypted ones add the container overhead to the compressed size. */
        private fun estimateSizes(snapshot: MensisExport): Map<Pair<ExportFormat, Boolean>, Long> =
            ExportFormat.entries
                .flatMap { format ->
                    val plain = ExportFiles.encode(snapshot, format)
                    val sealed = gzipSize(plain) + EncryptedContainer.HEADER_BYTES + CONTAINER_TAIL_BYTES
                    listOf((format to false) to plain.size.toLong(), (format to true) to sealed)
                }.toMap()

        private fun gzipSize(bytes: ByteArray): Long =
            ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.size().toLong()

        private companion object {
            /** GCM nonce and tag. */
            const val CONTAINER_TAIL_BYTES = 12 + 16
        }
    }
