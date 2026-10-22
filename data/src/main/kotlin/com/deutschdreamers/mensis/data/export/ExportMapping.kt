package com.deutschdreamers.mensis.data.export

import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.data.settings.UiPrefs
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/*
 * Rows ↔ export records. Stored enum names are upper case ("EGG_WHITE"); the export writes them in
 * lower case. The mapping is purely textual, so it is lossless even for values this version does
 * not know; importers validate values separately.
 */

private val names = ListSerializer(String.serializer())

private fun String?.exportName(): String? = this?.lowercase()

private fun String?.storedName(): String? = this?.uppercase()

private fun decodeNameList(encoded: String): List<String> =
    runCatching { Json.decodeFromString(names, encoded) }.getOrDefault(emptyList())

fun DayEntryEntity.toExported(): ExportedDayEntry =
    ExportedDayEntry(
        id = id,
        date = date,
        flow = flow.exportName(),
        temperatureC = temperatureC,
        temperatureTime = temperatureTime,
        symptoms = decodeNameList(symptoms).map { it.lowercase() }.sorted(),
        moods = decodeNameList(moods).map { it.lowercase() }.sorted(),
        cervicalMucus = cervicalMucus.exportName(),
        lhTest = lhTest.exportName(),
        pregnancyTest = pregnancyTest.exportName(),
        sex = sex.exportName(),
        pillState = pillState.exportName(),
        patchChanged = patchChanged,
        ringState = ringState.exportName(),
        injectionGiven = injectionGiven,
        emergencyContraception = emergencyContraception,
        sleepMinutes = sleepMinutes,
        weightKg = weightKg,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

fun ExportedDayEntry.toEntity(): DayEntryEntity =
    DayEntryEntity(
        id = id,
        date = date,
        flow = flow.storedName(),
        temperatureC = temperatureC,
        temperatureTime = temperatureTime,
        symptoms = Json.encodeToString(names, symptoms.map { it.uppercase() }.sorted()),
        moods = Json.encodeToString(names, moods.map { it.uppercase() }.sorted()),
        cervicalMucus = cervicalMucus.storedName(),
        lhTest = lhTest.storedName(),
        pregnancyTest = pregnancyTest.storedName(),
        sex = sex.storedName(),
        pillState = pillState.storedName(),
        patchChanged = patchChanged,
        ringState = ringState.storedName(),
        injectionGiven = injectionGiven,
        emergencyContraception = emergencyContraception,
        sleepMinutes = sleepMinutes,
        weightKg = weightKg,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

fun ContraceptionPeriodEntity.toExported(): ExportedContraceptionPeriod =
    ExportedContraceptionPeriod(
        id = id,
        method = method.lowercase(),
        regimen = regimen.exportName(),
        startDate = startDate,
        endDate = endDate,
        dailyTime = dailyTime,
        replaceByDate = replaceByDate,
        checkupDate = checkupDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

fun ExportedContraceptionPeriod.toEntity(): ContraceptionPeriodEntity =
    ContraceptionPeriodEntity(
        id = id,
        method = method.uppercase(),
        regimen = regimen.storedName(),
        startDate = startDate,
        endDate = endDate,
        dailyTime = dailyTime,
        replaceByDate = replaceByDate,
        checkupDate = checkupDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

fun TrackerSettingsEntity.toExported(): ExportedSettings =
    ExportedSettings(
        trackingLevel = trackingLevel.lowercase(),
        goal = goal.lowercase(),
        mode = mode.lowercase(),
        pregnancyLmp = pregnancyLmp,
        typicalCycleLength = typicalCycleLength,
        typicalPeriodLength = typicalPeriodLength,
        temperatureUnit = temperatureUnit.lowercase(),
        weightUnit = weightUnit.lowercase(),
        reminders = runCatching { Json.parseToJsonElement(remindersJson) }.getOrDefault(JsonObject(emptyMap())),
        discreetWording = discreetWording,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ExportedSettings.toEntity(): TrackerSettingsEntity =
    TrackerSettingsEntity(
        trackingLevel = trackingLevel.uppercase(),
        goal = goal.uppercase(),
        mode = mode.uppercase(),
        pregnancyLmp = pregnancyLmp,
        typicalCycleLength = typicalCycleLength,
        typicalPeriodLength = typicalPeriodLength,
        temperatureUnit = temperatureUnit.uppercase(),
        weightUnit = weightUnit.uppercase(),
        remindersJson = reminders.toString(),
        discreetWording = discreetWording,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun UiPrefs.toExported(): ExportedUiPrefs =
    ExportedUiPrefs(
        theme = theme.name.lowercase(),
        appearance = appearance.name.lowercase(),
        lockEnabled = lockEnabled,
        hideInRecents = hideInRecents,
        relockTimeoutMinutes = relockTimeoutMinutes,
    )

/** A restore's device preferences over [current]; values this version does not know keep the current ones. */
fun ExportedUiPrefs.applyTo(current: UiPrefs): UiPrefs =
    current.copy(
        theme = ThemeScheme.entries.firstOrNull { it.name.equals(theme, ignoreCase = true) } ?: current.theme,
        appearance =
            AppearanceMode.entries.firstOrNull { it.name.equals(appearance, ignoreCase = true) }
                ?: current.appearance,
        lockEnabled = lockEnabled,
        hideInRecents = hideInRecents,
        relockTimeoutMinutes = relockTimeoutMinutes,
    )
