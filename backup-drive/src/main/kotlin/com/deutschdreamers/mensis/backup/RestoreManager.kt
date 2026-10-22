package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.backup.auth.GoogleAuthException
import com.deutschdreamers.mensis.backup.drive.DriveFile
import com.deutschdreamers.mensis.backup.drive.DriveHttpException
import com.deutschdreamers.mensis.backup.drive.ReconnectRequiredException
import com.deutschdreamers.mensis.data.crypto.AuthenticationFailedException
import com.deutschdreamers.mensis.data.crypto.ContainerKey
import com.deutschdreamers.mensis.data.crypto.DamagedContainerException
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.export.ExportFiles
import com.deutschdreamers.mensis.data.export.ExportMigrations
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.export.ExportedUiPrefs
import com.deutschdreamers.mensis.data.export.MensisJson
import com.deutschdreamers.mensis.data.export.applyTo
import com.deutschdreamers.mensis.data.importer.ImportError
import com.deutschdreamers.mensis.data.importer.ImportException
import com.deutschdreamers.mensis.data.importer.ImportLimits
import com.deutschdreamers.mensis.data.importer.ImportReader
import com.deutschdreamers.mensis.data.importer.MensisJsonImporter
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.RestoreResult
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Instant

/** The backup the restore screen offers (design §12 step 2). */
data class BackupFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Instant,
    val device: String?,
    /** Drive's checksum; a download that does not match it was cut short or altered on the way. */
    val md5Checksum: String? = null,
)

enum class RestoreError {
    /** The password does not open the file (or the file was altered; the two look the same). */
    WRONG_PASSWORD,

    /** Truncated or not a Mensis backup. */
    DAMAGED,

    /** Written by a newer Mensis than this one. */
    NEWER_VERSION,

    NETWORK,

    /** Google access was revoked in the meantime; sign in again. */
    RECONNECT,
}

class RestoreException(
    val error: RestoreError,
    cause: Throwable? = null,
) : Exception(error.name, cause)

/** A decrypted, parsed backup, ready to replace what is on the phone. */
class OpenedBackup(
    val file: BackupFileInfo,
    val parsed: ParsedImport,
    val uiPrefs: ExportedUiPrefs?,
    val summary: ExportSummary,
    internal val key: ContainerKey,
)

/** What the restore screen needs; [RestoreManager] in the app, a fake in UI tests. */
interface RestoreSource {
    /** The newest Mensis backup in the account's app folder, or null when there is none. */
    suspend fun latestBackup(email: String): BackupFileInfo?

    suspend fun download(
        email: String,
        file: BackupFileInfo,
    ): ByteArray

    /** Decrypts and reads [bytes]; slow (Argon2id). @throws RestoreException */
    suspend fun open(
        file: BackupFileInfo,
        bytes: ByteArray,
        password: CharArray,
    ): OpenedBackup

    /** What the phone holds now, for the "this replaces …" warning. */
    suspend fun localData(): ExportSummary

    /** Replaces everything on the phone with [opened] and turns backup on for [email]. */
    suspend fun restore(
        email: String,
        opened: OpenedBackup,
    ): RestoreResult
}

/** Writes a restored backup's rows, replacing all rows on the phone; [ImportApplier.replaceAll] in the app. */
fun interface ReplaceAll {
    suspend fun replace(parsed: ParsedImport): RestoreResult
}

/**
 * Restore (design §12): download the newest backup, open it with the password, migrate older
 * format versions, then replace everything on the phone in one transaction. The key the password
 * gave is kept, so backup continues under the same password without asking again.
 */
class RestoreManager(
    private val drives: DriveFactory,
    private val snapshots: SnapshotSource,
    private val replaceAll: ReplaceAll,
    private val uiPrefsStore: UiPrefsStore,
    private val keys: BackupKeyStore,
    private val store: BackupStatusStore,
    private val scheduler: BackupScheduler,
    private val clock: Clock,
    private val migrations: ExportMigrations = ExportMigrations.Default,
) : RestoreSource {
    override suspend fun latestBackup(email: String): BackupFileInfo? =
        drive {
            drives
                .forAccount(email)
                .list()
                .filter(BackupPayload::isBackup)
                .maxByOrNull { it.createdTime }
                ?.toInfo()
        }

    override suspend fun download(
        email: String,
        file: BackupFileInfo,
    ): ByteArray {
        val bytes = drive { drives.forAccount(email).download(file.id, ImportLimits.MAX_FILE_BYTES.toLong()) }
        val intact =
            (file.sizeBytes <= 0 || bytes.size.toLong() == file.sizeBytes) &&
                (file.md5Checksum == null || BackupPayload.md5(bytes) == file.md5Checksum)
        if (!intact) throw RestoreException(RestoreError.DAMAGED)
        return bytes
    }

    override suspend fun open(
        file: BackupFileInfo,
        bytes: ByteArray,
        password: CharArray,
    ): OpenedBackup =
        withContext(Dispatchers.Default) {
            if (!EncryptedContainer.isContainer(bytes)) throw RestoreException(RestoreError.DAMAGED)
            val key =
                try {
                    EncryptedContainer.keyFor(bytes, password)
                } catch (e: DamagedContainerException) {
                    throw RestoreException(RestoreError.DAMAGED, e)
                }
            try {
                val text = ImportReader.text(EncryptedContainer.open(bytes, key))
                val export = MensisJson.decode(text, migrations)
                OpenedBackup(
                    file = file,
                    parsed = MensisJsonImporter.parse(text, migrations),
                    uiPrefs = export.uiPrefs,
                    summary = ExportFiles.summary(export),
                    key = key,
                )
            } catch (e: AuthenticationFailedException) {
                key.destroy()
                throw RestoreException(RestoreError.WRONG_PASSWORD, e)
            } catch (e: DamagedContainerException) {
                key.destroy()
                throw RestoreException(RestoreError.DAMAGED, e)
            } catch (e: ImportException) {
                key.destroy()
                throw RestoreException(
                    if (e.error == ImportError.NEWER_VERSION) RestoreError.NEWER_VERSION else RestoreError.DAMAGED,
                    e,
                )
            }
        }

    override suspend fun localData(): ExportSummary = ExportFiles.summary(snapshots.snapshot())

    override suspend fun restore(
        email: String,
        opened: OpenedBackup,
    ): RestoreResult {
        val result = replaceAll.replace(opened.parsed)
        uiPrefsStore.update { current ->
            (opened.uiPrefs?.applyTo(current) ?: current).copy(onboardingCompleted = true)
        }
        keys.store(opened.key)
        opened.key.destroy()
        val now = Instant.now(clock)
        store.update {
            it.copy(
                enabled = true,
                accountEmail = email,
                passwordSetAt = now,
                lastSuccessAt = opened.file.createdAt,
                lastSuccessBytes = opened.file.sizeBytes,
                lastSuccessDevice = opened.file.device,
                lastCheckedAt = now,
                lastError = null,
                lastErrorAt = null,
                lastFingerprint = null,
                needsReconnect = false,
            )
        }
        scheduler.apply(store.current())
        return result
    }

    private suspend fun <T> drive(block: suspend () -> T): T =
        try {
            block()
        } catch (e: ReconnectRequiredException) {
            throw RestoreException(RestoreError.RECONNECT, e)
        } catch (e: GoogleAuthException) {
            throw RestoreException(RestoreError.RECONNECT, e)
        } catch (e: DriveHttpException) {
            throw RestoreException(if (e.isTransient) RestoreError.NETWORK else RestoreError.DAMAGED, e)
        } catch (e: IOException) {
            throw RestoreException(RestoreError.NETWORK, e)
        }

    private fun DriveFile.toInfo(): BackupFileInfo =
        BackupFileInfo(
            id = id,
            name = name,
            sizeBytes = sizeBytes,
            createdAt = createdTime,
            device = appProperties[BackupPayload.PROP_DEVICE],
            md5Checksum = md5Checksum,
        )
}
