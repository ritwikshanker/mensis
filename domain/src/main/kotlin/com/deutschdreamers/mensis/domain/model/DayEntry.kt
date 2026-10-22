package com.deutschdreamers.mensis.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Menstrual flow logged for a day. `NONE` is an explicit "no bleeding"; an unlogged day has `null` flow. */
enum class Flow {
    NONE,
    SPOTTING,
    LIGHT,
    MEDIUM,
    HEAVY,
    ;

    /** Spotting or more: the day counts as a bleeding day (shown on the strip, counted in period length). */
    val isBleeding: Boolean get() = this != NONE

    /** Light or more: the only flow that can start a period (spotting alone never does). */
    val isMenstrual: Boolean get() = ordinal >= LIGHT.ordinal
}

enum class Symptom { CRAMPS, HEADACHE, BLOATING, TENDER_BREASTS, FATIGUE, BACK_PAIN, ACNE, NAUSEA }

enum class Mood { CALM, HAPPY, IRRITABLE, ANXIOUS, LOW, ENERGETIC }

enum class CervicalMucus { DRY, STICKY, CREAMY, WATERY, EGG_WHITE }

/** Result of an LH or pregnancy test. A test that was not taken is `null` on the entry. */
enum class TestResult { NEGATIVE, POSITIVE }

enum class SexType { NONE, PROTECTED, UNPROTECTED }

enum class PillState { TAKEN, LATE, MISSED }

enum class RingState { IN, OUT }

/**
 * Everything a user can log for one calendar day. Every field is optional: saving an entry with all
 * fields empty is valid. Temperatures are always °C and weights always kg.
 */
data class DayEntry(
    val date: LocalDate,
    val flow: Flow? = null,
    val temperatureC: Double? = null,
    val temperatureTime: LocalTime? = null,
    val symptoms: Set<Symptom> = emptySet(),
    val moods: Set<Mood> = emptySet(),
    val cervicalMucus: CervicalMucus? = null,
    val lhTest: TestResult? = null,
    val pregnancyTest: TestResult? = null,
    val sex: SexType? = null,
    val pillState: PillState? = null,
    val patchChanged: Boolean? = null,
    val ringState: RingState? = null,
    val injectionGiven: Boolean? = null,
    val emergencyContraception: Boolean? = null,
    val sleepMinutes: Int? = null,
    val weightKg: Double? = null,
    val notes: String? = null,
) {
    val isEmpty: Boolean
        get() =
            flow == null &&
                temperatureC == null &&
                temperatureTime == null &&
                symptoms.isEmpty() &&
                moods.isEmpty() &&
                cervicalMucus == null &&
                lhTest == null &&
                pregnancyTest == null &&
                sex == null &&
                pillState == null &&
                patchChanged == null &&
                ringState == null &&
                injectionGiven == null &&
                emergencyContraception == null &&
                sleepMinutes == null &&
                weightKg == null &&
                notes.isNullOrBlank()

    val isBleedingDay: Boolean get() = flow?.isBleeding == true

    val isMenstrualDay: Boolean get() = flow?.isMenstrual == true
}
