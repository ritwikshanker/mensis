package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.csv.CsvTable
import com.deutschdreamers.mensis.domain.model.DayEntry
import java.time.LocalDate

/** What a CSV column holds (design §14 step 2 target). [mensisColumn] is its header in Mensis's CSV export. */
enum class CsvField(
    val mensisColumn: String? = null,
) {
    DATE("date"),
    FLOW("flow"),
    TEMPERATURE_C("temperature_c"),
    TEMPERATURE_F,
    TEMPERATURE_TIME("temperature_time"),
    SYMPTOMS("symptoms"),
    MOODS("moods"),
    CERVICAL_MUCUS("cervical_mucus"),
    LH_TEST("lh_test"),
    PREGNANCY_TEST("pregnancy_test"),
    SEX("sex"),
    PILL("pill"),
    PATCH_CHANGED("patch_changed"),
    RING("ring"),
    INJECTION_GIVEN("injection_given"),
    EMERGENCY_CONTRACEPTION("emergency_contraception"),
    SLEEP_MINUTES("sleep_minutes"),
    SLEEP_HOURS,
    WEIGHT_KG("weight_kg"),
    WEIGHT_LB,
    NOTES("notes"),
    SKIP,
}

/** One target per column, plus how slashed dates are read. */
data class ColumnMapping(
    val fields: List<CsvField>,
    val dateOrder: DateOrder = DateOrder.DAY_FIRST,
) {
    val hasDate: Boolean get() = CsvField.DATE in fields

    fun with(
        column: Int,
        field: CsvField,
    ): ColumnMapping = copy(fields = fields.mapIndexed { i, f -> if (i == column) field else f })
}

/** A column as the mapping step shows it: header and the first non-empty value. */
data class CsvColumn(
    val header: String,
    val sample: String,
)

/**
 * Any CSV with one row per day. Columns are guessed from their headers (including every header of
 * Mensis's own CSV export) and, failing that, from their values; the user can correct the guess
 * before anything is imported. Values go through [ImportValues].
 */
object GenericCsvImporter {
    private const val SAMPLE_ROWS = 20
    private const val GUESS_SHARE = 0.8
    private const val FAHRENHEIT_THRESHOLD = 50.0

    private val headerSynonyms: Map<String, CsvField> =
        buildMap {
            CsvField.entries.forEach { field -> field.mensisColumn?.let { put(it, field) } }
            listOf("day", "datum", "fecha", "entry_date", "logged_date", "date_time", "timestamp")
                .forEach { put(it, CsvField.DATE) }
            listOf("bleeding", "period", "period_flow", "menstruation", "menses", "blutung", "flow_level")
                .forEach { put(it, CsvField.FLOW) }
            listOf("temp_c", "temperature", "temp", "bbt", "bbt_c", "basal_temperature", "waking_temperature")
                .forEach { put(it, CsvField.TEMPERATURE_C) }
            listOf("temperature_f", "temp_f", "bbt_f").forEach { put(it, CsvField.TEMPERATURE_F) }
            listOf("temp_time", "time", "measured_at", "temperature_time_of_day")
                .forEach { put(it, CsvField.TEMPERATURE_TIME) }
            listOf("symptom", "pain").forEach { put(it, CsvField.SYMPTOMS) }
            listOf("mood", "emotions", "feelings").forEach { put(it, CsvField.MOODS) }
            listOf("mucus", "discharge", "cervical_fluid", "fluid").forEach { put(it, CsvField.CERVICAL_MUCUS) }
            listOf("lh", "ovulation_test", "opk").forEach { put(it, CsvField.LH_TEST) }
            listOf("pregnancy", "hcg").forEach { put(it, CsvField.PREGNANCY_TEST) }
            put("intercourse", CsvField.SEX)
            put("pill_state", CsvField.PILL)
            put("patch", CsvField.PATCH_CHANGED)
            put("ring_state", CsvField.RING)
            listOf("injection", "shot").forEach { put(it, CsvField.INJECTION_GIVEN) }
            listOf("ec", "morning_after_pill").forEach { put(it, CsvField.EMERGENCY_CONTRACEPTION) }
            listOf("sleep", "sleep_duration", "sleep_hours", "hours_slept").forEach { put(it, CsvField.SLEEP_HOURS) }
            put("weight", CsvField.WEIGHT_KG)
            listOf("weight_lb", "weight_lbs").forEach { put(it, CsvField.WEIGHT_LB) }
            listOf("note", "comment", "comments", "notizen").forEach { put(it, CsvField.NOTES) }
        }

    /** "Temperature (°C)" → "temperature_c" */
    fun headerKey(header: String): String = header.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun columns(table: CsvTable): List<CsvColumn> =
        table.header.mapIndexed { i, header ->
            CsvColumn(
                header,
                table.rows
                    .asSequence()
                    .map { table.cell(it, i).trim() }
                    .firstOrNull { it.isNotEmpty() }
                    .orEmpty(),
            )
        }

    fun guess(table: CsvTable): ColumnMapping {
        val guessed =
            table.header.indices.map { i ->
                val samples = samples(table, i)
                val byHeader = headerSynonyms[headerKey(table.header[i])]
                when {
                    byHeader == CsvField.TEMPERATURE_C &&
                        samples.medianNumber()?.let { it > FAHRENHEIT_THRESHOLD } == true ->
                        CsvField.TEMPERATURE_F
                    byHeader != null -> byHeader
                    else -> guessFromValues(samples)
                }
            }
        val firstDate = guessed.indexOf(CsvField.DATE)
        val fields = guessed.mapIndexed { i, f -> if (f == CsvField.DATE && i != firstDate) CsvField.SKIP else f }
        val order = if (firstDate >= 0) ImportValues.inferDateOrder(samples(table, firstDate)) else null
        return ColumnMapping(fields, order ?: DateOrder.DAY_FIRST)
    }

    /** True when the date column holds slashed dates that read validly both ways, so the user must choose. */
    fun dateOrderAmbiguous(
        table: CsvTable,
        mapping: ColumnMapping,
    ): Boolean {
        val column = mapping.fields.indexOf(CsvField.DATE)
        if (column < 0) return false
        val values = table.rows.map { table.cell(it, column) }.filter { it.isNotBlank() }
        return values.any(ImportValues::isSlashedDate) && ImportValues.inferDateOrder(values) == null
    }

    fun parse(
        table: CsvTable,
        mapping: ColumnMapping,
        today: LocalDate,
        source: ImportSource = ImportSource.CSV,
    ): ParsedImport {
        val dateColumn = mapping.fields.indexOf(CsvField.DATE)
        if (dateColumn < 0) refuse(ImportError.NO_DATE_COLUMN)
        val collector = ParseCollector(source)
        table.rows.forEachIndexed { index, row ->
            collector.row()
            val line = index + 2
            val rawDate = table.cell(row, dateColumn)
            val date = ImportValues.date(rawDate, mapping.dateOrder)
            when {
                date == null -> collector.skip(line, SkipReason.UNREADABLE_DATE, rawDate.trim())
                date.isAfter(today) -> collector.skip(line, SkipReason.FUTURE_DATE, rawDate.trim())
                else -> {
                    val entry = readRow(table, row, mapping, date, collector)
                    if (entry.isEmpty) collector.skip(line, SkipReason.NO_DATA) else collector.add(entry)
                }
            }
        }
        return collector.result()
    }

    private fun readRow(
        table: CsvTable,
        row: List<String>,
        mapping: ColumnMapping,
        date: LocalDate,
        collector: ParseCollector,
    ): DayEntry {
        var entry = DayEntry(date)
        mapping.fields.forEachIndexed { column, field ->
            val raw = table.cell(row, column)
            if (field == CsvField.DATE ||
                field == CsvField.SKIP ||
                ImportValues.isBlankValue(raw)
            ) {
                return@forEachIndexed
            }
            val label = table.header.getOrNull(column)?.ifBlank { null } ?: field.name.lowercase()
            entry =
                apply(entry, field, raw.trim()) { unknown -> collector.issue(label, IssueKind.UNKNOWN_VALUE, unknown) }
        }
        return entry
    }

    /** Sets one field from a cell; single values keep the first readable column, lists and notes combine. */
    @Suppress("CyclomaticComplexMethod")
    internal fun apply(
        entry: DayEntry,
        field: CsvField,
        raw: String,
        onUnknown: (String) -> Unit,
    ): DayEntry {
        fun <T : Any> single(
            current: T?,
            value: T?,
            set: (T) -> DayEntry,
        ): DayEntry {
            if (value == null) {
                onUnknown(raw)
                return entry
            }
            return if (current == null) set(value) else entry
        }
        return when (field) {
            CsvField.FLOW -> single(entry.flow, ImportValues.flow(raw)) { entry.copy(flow = it) }
            CsvField.TEMPERATURE_C, CsvField.TEMPERATURE_F ->
                single(entry.temperatureC, ImportValues.temperatureC(raw, field == CsvField.TEMPERATURE_F)) {
                    entry.copy(temperatureC = it)
                }
            CsvField.TEMPERATURE_TIME ->
                single(
                    entry.temperatureTime,
                    ImportValues.time(raw),
                ) { entry.copy(temperatureTime = it) }
            CsvField.SYMPTOMS -> {
                val items = ImportValues.listItems(raw)
                items.filter { ImportValues.symptom(it) == null }.forEach(onUnknown)
                entry.copy(symptoms = entry.symptoms + items.mapNotNull(ImportValues::symptom))
            }
            CsvField.MOODS -> {
                val items = ImportValues.listItems(raw)
                items.filter { ImportValues.mood(it) == null }.forEach(onUnknown)
                entry.copy(moods = entry.moods + items.mapNotNull(ImportValues::mood))
            }
            CsvField.CERVICAL_MUCUS ->
                single(
                    entry.cervicalMucus,
                    ImportValues.mucus(raw),
                ) { entry.copy(cervicalMucus = it) }
            CsvField.LH_TEST -> single(entry.lhTest, ImportValues.testResult(raw)) { entry.copy(lhTest = it) }
            CsvField.PREGNANCY_TEST ->
                single(entry.pregnancyTest, ImportValues.testResult(raw)) { entry.copy(pregnancyTest = it) }
            CsvField.SEX -> single(entry.sex, ImportValues.sex(raw)) { entry.copy(sex = it) }
            CsvField.PILL -> single(entry.pillState, ImportValues.pill(raw)) { entry.copy(pillState = it) }
            CsvField.PATCH_CHANGED ->
                single(
                    entry.patchChanged,
                    ImportValues.bool(raw),
                ) { entry.copy(patchChanged = it) }
            CsvField.RING -> single(entry.ringState, ImportValues.ring(raw)) { entry.copy(ringState = it) }
            CsvField.INJECTION_GIVEN ->
                single(entry.injectionGiven, ImportValues.bool(raw)) { entry.copy(injectionGiven = it) }
            CsvField.EMERGENCY_CONTRACEPTION ->
                single(entry.emergencyContraception, ImportValues.bool(raw)) { entry.copy(emergencyContraception = it) }
            CsvField.SLEEP_MINUTES, CsvField.SLEEP_HOURS ->
                single(entry.sleepMinutes, ImportValues.sleepMinutes(raw, hours = field == CsvField.SLEEP_HOURS)) {
                    entry.copy(sleepMinutes = it)
                }
            CsvField.WEIGHT_KG, CsvField.WEIGHT_LB ->
                single(
                    entry.weightKg,
                    ImportValues.weightKg(raw, field == CsvField.WEIGHT_LB),
                ) { entry.copy(weightKg = it) }
            CsvField.NOTES -> entry.copy(notes = listOfNotNull(entry.notes, raw).joinToString("\n"))
            CsvField.DATE, CsvField.SKIP -> entry
        }
    }

    private fun samples(
        table: CsvTable,
        column: Int,
    ): List<String> =
        table.rows
            .asSequence()
            .map { table.cell(it, column).trim() }
            .filterNot(ImportValues::isBlankValue)
            .take(SAMPLE_ROWS)
            .toList()

    private fun List<String>.medianNumber(): Double? =
        mapNotNull { it.replace(',', '.').trimEnd('C', 'F', 'c', 'f', '°', ' ').toDoubleOrNull() }
            .sorted()
            .let { if (it.isEmpty()) null else it[it.size / 2] }

    private fun List<String>.mostly(predicate: (String) -> Boolean): Boolean =
        isNotEmpty() && count(predicate) >= size * GUESS_SHARE

    private val namedFlows = setOf("spotting", "light", "medium", "heavy")

    private fun guessFromValues(samples: List<String>): CsvField =
        when {
            samples.mostly { ImportValues.date(it) != null } -> CsvField.DATE
            samples.mostly { ImportValues.temperatureC(it) != null } -> CsvField.TEMPERATURE_C
            samples.mostly { ImportValues.temperatureC(it, defaultFahrenheit = true) != null } -> CsvField.TEMPERATURE_F
            samples.mostly { ImportValues.flow(it) != null } && samples.any { ImportValues.token(it) in namedFlows } ->
                CsvField.FLOW
            samples.mostly { s ->
                ImportValues.listItems(s).let {
                    it.isNotEmpty() &&
                        it.all { m -> ImportValues.mood(m) != null }
                }
            } ->
                CsvField.MOODS
            samples.mostly { s ->
                ImportValues.listItems(s).let { it.isNotEmpty() && it.all { m -> ImportValues.symptom(m) != null } }
            } -> CsvField.SYMPTOMS
            else -> CsvField.SKIP
        }
}
