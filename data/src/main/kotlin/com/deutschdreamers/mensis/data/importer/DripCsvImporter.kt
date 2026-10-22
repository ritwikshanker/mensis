package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.data.csv.CsvTable
import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import java.time.LocalDate

/**
 * drip. (github.com/bloodyhealth/drip) CSV export. drip writes one row per cycle day with dotted
 * column names flattened from its Realm schema (version 4, `db/schemas/4.js`, and
 * `lib/import-export/export-to-csv.js`): booleans as `true`/`false`, numbers bare, strings lower-cased
 * and quoted when they contain `,`, `;`, `.`, `'`, a tab or a line break.
 *
 * Mapping: `bleeding.value` 0–3 = spotting / light / medium / heavy, and bleeding marked "exclude"
 * (not menstrual) becomes spotting, which never starts a period. Temperatures are °C; excluded
 * temperatures are left out so they cannot feed the temperature-shift rule. Mucus comes from
 * texture (creamy, egg white) and feeling (dry, wet, slippery), with drip's NFP value as fallback.
 * Pain, mood and sex flags map to the nearest Mensis values; notes of every section are joined into
 * the day's notes. Cervix, desire and the flags without a Mensis counterpart are reported as not
 * tracked.
 */
object DripCsvImporter {
    /** Every column drip writes for schema 4, in its order (`isCycleStart` is derived and not exported). */
    val COLUMNS: List<String> =
        listOf(
            "date",
            "temperature.value",
            "temperature.exclude",
            "temperature.time",
            "temperature.note",
            "bleeding.value",
            "bleeding.exclude",
            "mucus.feeling",
            "mucus.texture",
            "mucus.value",
            "mucus.exclude",
            "cervix.opening",
            "cervix.firmness",
            "cervix.position",
            "cervix.exclude",
            "note.value",
            "desire.value",
            "sex.solo",
            "sex.partner",
            "sex.condom",
            "sex.pill",
            "sex.iud",
            "sex.patch",
            "sex.ring",
            "sex.implant",
            "sex.diaphragm",
            "sex.none",
            "sex.other",
            "sex.note",
            "pain.cramps",
            "pain.ovulationPain",
            "pain.headache",
            "pain.backache",
            "pain.nausea",
            "pain.tenderBreasts",
            "pain.migraine",
            "pain.other",
            "pain.note",
            "mood.happy",
            "mood.sad",
            "mood.stressed",
            "mood.balanced",
            "mood.fine",
            "mood.anxious",
            "mood.energetic",
            "mood.fatigue",
            "mood.angry",
            "mood.other",
            "mood.note",
        )

    private val markers = setOf("bleeding.value", "temperature.value", "mucus.texture")

    private val bleeding = listOf(Flow.SPOTTING, Flow.LIGHT, Flow.MEDIUM, Flow.HEAVY)

    private val painFlags: Map<String, Symptom> =
        mapOf(
            "pain.cramps" to Symptom.CRAMPS,
            "pain.headache" to Symptom.HEADACHE,
            "pain.migraine" to Symptom.HEADACHE,
            "pain.backache" to Symptom.BACK_PAIN,
            "pain.nausea" to Symptom.NAUSEA,
            "pain.tenderBreasts" to Symptom.TENDER_BREASTS,
        )

    private val moodFlags: Map<String, Mood> =
        mapOf(
            "mood.happy" to Mood.HAPPY,
            "mood.sad" to Mood.LOW,
            "mood.stressed" to Mood.ANXIOUS,
            "mood.anxious" to Mood.ANXIOUS,
            "mood.balanced" to Mood.CALM,
            "mood.fine" to Mood.CALM,
            "mood.energetic" to Mood.ENERGETIC,
            "mood.angry" to Mood.IRRITABLE,
        )

    private val contraceptiveFlags =
        listOf(
            "sex.condom",
            "sex.pill",
            "sex.iud",
            "sex.patch",
            "sex.ring",
            "sex.implant",
            "sex.diaphragm",
            "sex.other",
        )

    private val untracked =
        listOf(
            "cervix.opening",
            "cervix.firmness",
            "cervix.position",
            "desire.value",
            "pain.ovulationPain",
            "pain.other",
            "mood.other",
        )

    private val noteColumns =
        listOf(
            "note.value" to null,
            "temperature.note" to "temperature",
            "pain.note" to "pain",
            "mood.note" to "mood",
            "sex.note" to "sex",
        )

    fun looksLikeDrip(table: CsvTable): Boolean = "date" in table.header && table.header.any { it in markers }

    fun parse(
        table: CsvTable,
        today: LocalDate,
    ): ParsedImport {
        if (!looksLikeDrip(table)) refuse(ImportError.NOT_THIS_FORMAT)
        val column = table.header.withIndex().associate { (i, name) -> name to i }
        val collector = ParseCollector(ImportSource.DRIP)
        table.rows.forEachIndexed { index, row ->
            collector.row()
            val line = index + 2

            fun cell(name: String): String? =
                column[name]
                    ?.let {
                        table.cell(row, it).trim()
                    }?.takeUnless(ImportValues::isBlankValue)
            val rawDate = cell("date").orEmpty()
            val date = ImportValues.date(rawDate)
            when {
                date == null -> collector.skip(line, SkipReason.UNREADABLE_DATE, rawDate)
                date.isAfter(today) -> collector.skip(line, SkipReason.FUTURE_DATE, rawDate)
                else -> {
                    val entry = readDay(date, ::cell, collector)
                    if (entry.isEmpty) collector.skip(line, SkipReason.NO_DATA) else collector.add(entry)
                }
            }
        }
        return collector.result()
    }

    private fun readDay(
        date: LocalDate,
        cell: (String) -> String?,
        collector: ParseCollector,
    ): DayEntry {
        fun flag(name: String) = ImportValues.bool(cell(name)) == true
        untracked
            .filter {
                cell(
                    it,
                ) != null &&
                    cell(it) != "false"
            }.forEach { collector.issue(it, IssueKind.NOT_TRACKED) }
        if (flag("sex.solo") && !flag("sex.partner")) collector.issue("sex.solo", IssueKind.NOT_TRACKED)
        val temperature = temperature(cell, collector)
        return DayEntry(
            date = date,
            flow = flow(cell, collector),
            temperatureC = temperature,
            temperatureTime = if (temperature != null) ImportValues.time(cell("temperature.time")) else null,
            symptoms =
                painFlags.filterKeys(::flag).values.toSet() +
                    if (flag("mood.fatigue")) setOf(Symptom.FATIGUE) else emptySet(),
            moods = moodFlags.filterKeys(::flag).values.toSet(),
            cervicalMucus = mucus(cell, collector),
            sex =
                when {
                    !flag("sex.partner") -> null
                    contraceptiveFlags.any(::flag) -> SexType.PROTECTED
                    else -> SexType.UNPROTECTED
                },
            notes =
                noteColumns
                    .mapNotNull { (name, prefix) -> cell(name)?.let { if (prefix == null) it else "$prefix: $it" } }
                    .joinToString("\n")
                    .ifEmpty { null },
        )
    }

    private fun flow(
        cell: (String) -> String?,
        collector: ParseCollector,
    ): Flow? {
        val raw = cell("bleeding.value") ?: return null
        val value = raw.toDoubleOrNull()?.toInt()?.let(bleeding::getOrNull)
        if (value == null) {
            collector.issue("bleeding.value", IssueKind.UNKNOWN_VALUE, raw)
            return null
        }
        return if (ImportValues.bool(cell("bleeding.exclude")) == true) Flow.SPOTTING else value
    }

    private fun temperature(
        cell: (String) -> String?,
        collector: ParseCollector,
    ): Double? {
        val raw = cell("temperature.value") ?: return null
        if (ImportValues.bool(cell("temperature.exclude")) == true) {
            collector.issue("temperature.exclude", IssueKind.NOT_TRACKED, raw)
            return null
        }
        return ImportValues.temperatureC(raw).also {
            if (it == null) collector.issue("temperature.value", IssueKind.UNKNOWN_VALUE, raw)
        }
    }

    private fun mucus(
        cell: (String) -> String?,
        collector: ParseCollector,
    ): CervicalMucus? {
        val feeling = cell("mucus.feeling")?.toDoubleOrNull()?.toInt()
        val texture = cell("mucus.texture")?.toDoubleOrNull()?.toInt()
        val nfp = cell("mucus.value")?.toDoubleOrNull()?.toInt()
        val result =
            when {
                texture == TEXTURE_EGG_WHITE || feeling == FEELING_SLIPPERY -> CervicalMucus.EGG_WHITE
                texture == TEXTURE_CREAMY -> CervicalMucus.CREAMY
                feeling == FEELING_WET -> CervicalMucus.WATERY
                feeling == FEELING_DRY || feeling == FEELING_NOTHING -> CervicalMucus.DRY
                nfp != null -> NFP_MUCUS.getOrNull(nfp)
                else -> null
            }
        val logged = listOfNotNull(feeling, texture, nfp)
        if (result == null && logged.isNotEmpty()) {
            collector.issue("mucus", IssueKind.UNKNOWN_VALUE, logged.joinToString("/"))
        }
        return result
    }

    // drip's labels: feeling = dry, nothing, wet, slippery; texture = nothing, creamy, egg white.
    private const val FEELING_DRY = 0
    private const val FEELING_NOTHING = 1
    private const val FEELING_WET = 2
    private const val FEELING_SLIPPERY = 3
    private const val TEXTURE_CREAMY = 1
    private const val TEXTURE_EGG_WHITE = 2

    /** drip's NFP mucus categories t, Ø, f, S, S+. */
    private val NFP_MUCUS =
        listOf(
            CervicalMucus.DRY,
            CervicalMucus.DRY,
            CervicalMucus.WATERY,
            CervicalMucus.CREAMY,
            CervicalMucus.EGG_WHITE,
        )
}
