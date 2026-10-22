package com.deutschdreamers.mensis.ui.screens.importer

import com.deutschdreamers.mensis.data.importer.CsvField
import com.deutschdreamers.mensis.data.importer.DateOrder
import com.deutschdreamers.mensis.data.importer.ExtrasOutcome
import com.deutschdreamers.mensis.data.importer.ImportIssue
import com.deutschdreamers.mensis.data.importer.ImportResult
import com.deutschdreamers.mensis.data.importer.ImportValues
import com.deutschdreamers.mensis.data.importer.IssueKind
import com.deutschdreamers.mensis.data.importer.LastImport
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.SkipReason
import com.deutschdreamers.mensis.data.importer.SkippedRow
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The import screen's computed lines, as plain Kotlin so they are unit-tested. */
object ImportTexts {
    private val dayMonthTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)

    private fun count(
        n: Int,
        one: String,
        many: String = "${one}s",
    ): String = if (n == 1) "1 $one" else "$n $many"

    /** "cycle_export.csv · 6 columns · 243 rows" */
    fun mapSubtitle(
        fileName: String,
        columns: Int,
        rows: Int,
    ): String = "$fileName $MIDDLE_DOT ${count(columns, "column")} $MIDDLE_DOT ${count(rows, "row")}"

    /** "243 rows read · 240 valid · 3 skipped (unreadable dates)" */
    fun previewCounts(parsed: ParsedImport): String {
        val base = "${count(parsed.rowsRead, "row")} read $MIDDLE_DOT ${parsed.validRows} valid"
        if (parsed.skipped.isEmpty()) return base
        val reasons = parsed.skipped.groupingBy { it.reason }.eachCount()
        val why =
            if (reasons.size == 1) {
                val (reason, n) = reasons.entries.single()
                reasonNoun(reason, n)
            } else {
                reasons.entries.joinToString(", ") { (reason, n) -> "$n ${reasonNoun(reason, n)}" }
            }
        return "$base $MIDDLE_DOT ${parsed.skipped.size} skipped ($why)"
    }

    private fun reasonNoun(
        reason: SkipReason,
        n: Int,
    ): String =
        when (reason) {
            SkipReason.UNREADABLE_DATE -> if (n == 1) "unreadable date" else "unreadable dates"
            SkipReason.FUTURE_DATE -> if (n == 1) "date in the future" else "dates in the future"
            SkipReason.NO_DATA -> if (n == 1) "empty row" else "empty rows"
        }

    /** "Not imported: cervix.opening (12), desire.value (3). Unreadable values: mood (2)." or null. */
    fun issues(issues: List<ImportIssue>): String? {
        fun list(kind: IssueKind) = issues.filter { it.kind == kind }.joinToString(", ") { "${it.field} (${it.count})" }
        val notTracked = list(IssueKind.NOT_TRACKED)
        val unknown = list(IssueKind.UNKNOWN_VALUE)
        val parts =
            listOfNotNull(
                notTracked.ifEmpty { null }?.let { "Not imported: $it." },
                unknown.ifEmpty { null }?.let { "Unreadable values: $it." },
            )
        return parts.joinToString(" ").ifEmpty { null }
    }

    /** "12 days in the file already have data on this phone." */
    fun conflicts(n: Int): String? =
        when (n) {
            0 -> null
            1 -> "1 day in the file already has data on this phone."
            else -> "$n days in the file already have data on this phone."
        }

    /** What happens to a Mensis export's method history and settings; null for other sources. */
    fun extras(
        history: ExtrasOutcome,
        settings: ExtrasOutcome,
    ): String? {
        val fromFile = mutableListOf<String>()
        val kept = mutableListOf<String>()
        if (history ==
            ExtrasOutcome.TAKEN_FROM_FILE
        ) {
            fromFile += "method history"
        } else if (history ==
            ExtrasOutcome.KEPT
        ) {
            kept += "method history"
        }
        if (settings ==
            ExtrasOutcome.TAKEN_FROM_FILE
        ) {
            fromFile += "settings"
        } else if (settings ==
            ExtrasOutcome.KEPT
        ) {
            kept += "settings"
        }
        val parts =
            listOfNotNull(
                fromFile.ifEmpty { null }?.let {
                    val verb = if (it == listOf("method history")) "comes" else "come"
                    "${it.joinToString(" and ").replaceFirstChar(Char::uppercase)} $verb from the file."
                },
                kept.ifEmpty { null }?.let { "The phone keeps its own ${it.joinToString(" and ")}." },
            )
        return parts.joinToString(" ").ifEmpty { null }
    }

    /** "Import 240 entries" */
    fun importButton(entries: Int): String = "Import ${count(entries, "entry", "entries")}"

    /** "240 daily entries added, 8 cycles recognised. 3 rows were skipped; the list is below." */
    fun summary(result: ImportResult): String {
        val entries = if (result.entries == 1) "1 daily entry" else "${result.entries} daily entries"
        val cycles = if (result.cycles == 1) "1 cycle" else "${result.cycles} cycles"
        val skipped =
            when (result.skipped) {
                0 -> ""
                1 -> " 1 row was skipped; it is listed below."
                else -> " ${result.skipped} rows were skipped; the list is below."
            }
        val kept =
            when (result.kept) {
                0 -> ""
                1 -> " 1 day that already had data was kept as it was."
                else -> " ${result.kept} days that already had data were kept as they were."
            }
        return "$entries added, $cycles recognised.$skipped$kept"
    }

    /** "Row 6 · unreadable date · 2026-13-40" */
    fun skippedRow(row: SkippedRow): String {
        val reason = reasonNoun(row.reason, 1)
        val value = row.value?.takeIf { it.isNotBlank() }?.let { " $MIDDLE_DOT $it" } ?: ""
        return "Row ${row.row} $MIDDLE_DOT $reason$value"
    }

    /** "drip. export · 21 Oct, 15:00 · 240 entries" */
    fun lastImport(
        sourceLabel: String,
        last: LastImport,
        zone: ZoneId,
    ): String {
        val time = last.importedAt.atZone(zone).format(dayMonthTime)
        return "$sourceLabel $MIDDLE_DOT $time $MIDDLE_DOT ${count(last.entries, "entry", "entries")}"
    }

    /** "3 Apr 2026" for the sample "03/04/2026" read day first. */
    fun dateReading(
        sample: String,
        order: DateOrder,
    ): String = ImportValues.date(sample, order)?.let(Formats::dayMonthYear) ?: sample

    @Suppress("CyclomaticComplexMethod") // one label per target; `when` keeps the list exhaustive
    fun fieldLabel(field: CsvField): String =
        when (field) {
            CsvField.DATE -> "Date"
            CsvField.FLOW -> "Flow"
            CsvField.TEMPERATURE_C -> "Temperature °C"
            CsvField.TEMPERATURE_F -> "Temperature °F"
            CsvField.TEMPERATURE_TIME -> "Temperature time"
            CsvField.SYMPTOMS -> "Symptoms"
            CsvField.MOODS -> "Mood"
            CsvField.CERVICAL_MUCUS -> "Cervical mucus"
            CsvField.LH_TEST -> "LH test"
            CsvField.PREGNANCY_TEST -> "Pregnancy test"
            CsvField.SEX -> "Sex"
            CsvField.PILL -> "Pill"
            CsvField.PATCH_CHANGED -> "Patch changed"
            CsvField.RING -> "Ring"
            CsvField.INJECTION_GIVEN -> "Injection given"
            CsvField.EMERGENCY_CONTRACEPTION -> "Emergency contraception"
            CsvField.SLEEP_MINUTES -> "Sleep, minutes"
            CsvField.SLEEP_HOURS -> "Sleep, hours"
            CsvField.WEIGHT_KG -> "Weight kg"
            CsvField.WEIGHT_LB -> "Weight lb"
            CsvField.NOTES -> "Notes"
            CsvField.SKIP -> "Skip"
        }
}
