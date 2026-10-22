package com.deutschdreamers.mensis.backup.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Google Drive REST v3, restricted to the app's hidden `appDataFolder` (scope `drive.appdata`): the
 * app can neither see nor touch the user's other files. Plain HTTPS over OkHttp; the only network
 * code in Mensis. A 401 refreshes the token once and repeats the call.
 */
class DriveAppDataClient(
    private val http: OkHttpClient,
    private val tokens: AccessTokenSource,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) : BackupDrive {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun list(): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url =
                baseUrl
                    .newBuilder()
                    .addPathSegments("drive/v3/files")
                    .addQueryParameter("spaces", APP_DATA_FOLDER)
                    .addQueryParameter("fields", "nextPageToken,files($FILE_FIELDS)")
                    .addQueryParameter("orderBy", "createdTime desc")
                    .addQueryParameter("pageSize", PAGE_SIZE.toString())
                    .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                    .build()
            val page = decode(DriveFileListJson.serializer(), call { Request.Builder().url(url).get() })
            files += page.files.map { it.toDriveFile() }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return files.sortedByDescending { it.createdTime }
    }

    override suspend fun upload(
        name: String,
        bytes: ByteArray,
        appProperties: Map<String, String>,
    ): DriveFile {
        val metadata =
            json.encodeToString(
                DriveUploadMetadata.serializer(),
                DriveUploadMetadata(name, listOf(APP_DATA_FOLDER), appProperties),
            )
        val body =
            MultipartBody
                .Builder()
                .setType(MULTIPART_RELATED)
                .addPart(metadata.toRequestBody(JSON))
                .addPart(bytes.toRequestBody(OCTET_STREAM))
                .build()
        val url =
            baseUrl
                .newBuilder()
                .addPathSegments("upload/drive/v3/files")
                .addQueryParameter("uploadType", "multipart")
                .addQueryParameter("fields", FILE_FIELDS)
                .build()
        return decode(DriveFileJson.serializer(), call { Request.Builder().url(url).post(body) }).toDriveFile()
    }

    override suspend fun download(
        fileId: String,
        maxBytes: Long,
    ): ByteArray {
        val url = fileUrl(fileId).newBuilder().addQueryParameter("alt", "media").build()
        return execute({ Request.Builder().url(url).get() }) { response -> readCapped(response, maxBytes) }
    }

    override suspend fun delete(fileId: String) {
        execute({ Request.Builder().url(fileUrl(fileId)).delete() }) { }
    }

    private fun fileUrl(fileId: String): HttpUrl =
        baseUrl
            .newBuilder()
            .addPathSegments("drive/v3/files")
            .addPathSegment(fileId)
            .build()

    private suspend fun call(request: () -> Request.Builder): String =
        execute(request) { response -> response.body.string() }

    /** Runs the request with a bearer token; on 401 it invalidates the token and tries once more. */
    private suspend fun <T> execute(
        request: () -> Request.Builder,
        read: (Response) -> T,
    ): T {
        var attempt = 0
        while (true) {
            val token = tokens.token()
            val built = request().header("Authorization", "Bearer $token").build()
            val result =
                runInterruptible(Dispatchers.IO) {
                    http.newCall(built).execute().use { response ->
                        when {
                            response.isSuccessful -> Result.success(read(response))
                            else -> Result.failure(error(response))
                        }
                    }
                }
            val failure = result.exceptionOrNull() as? DriveHttpException
            if (failure?.code == UNAUTHORIZED && attempt == 0) {
                tokens.invalidate(token)
                attempt++
                continue
            }
            return result.getOrThrow()
        }
    }

    private fun error(response: Response): DriveHttpException {
        val reason =
            runCatching {
                json
                    .decodeFromString(DriveErrorJson.serializer(), response.body.string())
                    .error
                    ?.errors
                    ?.firstOrNull()
                    ?.reason
            }.getOrNull()
        return DriveHttpException(response.code, reason)
    }

    private fun <T> decode(
        serializer: KSerializer<T>,
        text: String,
    ): T =
        try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw IOException("unexpected Drive response", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("unexpected Drive response", e)
        }

    private fun readCapped(
        response: Response,
        maxBytes: Long,
    ): ByteArray {
        val declared = response.body.contentLength()
        if (declared > maxBytes) throw DriveHttpException(PAYLOAD_TOO_LARGE, "fileTooLarge")
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        response.body.byteStream().use { input ->
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                out.write(chunk, 0, read)
                if (out.size() > maxBytes) throw DriveHttpException(PAYLOAD_TOO_LARGE, "fileTooLarge")
            }
        }
        return out.toByteArray()
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://www.googleapis.com/"
        const val APP_DATA_FOLDER: String = "appDataFolder"
        const val SCOPE_APP_DATA: String = "https://www.googleapis.com/auth/drive.appdata"
        private const val FILE_FIELDS = "id,name,size,createdTime,md5Checksum,appProperties"
        private const val PAGE_SIZE = 100
        private const val UNAUTHORIZED = 401
        private const val PAYLOAD_TOO_LARGE = 413
        private const val CHUNK_BYTES = 8 * 1024
        private val MULTIPART_RELATED = "multipart/related".toMediaType()
        private val JSON = "application/json; charset=UTF-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
