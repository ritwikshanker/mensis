package com.deutschdreamers.mensis.ui.screens.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.domain.contraception.MethodSchedule
import com.deutschdreamers.mensis.domain.contraception.PackCalendar
import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.RingState
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.model.WeightUnit
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.domain.today.CyclePhase
import com.deutschdreamers.mensis.domain.today.TodayState
import com.deutschdreamers.mensis.domain.today.TodayStateResolver
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.format.MethodLabels
import com.deutschdreamers.mensis.ui.format.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** The editable copy of a day. Text fields keep what the user typed; parsing happens on save. */
data class LogDraft(
    val date: LocalDate,
    val flow: Flow? = null,
    val temperatureText: String = "",
    val temperatureTime: LocalTime? = null,
    val symptoms: Set<Symptom> = emptySet(),
    val moods: Set<Mood> = emptySet(),
    val cervicalMucus: CervicalMucus? = null,
    val lhTest: TestResult? = null,
    val pregnancyTest: TestResult? = null,
    val sex: SexType? = null,
    val pillState: PillState? = null,
    val patchChanged: Boolean = false,
    val ringState: RingState? = null,
    val injectionGiven: Boolean = false,
    val emergencyContraception: Boolean = false,
    val sleepText: String = "",
    val weightText: String = "",
    val notes: String = "",
)

enum class MethodItem { NONE, PILL, PATCH, RING, INJECTION, LARC }

data class LogMethodUi(
    val item: MethodItem,
    val title: String,
    val helper: String,
    val showEmergencyContraception: Boolean,
)

data class LogUiState(
    val draft: LogDraft,
    val dateLabel: String,
    val subtitle: String,
    val trackingLevel: TrackingLevel,
    val temperatureUnit: TemperatureUnit,
    val weightUnit: WeightUnit,
    val method: LogMethodUi,
    val testsFirst: Boolean,
    val temperatureError: Boolean = false,
    val sleepError: Boolean = false,
    val weightError: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class DailyLogViewModel
    @Inject
    constructor(
        private val trackerData: TrackerDataRepository,
        private val dayEntries: DayEntryRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow<LogUiState?>(null)
        val uiState: StateFlow<LogUiState?> = state

        fun open(request: LogRequest) {
            state.value = null
            viewModelScope.launch {
                val data = trackerData.observe().first()
                val existing = dayEntries.get(request.date)
                val settings = data.settings
                val draft =
                    (existing?.toDraft(settings) ?: LogDraft(request.date)).let { d ->
                        if (request.presetFlow != null && d.flow == null) d.copy(flow = request.presetFlow) else d
                    }
                val model = TodayStateResolver.resolve(data.inputFor(request.date))
                val method = data.contraception.activeOn(request.date)
                state.value =
                    LogUiState(
                        draft = draft,
                        dateLabel = Formats.weekdayDayMonth(request.date),
                        subtitle = subtitle(model.state, model.numeral, model.phase),
                        trackingLevel = settings.trackingLevel,
                        temperatureUnit = settings.temperatureUnit,
                        weightUnit = settings.weightUnit,
                        method = methodUi(method, request.date, data.entries),
                        testsFirst = request.testsFirst,
                    )
            }
        }

        fun close() {
            state.value = null
        }

        fun update(transform: (LogDraft) -> LogDraft) =
            state.update {
                it?.copy(draft = transform(it.draft), temperatureError = false, sleepError = false, weightError = false)
            }

        /** Persists only non-empty fields; an all-empty draft removes the day. */
        fun save() {
            val current = state.value ?: return
            val draft = current.draft
            val temperature = parseDecimal(draft.temperatureText)
            val sleep = parseSleep(draft.sleepText)
            val weight = parseDecimal(draft.weightText)
            val temperatureError = draft.temperatureText.isNotBlank() && temperature == null
            val sleepError = draft.sleepText.isNotBlank() && sleep == null
            val weightError = draft.weightText.isNotBlank() && weight == null
            if (temperatureError || sleepError || weightError) {
                state.value =
                    current.copy(
                        temperatureError = temperatureError,
                        sleepError = sleepError,
                        weightError = weightError,
                    )
                return
            }
            val entry =
                DayEntry(
                    date = draft.date,
                    flow = draft.flow,
                    temperatureC = temperature?.let { Units.toCelsius(it, current.temperatureUnit) },
                    temperatureTime = if (temperature != null) draft.temperatureTime else null,
                    symptoms = draft.symptoms,
                    moods = draft.moods,
                    cervicalMucus = draft.cervicalMucus,
                    lhTest = draft.lhTest,
                    pregnancyTest = draft.pregnancyTest,
                    sex = draft.sex,
                    pillState = draft.pillState,
                    patchChanged = draft.patchChanged.takeIf { it },
                    ringState = draft.ringState,
                    injectionGiven = draft.injectionGiven.takeIf { it },
                    emergencyContraception = draft.emergencyContraception.takeIf { it },
                    sleepMinutes = sleep,
                    weightKg = weight?.let { Units.toKilograms(it, current.weightUnit) },
                    notes = draft.notes.takeIf { it.isNotBlank() },
                )
            viewModelScope.launch {
                dayEntries.save(entry)
                state.update { it?.copy(saved = true) }
            }
        }

        private fun subtitle(
            todayState: TodayState,
            numeral: Int?,
            phase: CyclePhase?,
        ): String =
            when (todayState) {
                TodayState.FIRST_LAUNCH -> "No cycle yet"
                TodayState.PREGNANCY -> "Pregnancy mode"
                TodayState.SUPPRESSED -> "Predictions paused"
                TodayState.CYCLIC -> "Pack day $numeral"
                else -> {
                    val phaseName =
                        when (phase) {
                            CyclePhase.PERIOD -> "Period"
                            CyclePhase.FOLLICULAR -> "Follicular"
                            CyclePhase.FERTILE -> "Fertile window"
                            CyclePhase.LUTEAL, null -> "Luteal"
                        }
                    "Cycle day $numeral $MIDDLE_DOT $phaseName"
                }
            }

        private fun methodUi(
            method: ContraceptionPeriod?,
            date: LocalDate,
            entries: List<DayEntry>,
        ): LogMethodUi {
            if (method == null || method.method == ContraceptionMethod.NONE) {
                return LogMethodUi(MethodItem.NONE, "", "", showEmergencyContraception = true)
            }
            val name = MethodLabels.name(method.method)
            val breakDays = PredictionConstants.PACK_LENGTH_DAYS - PredictionConstants.PACK_ACTIVE_DAYS
            return when (method.method) {
                ContraceptionMethod.COMBINED_PILL, ContraceptionMethod.PROGESTIN_ONLY_PILL ->
                    LogMethodUi(
                        MethodItem.PILL,
                        name,
                        MethodLabels.pillHelp(method.method),
                        showEmergencyContraception = true,
                    )
                ContraceptionMethod.PATCH -> {
                    val position = PackCalendar.position(method.startDate, date)
                    val helper =
                        if (position.isBreak) {
                            "Patch-free week $MIDDLE_DOT next patch ${Formats.weekdayDayMonth(position.nextPackStart)}"
                        } else {
                            val from = Formats.weekdayDayMonth(position.breakStart)
                            "Week ${position.weekOfPack} of 3 $MIDDLE_DOT patch-free week from $from"
                        }
                    LogMethodUi(MethodItem.PATCH, name, helper, showEmergencyContraception = true)
                }
                ContraceptionMethod.RING -> {
                    val position = PackCalendar.position(method.startDate, date)
                    val helper =
                        if (position.isBreak) {
                            "Ring out $MIDDLE_DOT back in ${Formats.weekdayDayMonth(position.nextPackStart)}"
                        } else {
                            "Week ${position.weekOfPack} of 3 $MIDDLE_DOT out from " +
                                "${Formats.weekdayDayMonth(position.breakStart)} for $breakDays days"
                        }
                    LogMethodUi(MethodItem.RING, name, helper, showEmergencyContraception = true)
                }
                ContraceptionMethod.INJECTION -> {
                    val last = MethodSchedule.lastInjectionDate(method, entries)
                    val next = MethodSchedule.nextInjectionDate(method, entries)
                    val helper =
                        "Last ${Formats.dayMonth(
                            last,
                        )} $MIDDLE_DOT next due ${next?.let(Formats::dayMonthYear) ?: ""}".trim()
                    LogMethodUi(MethodItem.INJECTION, name, helper, showEmergencyContraception = false)
                }
                ContraceptionMethod.IMPLANT, ContraceptionMethod.HORMONAL_IUD, ContraceptionMethod.COPPER_IUD -> {
                    val replaceBy = MethodSchedule.replaceByDate(method)?.let(Formats::monthYear) ?: ""
                    LogMethodUi(
                        MethodItem.LARC,
                        name,
                        "No daily entry $MIDDLE_DOT Replace by $replaceBy",
                        showEmergencyContraception = false,
                    )
                }
                ContraceptionMethod.NONE -> LogMethodUi(MethodItem.NONE, "", "", showEmergencyContraception = true)
            }
        }

        private fun DayEntry.toDraft(settings: TrackerSettings): LogDraft =
            LogDraft(
                date = date,
                flow = flow,
                temperatureText =
                    temperatureC?.let { Formats.temperature(Units.toDisplayTemperature(it, settings.temperatureUnit)) }
                        ?: "",
                temperatureTime = temperatureTime,
                symptoms = symptoms,
                moods = moods,
                cervicalMucus = cervicalMucus,
                lhTest = lhTest,
                pregnancyTest = pregnancyTest,
                sex = sex,
                pillState = pillState,
                patchChanged = patchChanged == true,
                ringState = ringState,
                injectionGiven = injectionGiven == true,
                emergencyContraception = emergencyContraception == true,
                sleepText = sleepMinutes?.let { formatSleepInput(it) } ?: "",
                weightText = weightKg?.let { Formats.oneDecimal(Units.toDisplayWeight(it, settings.weightUnit)) } ?: "",
                notes = notes ?: "",
            )

        companion object {
            private const val MINUTES_PER_HOUR = 60
            private val decimal = Regex("""\d{1,3}([.,]\d{1,2})?""")
            private val hoursMinutes = Regex("""(\d{1,2})[:h]\s*(\d{1,2})?\s*(min|m)?""")

            fun parseDecimal(text: String): Double? {
                val trimmed = text.trim()
                if (!decimal.matches(trimmed)) return null
                return trimmed.replace(',', '.').toDoubleOrNull()
            }

            /** Accepts "7:20", "7h 20", "7h20min", "7h", or decimal hours "7.5". */
            fun parseSleep(text: String): Int? {
                val trimmed = text.trim().lowercase()
                val match = hoursMinutes.matchEntire(trimmed)
                return when {
                    trimmed.isEmpty() -> null
                    match != null -> {
                        val hours = match.groupValues[1].toInt()
                        val minutes = match.groupValues[2].toIntOrNull() ?: 0
                        if (minutes < MINUTES_PER_HOUR) hours * MINUTES_PER_HOUR + minutes else null
                    }
                    else -> parseDecimal(trimmed)?.let { (it * MINUTES_PER_HOUR).toInt() }
                }
            }

            fun formatSleepInput(minutes: Int): String =
                "${minutes / MINUTES_PER_HOUR}:${(minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')}"
        }
    }
