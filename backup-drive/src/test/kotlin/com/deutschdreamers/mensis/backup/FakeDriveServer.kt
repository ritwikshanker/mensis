package com.deutschdreamers.mensis.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.MultipartReader
import okio.Buffer
import java.security.MessageDigest
import java.time.Instant
import java.util.Collections

/**
 * A small stand-in for Drive's app folder behind MockWebServer: list (paged, newest first),
 * multipart upload, download and delete, with bearer-token checks. Enough to run backup and restore
 * end to end over real HTTP.
 */
class FakeDriveServer : Dispatcher() {
    class StoredFile(
        val id: String,
        val name: String,
        val bytes: ByteArray,
        val createdTime: Instant,
        val appProperties: Map<String, String>,
    )

    val files: MutableMap<String, StoredFile> = Collections.synchronizedMap(linkedMapOf())
    val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())

    /** Requests must carry this token; anything else gets 401. */
    @Volatile var acceptedToken: String = "token-1"

    /** Reported md5 of the next upload is wrong, as if the bytes were damaged on the way. */
    @Volatile var corruptNextUpload: Boolean = false

    /** The next request of any kind gets this response instead. */
    @Volatile var failNext: MockResponse? = null

    /** Files per list page; Drive may return fewer than asked for. */
    var pageSize: Int = 2

    private var nextId = 1
    private var time = Instant.parse("2026-10-20T21:14:00Z")

    fun put(
        name: String,
        bytes: ByteArray,
        appProperties: Map<String, String>,
        createdTime: Instant? = null,
    ): StoredFile =
        synchronized(this) {
            val created = createdTime ?: time.also { time = time.plusSeconds(60) }
            StoredFile("file-${nextId++}", name, bytes, created, appProperties).also { files[it.id] = it }
        }

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        if (request.headers["Authorization"] != "Bearer $acceptedToken") {
            return json(UNAUTHORIZED, """{"error":{"code":401,"errors":[{"reason":"authError"}]}}""")
        }
        failNext?.let {
            failNext = null
            return it
        }
        val path = request.url.encodedPath
        return when {
            request.method == "POST" && path == "/upload/drive/v3/files" -> upload(request)
            request.method == "GET" && path == "/drive/v3/files" -> list(request)
            request.method == "GET" && path.startsWith("/drive/v3/files/") -> download(path.substringAfterLast('/'))
            request.method == "DELETE" && path.startsWith("/drive/v3/files/") -> delete(path.substringAfterLast('/'))
            else -> json(NOT_FOUND, """{"error":{"code":404}}""")
        }
    }

    private fun list(request: RecordedRequest): MockResponse {
        check(request.url.queryParameter("spaces") == "appDataFolder")
        val all = synchronized(files) { files.values.sortedByDescending { it.createdTime } }
        val start = request.url.queryParameter("pageToken")?.toInt() ?: 0
        val page = all.drop(start).take(pageSize)
        val next = (start + pageSize).takeIf { it < all.size }
        val body =
            JsonObject(
                buildMap {
                    put("files", JsonArray(page.map { it.toJson() }))
                    next?.let { put("nextPageToken", JsonPrimitive(it.toString())) }
                },
            )
        return json(OK, body.toString())
    }

    private fun upload(request: RecordedRequest): MockResponse {
        check(request.url.queryParameter("uploadType") == "multipart")
        val boundary = requireNotNull(request.headers["Content-Type"]).substringAfter("boundary=")
        val reader = MultipartReader(Buffer().write(requireNotNull(request.body)), boundary)
        val metadata = Json.parseToJsonElement(requireNotNull(reader.nextPart()).body.readUtf8()).jsonObject
        val bytes = requireNotNull(reader.nextPart()).body.readByteArray()
        check(metadata["parents"].toString() == """["appDataFolder"]""") { "uploads must go to the app folder" }
        val stored =
            put(
                metadata.getValue("name").jsonPrimitive.content,
                bytes,
                metadata["appProperties"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
            )
        val described =
            if (corruptNextUpload) {
                corruptNextUpload = false
                stored.toJson(md5 = "0".repeat(MD5_HEX))
            } else {
                stored.toJson()
            }
        return json(OK, described.toString())
    }

    private fun download(id: String): MockResponse {
        val file = files[id] ?: return json(NOT_FOUND, """{"error":{"code":404}}""")
        return MockResponse
            .Builder()
            .code(OK)
            .setHeader("Content-Type", "application/octet-stream")
            .body(Buffer().write(file.bytes))
            .build()
    }

    private fun delete(id: String): MockResponse {
        files.remove(id) ?: return json(NOT_FOUND, """{"error":{"code":404}}""")
        return MockResponse.Builder().code(NO_CONTENT).build()
    }

    private fun StoredFile.toJson(md5: String = md5(bytes)): JsonObject =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "name" to JsonPrimitive(name),
                "size" to JsonPrimitive(bytes.size.toString()),
                "createdTime" to JsonPrimitive(createdTime.toString()),
                "md5Checksum" to JsonPrimitive(md5),
                "appProperties" to JsonObject(appProperties.mapValues { JsonPrimitive(it.value) }),
            ),
        )

    companion object {
        const val OK = 200
        const val NO_CONTENT = 204
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        private const val MD5_HEX = 32

        fun md5(bytes: ByteArray): String =
            MessageDigest.getInstance("MD5").digest(bytes).joinToString("") {
                "%02x".format(it)
            }

        fun json(
            code: Int,
            body: String,
        ): MockResponse =
            MockResponse
                .Builder()
                .code(code)
                .setHeader("Content-Type", "application/json; charset=UTF-8")
                .body(body)
                .build()
    }
}
