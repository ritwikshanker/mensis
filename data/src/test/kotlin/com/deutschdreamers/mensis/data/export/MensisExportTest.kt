package com.deutschdreamers.mensis.data.export

import com.deutschdreamers.mensis.data.Synthetic
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.csv.CsvTable
import com.deutschdreamers.mensis.data.db.StoredRows
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.importer.GenericCsvImporter
import com.deutschdreamers.mensis.data.importer.ImportError
import com.deutschdreamers.mensis.data.importer.ImportException
import com.deutschdreamers.mensis.data.importer.ImportReader
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.data.importer.MensisJsonImporter
import com.deutschdreamers.mensis.data.importer.ReadOutcome
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.data.settings.UiPrefs
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
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.reminders.ReminderOption
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate

class MensisExportTest {
    private val app = ExportedApp(versionName = "0.1.0-test", versionCode = 1)
    private val exportedAt = Instant.parse(Synthetic.NOW)

    private fun snapshot(
        rows: List<com.deutschdreamers.mensis.data.db.DayEntryEntity>,
        withExtras: Boolean = true,
    ) = ExportFiles.snapshot(
        rows =
            StoredRows(
                dayEntries = rows,
                contraception =
                    if (withExtras) {
                        listOf(
                            Synthetic.period("p2", "COMBINED_PILL", "2025-06-01", regimen = "CYCLIC"),
                            Synthetic.period("p1", "NONE", "2024-01-01", end = "2025-05-31"),
                        )
                    } else {
                        emptyList()
                    },
                settings = if (withExtras) Synthetic.settings() else null,
            ),
        uiPrefs = UiPrefs(),
        app = app,
        exportedAt = exportedAt,
    )

    @Test
    fun `JSON export then import reproduces every row, tombstones and extras included`() {
        runBlocking {
            checkAll(150, Synthetic.historyArb) { rows ->
                val export = snapshot(rows)
                val parsed = MensisJsonImporter.parse(String(MensisJson.encode(export)))
                val lossless = requireNotNull(parsed.lossless)
                val back = (lossless.dayEntries.values + lossless.tombstones).sortedBy { it.date }
                assertEquals(rows.sortedBy { it.date }, back)
                assertEquals(rows.filter { it.deletedAt == null }.map { it.toDomain() }, parsed.days)
                assertEquals(export.contraceptionPeriods.map { it.toEntity() }, lossless.contraception)
                assertEquals(Synthetic.settings(), lossless.settings)
                assertTrue(parsed.skipped.isEmpty() && parsed.issues.isEmpty())
            }
        }
    }

    @Test
    fun `CSV export then generic import with the guessed mapping reproduces every logged day`() {
        runBlocking {
            checkAll(150, Synthetic.historyArb) { rows ->
                val active = rows.filter { it.deletedAt == null }.map { it.toDomain() }
                val bytes = MensisCsv.encode(active)
                val outcome = ImportReader.read(ImportSource.CSV, bytes, Synthetic.TODAY) as ReadOutcome.NeedsMapping
                assertEquals(MensisCsv.COLUMNS, outcome.guess.fields)
                val parsed = GenericCsvImporter.parse(outcome.table, outcome.guess, Synthetic.TODAY)
                assertEquals(active, parsed.days)
                assertTrue(parsed.skipped.isEmpty() && parsed.issues.isEmpty(), "${parsed.skipped} ${parsed.issues}")
            }
        }
    }

    @Test
    fun `the same data always encodes to the same bytes, properties in a fixed order`() {
        val rows = listOf(Synthetic.day("2026-03-07"), Synthetic.day("2026-03-06", deletedAt = Synthetic.NOW))
        val a = MensisJson.encode(snapshot(rows))
        assertArrayEquals(a, MensisJson.encode(snapshot(rows.reversed())))
        val text = String(a)
        val keys =
            listOf(
                "\"format\"",
                "\"formatVersion\"",
                "\"schemaVersion\"",
                "\"exportedAt\"",
                "\"app\"",
                "\"settings\"",
                "\"uiPrefs\"",
            )
        assertEquals(keys, keys.sortedBy { text.indexOf(it) })
        assertTrue(text.indexOf("2026-03-06") < text.indexOf("2026-03-07"))
        assertTrue("\"flow\": \"medium\"" in text)
        assertTrue("null" !in text)
    }

    @Test
    fun `CSV export has a BOM, the documented header and lower-case values`() {
        val text =
            String(
                MensisCsv.encode(
                    listOf(
                        Synthetic.day("2026-03-06") { copy(symptoms = setOf(Symptom.TENDER_BREASTS)) }.toDomain(),
                    ),
                ),
            )
        val header = "date,flow,symptoms,moods,cervical_mucus,temperature_c,temperature_time,lh_test,"
        assertTrue(text.startsWith("\uFEFF$header"))
        assertTrue("2026-03-06,medium,tender_breasts," in text)
    }

    @Test
    fun `summary counts logged days, cycles and the date range`() {
        val rows =
            listOf("2026-01-05", "2026-01-06", "2026-02-02", "2026-03-01").map { Synthetic.day(it) } +
                Synthetic.day("2026-03-20", deletedAt = Synthetic.NOW)
        val summary = ExportFiles.summary(snapshot(rows))
        assertEquals(4, summary.entries)
        assertEquals(3, summary.cycles)
        assertEquals(LocalDate.of(2026, 1, 5), summary.firstDate)
        assertEquals(LocalDate.of(2026, 3, 1), summary.lastDate)
    }

    @Test
    fun `file names follow the design`() {
        val date = LocalDate.of(2026, 10, 21)
        assertEquals("mensis-export-2026-10-21.json", ExportFiles.fileName(ExportFormat.JSON, date, encrypted = false))
        assertEquals("mensis-export-2026-10-21.csv.enc", ExportFiles.fileName(ExportFormat.CSV, date, encrypted = true))
        assertEquals("application/octet-stream", ExportFiles.mimeType(ExportFormat.JSON, encrypted = true))
    }

    @Test
    fun `encrypted export opens with the password and reads like the plain file`() {
        val fast = KdfParams(1_024, 1, 1)
        val export = snapshot(listOf(Synthetic.day("2026-03-06")))
        val sealed = ExportFiles.encode(export, ExportFormat.JSON, "a long passphrase".toCharArray(), fast)
        assertTrue(EncryptedContainer.isContainer(sealed))
        assertArrayEquals(MensisJson.encode(export), ImportReader.decrypt(sealed, "a long passphrase".toCharArray()))
        val wrong = assertThrows<ImportException> { ImportReader.decrypt(sealed, "a long passphrasE".toCharArray()) }
        assertEquals(ImportError.WRONG_PASSWORD, wrong.error)
    }

    @Test
    fun `older format versions are upgraded through the migration chain`() {
        // A test-only format 2 that renamed "dayEntries" to "days": a version-1 file must still read.
        val v2 =
            ExportMigrations(
                currentVersion = 2,
                steps =
                    listOf(
                        ExportMigration(from = 1) { doc ->
                            JsonObject(doc.filterKeys { it != "dayEntries" } + ("days" to doc.getValue("dayEntries")))
                        },
                    ),
            )
        val v1Text = String(MensisJson.encode(snapshot(listOf(Synthetic.day("2026-03-06")))))
        val upgraded = v2.upgrade(Json.parseToJsonElement(v1Text) as JsonObject)
        assertEquals(JsonPrimitive(2), upgraded["formatVersion"])
        assertTrue("days" in upgraded && "dayEntries" !in upgraded)
    }

    @Test
    fun `newer, foreign and broken JSON are told apart`() {
        val text = String(MensisJson.encode(snapshot(listOf(Synthetic.day("2026-03-06")))))
        val newer = text.replace("\"formatVersion\": 1", "\"formatVersion\": 9")
        assertEquals(ImportError.NEWER_VERSION, assertThrows<ImportException> { MensisJsonImporter.parse(newer) }.error)
        val missingStep = ExportMigrations(currentVersion = 2)
        assertEquals(
            ImportError.DAMAGED,
            assertThrows<ImportException> { MensisJsonImporter.parse(text, missingStep) }.error,
        )
        assertEquals(
            ImportError.NOT_THIS_FORMAT,
            assertThrows<ImportException> {
                MensisJson.decode("{\"format\":\"other\"}")
            }.error,
        )
        assertEquals(ImportError.DAMAGED, assertThrows<ImportException> { MensisJson.decode(text.dropLast(30)) }.error)
        assertEquals(
            ImportError.DAMAGED,
            assertThrows<ImportException> {
                MensisJson.decode(text.replace("\"app\"", "\"ap\""))
            }.error,
        )
        assertEquals(ImportError.NOT_THIS_FORMAT, assertThrows<ImportException> { MensisJson.decode("[1,2]") }.error)
        assertEquals(CsvTable(emptyList(), emptyList()), CsvTable.parse(""))
    }

    @Test
    fun `unknown values from a newer app are dropped and reported, the rest of the day kept`() {
        val text =
            String(
                MensisJson.encode(
                    snapshot(listOf(Synthetic.day("2026-03-06") { copy(symptoms = setOf(Symptom.ACNE)) })),
                ),
            ).replace("\"acne\"", "\"hiccups\"")
                .replace("\"flow\": \"medium\"", "\"flow\": \"gushing\", \"notes\": \"kept\"")
        val parsed = MensisJsonImporter.parse(text)
        val day = parsed.days.single()
        assertEquals(null, day.flow)
        assertEquals("kept", day.notes)
        assertEquals(setOf("flow", "symptoms"), parsed.issues.map { it.field }.toSet())
    }

    @Test
    fun `the published JSON Schema lists exactly the values the app writes`() {
        val schema = Json.parseToJsonElement(java.io.File("../docs/mensis-export.schema.json").readText()).jsonObject
        val defs = schema.getValue("\$defs").jsonObject

        fun enumOf(vararg path: String): Set<String> {
            var node: JsonElement = defs
            path.forEach { node = node.jsonObject.getValue(it) }
            return node.jsonObject
                .getValue("enum")
                .jsonArray
                .map { it.jsonPrimitive.content }
                .toSet()
        }

        fun <E : Enum<E>> names(values: List<E>) = values.map { it.name.lowercase() }.toSet()
        val day = arrayOf("dayEntry", "properties")
        assertEquals(names(Flow.entries), enumOf(*day, "flow"))
        assertEquals(names(Symptom.entries), enumOf(*day, "symptoms", "items"))
        assertEquals(names(Mood.entries), enumOf(*day, "moods", "items"))
        assertEquals(names(CervicalMucus.entries), enumOf(*day, "cervicalMucus"))
        assertEquals(names(TestResult.entries), enumOf(*day, "lhTest"))
        assertEquals(names(SexType.entries), enumOf(*day, "sex"))
        assertEquals(names(PillState.entries), enumOf(*day, "pillState"))
        assertEquals(names(RingState.entries), enumOf(*day, "ringState"))
        assertEquals(names(ContraceptionMethod.entries), enumOf("contraceptionPeriod", "properties", "method"))
        assertEquals(names(Regimen.entries), enumOf("contraceptionPeriod", "properties", "regimen"))
        assertEquals(names(TrackingLevel.entries), enumOf("settings", "properties", "trackingLevel"))
        assertEquals(names(Goal.entries), enumOf("settings", "properties", "goal"))
        assertEquals(names(ThemeScheme.entries), enumOf("uiPrefs", "properties", "theme"))
        assertEquals(names(ReminderOption.entries), enumOf("reminder", "properties", "option"))
        val reminderKeys =
            defs
                .getValue("settings")
                .jsonObject
                .getValue("properties")
                .jsonObject
                .getValue("reminders")
                .jsonObject
                .getValue("properties")
                .jsonObject.keys
        assertEquals(names(ReminderType.entries) + setOf("discreetTitle", "discreetBody"), reminderKeys)
        assertEquals(
            MensisExport.FORMAT_VERSION,
            schema
                .getValue(
                    "properties",
                ).jsonObject
                .getValue("formatVersion")
                .jsonObject
                .getValue("const")
                .jsonPrimitive.content
                .toInt(),
        )
        val descriptor = ExportedDayEntry.serializer().descriptor
        val documented = (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()
        assertEquals(
            documented,
            defs
                .getValue("dayEntry")
                .jsonObject
                .getValue("properties")
                .jsonObject.keys,
        )
    }
}
