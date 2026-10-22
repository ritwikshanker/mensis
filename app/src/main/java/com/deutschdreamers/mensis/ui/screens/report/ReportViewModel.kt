package com.deutschdreamers.mensis.ui.screens.report

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.BuildConfig
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.files.DocumentFiles
import com.deutschdreamers.mensis.lock.AppLockManager
import com.deutschdreamers.mensis.report.ReportBuilder
import com.deutschdreamers.mensis.report.ReportModel
import com.deutschdreamers.mensis.report.ReportOptions
import com.deutschdreamers.mensis.report.ReportPdfWriter
import com.deutschdreamers.mensis.report.ReportRange
import com.deutschdreamers.mensis.report.ReportSection
import com.deutschdreamers.mensis.report.ReportTexts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class ReportStep { SETUP, PREVIEW, READY }

data class ReportUiState(
    val step: ReportStep = ReportStep.SETUP,
    val options: ReportOptions = ReportOptions(),
    /** Null until the data is read. */
    val hasEntries: Boolean? = null,
    /** Null while the PDF for the preview is being built. */
    val preview: ReportPreview? = null,
    /** The name the file was saved under through "Save to Files". */
    val savedName: String? = null,
    val failed: Boolean = false,
) {
    val canPreview: Boolean get() = hasEntries == true && options.sections.isNotEmpty()
}

/** The built report: what the preview card shows and the facts the "PDF ready" line needs. */
data class ReportPreview(
    val model: ReportModel,
    val pageCount: Int,
    val bytes: Long,
    /** "Page 1 of 2 · BBT chart on page 2" */
    val footer: String,
)

@HiltViewModel
class ReportViewModel
    @Inject
    constructor(
        private val trackerData: TrackerDataRepository,
        private val writer: ReportPdfWriter,
        private val files: DocumentFiles,
        private val lock: AppLockManager,
        private val clock: Clock,
    ) : ViewModel() {
        private val state = MutableStateFlow(ReportUiState())
        val uiState: StateFlow<ReportUiState> = state

        private val shareChannel = Channel<Uri>(Channel.BUFFERED)

        /** Content URIs for the share sheet, one per "Share" tap. */
        val shareRequests: Flow<Uri> = shareChannel.receiveAsFlow()

        /** The PDF lives only here until the user saves or shares it. */
        private var pdf: ByteArray? = null
        private var building: Job? = null

        init {
            viewModelScope.launch {
                trackerData.observe().collect { data ->
                    state.update { it.copy(hasEntries = data.entries.isNotEmpty()) }
                }
            }
        }

        val suggestedFileName: String
            get() =
                state.value.preview
                    ?.model
                    ?.fileName ?: ReportTexts.fileName(LocalDate.now(clock))

        fun setRange(range: ReportRange) = state.update { it.copy(options = it.options.copy(range = range)) }

        fun toggleSection(section: ReportSection) =
            state.update {
                val sections = it.options.sections
                it.copy(
                    options =
                        it.options.copy(
                            sections =
                                if (section in
                                    sections
                                ) {
                                    sections - section
                                } else {
                                    sections + section
                                },
                        ),
                )
            }

        /** Design §15 step 2: builds the PDF in memory so the preview shows its real page count. */
        fun preview() {
            if (!state.value.canPreview) return
            val options = state.value.options
            state.update { it.copy(step = ReportStep.PREVIEW, preview = null, savedName = null, failed = false) }
            building?.cancel()
            building =
                viewModelScope.launch {
                    val data = trackerData.observe().first()
                    val today = LocalDate.now(clock)
                    val (model, report) =
                        withContext(Dispatchers.Default) {
                            val model = ReportBuilder.build(data, options, today, BuildConfig.VERSION_NAME)
                            model to writer.write(model)
                        }
                    pdf = report.bytes
                    val footer = ReportTexts.footer(1, report.pageCount, report.temperaturePages)
                    state.update {
                        it.copy(preview = ReportPreview(model, report.pageCount, report.bytes.size.toLong(), footer))
                    }
                }
        }

        /** "Edit": back to the set-up step with the same choices. */
        fun edit() {
            building?.cancel()
            pdf = null
            state.update { it.copy(step = ReportStep.SETUP, preview = null) }
        }

        fun create() {
            if (state.value.preview != null) state.update { it.copy(step = ReportStep.READY) }
        }

        /** The system picker is about to open; returning from it must not trigger the app lock. */
        fun onPickerOpening() = lock.allowExternalTrip()

        /** [uri] from ACTION_CREATE_DOCUMENT, or null when the user backed out of the picker. */
        fun onLocationChosen(uri: Uri?) {
            lock.endExternalTrip()
            val bytes = pdf
            if (uri == null || bytes == null) return
            viewModelScope.launch {
                try {
                    files.write(uri, bytes)
                    val name = files.displayName(uri) ?: suggestedFileName
                    state.update { it.copy(savedName = name, failed = false) }
                } catch (expected: IOException) {
                    state.update { it.copy(failed = true) }
                } catch (expected: SecurityException) {
                    state.update { it.copy(failed = true) }
                }
            }
        }

        fun share() {
            val bytes = pdf ?: return
            viewModelScope.launch {
                lock.allowExternalTrip()
                shareChannel.send(files.shareableCopy(suggestedFileName, bytes))
            }
        }

        fun onShareClosed() = lock.endExternalTrip()

        /**
         * "Discard": forgets the PDF. A copy handed to another app through Share stays readable for it
         * until the app next starts, like an export's.
         */
        fun discard() {
            building?.cancel()
            pdf = null
            state.update { it.copy(step = ReportStep.SETUP, preview = null, savedName = null) }
        }

        override fun onCleared() {
            pdf = null
        }
    }
