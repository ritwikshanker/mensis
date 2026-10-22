package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.ImportBatchEntity
import com.deutschdreamers.mensis.data.db.StoredRows
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity
import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What the preview step needs to know about the phone before the user picks a strategy. */
data class ImportPreview(
    val conflicts: Int,
    val localHasHistory: Boolean,
    val localHasSettings: Boolean,
)

data class ImportResult(
    /** Days written: added plus updated. */
    val entries: Int,
    val added: Int,
    val updated: Int,
    /** Days in the file that were left as the phone had them. */
    val kept: Int,
    /** Cycles recognised in the file's entries. */
    val cycles: Int,
    val skipped: Int,
    val history: ExtrasOutcome,
    val settings: ExtrasOutcome,
    /** False when nothing changed, so there is nothing to undo. */
    val undoable: Boolean,
)

/** The latest import, as stored with its undo rows. */
data class LastImport(
    val source: ImportSource,
    val importedAt: Instant,
    val fileName: String?,
    val entries: Int,
)

@Serializable
private data class BatchSummary(
    val fileName: String? = null,
    val entries: Int,
    val cycles: Int,
    val skipped: Int,
)

/**
 * Applies a [ParsedImport] in one transaction and undoes the latest import. Each import replaces the
 * previous undo information, so only the most recent one can be undone.
 */
@Singleton
class ImportApplier
    @Inject
    constructor(
        private val store: ImportStore,
        private val clock: Clock,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun preview(parsed: ParsedImport): ImportPreview =
            store.transaction {
                ImportPreview(
                    conflicts = ImportPlanner.conflicts(parsed, store.dayEntries()),
                    localHasHistory = store.contraceptionPeriods().any { it.deletedAt == null },
                    localHasSettings = store.trackerSettings() != null,
                )
            }

        suspend fun apply(
            parsed: ParsedImport,
            strategy: ConflictStrategy,
            fileName: String? = null,
        ): ImportResult =
            store.transaction {
                val now = Instant.now(clock)
                val batchId = UUID.randomUUID().toString()
                val existing = StoredRows(store.dayEntries(), store.contraceptionPeriods(), store.trackerSettings())
                val plan = ImportPlanner.plan(parsed, strategy, existing, ImportStamp(batchId, now.toString()))
                val cycles = CycleDeriver.derive(parsed.days).size
                if (!plan.isEmpty) {
                    store.upsertDayEntries(plan.dayEntries)
                    if (plan.contraception.isNotEmpty()) store.upsertContraceptionPeriods(plan.contraception)
                    plan.settings?.let { store.upsertTrackerSettings(it) }
                    val summary = BatchSummary(fileName, plan.added + plan.updated, cycles, parsed.skipped.size)
                    store.replaceBatch(
                        ImportBatchEntity(
                            id = batchId,
                            source = parsed.source.name,
                            importedAt = now.toString(),
                            summaryJson = json.encodeToString(BatchSummary.serializer(), summary),
                        ),
                        plan.undo,
                    )
                }
                ImportResult(
                    entries = plan.added + plan.updated,
                    added = plan.added,
                    updated = plan.updated,
                    kept = plan.kept,
                    cycles = cycles,
                    skipped = parsed.skipped.size,
                    history = plan.history,
                    settings = plan.settingsOutcome,
                    undoable = !plan.isEmpty,
                )
            }

        suspend fun lastImport(): LastImport? {
            val batch = store.latestBatch() ?: return null
            // A summary this version cannot read still leaves the import undoable.
            val summary =
                runCatching {
                    json.decodeFromString(
                        BatchSummary.serializer(),
                        batch.summaryJson,
                    )
                }.getOrNull()
            return LastImport(
                source = ImportSource.entries.firstOrNull { it.name == batch.source } ?: ImportSource.CSV,
                importedAt = runCatching { Instant.parse(batch.importedAt) }.getOrDefault(Instant.EPOCH),
                fileName = summary?.fileName,
                entries = summary?.entries ?: 0,
            )
        }

        /** Restores every row the latest import touched to its earlier state; false when there was none. */
        suspend fun undoLast(): Boolean =
            store.transaction {
                val batch = store.latestBatch() ?: return@transaction false
                val rows = store.undoRows(batch.id).groupBy { it.tableName }
                rows[ImportPlanner.DAY_ENTRIES].orEmpty().let { undo ->
                    store.deleteDayEntries(undo.filter { it.previousRowJson == null }.map { it.rowId })
                    val restored =
                        undo.mapNotNull { it.previousRowJson }.map {
                            json.decodeFromString(
                                DayEntryEntity.serializer(),
                                it,
                            )
                        }
                    if (restored.isNotEmpty()) store.upsertDayEntries(restored)
                }
                rows[ImportPlanner.CONTRACEPTION].orEmpty().let { undo ->
                    store.deleteContraceptionPeriods(undo.filter { it.previousRowJson == null }.map { it.rowId })
                    val restored =
                        undo.mapNotNull { it.previousRowJson }.map {
                            json.decodeFromString(
                                ContraceptionPeriodEntity.serializer(),
                                it,
                            )
                        }
                    if (restored.isNotEmpty()) store.upsertContraceptionPeriods(restored)
                }
                rows[ImportPlanner.SETTINGS].orEmpty().firstOrNull()?.let { undo ->
                    val previous = undo.previousRowJson
                    if (previous == null) {
                        store.deleteTrackerSettings()
                    } else {
                        store.upsertTrackerSettings(json.decodeFromString(TrackerSettingsEntity.serializer(), previous))
                    }
                }
                store.clearBatches()
                true
            }
    }
