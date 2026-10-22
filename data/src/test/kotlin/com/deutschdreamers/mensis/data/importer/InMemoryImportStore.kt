package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.ImportBatchEntity
import com.deutschdreamers.mensis.data.db.ImportUndoRowEntity
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity

/** [ImportStore] over plain maps; a transaction restores the previous state when its block throws. */
class InMemoryImportStore : ImportStore {
    val days = linkedMapOf<String, DayEntryEntity>()
    val periods = linkedMapOf<String, ContraceptionPeriodEntity>()
    var settings: TrackerSettingsEntity? = null
    val batches = mutableListOf<ImportBatchEntity>()
    val undo = mutableListOf<ImportUndoRowEntity>()

    data class State(
        val days: List<DayEntryEntity>,
        val periods: List<ContraceptionPeriodEntity>,
        val settings: TrackerSettingsEntity?,
    )

    /** The data tables, sorted, for before/after comparisons. */
    fun state(): State = State(days.values.sortedBy { it.id }, periods.values.sortedBy { it.id }, settings)

    override suspend fun <R> transaction(block: suspend () -> R): R {
        val saved = Triple(LinkedHashMap(days), LinkedHashMap(periods), settings)
        val savedBatches = batches.toList() to undo.toList()
        return try {
            block()
        } catch (e: Exception) {
            days.clear()
            days.putAll(saved.first)
            periods.clear()
            periods.putAll(saved.second)
            settings = saved.third
            batches.clear()
            batches.addAll(savedBatches.first)
            undo.clear()
            undo.addAll(savedBatches.second)
            throw e
        }
    }

    override suspend fun dayEntries(): List<DayEntryEntity> = days.values.toList()

    override suspend fun contraceptionPeriods(): List<ContraceptionPeriodEntity> = periods.values.toList()

    override suspend fun trackerSettings(): TrackerSettingsEntity? = settings

    override suspend fun upsertDayEntries(rows: List<DayEntryEntity>) {
        for (row in rows) {
            check(days.values.none { it.date == row.date && it.id != row.id }) { "unique date violated: ${row.date}" }
            days[row.id] = row
        }
    }

    override suspend fun deleteDayEntries(ids: List<String>) {
        ids.forEach { days.remove(it) }
    }

    override suspend fun upsertContraceptionPeriods(rows: List<ContraceptionPeriodEntity>) {
        rows.forEach { periods[it.id] = it }
    }

    override suspend fun deleteContraceptionPeriods(ids: List<String>) {
        ids.forEach { periods.remove(it) }
    }

    override suspend fun upsertTrackerSettings(row: TrackerSettingsEntity) {
        settings = row
    }

    override suspend fun deleteTrackerSettings() {
        settings = null
    }

    override suspend fun latestBatch(): ImportBatchEntity? = batches.maxByOrNull { it.importedAt }

    override suspend fun undoRows(batchId: String): List<ImportUndoRowEntity> = undo.filter { it.batchId == batchId }

    override suspend fun replaceBatch(
        batch: ImportBatchEntity,
        rows: List<ImportUndoRowEntity>,
    ) {
        clearBatches()
        batches += batch
        undo += rows
    }

    override suspend fun clearBatches() {
        batches.clear()
        undo.clear()
    }

    fun put(vararg rows: DayEntryEntity) {
        rows.forEach { days[it.id] = it }
    }
}
