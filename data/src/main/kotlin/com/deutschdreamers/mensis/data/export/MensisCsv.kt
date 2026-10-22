package com.deutschdreamers.mensis.data.export

import com.deutschdreamers.mensis.data.csv.Csv
import com.deutschdreamers.mensis.data.importer.CsvField
import com.deutschdreamers.mensis.domain.model.DayEntry

/**
 * The CSV export: one row per logged day, for spreadsheets. Values are the lower-case names used in
 * the JSON export, lists joined with `;`, temperatures °C, weights kg, sleep in minutes. UTF-8 with a
 * byte-order mark so spreadsheet apps pick the right encoding. Method history and settings are not
 * included; use JSON for a complete copy. The generic CSV importer recognises every column.
 */
object MensisCsv {
    const val LIST_SEPARATOR: String = ";"

    val COLUMNS: List<CsvField> =
        listOf(
            CsvField.DATE,
            CsvField.FLOW,
            CsvField.SYMPTOMS,
            CsvField.MOODS,
            CsvField.CERVICAL_MUCUS,
            CsvField.TEMPERATURE_C,
            CsvField.TEMPERATURE_TIME,
            CsvField.LH_TEST,
            CsvField.PREGNANCY_TEST,
            CsvField.SEX,
            CsvField.PILL,
            CsvField.PATCH_CHANGED,
            CsvField.RING,
            CsvField.INJECTION_GIVEN,
            CsvField.EMERGENCY_CONTRACEPTION,
            CsvField.SLEEP_MINUTES,
            CsvField.WEIGHT_KG,
            CsvField.NOTES,
        )

    fun encode(entries: List<DayEntry>): ByteArray {
        val header = COLUMNS.map { requireNotNull(it.mensisColumn) }
        val rows =
            entries.filterNot { it.isEmpty }.sortedBy { it.date }.map { entry ->
                COLUMNS.map { cell(entry, it) }
            }
        return (Csv.BOM + Csv.write(listOf(header) + rows)).toByteArray()
    }

    @Suppress("CyclomaticComplexMethod") // one branch per column; `when` keeps the list exhaustive
    private fun cell(
        entry: DayEntry,
        field: CsvField,
    ): String =
        when (field) {
            CsvField.DATE -> entry.date.toString()
            CsvField.FLOW -> entry.flow.lower()
            CsvField.SYMPTOMS -> entry.symptoms.sorted().joinToString(LIST_SEPARATOR) { it.name.lowercase() }
            CsvField.MOODS -> entry.moods.sorted().joinToString(LIST_SEPARATOR) { it.name.lowercase() }
            CsvField.CERVICAL_MUCUS -> entry.cervicalMucus.lower()
            CsvField.TEMPERATURE_C -> entry.temperatureC?.toString().orEmpty()
            CsvField.TEMPERATURE_TIME -> entry.temperatureTime?.toString().orEmpty()
            CsvField.LH_TEST -> entry.lhTest.lower()
            CsvField.PREGNANCY_TEST -> entry.pregnancyTest.lower()
            CsvField.SEX -> entry.sex.lower()
            CsvField.PILL -> entry.pillState.lower()
            CsvField.PATCH_CHANGED -> entry.patchChanged?.toString().orEmpty()
            CsvField.RING -> entry.ringState.lower()
            CsvField.INJECTION_GIVEN -> entry.injectionGiven?.toString().orEmpty()
            CsvField.EMERGENCY_CONTRACEPTION -> entry.emergencyContraception?.toString().orEmpty()
            CsvField.SLEEP_MINUTES -> entry.sleepMinutes?.toString().orEmpty()
            CsvField.WEIGHT_KG -> entry.weightKg?.toString().orEmpty()
            CsvField.NOTES -> entry.notes.orEmpty()
            CsvField.TEMPERATURE_F, CsvField.SLEEP_HOURS, CsvField.WEIGHT_LB, CsvField.SKIP -> ""
        }

    private fun Enum<*>?.lower(): String = this?.name?.lowercase().orEmpty()
}
