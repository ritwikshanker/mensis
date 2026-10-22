package com.deutschdreamers.mensis.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs [BackupManager] for the scheduler. Scheduled runs retry transient failures with backoff;
 * a manual run reports its failure straight away, since the user is watching.
 */
@HiltWorker
class BackupWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val manager: BackupManager,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val manual = inputData.getBoolean(KEY_MANUAL, false)
            val result =
                manager.run(force = manual, interactive = manual) { phase ->
                    setProgress(workDataOf(KEY_PHASE to phase.name))
                }
            return when (result) {
                is BackupRunResult.Uploaded, BackupRunResult.Unchanged, BackupRunResult.NotEnabled -> Result.success()
                BackupRunResult.NeedsReconnect -> Result.failure()
                is BackupRunResult.Failed ->
                    if (result.retryable &&
                        !manual &&
                        runAttemptCount < MAX_RETRIES
                    ) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
            }
        }

        companion object {
            const val KEY_MANUAL: String = "manual"
            const val KEY_PHASE: String = "phase"

            /** Beyond this the periodic schedule takes over again. */
            private const val MAX_RETRIES = 5
        }
    }
