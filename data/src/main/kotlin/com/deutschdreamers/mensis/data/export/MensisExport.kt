package com.deutschdreamers.mensis.data.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The Mensis JSON export, documented in `docs/export-format.md` and `docs/mensis-export.schema.json`.
 * Lossless: every stored row is included, tombstones too, with its id and timestamps. Values are the
 * stored enum names in lower case ("medium", "egg_white"), dates and times ISO-8601, temperatures °C
 * and weights kg. Null fields are left out. Properties are written in declaration order, rows sorted,
 * so the same data always produces the same file apart from [exportedAt].
 *
 * [formatVersion] changes only when a reader written for the previous version would misread the file;
 * fields may be added without a bump and readers ignore fields they do not know.
 */
@Serializable
data class MensisExport(
    val format: String = FORMAT,
    val formatVersion: Int = FORMAT_VERSION,
    /** The Room schema version of the database the file was written from. */
    val schemaVersion: Int,
    val exportedAt: String,
    val app: ExportedApp,
    val settings: ExportedSettings? = null,
    val uiPrefs: ExportedUiPrefs? = null,
    val contraceptionPeriods: List<ExportedContraceptionPeriod> = emptyList(),
    val dayEntries: List<ExportedDayEntry> = emptyList(),
) {
    companion object {
        const val FORMAT: String = "mensis-export"
        const val FORMAT_VERSION: Int = 1
    }
}

@Serializable
data class ExportedApp(
    val versionName: String,
    val versionCode: Int,
)

@Serializable
data class ExportedSettings(
    val trackingLevel: String,
    val goal: String,
    val mode: String,
    val pregnancyLmp: String? = null,
    val typicalCycleLength: Int,
    val typicalPeriodLength: Int,
    val temperatureUnit: String,
    val weightUnit: String,
    val reminders: JsonElement = JsonObject(emptyMap()),
    val discreetWording: Boolean = true,
    val createdAt: String,
    val updatedAt: String,
)

/** Device preferences; exported for completeness, applied only by a full restore, never by an import. */
@Serializable
data class ExportedUiPrefs(
    val theme: String,
    val appearance: String,
    val lockEnabled: Boolean,
    val hideInRecents: Boolean,
    val relockTimeoutMinutes: Int,
)

@Serializable
data class ExportedContraceptionPeriod(
    val id: String,
    val method: String,
    val regimen: String? = null,
    val startDate: String,
    val endDate: String? = null,
    val dailyTime: String? = null,
    val replaceByDate: String? = null,
    val checkupDate: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class ExportedDayEntry(
    val id: String,
    val date: String,
    val flow: String? = null,
    val temperatureC: Double? = null,
    val temperatureTime: String? = null,
    val symptoms: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val cervicalMucus: String? = null,
    val lhTest: String? = null,
    val pregnancyTest: String? = null,
    val sex: String? = null,
    val pillState: String? = null,
    val patchChanged: Boolean? = null,
    val ringState: String? = null,
    val injectionGiven: Boolean? = null,
    val emergencyContraception: Boolean? = null,
    val sleepMinutes: Int? = null,
    val weightKg: Double? = null,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)
