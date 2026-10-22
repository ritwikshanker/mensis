package com.deutschdreamers.mensis.data.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

/** What the Today banner and the Settings row need to know about Drive backup. */
sealed interface BackupStatus {
    /** Backup is off (or unavailable in this build): data exists only on the phone. */
    data object Off : BackupStatus

    /** Backup is on and the last run succeeded. */
    data class Idle(
        val lastSuccessAt: Instant?,
    ) : BackupStatus

    /** The last run failed; the scheduler will retry. */
    data class Failing(
        val lastError: String,
    ) : BackupStatus

    /** The last success is older than twice the backup interval. */
    data class Overdue(
        val daysSinceSuccess: Int,
    ) : BackupStatus

    /** The Google account needs interactive re-authorisation before the next run. */
    data object NeedsReconnect : BackupStatus
}

/** Provided by the backup module once it exists; until then [NoBackupStatusSource] reports [BackupStatus.Off]. */
interface BackupStatusSource {
    val status: Flow<BackupStatus>
}

object NoBackupStatusSource : BackupStatusSource {
    override val status: Flow<BackupStatus> = flowOf(BackupStatus.Off)
}
