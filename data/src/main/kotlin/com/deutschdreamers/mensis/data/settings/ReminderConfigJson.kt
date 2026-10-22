package com.deutschdreamers.mensis.data.settings

import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.domain.reminders.ReminderOption
import com.deutschdreamers.mensis.domain.reminders.ReminderSetting
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalTime

/**
 * The `reminders_json` column of `tracker_settings`, which is also the `reminders` object of the export:
 *
 * ```
 * {"period": {"enabled": true, "time": "09:00", "option": "two_days_before"},
 *  "discreetTitle": "Reminder", "discreetBody": "Tap to open"}
 * ```
 *
 * One key per [ReminderType] and option names in lower case, like every enum in the export; absent
 * fields mean the default. Reading is lenient: an unknown option or time falls back to the default
 * rather than failing (exports from other versions).
 * The discreet switch itself is the `discreet_wording` column.
 */
object ReminderConfigJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Serializable
    private data class Stored(
        val enabled: Boolean = false,
        val time: String? = null,
        val option: String? = null,
    )

    private const val DISCREET_TITLE = "discreetTitle"
    private const val DISCREET_BODY = "discreetBody"

    fun encode(config: ReminderConfig): String {
        val fields = linkedMapOf<String, JsonElement>()
        for (type in ReminderType.entries) {
            val setting = config.settings[type] ?: continue
            val stored = Stored(setting.enabled, setting.time?.toString(), setting.option?.name?.lowercase())
            fields[type.key] = json.encodeToJsonElement(Stored.serializer(), stored)
        }
        config.discreetTitle?.let { fields[DISCREET_TITLE] = JsonPrimitive(it) }
        config.discreetBody?.let { fields[DISCREET_BODY] = JsonPrimitive(it) }
        return JsonObject(fields).toString()
    }

    fun decode(
        text: String,
        discreet: Boolean,
    ): ReminderConfig {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        val settings =
            ReminderType.entries
                .mapNotNull { type ->
                    val element = root[type.key] ?: return@mapNotNull null
                    val stored =
                        runCatching { json.decodeFromJsonElement(Stored.serializer(), element) }.getOrNull()
                            ?: return@mapNotNull null
                    type to
                        ReminderSetting(
                            enabled = stored.enabled,
                            time = stored.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                            option = stored.option?.let(::optionNamed),
                        )
                }.toMap()
        return ReminderConfig(
            settings = settings,
            discreet = discreet,
            discreetTitle = root.string(DISCREET_TITLE),
            discreetBody = root.string(DISCREET_BODY),
        )
    }

    private val ReminderType.key: String get() = name.lowercase()

    private fun optionNamed(name: String): ReminderOption? =
        ReminderOption.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.takeIf {
                it.isString
            }?.contentOrNull
}
