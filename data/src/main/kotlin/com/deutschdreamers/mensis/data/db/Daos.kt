package com.deutschdreamers.mensis.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DayEntryDao {
    @Query("SELECT * FROM day_entries WHERE deleted_at IS NULL ORDER BY date")
    fun observeActive(): Flow<List<DayEntryEntity>>

    @Query("SELECT * FROM day_entries WHERE deleted_at IS NULL ORDER BY date")
    suspend fun active(): List<DayEntryEntity>

    @Query("SELECT * FROM day_entries ORDER BY date")
    suspend fun allIncludingDeleted(): List<DayEntryEntity>

    @Query("SELECT * FROM day_entries WHERE date = :date LIMIT 1")
    suspend fun byDate(date: String): DayEntryEntity?

    @Query("SELECT COUNT(*) FROM day_entries WHERE deleted_at IS NULL")
    suspend fun countActive(): Int

    @Upsert
    suspend fun upsert(entity: DayEntryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DayEntryEntity>)
}

@Dao
interface ContraceptionDao {
    @Query("SELECT * FROM contraception_periods WHERE deleted_at IS NULL ORDER BY start_date")
    fun observeActive(): Flow<List<ContraceptionPeriodEntity>>

    @Query("SELECT * FROM contraception_periods WHERE deleted_at IS NULL ORDER BY start_date")
    suspend fun active(): List<ContraceptionPeriodEntity>

    @Query("SELECT * FROM contraception_periods ORDER BY start_date")
    suspend fun allIncludingDeleted(): List<ContraceptionPeriodEntity>

    @Query("SELECT * FROM contraception_periods WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): ContraceptionPeriodEntity?

    @Upsert
    suspend fun upsert(entity: ContraceptionPeriodEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ContraceptionPeriodEntity>)
}

@Dao
interface TrackerSettingsDao {
    @Query("SELECT * FROM tracker_settings WHERE id = 'default' LIMIT 1")
    fun observe(): Flow<TrackerSettingsEntity?>

    @Query("SELECT * FROM tracker_settings WHERE id = 'default' LIMIT 1")
    suspend fun get(): TrackerSettingsEntity?

    @Upsert
    suspend fun upsert(entity: TrackerSettingsEntity)
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_batches ORDER BY imported_at DESC LIMIT 1")
    suspend fun latestBatch(): ImportBatchEntity?

    @Query("SELECT * FROM import_undo_rows WHERE batch_id = :batchId")
    suspend fun undoRows(batchId: String): List<ImportUndoRowEntity>

    @Upsert
    suspend fun upsertBatch(batch: ImportBatchEntity)

    @Upsert
    suspend fun upsertUndoRows(rows: List<ImportUndoRowEntity>)

    @Query("DELETE FROM import_undo_rows")
    suspend fun clearUndoRows()

    @Query("DELETE FROM import_batches")
    suspend fun clearBatches()
}
