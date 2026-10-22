package com.deutschdreamers.mensis.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.deutschdreamers.mensis.data.backup.BackupTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** "Back up now" as the Backup screen shows it. */
sealed interface ManualBackupState {
    data object None : ManualBackupState

    /** Queued until the phone is online. */
    data object Waiting : ManualBackupState

    data class Running(
        val phase: BackupPhase?,
    ) : ManualBackupState

    data object Succeeded : ManualBackupState

    /** The reason is in [BackupConfig.lastError] or [BackupConfig.needsReconnect]. */
    data object Failed : ManualBackupState
}

/** Keeps WorkManager in line with [BackupConfig]. */
interface BackupScheduler : BackupTrigger {
    /** Schedules the periodic backup for [config], or cancels it when backup is off or manual. */
    fun apply(config: BackupConfig)

    fun manualRuns(): Flow<ManualBackupState>

    fun cancelAll()
}

/**
 * Periodic work every 1, 7 or 30 days on an unmetered network when "Only on Wi-Fi" is on (which
 * also allows unmetered mobile data), any network otherwise, and never on a low battery. "Back up
 * now" is one-time work on any network that always uploads.
 */
@Singleton
class WorkManagerBackupScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BackupScheduler {
        // Resolved on use: WorkManager is configured by the Application after injection.
        private val workManager: WorkManager get() = WorkManager.getInstance(context)

        override fun apply(config: BackupConfig) {
            val days = config.frequency.days
            if (!config.enabled || days == null) {
                workManager.cancelUniqueWork(PERIODIC)
                return
            }
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(if (config.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            val request =
                PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                    .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        override fun backUpNow() {
            val request =
                OneTimeWorkRequestBuilder<BackupWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(BackupWorker.KEY_MANUAL to true))
                    .build()
            // KEEP: a second tap while a run is queued or running does not start another.
            workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }

        override fun manualRuns(): Flow<ManualBackupState> =
            workManager.getWorkInfosForUniqueWorkFlow(NOW).map { infos ->
                val info = infos.lastOrNull() ?: return@map ManualBackupState.None
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ManualBackupState.Waiting
                    WorkInfo.State.RUNNING ->
                        ManualBackupState.Running(
                            BackupPhase.entries.firstOrNull {
                                it.name ==
                                    info.progress.getString(
                                        BackupWorker.KEY_PHASE,
                                    )
                            },
                        )
                    WorkInfo.State.SUCCEEDED -> ManualBackupState.Succeeded
                    WorkInfo.State.FAILED -> ManualBackupState.Failed
                    WorkInfo.State.CANCELLED -> ManualBackupState.None
                }
            }

        override fun cancelAll() {
            workManager.cancelUniqueWork(PERIODIC)
            workManager.cancelUniqueWork(NOW)
        }

        private companion object {
            const val PERIODIC = "mensis-backup-periodic"
            const val NOW = "mensis-backup-now"
            const val BACKOFF_MINUTES = 30L
        }
    }
