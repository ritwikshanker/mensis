package com.deutschdreamers.mensis.backup.drive

import kotlinx.serialization.Serializable
import java.io.IOException
import java.time.Instant

/** A file in the app's hidden Drive folder, as the list and upload calls describe it. */
data class DriveFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdTime: Instant,
    val md5Checksum: String?,
    val appProperties: Map<String, String>,
)

/** The four calls backup and restore need, all inside the `appDataFolder` space. */
interface BackupDrive {
    /** Every file in the app folder, newest first. */
    suspend fun list(): List<DriveFile>

    suspend fun upload(
        name: String,
        bytes: ByteArray,
        appProperties: Map<String, String>,
    ): DriveFile

    /** @throws DriveHttpException when the file is larger than [maxBytes] or cannot be read. */
    suspend fun download(
        fileId: String,
        maxBytes: Long,
    ): ByteArray

    suspend fun delete(fileId: String)
}

/** Supplies OAuth access tokens for one Google account. */
interface AccessTokenSource {
    /** @throws ReconnectRequiredException the account needs the user to grant access again. */
    suspend fun token(): String

    /** Drive rejected [token]; the next [token] call must fetch a fresh one. */
    suspend fun invalidate(token: String)
}

/** Drive access was revoked or never granted; only the user can fix it, in the foreground. */
class ReconnectRequiredException(
    message: String = "the Google account needs to be reconnected",
) : Exception(message)

/** Drive answered with an error status. [reason] is the first `errors[].reason` of the response, if any. */
class DriveHttpException(
    val code: Int,
    val reason: String?,
) : IOException("Drive answered $code${reason?.let { " ($it)" } ?: ""}") {
    val isStorageFull: Boolean get() = reason == "storageQuotaExceeded"

    /** Server trouble and rate limits pass; asking again later can help. */
    val isTransient: Boolean
        get() = code >= SERVER_ERROR || code == TOO_MANY_REQUESTS || reason in RATE_LIMIT_REASONS

    private companion object {
        const val SERVER_ERROR = 500
        const val TOO_MANY_REQUESTS = 429
        val RATE_LIMIT_REASONS = setOf("rateLimitExceeded", "userRateLimitExceeded")
    }
}

@Serializable
internal data class DriveFileJson(
    val id: String,
    val name: String = "",
    /** Drive writes int64 values as strings. */
    val size: String? = null,
    val createdTime: String? = null,
    val md5Checksum: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
) {
    fun toDriveFile(): DriveFile =
        DriveFile(
            id = id,
            name = name,
            sizeBytes = size?.toLongOrNull() ?: 0L,
            createdTime = createdTime?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
            md5Checksum = md5Checksum,
            appProperties = appProperties,
        )
}

@Serializable
internal data class DriveFileListJson(
    val files: List<DriveFileJson> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
internal data class DriveUploadMetadata(
    val name: String,
    val parents: List<String>,
    val appProperties: Map<String, String>,
)

@Serializable
internal data class DriveErrorJson(
    val error: DriveErrorBody? = null,
)

@Serializable
internal data class DriveErrorBody(
    val code: Int? = null,
    val errors: List<DriveErrorItem> = emptyList(),
)

@Serializable
internal data class DriveErrorItem(
    val reason: String? = null,
)
