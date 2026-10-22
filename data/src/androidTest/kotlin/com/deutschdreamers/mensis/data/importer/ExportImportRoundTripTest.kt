package com.deutschdreamers.mensis.data.importer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.TestPrefs
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.db.DatabaseFactory
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.export.DataExporter
import com.deutschdreamers.mensis.data.export.ExportFiles
import com.deutschdreamers.mensis.data.export.ExportFormat
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.data.keys.DatabaseKeyProvider
import com.deutschdreamers.mensis.data.repo.ContraceptionRepository
import com.deutschdreamers.mensis.data.repo.DataWipe
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.Symptom
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Export, delete all data, import: the encrypted database ends up with the same rows. Synthetic data only. */
@RunWith(AndroidJUnit4::class)
class ExportImportRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)
    private val app = ExportedApp("test", 1)
    private lateinit var holder: DatabaseHolder
    private lateinit var keyProvider: DatabaseKeyProvider

    @Before
    fun setUp() {
        DatabaseFactory.databaseFiles(context).forEach { it.delete() }
        keyProvider = DatabaseKeyProvider(context)
        holder = DatabaseHolder(context, keyProvider)
    }

    /** Later tests open the default database with whatever key is current; leave no file behind. */
    @After
    fun tearDown() {
        holder.close()
        DatabaseFactory.databaseFiles(context).forEach { it.delete() }
    }

    private suspend fun seed() {
        val days = DayEntryRepository(holder, clock)
        days.save(
            DayEntry(
                LocalDate.of(2026, 9, 1),
                flow = Flow.HEAVY,
                symptoms = setOf(Symptom.CRAMPS),
                notes = "a, \"quoted\"\nnote",
            ),
        )
        days.save(
            DayEntry(LocalDate.of(2026, 9, 2), flow = Flow.MEDIUM, temperatureC = 36.41, moods = setOf(Mood.CALM)),
        )
        days.save(DayEntry(LocalDate.of(2026, 9, 30), flow = Flow.LIGHT, weightKg = 61.3, sleepMinutes = 430))
        days.save(DayEntry(LocalDate.of(2026, 9, 3), flow = Flow.SPOTTING))
        days.delete(LocalDate.of(2026, 9, 3))
        ContraceptionRepository(holder, clock).save(
            ContraceptionPeriod(ContraceptionMethod.COMBINED_PILL, LocalDate.of(2026, 1, 1), regimen = Regimen.CYCLIC),
        )
        SettingsRepository(holder, prefsStore, clock).updateTracker { it.copy(typicalCycleLength = 31) }
    }

    private suspend fun rows() =
        holder.get().let { db ->
            Triple(
                db.dayEntryDao().allIncludingDeleted().map { it.copy(importBatchId = null) },
                db.contraceptionDao().allIncludingDeleted(),
                db.trackerSettingsDao().get(),
            )
        }

    @Test
    fun json_export_then_delete_all_then_import_reproduces_identical_rows() =
        runBlocking {
            seed()
            val before = rows()
            val bytes = ExportFiles.encode(DataExporter(holder, prefsStore, clock).snapshot(app), ExportFormat.JSON)

            DataWipe(context, holder, keyProvider, prefsStore).wipeAll()
            assertEquals(
                0,
                holder
                    .get()
                    .dayEntryDao()
                    .allIncludingDeleted()
                    .size,
            )

            val parsed =
                (
                    ImportReader.read(
                        ImportSource.MENSIS,
                        bytes,
                        LocalDate.of(2026, 10, 21),
                    ) as ReadOutcome.Parsed
                ).parsed
            ImportApplier(RoomImportStore(holder), clock).apply(parsed, ConflictStrategy.MERGE)
            assertEquals(before, rows())
        }

    @Test
    fun encrypted_csv_export_imports_its_days_and_undo_empties_the_database_again() =
        runBlocking {
            seed()
            val activeBefore = DayEntryRepository(holder, clock).all()
            val sealed =
                ExportFiles.encode(
                    DataExporter(holder, prefsStore, clock).snapshot(app),
                    ExportFormat.CSV,
                    "a synthetic passphrase".toCharArray(),
                    KdfParams(memoryKib = 8_192, iterations = 1, parallelism = 1),
                )
            DataWipe(context, holder, keyProvider, prefsStore).wipeAll()

            val plain = ImportReader.decrypt(sealed, "a synthetic passphrase".toCharArray())
            val outcome =
                ImportReader.read(
                    ImportSource.CSV,
                    plain,
                    LocalDate.of(2026, 10, 21),
                ) as ReadOutcome.NeedsMapping
            val parsed = GenericCsvImporter.parse(outcome.table, outcome.guess, LocalDate.of(2026, 10, 21))
            val applier = ImportApplier(RoomImportStore(holder), clock)
            val result = applier.apply(parsed, ConflictStrategy.SKIP, "export.csv.enc")
            assertEquals(3, result.added)
            assertEquals(activeBefore, DayEntryRepository(holder, clock).all())

            assertTrue(applier.undoLast())
            assertEquals(
                0,
                holder
                    .get()
                    .dayEntryDao()
                    .allIncludingDeleted()
                    .size,
            )
            assertFalse(applier.undoLast())
        }

    private val prefsStore: UiPrefsStore get() = TestPrefs.store
}
