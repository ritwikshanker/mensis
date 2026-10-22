package com.deutschdreamers.mensis.data.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/** Why the last backup run failed, as far as the user needs to know. */
enum class BackupError {
    /** No connection, or the connection dropped. Retried by the scheduler. */
    NETWORK,

    /** Google Drive answered with a server error. Retried by the scheduler. */
    SERVER,

    /** The account's Drive storage is full. */
    DRIVE_FULL,

    /** The uploaded file did not match what was sent; it was deleted again. */
    VERIFY_FAILED,

    /** The stored backup key is gone (for example after the phone's Keystore was reset). */
    NO_PASSWORD,

    /** Anything else. */
    OTHER,
}

/** What the Today banner and the Settings row need to know about Drive backup. */
sealed interface BackupStatus {
    /** Drive backup cannot be used here: no Google Play services, or no web client ID in this build. */
    data object Unavailable : BackupStatus

    /** Backup is off: data exists only on the phone. */
    data object Off : BackupStatus

    /** Backup is on and the last run succeeded. */
    data class Idle(
        val lastSuccessAt: Instant?,
    ) : BackupStatus

    /** The last run failed; the scheduler will retry. */
    data class Failing(
        val error: BackupError,
        /** Retries wait for an unmetered network ("It will retry on Wi-Fi"). */
        val wifiOnly: Boolean,
    ) : BackupStatus

    /** The last success is older than twice the backup interval. */
    data class Overdue(
        val daysSinceSuccess: Int,
    ) : BackupStatus

    /** The Google account needs interactive re-authorisation before the next run. */
    data object NeedsReconnect : BackupStatus
}

/** Provided by the backup module; [NoBackupStatusSource] reports [BackupStatus.Off] for tests and previews. */
interface BackupStatusSource {
    val status: Flow<BackupStatus>
}

/** Starts a backup right away (Today's "Retry" and "Back up now"); provided by the backup module. */
fun interface BackupTrigger {
    fun backUpNow()
}

object NoBackupStatusSource : BackupStatusSource {
    override val status: Flow<BackupStatus> = flowOf(BackupStatus.Off)
}
