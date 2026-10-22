package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.db.toEntity
import com.deutschdreamers.mensis.data.export.ExportMigrations
import com.deutschdreamers.mensis.data.export.ExportedDayEntry
import com.deutschdreamers.mensis.data.export.MensisJson
import com.deutschdreamers.mensis.data.export.toEntity
import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Goal
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.RingState
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.model.WeightUnit
import java.time.LocalDate
import java.time.LocalTime

/**
 * Mensis's own JSON export: migrated to the current format version, then read without loss. Rows are
 * kept as written (ids, timestamps, tombstones) for [LosslessRows]; values this version does not know
 * are dropped and reported. Future dates are allowed, since the file comes from Mensis itself.
 */
object MensisJsonImporter {
    fun parse(
        text: String,
        migrations: ExportMigrations = ExportMigrations.Default,
    ): ParsedImport {
        val export = MensisJson.decode(text, migrations)
        val collector = ParseCollector(ImportSource.MENSIS)
        val kept = linkedMapOf<String, DayEntryEntity>()
        val tombstones = mutableListOf<DayEntryEntity>()
        export.dayEntries.forEachIndexed { index, exported ->
            val row = index + 1
            val entity = clean(exported, collector)
            // Tombstones are deleted days, not data: kept for a lossless copy but not counted as rows.
            if (exported.deletedAt != null) {
                if (entity != null) tombstones += entity.copy(deletedAt = exported.deletedAt)
                return@forEachIndexed
            }
            collector.row()
            when {
                entity == null -> collector.skip(row, SkipReason.UNREADABLE_DATE, exported.date)
                else -> {
                    val entry = entity.toDomain()
                    if (entry.isEmpty) {
                        collector.skip(row, SkipReason.NO_DATA)
                    } else {
                        kept[entity.date] = entity
                        collector.add(entry)
                    }
                }
            }
        }
        val contraception =
            export.contraceptionPeriods.map { it.toEntity() }.filter { row ->
                val valid = row.toDomainOrNull() != null
                if (!valid) collector.issue("contraceptionPeriods", IssueKind.UNKNOWN_VALUE, row.method.lowercase())
                valid
            }
        val settings = export.settings?.toEntity()?.takeIf { it.isValid() }
        if (export.settings != null && settings == null) collector.issue("settings", IssueKind.UNKNOWN_VALUE)
        return collector.result(LosslessRows(kept, tombstones, contraception, settings))
    }

    /** The stored row with unknown values dropped (and reported); null when the date itself is unreadable. */
    private fun clean(
        exported: ExportedDayEntry,
        collector: ParseCollector,
    ): DayEntryEntity? {
        if (parseDate(exported.date) == null) return null
        if (exported.temperatureTime != null && parseTime(exported.temperatureTime) == null) {
            collector.issue("temperatureTime", IssueKind.UNKNOWN_VALUE, exported.temperatureTime)
        }
        known("flow", exported.flow, Flow.entries, collector)
        known("cervicalMucus", exported.cervicalMucus, CervicalMucus.entries, collector)
        known("lhTest", exported.lhTest, TestResult.entries, collector)
        known("pregnancyTest", exported.pregnancyTest, TestResult.entries, collector)
        known("sex", exported.sex, SexType.entries, collector)
        known("pillState", exported.pillState, PillState.entries, collector)
        known("ringState", exported.ringState, RingState.entries, collector)
        exported.symptoms.forEach { known("symptoms", it, Symptom.entries, collector) }
        exported.moods.forEach { known("moods", it, Mood.entries, collector) }
        val raw = exported.copy(temperatureTime = exported.temperatureTime?.takeIf { parseTime(it) != null }).toEntity()
        // Round trip through the domain model so only known values reach the database.
        return raw.toDomain().toEntity(id = raw.id, createdAt = raw.createdAt, updatedAt = raw.updatedAt)
    }

    private fun known(
        field: String,
        value: String?,
        entries: List<Enum<*>>,
        collector: ParseCollector,
    ) {
        if (value != null && entries.none { it.name.equals(value, ignoreCase = true) }) {
            collector.issue(field, IssueKind.UNKNOWN_VALUE, value)
        }
    }

    private fun TrackerSettingsEntity.isValid(): Boolean =
        TrackingLevel.entries.any { it.name == trackingLevel } &&
            Goal.entries.any { it.name == goal } &&
            TrackerMode.entries.any { it.name == mode } &&
            TemperatureUnit.entries.any { it.name == temperatureUnit } &&
            WeightUnit.entries.any { it.name == weightUnit }

    private fun ContraceptionPeriodEntity.toDomainOrNull() =
        runCatching {
            if (ContraceptionMethod.entries.none { it.name == method }) return@runCatching null
            if (regimen != null && Regimen.entries.none { it.name == regimen }) return@runCatching null
            toDomain()
        }.getOrNull()

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

    private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw) }.getOrNull()
}
