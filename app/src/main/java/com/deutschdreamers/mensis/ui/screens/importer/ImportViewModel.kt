package com.deutschdreamers.mensis.ui.screens.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.csv.CsvTable
import com.deutschdreamers.mensis.data.importer.ColumnMapping
import com.deutschdreamers.mensis.data.importer.ConflictStrategy
import com.deutschdreamers.mensis.data.importer.CsvColumn
import com.deutschdreamers.mensis.data.importer.CsvField
import com.deutschdreamers.mensis.data.importer.DateOrder
import com.deutschdreamers.mensis.data.importer.GenericCsvImporter
import com.deutschdreamers.mensis.data.importer.ImportApplier
import com.deutschdreamers.mensis.data.importer.ImportError
import com.deutschdreamers.mensis.data.importer.ImportException
import com.deutschdreamers.mensis.data.importer.ImportLimits
import com.deutschdreamers.mensis.data.importer.ImportPreview
import com.deutschdreamers.mensis.data.importer.ImportReader
import com.deutschdreamers.mensis.data.importer.ImportResult
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.data.importer.ImportValues
import com.deutschdreamers.mensis.data.importer.LastImport
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.ReadOutcome
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.files.DocumentFiles
import com.deutschdreamers.mensis.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Design §14 steps plus the in-between states a real file needs (reading, password, applying). */
enum class ImportStep(
    /** "Step n of 4" in the top bar. */
    val number: Int,
) {
    SOURCE(1),
    READING(1),
    PASSWORD(1),
    MAPPING(2),
    PREVIEW(3),
    APPLYING(3),
    DONE(4),
    UNDONE(4),
}

/** Why the current file was refused; [ImportError] plus files the system could not open at all. */
enum class ImportProblem { NOT_THIS_FORMAT, NEWER_VERSION, DAMAGED, TOO_LARGE, UNREADABLE }

data class MappingUi(
    val columns: List<CsvColumn>,
    val mapping: ColumnMapping,
    val rows: Int,
    /** A slashed sample such as "03/04/2026" when the user must say which order the dates are in. */
    val ambiguousDateSample: String?,
)

data class ImportUiState(
    val step: ImportStep = ImportStep.SOURCE,
    val source: ImportSource = ImportSource.CSV,
    val problem: ImportProblem? = null,
    val fileName: String = "",
    val lastImport: LastImport? = null,
    val password: String = "",
    val wrongPassword: Boolean = false,
    val mapping: MappingUi? = null,
    val parsed: ParsedImport? = null,
    val preview: ImportPreview? = null,
    val strategy: ConflictStrategy = ConflictStrategy.MERGE,
    val result: ImportResult? = null,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

@HiltViewModel
class ImportViewModel
    @Inject
    constructor(
        private val files: DocumentFiles,
        private val applier: ImportApplier,
        private val settings: SettingsRepository,
        private val lock: AppLockManager,
        private val clock: Clock,
    ) : ViewModel() {
        private val state = MutableStateFlow(ImportUiState())
        val uiState: StateFlow<ImportUiState> = state

        private var encrypted: ByteArray? = null
        private var table: CsvTable? = null

        private val today: LocalDate get() = LocalDate.now(clock)

        init {
            refreshLastImport()
            viewModelScope.launch {
                settings.observeTracker().collect { tracker ->
                    state.update { it.copy(temperatureUnit = tracker.temperatureUnit) }
                }
            }
        }

        fun setSource(source: ImportSource) = state.update { it.copy(source = source, problem = null) }

        fun onPickerOpening() = lock.allowExternalTrip()

        /** [uri] from ACTION_OPEN_DOCUMENT, or null when the user backed out. */
        fun onFileChosen(uri: Uri?) {
            lock.endExternalTrip()
            if (uri == null) return
            state.update { it.copy(step = ImportStep.READING, problem = null) }
            viewModelScope.launch {
                try {
                    val bytes = files.read(uri, ImportLimits.MAX_FILE_BYTES)
                    val name = files.displayName(uri) ?: uri.lastPathSegment.orEmpty()
                    state.update { it.copy(fileName = name) }
                    if (ImportReader.isEncrypted(bytes)) {
                        encrypted = bytes
                        state.update { it.copy(step = ImportStep.PASSWORD, password = "", wrongPassword = false) }
                    } else {
                        read(bytes)
                    }
                } catch (e: ImportException) {
                    fail(e.error)
                } catch (expected: IOException) {
                    fail(ImportProblem.UNREADABLE)
                } catch (expected: SecurityException) {
                    fail(ImportProblem.UNREADABLE)
                }
            }
        }

        fun setPassword(value: String) = state.update { it.copy(password = value, wrongPassword = false) }

        fun decrypt() {
            val bytes = encrypted ?: return
            val password = state.value.password.toCharArray()
            state.update { it.copy(step = ImportStep.READING) }
            viewModelScope.launch {
                try {
                    val plain = withContext(Dispatchers.Default) { ImportReader.decrypt(bytes, password) }
                    encrypted = null
                    state.update { it.copy(password = "") }
                    read(plain)
                } catch (e: ImportException) {
                    if (e.error == ImportError.WRONG_PASSWORD) {
                        state.update { it.copy(step = ImportStep.PASSWORD, wrongPassword = true) }
                    } else {
                        fail(e.error)
                    }
                } finally {
                    password.fill('\u0000')
                }
            }
        }

        private suspend fun read(bytes: ByteArray) {
            val source = state.value.source
            when (val outcome = withContext(Dispatchers.Default) { ImportReader.read(source, bytes, today) }) {
                is ReadOutcome.Parsed -> showPreview(outcome.parsed)
                is ReadOutcome.NeedsMapping -> {
                    table = outcome.table
                    state.update {
                        it.copy(
                            step = ImportStep.MAPPING,
                            mapping = mappingUi(outcome.table, outcome.guess),
                        )
                    }
                }
            }
        }

        fun setField(
            column: Int,
            field: CsvField,
        ) = updateMapping { it.with(column, field) }

        fun setDateOrder(order: DateOrder) = updateMapping { it.copy(dateOrder = order) }

        private fun updateMapping(transform: (ColumnMapping) -> ColumnMapping) {
            val currentTable = table ?: return
            state.update { s -> s.copy(mapping = s.mapping?.let { mappingUi(currentTable, transform(it.mapping)) }) }
        }

        private fun mappingUi(
            table: CsvTable,
            mapping: ColumnMapping,
        ): MappingUi {
            val dateColumn = mapping.fields.indexOf(CsvField.DATE)
            val ambiguous =
                if (GenericCsvImporter.dateOrderAmbiguous(table, mapping)) {
                    table.rows.map { table.cell(it, dateColumn).trim() }.firstOrNull(ImportValues::isSlashedDate)
                } else {
                    null
                }
            return MappingUi(GenericCsvImporter.columns(table), mapping, table.rows.size, ambiguous)
        }

        /** Mapping → preview. */
        fun confirmMapping() {
            val currentTable = table ?: return
            val mapping = state.value.mapping?.mapping ?: return
            if (!mapping.hasDate) return
            viewModelScope.launch {
                val parsed = withContext(Dispatchers.Default) { GenericCsvImporter.parse(currentTable, mapping, today) }
                showPreview(parsed)
            }
        }

        private suspend fun showPreview(parsed: ParsedImport) {
            val preview = applier.preview(parsed)
            state.update { it.copy(step = ImportStep.PREVIEW, parsed = parsed, preview = preview) }
        }

        fun setStrategy(strategy: ConflictStrategy) = state.update { it.copy(strategy = strategy) }

        fun import() {
            val current = state.value
            val parsed = current.parsed ?: return
            state.update { it.copy(step = ImportStep.APPLYING) }
            viewModelScope.launch {
                val result = applier.apply(parsed, current.strategy, current.fileName.ifBlank { null })
                state.update { it.copy(step = ImportStep.DONE, result = result) }
                refreshLastImport()
            }
        }

        /** The summary's "Undo import", or the source step's "Undo last import". */
        fun undo() {
            viewModelScope.launch {
                applier.undoLast()
                state.update { it.copy(step = ImportStep.UNDONE, lastImport = null) }
            }
        }

        /** In-screen back: true when it moved to an earlier step, false when the screen should close. */
        fun back(): Boolean {
            val current = state.value
            val previous =
                when (current.step) {
                    ImportStep.PASSWORD, ImportStep.MAPPING -> ImportStep.SOURCE
                    ImportStep.PREVIEW ->
                        if (table != null &&
                            current.mapping != null
                        ) {
                            ImportStep.MAPPING
                        } else {
                            ImportStep.SOURCE
                        }
                    else -> return false
                }
            if (previous == ImportStep.SOURCE) {
                table = null
                encrypted = null
            }
            state.update {
                it.copy(
                    step = previous,
                    password = "",
                    wrongPassword = false,
                    mapping = if (previous == ImportStep.SOURCE) null else it.mapping,
                    parsed = null,
                    preview = null,
                )
            }
            return true
        }

        private fun fail(error: ImportError) =
            fail(
                when (error) {
                    ImportError.NEWER_VERSION -> ImportProblem.NEWER_VERSION
                    ImportError.DAMAGED, ImportError.WRONG_PASSWORD -> ImportProblem.DAMAGED
                    ImportError.TOO_LARGE -> ImportProblem.TOO_LARGE
                    ImportError.NOT_THIS_FORMAT, ImportError.NO_DATE_COLUMN -> ImportProblem.NOT_THIS_FORMAT
                },
            )

        private fun fail(problem: ImportProblem) {
            table = null
            encrypted = null
            state.update {
                it.copy(
                    step = ImportStep.SOURCE,
                    problem = problem,
                    mapping = null,
                    parsed = null,
                    preview = null,
                )
            }
        }

        private fun refreshLastImport() {
            viewModelScope.launch {
                val last = applier.lastImport()
                state.update { it.copy(lastImport = last) }
            }
        }
    }
