package com.deutschdreamers.mensis.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Re-plans reminders once a day, in case an alarm was lost without any broadcast telling us. */
@HiltWorker
class ReminderRefreshWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val sync: ReminderSync,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            sync.refresh()
            return Result.success()
        }

        companion object {
            private const val NAME = "reminder-refresh"

            fun enqueue(context: Context) {
                val request = PeriodicWorkRequestBuilder<ReminderRefreshWorker>(1, TimeUnit.DAYS).build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
