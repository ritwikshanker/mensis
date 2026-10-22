package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.Synthetic
import com.deutschdreamers.mensis.data.csv.CsvTable
import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TestResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalTime

class SourceImportersTest {
    /**
     * Builds a drip. export the way drip writes it (`export-to-csv.js`): every schema-4 column, values
     * looked up by dotted path, missing values empty, strings lower-cased and quoted when they contain
     * `.`, `,`, `;`, `'`, a tab or a line break. Synthetic values only.
     */
    private fun dripCsv(vararg days: Map<String, Any>): String {
        fun cell(value: Any?): String =
            when (value) {
                null -> ""
                is String -> {
                    val v = value.replace("\"", "\"\"").lowercase()
                    if (listOf("\n", "\t", ",", ";", ".", "'").any { it in v }) "\"$v\"" else v
                }
                else -> value.toString()
            }
        val rows = days.map { day -> DripCsvImporter.COLUMNS.joinToString(",") { cell(day[it]) } }
        return (listOf(DripCsvImporter.COLUMNS.joinToString(",")) + rows).joinToString("\n")
    }

    @Test
    fun `drip columns match its schema 4 export`() {
        assertEquals(49, DripCsvImporter.COLUMNS.size)
        assertEquals("date", DripCsvImporter.COLUMNS.first())
        assertEquals("mood.note", DripCsvImporter.COLUMNS.last())
    }

    /** Four synthetic drip. days covering every mapped section. */
    private val dripSample =
        dripCsv(
            mapOf(
                "date" to "2026-03-06",
                "bleeding.value" to 2,
                "bleeding.exclude" to false,
                "temperature.value" to 36.55,
                "temperature.exclude" to false,
                "temperature.time" to "06:30",
                "temperature.note" to "Slept badly.",
                "pain.cramps" to true,
                "pain.migraine" to true,
                "pain.ovulationPain" to false,
                "mood.sad" to true,
                "mood.fatigue" to true,
                "mood.balanced" to false,
                "note.value" to "First day, heavy-ish.",
            ),
            mapOf(
                "date" to "2026-03-07",
                "bleeding.value" to 0,
                "bleeding.exclude" to true,
                "mucus.feeling" to 2,
                "mucus.texture" to 2,
                "mucus.value" to 4,
                "mucus.exclude" to false,
                "sex.partner" to true,
                "sex.condom" to true,
                "cervix.opening" to 1,
                "cervix.exclude" to false,
                "desire.value" to 2,
            ),
            mapOf("date" to "2026-03-08", "temperature.value" to 36.9, "temperature.exclude" to true),
            mapOf(
                "date" to "2026-03-09",
                "sex.partner" to true,
                "sex.none" to true,
                "mucus.feeling" to 0,
                "mucus.texture" to 0,
            ),
        )

    private fun dripSampleParsed() = DripCsvImporter.parse(CsvTable.parse(dripSample), Synthetic.TODAY)

    @Test
    fun `drip bleeding, temperature, pain, mood and notes`() {
        assertTrue(DripCsvImporter.looksLikeDrip(CsvTable.parse(dripSample)))
        val first = dripSampleParsed().days[0]
        assertEquals(Flow.MEDIUM, first.flow)
        assertEquals(36.55, first.temperatureC)
        assertEquals(LocalTime.of(6, 30), first.temperatureTime)
        assertEquals(setOf(Symptom.CRAMPS, Symptom.HEADACHE, Symptom.FATIGUE), first.symptoms)
        assertEquals(setOf(Mood.LOW), first.moods)
        assertEquals("first day, heavy-ish.\ntemperature: slept badly.", first.notes)
    }

    @Test
    fun `drip mucus, sex, excluded temperatures and fields Mensis does not track`() {
        val parsed = dripSampleParsed()
        val second = parsed.days[1]
        assertEquals(Flow.SPOTTING, second.flow)
        assertEquals(CervicalMucus.EGG_WHITE, second.cervicalMucus)
        assertEquals(SexType.PROTECTED, second.sex)

        assertEquals(listOf(SkippedRow(4, SkipReason.NO_DATA)), parsed.skipped)
        val fourth = parsed.days[2]
        assertEquals(LocalDate.of(2026, 3, 9), fourth.date)
        assertEquals(SexType.UNPROTECTED, fourth.sex)
        assertEquals(CervicalMucus.DRY, fourth.cervicalMucus)

        val notTracked =
            parsed.issues
                .filter { it.kind == IssueKind.NOT_TRACKED }
                .map { it.field }
                .toSet()
        assertEquals(setOf("cervix.opening", "desire.value", "temperature.exclude"), notTracked)
    }

    @Test
    fun `drip mucus falls back to the NFP value`() {
        val parsed =
            DripCsvImporter.parse(
                CsvTable.parse(dripCsv(mapOf("date" to "2026-03-06", "mucus.value" to 3))),
                Synthetic.TODAY,
            )
        assertEquals(CervicalMucus.CREAMY, parsed.days.single().cervicalMucus)
    }

    @Test
    fun `drip rows in the future or with broken dates are skipped`() {
        val csv =
            dripCsv(
                mapOf("date" to "2026-10-30", "bleeding.value" to 1),
                mapOf(
                    "date" to "someday",
                    "bleeding.value" to 1,
                ),
            )
        val parsed = DripCsvImporter.parse(CsvTable.parse(csv), Synthetic.TODAY)
        assertEquals(listOf(SkipReason.FUTURE_DATE, SkipReason.UNREADABLE_DATE), parsed.skipped.map { it.reason })
    }

    @Test
    fun `a generic CSV is not mistaken for drip`() {
        val table = CsvTable.parse("date,flow\n2026-03-06,light\n")
        assertEquals(false, DripCsvImporter.looksLikeDrip(table))
        assertEquals(
            ImportError.NOT_THIS_FORMAT,
            assertThrows<ImportException> {
                DripCsvImporter.parse(table, Synthetic.TODAY)
            }.error,
        )
    }

    /** Fixture for the assumed Clue structure (experimental, see ClueJsonImporter). */
    private val clueFixture =
        """
        {"data": [
          {"day": "2026-03-06T00:00:00Z", "period": "heavy", "pain": ["cramps", "ovulation"], "emotions": ["sad"],
           "bbt": 36.45, "notes": "synthetic"},
          {"day": "2026-03-07", "period": "spotting", "fluid": "egg_white", "ovulation_test": "pos", "energy": ["exhausted"],
           "sex": "protected", "weight": 61.2},
          {"day": "2026-03-08", "temperature": 97.7},
          {"day": "not a date", "period": "light"}
        ]}
        """.trimIndent()

    @Test
    fun `clue fixture maps the assumed keys and reports the rest`() {
        assertTrue(ClueJsonImporter.looksLikeClue(clueFixture))
        val parsed = ClueJsonImporter.parse(clueFixture, Synthetic.TODAY)
        assertEquals(4, parsed.rowsRead)
        assertEquals(listOf(SkippedRow(4, SkipReason.UNREADABLE_DATE, "not a date")), parsed.skipped)
        val (first, second, third) = parsed.days
        assertEquals(Flow.HEAVY, first.flow)
        assertEquals(setOf(Symptom.CRAMPS), first.symptoms)
        assertEquals(setOf(Mood.LOW), first.moods)
        assertEquals(36.45, first.temperatureC)
        assertEquals("synthetic", first.notes)
        assertEquals(Flow.SPOTTING, second.flow)
        assertEquals(CervicalMucus.EGG_WHITE, second.cervicalMucus)
        assertEquals(TestResult.POSITIVE, second.lhTest)
        assertEquals(setOf(Symptom.FATIGUE), second.symptoms)
        assertEquals(SexType.PROTECTED, second.sex)
        assertEquals(36.5, third.temperatureC)
        assertEquals(setOf("pain", "weight"), parsed.issues.map { it.field }.toSet())
    }

    @Test
    fun `clue accepts a bare array and rejects JSON without days`() {
        assertEquals(
            1,
            ClueJsonImporter.parse("[{\"date\": \"2026-03-06\", \"period\": \"light\"}]", Synthetic.TODAY).days.size,
        )
        assertEquals(
            ImportError.NOT_THIS_FORMAT,
            assertThrows<ImportException> {
                ClueJsonImporter.parse("{\"x\": 1}", Synthetic.TODAY)
            }.error,
        )
        assertEquals(
            ImportError.DAMAGED,
            assertThrows<ImportException> {
                ClueJsonImporter.parse("{\"data\": [", Synthetic.TODAY)
            }.error,
        )
    }
}
