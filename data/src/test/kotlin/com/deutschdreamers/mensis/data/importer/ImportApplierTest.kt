package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.Synthetic
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.StoredRows
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.export.ExportFiles
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.data.export.MensisJson
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.Symptom
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ImportApplierTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneOffset.UTC)
    private val store = InMemoryImportStore()
    private val applier = ImportApplier(store, clock)

    private fun parsed(
        vararg days: DayEntry,
        source: ImportSource = ImportSource.CSV,
    ) = ParsedImport(source, days.size, days.sortedBy { it.date }, emptyList(), emptyList())

    private fun mensisFile(
        rows: List<DayEntryEntity>,
        periods: List<com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity> = emptyList(),
        settings: com.deutschdreamers.mensis.data.db.TrackerSettingsEntity? = null,
    ): ParsedImport {
        val export =
            ExportFiles.snapshot(
                StoredRows(rows, periods, settings),
                null,
                ExportedApp("t", 1),
                Instant.parse(Synthetic.NOW),
            )
        return MensisJsonImporter.parse(String(MensisJson.encode(export)))
    }

    private val local =
        Synthetic.day("2026-03-06", Flow.LIGHT, id = "local-1", createdAt = "2026-03-06T07:00:00Z") {
            copy(symptoms = setOf(Symptom.CRAMPS))
        }
    private val fileSameDay =
        DayEntry(LocalDate.of(2026, 3, 6), flow = Flow.HEAVY, moods = setOf(Mood.CALM), notes = "file")
    private val fileNewDay = DayEntry(LocalDate.of(2026, 3, 7), flow = Flow.MEDIUM)

    @Test
    fun `merge keeps what was logged and fills only empty fields`() =
        runBlocking {
            store.put(local)
            val result = applier.apply(parsed(fileSameDay, fileNewDay), ConflictStrategy.MERGE)
            val merged = store.days.getValue("local-1")
            assertEquals(
                DayEntry(
                    LocalDate.of(2026, 3, 6),
                    flow = Flow.LIGHT,
                    symptoms = setOf(Symptom.CRAMPS),
                    moods = setOf(Mood.CALM),
                    notes = "file",
                ),
                merged.toDomain(),
            )
            assertEquals("2026-03-06T07:00:00Z", merged.createdAt)
            assertEquals(Triple(1, 1, 0), Triple(result.added, result.updated, result.kept))
        }

    @Test
    fun `replace puts the file's entry in place of the phone's, under the same row id`() =
        runBlocking {
            store.put(local)
            val result = applier.apply(parsed(fileSameDay, fileNewDay), ConflictStrategy.REPLACE)
            assertEquals(fileSameDay, store.days.getValue("local-1").toDomain())
            assertEquals(2, result.entries)
        }

    @Test
    fun `skip leaves days with data untouched and adds the rest`() =
        runBlocking {
            store.put(local)
            val result = applier.apply(parsed(fileSameDay, fileNewDay), ConflictStrategy.SKIP)
            assertEquals(local, store.days.getValue("local-1"))
            assertEquals(Triple(1, 0, 1), Triple(result.added, result.updated, result.kept))
            assertEquals(
                fileNewDay,
                store.days.values
                    .single { it.date == "2026-03-07" }
                    .toDomain(),
            )
        }

    @Test
    fun `a soft-deleted day is revived under its own id`() =
        runBlocking {
            val tombstone = Synthetic.day("2026-03-07", id = "gone", deletedAt = "2026-03-08T00:00:00Z")
            store.put(tombstone)
            applier.apply(parsed(fileNewDay), ConflictStrategy.SKIP)
            val revived = store.days.getValue("gone")
            assertNull(revived.deletedAt)
            assertEquals(fileNewDay, revived.toDomain())
        }

    @Test
    fun `preview counts the days that already hold data`() =
        runBlocking {
            store.put(local, Synthetic.day("2026-03-07", deletedAt = Synthetic.NOW))
            assertEquals(1, applier.preview(parsed(fileSameDay, fileNewDay)).conflicts)
        }

    @Test
    fun `importing the same file twice changes nothing, and undo restores the phone exactly`() {
        runBlocking {
            checkAll(
                120,
                Synthetic.historyArb,
                Synthetic.historyArb,
                Arb.enum<ConflictStrategy>(),
            ) { phone, file, strategy ->
                store.days.clear()
                store.clearBatches()
                store.put(*phone.toTypedArray())
                val before = store.state()
                val parsedFile = mensisFile(file)

                applier.apply(parsedFile, strategy)
                val once = store.state()
                val again = applier.apply(parsedFile, strategy)
                assertEquals(once, store.state(), "second import of the same file must not write")
                assertFalse(again.undoable)
                assertEquals(0, again.entries)

                applier.undoLast()
                assertEquals(before, store.state())
                assertFalse(applier.undoLast())
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ConflictStrategy::class)
    fun `a Mensis export onto an empty phone reproduces its rows, method history and settings`(
        strategy: ConflictStrategy,
    ) = runBlocking {
        val rows =
            listOf(
                Synthetic.day("2026-03-06"),
                Synthetic.day("2026-03-07", deletedAt = Synthetic.NOW),
                Synthetic.day("2026-04-02"),
            )
        val periods = listOf(Synthetic.period("p1", "COMBINED_PILL", "2025-01-01", regimen = "CYCLIC"))
        val result = applier.apply(mensisFile(rows, periods, Synthetic.settings(cycleLength = 31)), strategy)
        assertEquals(
            rows,
            store.days.values
                .map { it.copy(importBatchId = null) }
                .sortedBy { it.date },
        )
        assertEquals(periods, store.periods.values.toList())
        assertEquals(Synthetic.settings(cycleLength = 31), store.settings)
        assertEquals(ExtrasOutcome.TAKEN_FROM_FILE, result.history)
        assertEquals(ExtrasOutcome.TAKEN_FROM_FILE, result.settings)
        assertEquals(2, result.cycles)
    }

    @Test
    fun `method history and settings on the phone are kept unless the strategy is replace`() =
        runBlocking {
            val mine = Synthetic.period("mine", "PATCH", "2026-01-01")
            store.periods["mine"] = mine
            store.settings = Synthetic.settings(cycleLength = 27)
            val file =
                mensisFile(
                    listOf(Synthetic.day("2026-03-06")),
                    listOf(Synthetic.period("theirs", "RING", "2025-01-01")),
                    Synthetic.settings(31),
                )

            val merged = applier.apply(file, ConflictStrategy.MERGE)
            assertEquals(ExtrasOutcome.KEPT, merged.history)
            assertEquals(ExtrasOutcome.KEPT, merged.settings)
            assertEquals(listOf(mine), store.periods.values.toList())
            applier.undoLast()

            val replaced = applier.apply(file, ConflictStrategy.REPLACE)
            assertEquals(ExtrasOutcome.TAKEN_FROM_FILE, replaced.history)
            assertEquals("2026-10-21T15:00:00Z", store.periods.getValue("mine").deletedAt)
            assertEquals(null, store.periods.getValue("theirs").deletedAt)
            assertEquals(31, store.settings?.typicalCycleLength)
            assertEquals("2026-01-01T08:00:00Z", store.settings?.createdAt)

            applier.undoLast()
            assertEquals(listOf(mine), store.periods.values.toList())
            assertEquals(27, store.settings?.typicalCycleLength)
        }

    @Test
    fun `an import never deletes, so tombstones in the file leave the phone day alone`() =
        runBlocking {
            store.put(local)
            applier.apply(
                mensisFile(listOf(Synthetic.day("2026-03-06", id = "other", deletedAt = Synthetic.NOW))),
                ConflictStrategy.REPLACE,
            )
            assertEquals(listOf(local), store.days.values.toList())
        }

    @Test
    fun `only the latest import can be undone`() =
        runBlocking {
            applier.apply(parsed(fileNewDay), ConflictStrategy.MERGE)
            val afterFirst = store.state()
            applier.apply(parsed(fileSameDay), ConflictStrategy.MERGE)
            assertTrue(applier.undoLast())
            assertEquals(afterFirst, store.state())
            assertFalse(applier.undoLast())
        }

    @Test
    fun `the latest import is described for the undo row`() =
        runBlocking {
            assertNull(applier.lastImport())
            applier.apply(
                parsed(fileNewDay, fileSameDay, source = ImportSource.DRIP),
                ConflictStrategy.MERGE,
                fileName = "drip.csv",
            )
            assertEquals(
                LastImport(ImportSource.DRIP, Instant.parse("2026-10-21T15:00:00Z"), "drip.csv", 2),
                applier.lastImport(),
            )
        }

    @Test
    fun `nothing to write means no batch, so an earlier import stays undoable`() =
        runBlocking {
            applier.apply(parsed(fileNewDay), ConflictStrategy.MERGE)
            val batch = store.latestBatch()
            val result = applier.apply(parsed(fileNewDay), ConflictStrategy.SKIP)
            assertFalse(result.undoable)
            assertEquals(1, result.kept)
            assertEquals(batch, store.latestBatch())
        }
}
