package com.deutschdreamers.mensis.ui.format

import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.RingState
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.screens.today.ValueChipUi

/** The "Logged today" / calendar day-card chips for one entry, in the log's section order. */
object LoggedChips {
    fun of(
        entry: DayEntry?,
        settings: TrackerSettings,
    ): List<ValueChipUi> {
        if (entry == null || entry.isEmpty) return emptyList()
        return bleedingChips(entry, settings) + symptomChips(entry) + testChips(entry) + methodChips(entry) +
            bodyChips(entry, settings)
    }

    private fun bleedingChips(
        entry: DayEntry,
        settings: TrackerSettings,
    ): List<ValueChipUi> {
        val chips = mutableListOf<ValueChipUi>()
        entry.flow?.let { chips += ValueChipUi(R.drawable.ms_water_drop, flowLabel(it)) }
        entry.temperatureC?.let { celsius ->
            val shown = Units.toDisplayTemperature(celsius, settings.temperatureUnit)
            val text = "${Formats.temperature(shown)} ${Units.temperatureSymbol(settings.temperatureUnit)}"
            val time = entry.temperatureTime?.let { " $MIDDLE_DOT ${Formats.time(it)}" } ?: ""
            chips += ValueChipUi(R.drawable.ms_thermostat, text + time)
        }
        return chips
    }

    private fun symptomChips(entry: DayEntry): List<ValueChipUi> =
        Symptom.entries.filter { it in entry.symptoms }.map { ValueChipUi(symptomIcon(it), symptomLabel(it)) } +
            Mood.entries.filter { it in entry.moods }.map { ValueChipUi(R.drawable.ms_mood, moodLabel(it)) }

    private fun testChips(entry: DayEntry): List<ValueChipUi> =
        listOfNotNull(
            entry.cervicalMucus?.let { ValueChipUi(R.drawable.ms_egg, "${mucusLabel(it)} mucus") },
            entry.lhTest?.let { ValueChipUi(R.drawable.ms_science, "LH ${testLabel(it)}") },
            entry.pregnancyTest?.let { ValueChipUi(R.drawable.ms_science, "Pregnancy test ${testLabel(it)}") },
            entry.sex?.takeIf { it != SexType.NONE }?.let { ValueChipUi(R.drawable.ms_check_circle, sexLabel(it)) },
        )

    private fun methodChips(entry: DayEntry): List<ValueChipUi> =
        listOfNotNull(
            entry.pillState?.let { ValueChipUi(R.drawable.ms_pill, pillLabel(it)) },
            ValueChipUi(R.drawable.ms_healing, "Patch changed").takeIf { entry.patchChanged == true },
            entry.ringState?.let { ValueChipUi(R.drawable.ms_radio_button_unchecked, ringLabel(it)) },
            ValueChipUi(R.drawable.ms_vaccines, "Injection given").takeIf { entry.injectionGiven == true },
            ValueChipUi(R.drawable.ms_emergency, "Emergency contraception").takeIf {
                entry.emergencyContraception ==
                    true
            },
        )

    private fun bodyChips(
        entry: DayEntry,
        settings: TrackerSettings,
    ): List<ValueChipUi> =
        listOfNotNull(
            entry.sleepMinutes?.let { ValueChipUi(R.drawable.ms_bedtime, Formats.sleep(it)) },
            entry.weightKg?.let { kg ->
                val shown = Units.toDisplayWeight(kg, settings.weightUnit)
                ValueChipUi(
                    R.drawable.ms_monitor_weight,
                    "${Formats.weight(shown)} ${Units.weightSymbol(settings.weightUnit)}",
                )
            },
            ValueChipUi(R.drawable.ms_draft, "Note").takeIf { !entry.notes.isNullOrBlank() },
        )

    fun flowLabel(flow: Flow): String =
        when (flow) {
            Flow.NONE -> "No bleeding"
            Flow.SPOTTING -> "Spotting"
            Flow.LIGHT -> "Light flow"
            Flow.MEDIUM -> "Medium flow"
            Flow.HEAVY -> "Heavy flow"
        }

    fun flowOption(flow: Flow): String =
        when (flow) {
            Flow.NONE -> "None"
            Flow.SPOTTING -> "Spotting"
            Flow.LIGHT -> "Light"
            Flow.MEDIUM -> "Medium"
            Flow.HEAVY -> "Heavy"
        }

    fun symptomLabel(symptom: Symptom): String =
        when (symptom) {
            Symptom.CRAMPS -> "Cramps"
            Symptom.HEADACHE -> "Headache"
            Symptom.BLOATING -> "Bloating"
            Symptom.TENDER_BREASTS -> "Tender breasts"
            Symptom.FATIGUE -> "Fatigue"
            Symptom.BACK_PAIN -> "Back pain"
            Symptom.ACNE -> "Acne"
            Symptom.NAUSEA -> "Nausea"
        }

    private fun symptomIcon(symptom: Symptom): Int =
        when (symptom) {
            Symptom.CRAMPS, Symptom.BACK_PAIN -> R.drawable.ms_bolt
            Symptom.FATIGUE -> R.drawable.ms_battery_low
            else -> R.drawable.ms_sick
        }

    fun moodLabel(mood: Mood): String =
        when (mood) {
            Mood.CALM -> "Calm"
            Mood.HAPPY -> "Happy"
            Mood.IRRITABLE -> "Irritable"
            Mood.ANXIOUS -> "Anxious"
            Mood.LOW -> "Low"
            Mood.ENERGETIC -> "Energetic"
        }

    fun mucusLabel(mucus: CervicalMucus): String =
        when (mucus) {
            CervicalMucus.DRY -> "Dry"
            CervicalMucus.STICKY -> "Sticky"
            CervicalMucus.CREAMY -> "Creamy"
            CervicalMucus.WATERY -> "Watery"
            CervicalMucus.EGG_WHITE -> "Egg white"
        }

    fun testLabel(result: TestResult): String =
        when (result) {
            TestResult.NEGATIVE -> "negative"
            TestResult.POSITIVE -> "positive"
        }

    fun testOption(result: TestResult?): String =
        when (result) {
            null -> "Not taken"
            TestResult.NEGATIVE -> "Negative"
            TestResult.POSITIVE -> "Positive"
        }

    fun sexLabel(sex: SexType): String =
        when (sex) {
            SexType.NONE -> "No sex"
            SexType.PROTECTED -> "Protected sex"
            SexType.UNPROTECTED -> "Unprotected sex"
        }

    fun sexOption(sex: SexType): String =
        when (sex) {
            SexType.NONE -> "None"
            SexType.PROTECTED -> "Protected"
            SexType.UNPROTECTED -> "Unprotected"
        }

    fun pillLabel(state: PillState): String =
        when (state) {
            PillState.TAKEN -> "Pill taken"
            PillState.LATE -> "Pill late"
            PillState.MISSED -> "Pill missed"
        }

    fun pillOption(state: PillState): String =
        when (state) {
            PillState.TAKEN -> "Taken"
            PillState.LATE -> "Late"
            PillState.MISSED -> "Missed"
        }

    fun ringLabel(state: RingState): String =
        when (state) {
            RingState.IN -> "Ring in"
            RingState.OUT -> "Ring out"
        }

    fun ringOption(state: RingState): String =
        when (state) {
            RingState.IN -> "In"
            RingState.OUT -> "Out"
        }
}
