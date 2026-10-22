package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.crypto.KdfParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant

/** What the Backup screen and onboarding change; the runs themselves belong to [BackupManager]. */
class BackupController(
    private val store: BackupStatusStore,
    private val keys: BackupKeyStore,
    private val scheduler: BackupScheduler,
    private val manager: BackupManager,
    private val drives: DriveFactory,
    private val gateway: GoogleAccountGateway,
    private val notifier: ReconnectNotifier,
    private val clock: Clock,
    private val kdfParams: KdfParams = KdfParams.DEFAULT,
) {
    val config: Flow<BackupConfig> get() = store.config

    fun availability(): BackupAvailability = gateway.availability()

    fun manualRuns(): Flow<ManualBackupState> = scheduler.manualRuns()

    /**
     * Derives the backup key from [password] (Argon2id, a second or two), keeps it wrapped in the
     * Keystore, turns scheduled backups on for [email] and starts the first backup.
     */
    suspend fun turnOn(
        email: String,
        password: CharArray,
    ) {
        val key = withContext(Dispatchers.Default) { EncryptedContainer.deriveKey(password, kdfParams) }
        try {
            keys.store(key)
        } finally {
            key.destroy()
        }
        val now = Instant.now(clock)
        store.update {
            it.copy(
                enabled = true,
                accountEmail = email,
                passwordSetAt = now,
                needsReconnect = false,
                lastError = null,
                lastErrorAt = null,
                lastFingerprint = null,
            )
        }
        scheduler.apply(store.current())
        scheduler.backUpNow()
    }

    /** The user granted access again (or picked another account); the next backup can run. */
    suspend fun reconnected(email: String) {
        store.update { it.copy(accountEmail = email, needsReconnect = false) }
        notifier.cancel()
        scheduler.backUpNow()
    }

    suspend fun setFrequency(frequency: BackupFrequency) {
        store.update { it.copy(frequency = frequency) }
        scheduler.apply(store.current())
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        store.update { it.copy(wifiOnly = wifiOnly) }
        scheduler.apply(store.current())
    }

    fun backUpNow() = scheduler.backUpNow()

    /**
     * A new password: a new key, a fresh backup sealed with it, and backups under the old password
     * are deleted once that backup is safely on Drive.
     */
    suspend fun changePassword(password: CharArray): BackupRunResult {
        val old = keys.load()
        val key = withContext(Dispatchers.Default) { EncryptedContainer.deriveKey(password, kdfParams) }
        try {
            keys.store(key)
        } finally {
            key.destroy()
        }
        val retired =
            old?.let { previous ->
                try {
                    BackupPayload.keyId(previous)
                } finally {
                    previous.destroy()
                }
            }
        val now = Instant.now(clock)
        store.update {
            it.copy(
                passwordSetAt = now,
                lastFingerprint = null,
                retiredKeyIds = it.retiredKeyIds + listOfNotNull(retired),
            )
        }
        return manager.run(force = true, interactive = true)
    }

    /**
     * Stops backing up from this phone: cancels the schedule, forgets the key and the account and
     * revokes Mensis's Drive access. Backups already on Drive stay there for a later restore.
     */
    suspend fun turnOff() {
        scheduler.cancelAll()
        val email = store.current().accountEmail
        keys.delete()
        store.update { BackupConfig(frequency = it.frequency, wifiOnly = it.wifiOnly) }
        notifier.cancel()
        email?.let { gateway.disconnect(it) }
    }

    /**
     * Deletes every Mensis backup in the account's app folder (Backup screen, delete-all-data).
     * @return how many files were deleted.
     */
    suspend fun deleteDriveBackups(): Int {
        val email = store.current().accountEmail ?: return 0
        val drive = drives.forAccount(email)
        val backups = drive.list().filter(BackupPayload::isBackup)
        backups.forEach { drive.delete(it.id) }
        store.update {
            it.copy(lastSuccessAt = null, lastSuccessBytes = null, lastSuccessDevice = null, lastFingerprint = null)
        }
        return backups.size
    }
}
