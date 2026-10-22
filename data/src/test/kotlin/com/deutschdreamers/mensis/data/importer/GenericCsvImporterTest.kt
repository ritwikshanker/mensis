package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.Synthetic
import com.deutschdreamers.mensis.data.csv.CsvTable
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.Symptom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class GenericCsvImporterTest {
    /** The design's sample file (§14 step 2), synthetic values. */
    private val designSample =
        """
        date,period_flow,temp_c,mood,notes,device_id
        2026-03-06,medium,36.42,calm,headache am,a81f
        2026-03-07,heavy,36.38,irritable,,a81f
        2026-03-08,medium,36.45,calm,,a81f
        2026-03-09,light,,low,,a81f
        2026-13-40,light,,,,a81f
        """.trimIndent()

    @Test
    fun `guesses the design sample's columns from headers`() {
        val mapping = GenericCsvImporter.guess(CsvTable.parse(designSample))
        assertEquals(
            listOf(CsvField.DATE, CsvField.FLOW, CsvField.TEMPERATURE_C, CsvField.MOODS, CsvField.NOTES, CsvField.SKIP),
            mapping.fields,
        )
    }

    @Test
    fun `columns show header and first non-empty sample`() {
        val columns = GenericCsvImporter.columns(CsvTable.parse(designSample))
        assertEquals(CsvColumn("notes", "headache am"), columns[4])
        assertEquals(CsvColumn("device_id", "a81f"), columns[5])
    }

    @Test
    fun `parses rows, skips unreadable dates and reports counts`() {
        val table = CsvTable.parse(designSample)
        val parsed = GenericCsvImporter.parse(table, GenericCsvImporter.guess(table), Synthetic.TODAY)
        assertEquals(5, parsed.rowsRead)
        assertEquals(4, parsed.validRows)
        assertEquals(listOf(SkippedRow(6, SkipReason.UNREADABLE_DATE, "2026-13-40")), parsed.skipped)
        val first = parsed.days.first()
        assertEquals(LocalDate.of(2026, 3, 6), first.date)
        assertEquals(Flow.MEDIUM, first.flow)
        assertEquals(36.42, first.temperatureC)
        assertEquals(setOf(Mood.CALM), first.moods)
        assertEquals("headache am", first.notes)
        assertEquals(setOf(Mood.LOW), parsed.days.last().moods)
    }

    @Test
    fun `guesses unnamed columns from their values, Fahrenheit included`() {
        val table =
            CsvTable.parse(
                """
                Tag;Wert;Blut;Gefühl
                06.03.2026;97,9;heavy;sad, anxious
                07.03.2026;97,7;medium;calm
                """.trimIndent(),
            )
        val mapping = GenericCsvImporter.guess(table)
        assertEquals(listOf(CsvField.DATE, CsvField.TEMPERATURE_F, CsvField.FLOW, CsvField.MOODS), mapping.fields)
        val day = GenericCsvImporter.parse(table, mapping, Synthetic.TODAY).days.first()
        assertEquals(36.61, day.temperatureC)
        assertEquals(setOf(Mood.LOW, Mood.ANXIOUS), day.moods)
    }

    @Test
    fun `a temperature header with Fahrenheit values is read as Fahrenheit`() {
        val table = CsvTable.parse("date,temperature\n2026-03-06,97.9\n2026-03-07,98.1\n")
        assertEquals(CsvField.TEMPERATURE_F, GenericCsvImporter.guess(table).fields[1])
    }

    @Test
    fun `only the first date-like column becomes the date`() {
        val table = CsvTable.parse("date,logged_date,flow\n2026-03-06,2026-03-07,light\n")
        assertEquals(listOf(CsvField.DATE, CsvField.SKIP, CsvField.FLOW), GenericCsvImporter.guess(table).fields)
    }

    @Test
    fun `slashed dates that fit both orders ask the user, a deciding sample does not`() {
        val ambiguous = CsvTable.parse("date,flow\n03/04/2026,light\n05/04/2026,medium\n")
        val guessed = GenericCsvImporter.guess(ambiguous)
        assertTrue(GenericCsvImporter.dateOrderAmbiguous(ambiguous, guessed))
        val monthFirst =
            GenericCsvImporter.parse(
                ambiguous,
                guessed.copy(dateOrder = DateOrder.MONTH_FIRST),
                Synthetic.TODAY,
            )
        assertEquals(LocalDate.of(2026, 3, 4), monthFirst.days.first().date)
        val dayFirst = GenericCsvImporter.parse(ambiguous, guessed, Synthetic.TODAY)
        assertEquals(LocalDate.of(2026, 4, 3), dayFirst.days.first().date)

        val decided = CsvTable.parse("date,flow\n03/04/2026,light\n25/04/2026,medium\n")
        assertFalse(GenericCsvImporter.dateOrderAmbiguous(decided, GenericCsvImporter.guess(decided)))
        assertEquals(DateOrder.DAY_FIRST, GenericCsvImporter.guess(decided).dateOrder)
    }

    @Test
    fun `future dates, empty rows and unknown values`() {
        val table =
            CsvTable.parse(
                """
                date,flow,symptoms
                2026-10-20,light,cramps;hiccups
                2026-10-22,light,
                2026-10-19,,
                2026-10-18,gushing,
                """.trimIndent(),
            )
        val parsed = GenericCsvImporter.parse(table, GenericCsvImporter.guess(table), Synthetic.TODAY)
        assertEquals(1, parsed.days.size)
        assertEquals(setOf(Symptom.CRAMPS), parsed.days.single().symptoms)
        assertEquals(
            listOf(SkipReason.FUTURE_DATE, SkipReason.NO_DATA, SkipReason.NO_DATA),
            parsed.skipped.map { it.reason },
        )
        assertEquals(
            listOf(
                ImportIssue("symptoms", IssueKind.UNKNOWN_VALUE, 1, "hiccups"),
                ImportIssue("flow", IssueKind.UNKNOWN_VALUE, 1, "gushing"),
            ),
            parsed.issues,
        )
    }

    @Test
    fun `rows sharing a date are combined into one entry`() {
        val table =
            CsvTable.parse(
                "date,flow,symptoms,notes\n2026-03-06,light,cramps,am\n2026-03-06,heavy,headache,pm\n",
            )
        val parsed = GenericCsvImporter.parse(table, GenericCsvImporter.guess(table), Synthetic.TODAY)
        val day = parsed.days.single()
        assertEquals(Flow.LIGHT, day.flow)
        assertEquals(setOf(Symptom.CRAMPS, Symptom.HEADACHE), day.symptoms)
        assertEquals("am\npm", day.notes)
        assertEquals(2, parsed.validRows)
    }

    @Test
    fun `a mapping without a date column is refused`() {
        val table = CsvTable.parse("flow\nlight\n")
        val mapping = GenericCsvImporter.guess(table)
        assertFalse(mapping.hasDate)
        assertEquals(
            ImportError.NO_DATE_COLUMN,
            assertThrows<ImportException> {
                GenericCsvImporter.parse(table, mapping, Synthetic.TODAY)
            }.error,
        )
    }

    @Test
    fun `remapping a column changes what is read`() {
        val table = CsvTable.parse(designSample)
        val mapping = GenericCsvImporter.guess(table).with(4, CsvField.SKIP).with(3, CsvField.SYMPTOMS)
        val parsed = GenericCsvImporter.parse(table, mapping, Synthetic.TODAY)
        assertEquals(null, parsed.days.first().notes)
        assertTrue(
            parsed.days
                .first()
                .moods
                .isEmpty(),
        )
        assertTrue(parsed.issues.any { it.field == "mood" && it.kind == IssueKind.UNKNOWN_VALUE })
    }
}
