package com.deutschdreamers.mensis.data.db

import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Goal
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.RingState
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.model.WeightUnit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

private val json = Json { ignoreUnknownKeys = true }
private val stringList = ListSerializer(String.serializer())

/** Enum names are stored as-is; unknown names (from a newer app version) map to null rather than crash. */
inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

fun encodeNames(values: Set<Enum<*>>): String = json.encodeToString(stringList, values.map { it.name }.sorted())

inline fun <reified E : Enum<E>> decodeNames(encoded: String): Set<E> =
    runCatching { Json.decodeFromString(ListSerializer(String.serializer()), encoded) }
        .getOrDefault(emptyList())
        .mapNotNull { it.toEnumOrNull<E>() }
        .toSet()

fun DayEntryEntity.toDomain(): DayEntry =
    DayEntry(
        date = LocalDate.parse(date),
        flow = flow.toEnumOrNull<Flow>(),
        temperatureC = temperatureC,
        temperatureTime = temperatureTime?.let(LocalTime::parse),
        symptoms = decodeNames<Symptom>(symptoms),
        moods = decodeNames<Mood>(moods),
        cervicalMucus = cervicalMucus.toEnumOrNull<CervicalMucus>(),
        lhTest = lhTest.toEnumOrNull<TestResult>(),
        pregnancyTest = pregnancyTest.toEnumOrNull<TestResult>(),
        sex = sex.toEnumOrNull<SexType>(),
        pillState = pillState.toEnumOrNull<PillState>(),
        patchChanged = patchChanged,
        ringState = ringState.toEnumOrNull<RingState>(),
        injectionGiven = injectionGiven,
        emergencyContraception = emergencyContraception,
        sleepMinutes = sleepMinutes,
        weightKg = weightKg,
        notes = notes,
    )

fun DayEntry.toEntity(
    id: String,
    createdAt: String,
    updatedAt: String,
    importBatchId: String? = null,
): DayEntryEntity =
    DayEntryEntity(
        id = id,
        date = date.toString(),
        flow = flow?.name,
        temperatureC = temperatureC,
        temperatureTime = temperatureTime?.toString(),
        symptoms = encodeNames(symptoms),
        moods = encodeNames(moods),
        cervicalMucus = cervicalMucus?.name,
        lhTest = lhTest?.name,
        pregnancyTest = pregnancyTest?.name,
        sex = sex?.name,
        pillState = pillState?.name,
        patchChanged = patchChanged,
        ringState = ringState?.name,
        injectionGiven = injectionGiven,
        emergencyContraception = emergencyContraception,
        sleepMinutes = sleepMinutes,
        weightKg = weightKg,
        notes = notes?.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = null,
        importBatchId = importBatchId,
    )

fun ContraceptionPeriodEntity.toDomain(): ContraceptionPeriod? {
    val method = method.toEnumOrNull<ContraceptionMethod>() ?: return null
    return ContraceptionPeriod(
        method = method,
        startDate = LocalDate.parse(startDate),
        endDate = endDate?.let(LocalDate::parse),
        regimen = regimen.toEnumOrNull<Regimen>(),
        dailyTime = dailyTime?.let(LocalTime::parse),
        replaceByDate = replaceByDate?.let(LocalDate::parse),
        checkupDate = checkupDate?.let(LocalDate::parse),
    )
}

fun ContraceptionPeriod.toEntity(
    id: String,
    createdAt: String,
    updatedAt: String,
): ContraceptionPeriodEntity =
    ContraceptionPeriodEntity(
        id = id,
        method = method.name,
        regimen = regimen?.name,
        startDate = startDate.toString(),
        endDate = endDate?.toString(),
        dailyTime = dailyTime?.toString(),
        replaceByDate = replaceByDate?.toString(),
        checkupDate = checkupDate?.toString(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun TrackerSettingsEntity.toDomain(): TrackerSettings {
    val defaults = TrackerSettings()
    return TrackerSettings(
        trackingLevel = trackingLevel.toEnumOrNull<TrackingLevel>() ?: defaults.trackingLevel,
        goal = goal.toEnumOrNull<Goal>() ?: defaults.goal,
        mode = mode.toEnumOrNull<TrackerMode>() ?: defaults.mode,
        pregnancyLmp = pregnancyLmp?.let(LocalDate::parse),
        typicalCycleLength = typicalCycleLength,
        typicalPeriodLength = typicalPeriodLength,
        temperatureUnit = temperatureUnit.toEnumOrNull<TemperatureUnit>() ?: defaults.temperatureUnit,
        weightUnit = weightUnit.toEnumOrNull<WeightUnit>() ?: defaults.weightUnit,
    )
}

fun TrackerSettings.toEntity(
    createdAt: String,
    updatedAt: String,
    existing: TrackerSettingsEntity? = null,
): TrackerSettingsEntity =
    TrackerSettingsEntity(
        trackingLevel = trackingLevel.name,
        goal = goal.name,
        mode = mode.name,
        pregnancyLmp = pregnancyLmp?.toString(),
        typicalCycleLength = typicalCycleLength,
        typicalPeriodLength = typicalPeriodLength,
        temperatureUnit = temperatureUnit.name,
        weightUnit = weightUnit.name,
        remindersJson = existing?.remindersJson ?: "{}",
        discreetWording = existing?.discreetWording ?: true,
        createdAt = existing?.createdAt ?: createdAt,
        updatedAt = updatedAt,
    )
