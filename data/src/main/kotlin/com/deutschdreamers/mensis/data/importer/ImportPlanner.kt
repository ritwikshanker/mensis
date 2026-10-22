package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.ImportUndoRowEntity
import com.deutschdreamers.mensis.data.db.StoredRows
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.db.toEntity
import com.deutschdreamers.mensis.domain.model.DayEntry
import kotlinx.serialization.json.Json
import java.util.UUID

/** What a Mensis export's method history or settings do on import. */
enum class ExtrasOutcome {
    NOT_IN_FILE,
    TAKEN_FROM_FILE,
    KEPT,
}

/** The batch an import writes under: its id, its timestamp and how new row ids are made. */
data class ImportStamp(
    val batchId: String,
    val now: String,
    val newId: () -> String = { UUID.randomUUID().toString() },
)

/** Everything one import writes, with the previous state of every row it touches. */
data class ImportPlan(
    val dayEntries: List<DayEntryEntity>,
    val contraception: List<ContraceptionPeriodEntity>,
    val settings: TrackerSettingsEntity?,
    val undo: List<ImportUndoRowEntity>,
    val added: Int,
    val updated: Int,
    val kept: Int,
    val history: ExtrasOutcome,
    val settingsOutcome: ExtrasOutcome,
) {
    val isEmpty: Boolean get() = dayEntries.isEmpty() && contraception.isEmpty() && settings == null
}

/**
 * Decides, without touching the database, what an import changes. Days are matched by date: a day
 * with no data on the phone is added; a day with data follows the [ConflictStrategy]. Rows are written
 * only when their content changes, so importing the same file twice leaves the database unchanged
 * under every strategy. Imports never delete: tombstones in a Mensis file are copied only for dates
 * the phone has never seen. A Mensis file's method history and settings are taken when the phone has
 * none, or with [ConflictStrategy.REPLACE]; otherwise the phone's are kept.
 */
object ImportPlanner {
    const val DAY_ENTRIES: String = "day_entries"
    const val CONTRACEPTION: String = "contraception_periods"
    const val SETTINGS: String = "tracker_settings"

    private val json = Json

    fun historyOutcome(
        parsed: ParsedImport,
        strategy: ConflictStrategy,
        localHasHistory: Boolean,
    ): ExtrasOutcome =
        when {
            parsed.lossless?.contraception.isNullOrEmpty() -> ExtrasOutcome.NOT_IN_FILE
            !localHasHistory || strategy == ConflictStrategy.REPLACE -> ExtrasOutcome.TAKEN_FROM_FILE
            else -> ExtrasOutcome.KEPT
        }

    fun settingsOutcome(
        parsed: ParsedImport,
        strategy: ConflictStrategy,
        localHasSettings: Boolean,
    ): ExtrasOutcome =
        when {
            parsed.lossless?.settings == null -> ExtrasOutcome.NOT_IN_FILE
            !localHasSettings || strategy == ConflictStrategy.REPLACE -> ExtrasOutcome.TAKEN_FROM_FILE
            else -> ExtrasOutcome.KEPT
        }

    /** Days in the file that already hold data on the phone. */
    fun conflicts(
        parsed: ParsedImport,
        existing: List<DayEntryEntity>,
    ): Int {
        val withData = existing.filter { it.deletedAt == null && !it.toDomain().isEmpty }.mapTo(HashSet()) { it.date }
        return parsed.days.count { it.date.toString() in withData }
    }

    fun plan(
        parsed: ParsedImport,
        strategy: ConflictStrategy,
        existing: StoredRows,
        stamp: ImportStamp,
    ): ImportPlan {
        val days = DayPlan(parsed, strategy, existing.dayEntries, stamp).apply { run() }
        val history = historyOutcome(parsed, strategy, existing.contraception.any { it.deletedAt == null })
        val contraception =
            if (history == ExtrasOutcome.TAKEN_FROM_FILE) {
                planHistory(parsed.lossless?.contraception.orEmpty(), existing.contraception, stamp)
            } else {
                emptyList()
            }
        val settingsOutcome = settingsOutcome(parsed, strategy, existing.settings != null)
        val settings =
            parsed.lossless
                ?.settings
                ?.takeIf { settingsOutcome == ExtrasOutcome.TAKEN_FROM_FILE }
                ?.let { planSettings(it, existing.settings, stamp) }
        return ImportPlan(
            dayEntries = days.rows,
            contraception = contraception.map { it.first },
            settings = settings?.first,
            undo = days.undo + contraception.map { it.second } + listOfNotNull(settings?.second),
            added = days.added,
            updated = days.updated,
            kept = days.kept,
            history = history,
            settingsOutcome = settingsOutcome,
        )
    }

    /** The day rows of one import, worked out date by date. */
    private class DayPlan(
        private val parsed: ParsedImport,
        private val strategy: ConflictStrategy,
        existing: List<DayEntryEntity>,
        private val stamp: ImportStamp,
    ) {
        val rows = mutableListOf<DayEntryEntity>()
        val undo = mutableListOf<ImportUndoRowEntity>()
        var added = 0
        var updated = 0
        var kept = 0
        private val byDate = existing.associateBy { it.date }
        private val usedIds = existing.mapTo(HashSet()) { it.id }

        fun run() {
            parsed.days.forEach(::day)
            val written = rows.mapTo(HashSet()) { it.date }
            parsed.lossless?.tombstones.orEmpty().forEach { tombstone ->
                if (tombstone.date !in byDate && tombstone.date !in written && tombstone.id !in usedIds) {
                    write(tombstone.copy(importBatchId = stamp.batchId), previous = null)
                }
            }
        }

        private fun day(incoming: DayEntry) {
            val date = incoming.date.toString()
            val local = byDate[date]
            val localEntry = local?.takeIf { it.deletedAt == null }?.toDomain()?.takeUnless { it.isEmpty }
            if (local == null || localEntry == null) {
                write(newRow(incoming, local, parsed.lossless?.dayEntries?.get(date)), local)
                added++
                return
            }
            val target =
                when (strategy) {
                    ConflictStrategy.MERGE -> EntryMerge.fillEmpty(localEntry, incoming)
                    ConflictStrategy.REPLACE -> incoming
                    ConflictStrategy.SKIP -> localEntry
                }
            if (target == localEntry) {
                kept++
            } else {
                write(target.toEntity(local.id, local.createdAt, stamp.now, stamp.batchId), local)
                updated++
            }
        }

        /**
         * A day the phone holds no data for. A Mensis file's row is copied as it was (id and timestamps)
         * when the phone has never had that date and the id is free; otherwise a tombstone on the phone is
         * revived under its own id, or a fresh row is created.
         */
        private fun newRow(
            incoming: DayEntry,
            local: DayEntryEntity?,
            preserved: DayEntryEntity?,
        ): DayEntryEntity =
            if (local == null && preserved != null && preserved.id !in usedIds) {
                preserved.copy(importBatchId = stamp.batchId)
            } else {
                incoming.toEntity(
                    id = local?.id ?: stamp.newId(),
                    createdAt = local?.createdAt ?: stamp.now,
                    updatedAt = stamp.now,
                    importBatchId = stamp.batchId,
                )
            }

        private fun write(
            row: DayEntryEntity,
            previous: DayEntryEntity?,
        ) {
            rows += row
            usedIds += row.id
            undo +=
                undoRow(
                    stamp,
                    DAY_ENTRIES,
                    row.id,
                    previous?.let { json.encodeToString(DayEntryEntity.serializer(), it) },
                )
        }
    }

    /** The file's method history in place of the phone's; the phone's own periods are soft-deleted. */
    private fun planHistory(
        fileRows: List<ContraceptionPeriodEntity>,
        existing: List<ContraceptionPeriodEntity>,
        stamp: ImportStamp,
    ): List<Pair<ContraceptionPeriodEntity, ImportUndoRowEntity>> {
        val localById = existing.associateBy { it.id }
        val fileIds = fileRows.mapTo(HashSet()) { it.id }
        val replaced =
            existing
                .filter { it.deletedAt == null && it.id !in fileIds }
                .map { it.copy(updatedAt = stamp.now, deletedAt = stamp.now) }
        return (fileRows + replaced).mapNotNull { row ->
            val previous = localById[row.id]
            if (previous == row) return@mapNotNull null
            val previousJson = previous?.let { json.encodeToString(ContraceptionPeriodEntity.serializer(), it) }
            row to undoRow(stamp, CONTRACEPTION, row.id, previousJson)
        }
    }

    /** The file's settings, keeping the phone's creation time; null when they already match. */
    private fun planSettings(
        fileSettings: TrackerSettingsEntity,
        local: TrackerSettingsEntity?,
        stamp: ImportStamp,
    ): Pair<TrackerSettingsEntity, ImportUndoRowEntity>? {
        if (local != null && sameContent(local, fileSettings)) return null
        val row =
            if (local ==
                null
            ) {
                fileSettings
            } else {
                fileSettings.copy(createdAt = local.createdAt, updatedAt = stamp.now)
            }
        val previousJson = local?.let { json.encodeToString(TrackerSettingsEntity.serializer(), it) }
        return row to undoRow(stamp, SETTINGS, TrackerSettingsEntity.SINGLETON_ID, previousJson)
    }

    private fun sameContent(
        a: TrackerSettingsEntity,
        b: TrackerSettingsEntity,
    ): Boolean = a.copy(createdAt = "", updatedAt = "") == b.copy(createdAt = "", updatedAt = "")

    private fun undoRow(
        stamp: ImportStamp,
        table: String,
        rowId: String,
        previousJson: String?,
    ) = ImportUndoRowEntity(batchId = stamp.batchId, tableName = table, rowId = rowId, previousRowJson = previousJson)
}
