package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.Synthetic
import com.deutschdreamers.mensis.data.db.StoredRows
import com.deutschdreamers.mensis.data.export.ExportFiles
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.data.export.MensisJson
import com.deutschdreamers.mensis.domain.model.Flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReplaceAllTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneOffset.UTC)
    private val store = InMemoryImportStore()
    private val applier = ImportApplier(store, clock)

    private val backupRows =
        StoredRows(
            dayEntries =
                listOf(
                    Synthetic.day("2026-09-01", Flow.HEAVY, id = "b-1"),
                    Synthetic.day("2026-09-02", Flow.MEDIUM, id = "b-2"),
                    Synthetic.day("2026-09-29", Flow.LIGHT, id = "b-3"),
                    Synthetic.day("2026-09-30", Flow.MEDIUM, id = "b-4", deletedAt = "2026-10-01T08:00:00Z"),
                ),
            contraception = listOf(Synthetic.period("p-1", "COPPER_IUD", "2025-01-10")),
            settings = Synthetic.settings(),
        )

    private fun parsedBackup(): ParsedImport {
        val export = ExportFiles.snapshot(backupRows, null, ExportedApp("t", 1), Instant.parse(Synthetic.NOW))
        return MensisJsonImporter.parse(String(MensisJson.encode(export)))
    }

    @Test
    fun `restore replaces every row with the backup's rows as stored`() =
        runBlocking {
            store.put(Synthetic.day("2026-09-01", Flow.LIGHT, id = "local-1"))
            store.put(Synthetic.day("2026-10-15", Flow.HEAVY, id = "local-2"))
            store.upsertContraceptionPeriods(listOf(Synthetic.period("local-p", "COMBINED_PILL", "2026-01-01")))

            val result = applier.replaceAll(parsedBackup())

            assertEquals(backupRows.dayEntries.sortedBy { it.id }, store.days.values.sortedBy { it.id })
            assertEquals(backupRows.contraception, store.periods.values.toList())
            assertEquals(backupRows.settings, store.settings)
            assertEquals(3, result.entries)
            assertEquals(2, result.cycles)
            assertEquals(LocalDate.of(2026, 9, 1), result.firstDate)
            assertEquals(LocalDate.of(2026, 9, 29), result.lastDate)
            assertTrue(result.settingsRestored)
        }

    @Test
    fun `restore drops the undo of an earlier import`() =
        runBlocking {
            applier.apply(
                ParsedImport(
                    ImportSource.CSV,
                    1,
                    listOf(
                        com.deutschdreamers.mensis.domain.model
                            .DayEntry(LocalDate.of(2026, 10, 2), Flow.LIGHT),
                    ),
                    emptyList(),
                    emptyList(),
                ),
                ConflictStrategy.MERGE,
            )
            applier.replaceAll(parsedBackup())
            assertNull(applier.lastImport())
        }

    @Test
    fun `restoring the same backup twice gives the same rows`() =
        runBlocking {
            applier.replaceAll(parsedBackup())
            val once = store.state()
            applier.replaceAll(parsedBackup())
            assertEquals(once, store.state())
        }

    @Test
    fun `only a Mensis export can be restored`() {
        val csv = ParsedImport(ImportSource.CSV, 0, emptyList(), emptyList(), emptyList())
        assertThrows<IllegalArgumentException> { runBlocking { applier.replaceAll(csv) } }
    }
}
