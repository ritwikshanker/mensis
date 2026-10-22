package com.deutschdreamers.mensis.data.importer

import androidx.room.withTransaction
import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.ImportBatchEntity
import com.deutschdreamers.mensis.data.db.ImportUndoRowEntity
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity

/**
 * The tables an import reads and writes, behind one transaction. [RoomImportStore] in the app; an
 * in-memory version lets JVM tests cover every conflict strategy, idempotency and undo.
 */
@Suppress("TooManyFunctions") // read, write and delete for each of the three tables plus the batch
interface ImportStore {
    suspend fun <R> transaction(block: suspend () -> R): R

    suspend fun dayEntries(): List<DayEntryEntity>

    suspend fun contraceptionPeriods(): List<ContraceptionPeriodEntity>

    suspend fun trackerSettings(): TrackerSettingsEntity?

    suspend fun upsertDayEntries(rows: List<DayEntryEntity>)

    suspend fun deleteDayEntries(ids: List<String>)

    suspend fun upsertContraceptionPeriods(rows: List<ContraceptionPeriodEntity>)

    suspend fun deleteContraceptionPeriods(ids: List<String>)

    suspend fun upsertTrackerSettings(row: TrackerSettingsEntity)

    suspend fun deleteTrackerSettings()

    suspend fun latestBatch(): ImportBatchEntity?

    suspend fun undoRows(batchId: String): List<ImportUndoRowEntity>

    /** Only the latest import is undoable: earlier batches and their undo rows are dropped first. */
    suspend fun replaceBatch(
        batch: ImportBatchEntity,
        rows: List<ImportUndoRowEntity>,
    )

    suspend fun clearBatches()
}

class RoomImportStore(
    private val database: DatabaseHolder,
) : ImportStore {
    private val db get() = database.get()

    override suspend fun <R> transaction(block: suspend () -> R): R = db.withTransaction { block() }

    override suspend fun dayEntries(): List<DayEntryEntity> = db.dayEntryDao().allIncludingDeleted()

    override suspend fun contraceptionPeriods(): List<ContraceptionPeriodEntity> =
        db.contraceptionDao().allIncludingDeleted()

    override suspend fun trackerSettings(): TrackerSettingsEntity? = db.trackerSettingsDao().get()

    override suspend fun upsertDayEntries(rows: List<DayEntryEntity>) = db.dayEntryDao().upsertAll(rows)

    override suspend fun deleteDayEntries(ids: List<String>) =
        ids.chunked(MAX_IDS_PER_QUERY).forEach {
            db.dayEntryDao().deleteByIds(it)
        }

    override suspend fun upsertContraceptionPeriods(rows: List<ContraceptionPeriodEntity>) =
        db.contraceptionDao().upsertAll(rows)

    override suspend fun deleteContraceptionPeriods(ids: List<String>) =
        ids.chunked(MAX_IDS_PER_QUERY).forEach { db.contraceptionDao().deleteByIds(it) }

    override suspend fun upsertTrackerSettings(row: TrackerSettingsEntity) = db.trackerSettingsDao().upsert(row)

    override suspend fun deleteTrackerSettings() = db.trackerSettingsDao().delete()

    override suspend fun latestBatch(): ImportBatchEntity? = db.importDao().latestBatch()

    override suspend fun undoRows(batchId: String): List<ImportUndoRowEntity> = db.importDao().undoRows(batchId)

    override suspend fun replaceBatch(
        batch: ImportBatchEntity,
        rows: List<ImportUndoRowEntity>,
    ) {
        clearBatches()
        db.importDao().upsertBatch(batch)
        rows.chunked(MAX_IDS_PER_QUERY).forEach { db.importDao().upsertUndoRows(it) }
    }

    override suspend fun clearBatches() {
        db.importDao().clearUndoRows()
        db.importDao().clearBatches()
    }

    private companion object {
        /** Stays below SQLite's bound-variable limit on every supported version. */
        const val MAX_IDS_PER_QUERY = 500
    }
}
