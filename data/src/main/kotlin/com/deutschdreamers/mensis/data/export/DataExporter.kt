package com.deutschdreamers.mensis.data.export

import androidx.room.withTransaction
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.db.MensisDatabase
import com.deutschdreamers.mensis.data.db.StoredRows
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.settings.UiPrefs
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import com.deutschdreamers.mensis.domain.model.DayEntry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
}

/** What the export screen's "Included" card shows. */
data class ExportSummary(
    val entries: Int,
    val cycles: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
)

/** Pure helpers around [MensisExport], kept apart from the database so they run in JVM tests. */
object ExportFiles {
    const val ENCRYPTED_EXTENSION: String = "enc"
    const val ENCRYPTED_MIME_TYPE: String = "application/octet-stream"

    /** `mensis-export-2026-10-21.json`, `mensis-export-2026-10-21.csv.enc` */
    fun fileName(
        format: ExportFormat,
        date: LocalDate,
        encrypted: Boolean,
    ): String = "mensis-export-$date.${format.extension}" + if (encrypted) ".$ENCRYPTED_EXTENSION" else ""

    fun mimeType(
        format: ExportFormat,
        encrypted: Boolean,
    ): String = if (encrypted) ENCRYPTED_MIME_TYPE else format.mimeType

    fun snapshot(
        rows: StoredRows,
        uiPrefs: UiPrefs?,
        app: ExportedApp,
        exportedAt: Instant,
        schemaVersion: Int = MensisDatabase.VERSION,
    ): MensisExport =
        MensisExport(
            schemaVersion = schemaVersion,
            exportedAt = exportedAt.toString(),
            app = app,
            settings = rows.settings?.toExported(),
            uiPrefs = uiPrefs?.toExported(),
            contraceptionPeriods =
                rows.contraception.sortedWith(compareBy({ it.startDate }, { it.id })).map { it.toExported() },
            dayEntries = rows.dayEntries.sortedBy { it.date }.map { it.toExported() },
        )

    /** The logged days of an export (tombstones and empty rows left out), as the engine sees them. */
    fun activeEntries(export: MensisExport): List<DayEntry> =
        export.dayEntries
            .filter { it.deletedAt == null }
            .map { it.toEntity().toDomain() }
            .filterNot { it.isEmpty }

    fun summary(export: MensisExport): ExportSummary {
        val entries = activeEntries(export)
        return ExportSummary(
            entries = entries.size,
            cycles = CycleDeriver.derive(entries).size,
            firstDate = entries.minOfOrNull { it.date },
            lastDate = entries.maxOfOrNull { it.date },
        )
    }

    /** The file's bytes; with a [password] the result is an [EncryptedContainer] around the plain file. */
    fun encode(
        export: MensisExport,
        format: ExportFormat,
        password: CharArray? = null,
        params: KdfParams = KdfParams.DEFAULT,
    ): ByteArray {
        val plain =
            when (format) {
                ExportFormat.JSON -> MensisJson.encode(export)
                ExportFormat.CSV -> MensisCsv.encode(activeEntries(export))
            }
        return if (password == null) plain else EncryptedContainer.seal(plain, password, params)
    }
}

/** Reads one consistent snapshot of everything stored, for export and (from build step 6) backup. */
@Singleton
class DataExporter
    @Inject
    constructor(
        private val database: DatabaseHolder,
        private val uiPrefsStore: UiPrefsStore,
        private val clock: Clock,
    ) {
        suspend fun snapshot(app: ExportedApp): MensisExport {
            val db = database.get()
            val rows =
                db.withTransaction {
                    StoredRows(
                        dayEntries = db.dayEntryDao().allIncludingDeleted(),
                        contraception = db.contraceptionDao().allIncludingDeleted(),
                        settings = db.trackerSettingsDao().get(),
                    )
                }
            return ExportFiles.snapshot(rows, uiPrefsStore.current(), app, Instant.now(clock))
        }
    }
