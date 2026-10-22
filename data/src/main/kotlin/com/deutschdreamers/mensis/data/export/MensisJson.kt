package com.deutschdreamers.mensis.data.export

import com.deutschdreamers.mensis.data.importer.ImportError
import com.deutschdreamers.mensis.data.importer.refuse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** One step of the export format: turns a `formatVersion` [from] document into version `from + 1`. */
class ExportMigration(
    val from: Int,
    val migrate: (JsonObject) -> JsonObject,
)

/**
 * Upgrades older export documents step by step to [currentVersion] before they are decoded. Version 1
 * is the first format, so the default chain is empty; each later format bump adds one step here and
 * a fixture test for it.
 */
class ExportMigrations(
    val currentVersion: Int = MensisExport.FORMAT_VERSION,
    private val steps: List<ExportMigration> = emptyList(),
) {
    fun upgrade(document: JsonObject): JsonObject {
        var version =
            (document[FORMAT_VERSION_KEY] as? JsonPrimitive)?.intOrNull
                ?: refuse(ImportError.DAMAGED, "formatVersion missing")
        if (version > currentVersion) refuse(ImportError.NEWER_VERSION)
        if (version < 1) refuse(ImportError.DAMAGED, "formatVersion $version")
        var upgraded = document
        while (version < currentVersion) {
            val step =
                steps.firstOrNull { it.from == version }
                    ?: refuse(ImportError.DAMAGED, "no migration from formatVersion $version")
            version++
            upgraded = JsonObject(step.migrate(upgraded) + (FORMAT_VERSION_KEY to JsonPrimitive(version)))
        }
        return upgraded
    }

    companion object {
        const val FORMAT_VERSION_KEY: String = "formatVersion"
        val Default: ExportMigrations = ExportMigrations()
    }
}

/** Reads and writes [MensisExport] as pretty-printed UTF-8 JSON. */
object MensisJson {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

    fun encode(export: MensisExport): ByteArray =
        (json.encodeToString(MensisExport.serializer(), export) + "\n").toByteArray()

    /** True when [text] is a JSON object that names itself a Mensis export. */
    fun looksLikeMensis(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("{") && trimmed.take(SNIFF_CHARS).contains("\"${MensisExport.FORMAT}\"")
    }

    fun decode(
        text: String,
        migrations: ExportMigrations = ExportMigrations.Default,
    ): MensisExport {
        val root =
            try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (e: SerializationException) {
                refuse(ImportError.DAMAGED, "not valid JSON", e)
            } ?: refuse(ImportError.NOT_THIS_FORMAT)
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != MensisExport.FORMAT) refuse(ImportError.NOT_THIS_FORMAT)
        val upgraded = migrations.upgrade(root)
        return try {
            json.decodeFromJsonElement(MensisExport.serializer(), upgraded)
        } catch (e: SerializationException) {
            refuse(ImportError.DAMAGED, "unexpected structure", e)
        } catch (e: IllegalArgumentException) {
            refuse(ImportError.DAMAGED, "unexpected structure", e)
        }
    }

    /** The format field sits at the top of files Mensis writes; other writers may reorder, hence the margin. */
    private const val SNIFF_CHARS = 4_096
}
