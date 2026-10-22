package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.RingState
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TestResult
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** How to read `03/04/2026`: 3 April (day first) or 4 March (month first). */
enum class DateOrder { DAY_FIRST, MONTH_FIRST }

/**
 * Normalisers from the words and numbers other apps and spreadsheets use to Mensis values. Every
 * function returns null for blank input and for input it cannot read; callers tell the two apart
 * with [isBlankValue] and report unreadable values. Unit conversion happens here and only here on
 * import: temperatures end up in °C (two decimals), weights in kg (one decimal).
 */
object ImportValues {
    private const val F_SCALE = 9.0 / 5.0
    private const val F_OFFSET = 32.0
    private const val LB_PER_KG = 2.20462262
    private const val TWO_DECIMALS = 100.0
    private const val ONE_DECIMAL = 10.0
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 1_440
    private const val CENTURY = 2_000
    private const val TWO_DIGIT_YEAR = 100
    private const val MONTHS = 12

    /** Plausible waking temperatures; anything outside is a typo or a different unit. */
    val CELSIUS_RANGE: ClosedFloatingPointRange<Double> = 34.0..43.0
    val FAHRENHEIT_RANGE: ClosedFloatingPointRange<Double> = 93.2..109.4
    val KILOGRAM_RANGE: ClosedFloatingPointRange<Double> = 20.0..300.0
    val POUND_RANGE: ClosedFloatingPointRange<Double> = 44.0..661.0

    private val blanks = setOf("", "-", "—", "–", "n/a", "na", "null")

    fun isBlankValue(raw: String?): Boolean = raw == null || raw.trim().lowercase() in blanks

    /** Lower case, spaces and hyphens folded to `_`: "Egg-white" → "egg_white". */
    fun token(raw: String): String = raw.trim().lowercase().replace(Regex("[\\s\\-]+"), "_")

    private val flowWords: Map<String, Flow> =
        mapOf(
            "none" to Flow.NONE,
            "no" to Flow.NONE,
            "0" to Flow.NONE,
            "false" to Flow.NONE,
            "nothing" to Flow.NONE,
            "spotting" to Flow.SPOTTING,
            "spot" to Flow.SPOTTING,
            "spotted" to Flow.SPOTTING,
            "brown" to Flow.SPOTTING,
            "light" to Flow.LIGHT,
            "low" to Flow.LIGHT,
            "little" to Flow.LIGHT,
            "1" to Flow.LIGHT,
            "medium" to Flow.MEDIUM,
            "moderate" to Flow.MEDIUM,
            "normal" to Flow.MEDIUM,
            "mid" to Flow.MEDIUM,
            "2" to Flow.MEDIUM,
            "heavy" to Flow.HEAVY,
            "high" to Flow.HEAVY,
            "strong" to Flow.HEAVY,
            "very_heavy" to Flow.HEAVY,
            "3" to Flow.HEAVY,
            "4" to Flow.HEAVY,
            // A plain "this was a period day" marker becomes medium, as the calendar's period editor does.
            "yes" to Flow.MEDIUM,
            "true" to Flow.MEDIUM,
            "x" to Flow.MEDIUM,
            "y" to Flow.MEDIUM,
            "period" to Flow.MEDIUM,
            "bleeding" to Flow.MEDIUM,
        )

    private val symptomWords: Map<String, Symptom> =
        mapOf(
            "cramps" to Symptom.CRAMPS,
            "cramp" to Symptom.CRAMPS,
            "cramping" to Symptom.CRAMPS,
            "period_pain" to Symptom.CRAMPS,
            "abdominal_pain" to Symptom.CRAMPS,
            "headache" to Symptom.HEADACHE,
            "headaches" to Symptom.HEADACHE,
            "migraine" to Symptom.HEADACHE,
            "bloating" to Symptom.BLOATING,
            "bloated" to Symptom.BLOATING,
            "tender_breasts" to Symptom.TENDER_BREASTS,
            "breast_tenderness" to Symptom.TENDER_BREASTS,
            "sore_breasts" to Symptom.TENDER_BREASTS,
            "tender_breast" to Symptom.TENDER_BREASTS,
            "fatigue" to Symptom.FATIGUE,
            "tired" to Symptom.FATIGUE,
            "tiredness" to Symptom.FATIGUE,
            "exhausted" to Symptom.FATIGUE,
            "back_pain" to Symptom.BACK_PAIN,
            "backache" to Symptom.BACK_PAIN,
            "acne" to Symptom.ACNE,
            "pimples" to Symptom.ACNE,
            "nausea" to Symptom.NAUSEA,
            "nauseous" to Symptom.NAUSEA,
            "nauseated" to Symptom.NAUSEA,
        )

    private val moodWords: Map<String, Mood> =
        mapOf(
            "calm" to Mood.CALM,
            "balanced" to Mood.CALM,
            "relaxed" to Mood.CALM,
            "fine" to Mood.CALM,
            "happy" to Mood.HAPPY,
            "good" to Mood.HAPPY,
            "joyful" to Mood.HAPPY,
            "irritable" to Mood.IRRITABLE,
            "irritated" to Mood.IRRITABLE,
            "angry" to Mood.IRRITABLE,
            "annoyed" to Mood.IRRITABLE,
            "anxious" to Mood.ANXIOUS,
            "anxiety" to Mood.ANXIOUS,
            "stressed" to Mood.ANXIOUS,
            "nervous" to Mood.ANXIOUS,
            "worried" to Mood.ANXIOUS,
            "low" to Mood.LOW,
            "sad" to Mood.LOW,
            "down" to Mood.LOW,
            "depressed" to Mood.LOW,
            "energetic" to Mood.ENERGETIC,
            "energized" to Mood.ENERGETIC,
            "energised" to Mood.ENERGETIC,
            "active" to Mood.ENERGETIC,
        )

    private val mucusWords: Map<String, CervicalMucus> =
        mapOf(
            "dry" to CervicalMucus.DRY,
            "none" to CervicalMucus.DRY,
            "sticky" to CervicalMucus.STICKY,
            "tacky" to CervicalMucus.STICKY,
            "creamy" to CervicalMucus.CREAMY,
            "lotion" to CervicalMucus.CREAMY,
            "watery" to CervicalMucus.WATERY,
            "wet" to CervicalMucus.WATERY,
            "egg_white" to CervicalMucus.EGG_WHITE,
            "eggwhite" to CervicalMucus.EGG_WHITE,
            "stretchy" to CervicalMucus.EGG_WHITE,
            "slippery" to CervicalMucus.EGG_WHITE,
        )

    private val testWords: Map<String, TestResult> =
        mapOf(
            "positive" to TestResult.POSITIVE,
            "pos" to TestResult.POSITIVE,
            "+" to TestResult.POSITIVE,
            "yes" to TestResult.POSITIVE,
            "true" to TestResult.POSITIVE,
            "1" to TestResult.POSITIVE,
            "negative" to TestResult.NEGATIVE,
            "neg" to TestResult.NEGATIVE,
            "no" to TestResult.NEGATIVE,
            "false" to TestResult.NEGATIVE,
            "0" to TestResult.NEGATIVE,
        )

    private val sexWords: Map<String, SexType> =
        mapOf(
            "none" to SexType.NONE,
            "no" to SexType.NONE,
            "protected" to SexType.PROTECTED,
            "condom" to SexType.PROTECTED,
            "safe" to SexType.PROTECTED,
            "unprotected" to SexType.UNPROTECTED,
            "unsafe" to SexType.UNPROTECTED,
        )

    private val pillWords: Map<String, PillState> =
        mapOf(
            "taken" to PillState.TAKEN,
            "yes" to PillState.TAKEN,
            "true" to PillState.TAKEN,
            "late" to PillState.LATE,
            "missed" to PillState.MISSED,
            "skipped" to PillState.MISSED,
            "no" to PillState.MISSED,
            "false" to PillState.MISSED,
        )

    private val ringWords: Map<String, RingState> =
        mapOf("in" to RingState.IN, "inserted" to RingState.IN, "out" to RingState.OUT, "removed" to RingState.OUT)

    private val trueWords = setOf("true", "yes", "y", "1", "x", "✓")
    private val falseWords = setOf("false", "no", "n", "0")

    fun flow(raw: String?): Flow? = raw?.let { flowWords[token(it)] }

    fun symptom(raw: String): Symptom? = symptomWords[token(raw)]

    fun mood(raw: String): Mood? = moodWords[token(raw)]

    fun mucus(raw: String?): CervicalMucus? = raw?.let { mucusWords[token(it)] }

    fun testResult(raw: String?): TestResult? = raw?.let { testWords[token(it)] }

    fun sex(raw: String?): SexType? = raw?.let { sexWords[token(it)] }

    fun pill(raw: String?): PillState? = raw?.let { pillWords[token(it)] }

    fun ring(raw: String?): RingState? = raw?.let { ringWords[token(it)] }

    fun bool(raw: String?): Boolean? {
        val t = raw?.let(::token) ?: return null
        return when (t) {
            in trueWords -> true
            in falseWords -> false
            else -> null
        }
    }

    /** "cramps; headache", "Cramps, Headache", "cramps|headache" → the list items, blanks dropped. */
    fun listItems(raw: String?): List<String> =
        raw
            ?.split(';', ',', '|', '/')
            ?.map { it.trim() }
            ?.filterNot { isBlankValue(it) }
            .orEmpty()

    /** "36,62", "36.62 °C", "97.9F" → a number; the unit suffix, if any, overrides [defaultFahrenheit]. */
    fun temperatureC(
        raw: String?,
        defaultFahrenheit: Boolean = false,
    ): Double? {
        val text = raw?.trim()?.uppercase() ?: return null
        val fahrenheit =
            when {
                text.endsWith("F") -> true
                text.endsWith("C") -> false
                else -> defaultFahrenheit
            }
        val value = number(text.trimEnd('C', 'F', '°', ' ')) ?: return null
        return if (fahrenheit) {
            value.takeIf { it in FAHRENHEIT_RANGE }?.let { round2((it - F_OFFSET) / F_SCALE) }
        } else {
            value.takeIf { it in CELSIUS_RANGE }?.let(::round2)
        }
    }

    fun weightKg(
        raw: String?,
        pounds: Boolean = false,
    ): Double? {
        val text = raw?.trim()?.lowercase() ?: return null
        val isPounds =
            when {
                text.endsWith("lb") || text.endsWith("lbs") -> true
                text.endsWith("kg") -> false
                else -> pounds
            }
        val value =
            number(
                text
                    .removeSuffix("lbs")
                    .removeSuffix("lb")
                    .removeSuffix("kg")
                    .trim(),
            ) ?: return null
        return if (isPounds) {
            value.takeIf { it in POUND_RANGE }?.let { round1(it / LB_PER_KG) }
        } else {
            value.takeIf { it in KILOGRAM_RANGE }?.let(::round1)
        }
    }

    /** "06:40", "6:40", "06:40:00", "6.40" → a time of day. */
    fun time(raw: String?): LocalTime? {
        val match = raw?.trim()?.let { Regex("^(\\d{1,2})[:.](\\d{2})(?::\\d{2})?$").find(it) } ?: return null
        val (h, m) = match.destructured
        return runCatching { LocalTime.of(h.toInt(), m.toInt()) }.getOrNull()
    }

    /**
     * Sleep as minutes: "7:20" and "7 h 20 min" read as hours and minutes; a plain number is hours
     * ("7.5") when [hours] is set, else minutes ("440").
     */
    fun sleepMinutes(
        raw: String?,
        hours: Boolean = false,
    ): Int? {
        val text = raw?.trim()?.lowercase() ?: return null
        val clock = Regex("^(\\d{1,2}):(\\d{2})$").find(text)
        val words = Regex("^(\\d{1,2})\\s*h(?:ours?)?\\s*(?:(\\d{1,2})\\s*m(?:in(?:utes?)?)?)?$").find(text)
        val minutes =
            when {
                clock != null -> clock.groupValues[1].toInt() * MINUTES_PER_HOUR + clock.groupValues[2].toInt()
                words != null ->
                    words.groupValues[1].toInt() * MINUTES_PER_HOUR + (words.groupValues[2].toIntOrNull() ?: 0)
                else -> {
                    val value = number(text) ?: return null
                    if (hours) (value * MINUTES_PER_HOUR).roundToInt() else value.roundToInt()
                }
            }
        return minutes.takeIf { it in 0..MINUTES_PER_DAY }
    }

    private fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    private fun round2(value: Double) = (value * TWO_DECIMALS).roundToInt() / TWO_DECIMALS

    private fun round1(value: Double) = (value * ONE_DECIMAL).roundToInt() / ONE_DECIMAL

    // Dates

    private val isoDate = Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$")
    private val compactDate = Regex("^(\\d{4})(\\d{2})(\\d{2})$")
    private val dottedDate = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2}|\\d{4})$")
    private val slashDate = Regex("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2}|\\d{4})$")
    private val dayMonthName = Regex("^(\\d{1,2})\\.?\\s+([a-z]+)\\.?,?\\s+(\\d{4})$")
    private val monthNameDay = Regex("^([a-z]+)\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})$")

    private val monthNames: Map<String, Month> =
        Month.entries
            .flatMap { month ->
                listOf(
                    month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase() to month,
                    month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() to month,
                )
            }.toMap() + ("sept" to Month.SEPTEMBER)

    /** Drops a time part: "2026-03-06T00:00:00Z" and "06/03/2026 08:15" keep only the date. */
    private fun datePart(raw: String): String {
        val text = raw.trim().lowercase()
        val iso = Regex("^(\\d{4}-\\d{1,2}-\\d{1,2})[t ].*$").find(text)
        if (iso != null) return iso.groupValues[1]
        val numeric = Regex("^([\\d./-]+)\\s+\\d{1,2}:\\d{2}.*$").find(text)
        return numeric?.groupValues?.get(1) ?: text
    }

    /**
     * ISO (2026-03-06), compact (20260306), dotted day-first (6.3.2026), slashed (06/03/2026, read with
     * [order]) and English month names (6 Mar 2026, March 6, 2026). Two-digit years are 20xx.
     */
    fun date(
        raw: String?,
        order: DateOrder = DateOrder.DAY_FIRST,
    ): LocalDate? {
        if (raw == null) return null
        val text = datePart(raw)
        return datePatterns(order).firstNotNullOfOrNull { (pattern, read) ->
            pattern.find(text)?.destructured?.let(read)
        }
    }

    /** Each pattern with how its three groups become year, month and day. */
    private fun datePatterns(order: DateOrder): List<Pair<Regex, (MatchResult.Destructured) -> LocalDate?>> =
        listOf(
            isoDate to { (y, m, d) -> ymd(y, m, d) },
            compactDate to { (y, m, d) -> ymd(y, m, d) },
            dottedDate to { (d, m, y) -> ymd(y, m, d) },
            slashDate to { (a, b, y) -> if (order == DateOrder.DAY_FIRST) ymd(y, b, a) else ymd(y, a, b) },
            dayMonthName to { (d, name, y) -> monthNames[name]?.let { ymd(y, it.value.toString(), d) } },
            monthNameDay to { (name, d, y) -> monthNames[name]?.let { ymd(y, it.value.toString(), d) } },
        )

    /** True when [raw] is a slashed date such as 03/04/2026, whose reading depends on [DateOrder]. */
    fun isSlashedDate(raw: String): Boolean = slashDate.matches(datePart(raw))

    /**
     * The order implied by slashed dates: a first number above 12 means day first, a second number above
     * 12 means month first. Null when every sample fits both orders (or there are no slashed dates).
     */
    fun inferDateOrder(samples: List<String>): DateOrder? {
        for (sample in samples) {
            val match = slashDate.find(datePart(sample)) ?: continue
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            if (first > MONTHS && second <= MONTHS) return DateOrder.DAY_FIRST
            if (second > MONTHS && first <= MONTHS) return DateOrder.MONTH_FIRST
        }
        return null
    }

    private fun ymd(
        year: String,
        month: String,
        day: String,
    ): LocalDate? {
        val y = year.toInt().let { if (it < TWO_DIGIT_YEAR) CENTURY + it else it }
        // 31 February and the like are unreadable dates, not errors.
        return runCatching { LocalDate.of(y, month.toInt(), day.toInt()) }.getOrNull()
    }
}
