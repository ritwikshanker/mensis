package com.deutschdreamers.mensis.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Raw facts for one calendar day. Dates and times are ISO-8601 strings, enums are stored by name,
 * lists as JSON arrays. Every row carries created/updated timestamps and a soft-delete tombstone so
 * the schema is ready for sync. Temperatures are °C, weights kg.
 */
@Entity(tableName = "day_entries", indices = [Index(value = ["date"], unique = true)])
data class DayEntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val flow: String? = null,
    @ColumnInfo(name = "temperature_c") val temperatureC: Double? = null,
    @ColumnInfo(name = "temperature_time") val temperatureTime: String? = null,
    val symptoms: String = "[]",
    val moods: String = "[]",
    @ColumnInfo(name = "cervical_mucus") val cervicalMucus: String? = null,
    @ColumnInfo(name = "lh_test") val lhTest: String? = null,
    @ColumnInfo(name = "pregnancy_test") val pregnancyTest: String? = null,
    val sex: String? = null,
    @ColumnInfo(name = "pill_state") val pillState: String? = null,
    @ColumnInfo(name = "patch_changed") val patchChanged: Boolean? = null,
    @ColumnInfo(name = "ring_state") val ringState: String? = null,
    @ColumnInfo(name = "injection_given") val injectionGiven: Boolean? = null,
    @ColumnInfo(name = "emergency_contraception") val emergencyContraception: Boolean? = null,
    @ColumnInfo(name = "sleep_minutes") val sleepMinutes: Int? = null,
    @ColumnInfo(name = "weight_kg") val weightKg: Double? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @ColumnInfo(name = "import_batch_id") val importBatchId: String? = null,
)

/** A contraception method in effect from start to end (null end = current). */
@Entity(tableName = "contraception_periods")
data class ContraceptionPeriodEntity(
    @PrimaryKey val id: String,
    val method: String,
    val regimen: String? = null,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String? = null,
    @ColumnInfo(name = "daily_time") val dailyTime: String? = null,
    @ColumnInfo(name = "replace_by_date") val replaceByDate: String? = null,
    @ColumnInfo(name = "checkup_date") val checkupDate: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

/** Single-row table of the health-relevant settings. UI preferences live in DataStore instead. */
@Entity(tableName = "tracker_settings")
data class TrackerSettingsEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    @ColumnInfo(name = "tracking_level") val trackingLevel: String,
    val goal: String,
    val mode: String,
    @ColumnInfo(name = "pregnancy_lmp") val pregnancyLmp: String? = null,
    @ColumnInfo(name = "typical_cycle_length") val typicalCycleLength: Int,
    @ColumnInfo(name = "typical_period_length") val typicalPeriodLength: Int,
    @ColumnInfo(name = "temperature_unit") val temperatureUnit: String,
    @ColumnInfo(name = "weight_unit") val weightUnit: String,
    @ColumnInfo(name = "reminders_json") val remindersJson: String = "{}",
    @ColumnInfo(name = "discreet_wording") val discreetWording: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
) {
    companion object {
        const val SINGLETON_ID = "default"
    }
}

/** One applied import; the latest batch can be undone from its undo rows. */
@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val source: String,
    @ColumnInfo(name = "imported_at") val importedAt: String,
    @ColumnInfo(name = "summary_json") val summaryJson: String,
)

/** The previous state of a row touched by an import (null = the row did not exist). */
@Entity(tableName = "import_undo_rows", primaryKeys = ["batch_id", "table_name", "row_id"])
data class ImportUndoRowEntity(
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    @ColumnInfo(name = "previous_row_json") val previousRowJson: String? = null,
)
