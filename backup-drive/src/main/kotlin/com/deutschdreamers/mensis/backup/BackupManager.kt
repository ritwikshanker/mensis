package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.backup.auth.GoogleAuthException
import com.deutschdreamers.mensis.backup.drive.BackupDrive
import com.deutschdreamers.mensis.backup.drive.DriveFile
import com.deutschdreamers.mensis.backup.drive.DriveHttpException
import com.deutschdreamers.mensis.backup.drive.ReconnectRequiredException
import com.deutschdreamers.mensis.data.backup.BackupError
import com.deutschdreamers.mensis.data.crypto.ContainerKey
import com.deutschdreamers.mensis.data.export.MensisExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Instant

/** The steps a run reports while it works (design §11 "Encrypting on the phone, then uploading"). */
enum class BackupPhase { PREPARING, ENCRYPTING, UPLOADING, VERIFYING }

sealed interface BackupRunResult {
    data class Uploaded(
        val file: DriveFile,
    ) : BackupRunResult

    /** Drive already holds a backup of exactly this data. */
    data object Unchanged : BackupRunResult

    data object NotEnabled : BackupRunResult

    data object NeedsReconnect : BackupRunResult

    data class Failed(
        val error: BackupError,
        /** A later attempt may succeed without the user doing anything. */
        val retryable: Boolean,
    ) : BackupRunResult
}

/** The full export to back up; [ExporterSnapshotSource] in the app. */
fun interface SnapshotSource {
    suspend fun snapshot(): MensisExport
}

/** The Drive app folder of one account. */
fun interface DriveFactory {
    fun forAccount(email: String): BackupDrive
}

/** Tells the user, outside the app, that Google access needs renewing. Posted only by background runs. */
interface ReconnectNotifier {
    fun notifyReconnect()

    fun cancel()
}

/**
 * One backup run: read a snapshot, skip it when Drive already has the same data, otherwise seal it
 * with the stored key, upload, verify the upload by size and MD5, and keep only the
 * [KEEP_BACKUPS] newest backups. Runs are serialized; the outcome is written to [BackupStatusStore].
 */
class BackupManager(
    private val snapshots: SnapshotSource,
    private val keys: BackupKeyStore,
    private val drives: DriveFactory,
    private val store: BackupStatusStore,
    private val notifier: ReconnectNotifier,
    private val clock: Clock,
    private val device: String,
) {
    private val mutex = Mutex()

    /**
     * @param force upload even when the data has not changed (a new password needs a new file).
     * @param interactive started from the screen: a reconnect is shown there, not as a notification.
     */
    suspend fun run(
        force: Boolean = false,
        interactive: Boolean = false,
        onPhase: suspend (BackupPhase) -> Unit = {},
    ): BackupRunResult =
        mutex.withLock {
            val config = store.current()
            val email = config.accountEmail
            if (!config.enabled || email == null) return@withLock BackupRunResult.NotEnabled
            val key = keys.load() ?: return@withLock failed(BackupError.NO_PASSWORD, retryable = false)
            try {
                runWith(drives.forAccount(email), key, config, force, onPhase)
            } catch (expected: ReconnectRequiredException) {
                store.update { it.copy(needsReconnect = true) }
                if (!interactive) notifier.notifyReconnect()
                BackupRunResult.NeedsReconnect
            } catch (e: DriveHttpException) {
                when {
                    e.isStorageFull -> failed(BackupError.DRIVE_FULL, retryable = false)
                    e.isTransient -> failed(BackupError.SERVER, retryable = true)
                    else -> failed(BackupError.OTHER, retryable = false)
                }
            } catch (expected: IOException) {
                failed(BackupError.NETWORK, retryable = true)
            } catch (expected: GoogleAuthException) {
                failed(BackupError.OTHER, retryable = true)
            } finally {
                key.destroy()
            }
        }

    private suspend fun runWith(
        drive: BackupDrive,
        key: ContainerKey,
        config: BackupConfig,
        force: Boolean,
        onPhase: suspend (BackupPhase) -> Unit,
    ): BackupRunResult {
        onPhase(BackupPhase.PREPARING)
        val export = snapshots.snapshot()
        val keyId = BackupPayload.keyId(key)
        val fingerprint = withContext(Dispatchers.Default) { BackupPayload.fingerprint(export, key) }
        val existing = drive.list().filter(BackupPayload::isBackup)
        val newest = existing.filter { it.keyId == keyId }.maxByOrNull { it.createdTime }
        if (!force && newest != null && newest.appProperties[BackupPayload.PROP_FINGERPRINT] == fingerprint) {
            prune(drive, existing, keyId, config.retiredKeyIds)
            val now = Instant.now(clock)
            store.update {
                it.copy(
                    lastCheckedAt = now,
                    lastError = null,
                    lastErrorAt = null,
                    needsReconnect = false,
                    lastFingerprint = fingerprint,
                    lastSuccessAt = newest.createdTime,
                    lastSuccessBytes = newest.sizeBytes,
                    lastSuccessDevice = newest.appProperties[BackupPayload.PROP_DEVICE],
                )
            }
            notifier.cancel()
            return BackupRunResult.Unchanged
        }
        onPhase(BackupPhase.ENCRYPTING)
        val sealed = withContext(Dispatchers.Default) { BackupPayload.seal(export, key) }
        onPhase(BackupPhase.UPLOADING)
        val uploaded =
            drive.upload(
                BackupPayload.FILE_NAME,
                sealed,
                BackupPayload.appProperties(export, fingerprint, keyId, device),
            )
        onPhase(BackupPhase.VERIFYING)
        if (uploaded.sizeBytes != sealed.size.toLong() || uploaded.md5Checksum != BackupPayload.md5(sealed)) {
            runCatching { drive.delete(uploaded.id) }
            return failed(BackupError.VERIFY_FAILED, retryable = true)
        }
        prune(drive, existing + uploaded, keyId, config.retiredKeyIds)
        val now = Instant.now(clock)
        store.update {
            it.copy(
                lastSuccessAt = uploaded.createdTime.takeIf { time -> time != Instant.EPOCH } ?: now,
                lastSuccessBytes = sealed.size.toLong(),
                lastSuccessDevice = device,
                lastCheckedAt = now,
                lastError = null,
                lastErrorAt = null,
                lastFingerprint = fingerprint,
                needsReconnect = false,
            )
        }
        notifier.cancel()
        return BackupRunResult.Uploaded(uploaded)
    }

    /**
     * Keeps the [KEEP_BACKUPS] newest backups under the current password and deletes backups under
     * passwords the user replaced. Backups under any other password (another phone, an earlier install)
     * are left alone: they may be the only copy of someone's history. Deletion failures wait for the
     * next run.
     */
    private suspend fun prune(
        drive: BackupDrive,
        files: List<DriveFile>,
        keyId: String,
        retired: Set<String>,
    ) {
        val current = files.filter { it.keyId == keyId }.sortedByDescending { it.createdTime }
        val obsolete = current.drop(KEEP_BACKUPS) + files.filter { it.keyId in retired && it.keyId != keyId }
        val failures = obsolete.count { file -> runCatching { drive.delete(file.id) }.isFailure }
        if (failures == 0 && retired.isNotEmpty()) store.update { it.copy(retiredKeyIds = emptySet()) }
    }

    private suspend fun failed(
        error: BackupError,
        retryable: Boolean,
    ): BackupRunResult.Failed {
        val now = Instant.now(clock)
        store.update { it.copy(lastError = error, lastErrorAt = now) }
        return BackupRunResult.Failed(error, retryable)
    }

    private val DriveFile.keyId: String? get() = appProperties[BackupPayload.PROP_KEY_ID]

    companion object {
        /** Plan: "prune to the 2 newest". */
        const val KEEP_BACKUPS: Int = 2
    }
}
