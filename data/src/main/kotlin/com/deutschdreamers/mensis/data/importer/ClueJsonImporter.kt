package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TestResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate

/**
 * Clue "download your data" JSON. **Experimental**: Clue does not publish its export format and no
 * sample could be verified, so this reads an assumed structure, documented in
 * `docs/export-format.md`: a JSON array of day objects, or an object holding that array under
 * `data`, `days` or `entries`. Each day has `day` or `date` (ISO date or date-time) and optional
 * keys whose values are a string or a list of strings: `period` (spotting / light / medium / heavy),
 * `pain`, `emotions` or `mood`, `energy`, `digestion`, `skin`, `fluid` or `discharge`, `sex`,
 * `ovulation_test`, `pregnancy_test`, a number in `bbt` or `temperature` (°C, or °F above 50), and
 * `notes` or `note`. Anything else is reported as not tracked, never guessed.
 */
object ClueJsonImporter {
    private const val FAHRENHEIT_THRESHOLD = 50.0
    private val json = Json { ignoreUnknownKeys = true }
    private val containerKeys = listOf("data", "days", "entries")
    private val dateKeys = listOf("day", "date")

    private val periodWords: Map<String, Flow> =
        mapOf("spotting" to Flow.SPOTTING, "light" to Flow.LIGHT, "medium" to Flow.MEDIUM, "heavy" to Flow.HEAVY)

    private val symptomWords: Map<String, Symptom> =
        mapOf(
            "cramps" to Symptom.CRAMPS,
            "headache" to Symptom.HEADACHE,
            "tender_breasts" to Symptom.TENDER_BREASTS,
            "exhausted" to Symptom.FATIGUE,
            "low_energy" to Symptom.FATIGUE,
            "tired" to Symptom.FATIGUE,
            "nauseated" to Symptom.NAUSEA,
            "bloated" to Symptom.BLOATING,
            "acne" to Symptom.ACNE,
            "back_pain" to Symptom.BACK_PAIN,
        )

    private val moodWords: Map<String, Mood> =
        mapOf(
            "happy" to Mood.HAPPY,
            "sad" to Mood.LOW,
            "calm" to Mood.CALM,
            "anxious" to Mood.ANXIOUS,
            "stressed" to Mood.ANXIOUS,
            "irritable" to Mood.IRRITABLE,
            "energetic" to Mood.ENERGETIC,
            "high_energy" to Mood.ENERGETIC,
        )

    private val fluidWords: Map<String, CervicalMucus> =
        mapOf(
            "dry" to CervicalMucus.DRY,
            "sticky" to CervicalMucus.STICKY,
            "creamy" to CervicalMucus.CREAMY,
            "watery" to CervicalMucus.WATERY,
            "egg_white" to CervicalMucus.EGG_WHITE,
        )

    private val sexWords: Map<String, SexType> =
        mapOf(
            "protected" to SexType.PROTECTED,
            "unprotected" to SexType.UNPROTECTED,
            "withdrawal" to SexType.UNPROTECTED,
        )

    private val testWords: Map<String, TestResult> =
        mapOf(
            "pos" to TestResult.POSITIVE,
            "positive" to TestResult.POSITIVE,
            "neg" to TestResult.NEGATIVE,
            "negative" to TestResult.NEGATIVE,
        )

    private val symptomKeys = setOf("pain", "energy", "digestion", "skin")
    private val moodKeys = setOf("emotions", "mood")
    private val handledKeys =
        dateKeys + symptomKeys + moodKeys +
            listOf(
                "period",
                "fluid",
                "discharge",
                "sex",
                "ovulation_test",
                "pregnancy_test",
                "bbt",
                "temperature",
                "notes",
                "note",
            )

    fun looksLikeClue(text: String): Boolean =
        runCatching { days(json.parseToJsonElement(text)) }.getOrNull()?.isNotEmpty() == true

    fun parse(
        text: String,
        today: LocalDate,
    ): ParsedImport {
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (e: SerializationException) {
                refuse(ImportError.DAMAGED, "not valid JSON", e)
            }
        val days = days(root) ?: refuse(ImportError.NOT_THIS_FORMAT)
        val collector = ParseCollector(ImportSource.CLUE)
        days.forEachIndexed { index, element ->
            collector.row()
            val row = index + 1
            val day = element as? JsonObject
            val rawDate = day?.let { d -> dateKeys.firstNotNullOfOrNull { d.string(it) } }.orEmpty()
            val date = ImportValues.date(rawDate)
            when {
                day == null || date == null -> collector.skip(row, SkipReason.UNREADABLE_DATE, rawDate)
                date.isAfter(today) -> collector.skip(row, SkipReason.FUTURE_DATE, rawDate)
                else -> {
                    val entry = readDay(day, date, collector)
                    if (entry.isEmpty) collector.skip(row, SkipReason.NO_DATA) else collector.add(entry)
                }
            }
        }
        return collector.result()
    }

    private fun days(root: JsonElement): JsonArray? {
        val array =
            when (root) {
                is JsonArray -> root
                is JsonObject -> containerKeys.firstNotNullOfOrNull { root[it] as? JsonArray }
                else -> null
            } ?: return null
        val first = array.firstOrNull() as? JsonObject ?: return if (array.isEmpty()) array else null
        return array.takeIf { dateKeys.any { it in first } }
    }

    private fun readDay(
        day: JsonObject,
        date: LocalDate,
        collector: ParseCollector,
    ): DayEntry {
        day.keys.filterNot { it in handledKeys }.forEach { collector.issue(it, IssueKind.NOT_TRACKED) }

        fun <T> pick(
            key: String,
            words: Map<String, T>,
        ): List<T> =
            day.strings(key).mapNotNull { raw ->
                words[ImportValues.token(raw)].also { if (it == null) collector.issue(key, IssueKind.NOT_TRACKED, raw) }
            }
        val temperature =
            listOf("bbt", "temperature").firstNotNullOfOrNull { key ->
                (day[key] as? JsonPrimitive)?.doubleOrNull?.let {
                    ImportValues.temperatureC(it.toString(), defaultFahrenheit = it > FAHRENHEIT_THRESHOLD)
                }
            }
        return DayEntry(
            date = date,
            flow = pick("period", periodWords).firstOrNull(),
            temperatureC = temperature,
            symptoms = symptomKeys.flatMap { pick(it, symptomWords) }.toSet(),
            moods = moodKeys.flatMap { pick(it, moodWords) }.toSet(),
            cervicalMucus = (pick("fluid", fluidWords) + pick("discharge", fluidWords)).firstOrNull(),
            lhTest = pick("ovulation_test", testWords).firstOrNull(),
            pregnancyTest = pick("pregnancy_test", testWords).firstOrNull(),
            sex = pick("sex", sexWords).firstOrNull(),
            notes = (day.string("notes") ?: day.string("note"))?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.strings(key: String): List<String> =
        when (val value = this[key]) {
            is JsonPrimitive -> listOfNotNull(value.takeIf { it.isString }?.content)
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            else -> emptyList()
        }
}
