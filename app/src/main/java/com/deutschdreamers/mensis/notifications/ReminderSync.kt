package com.deutschdreamers.mensis.notifications

import android.content.Context
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.domain.reminders.ReminderPlanner
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one alarm per enabled reminder in line with the data. Alarms are re-planned whenever logs,
 * settings, method or reminder settings change (while the process lives), after each reminder fires,
 * after a reboot, an app update, a time or time-zone change or an exact-alarm permission change, and
 * once a day as a safety net. Nothing is planned before onboarding is complete.
 */
@Singleton
class ReminderSync
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val trackerData: TrackerDataRepository,
        private val settings: SettingsRepository,
        private val scheduler: ReminderScheduler,
        private val notifications: ReminderNotifications,
        private val clock: Clock,
    ) {
        /**
         * For receivers and the observer. Reminders are best effort: a failure (say, the database being
         * replaced during delete-all) must not crash a background process, and the next trigger re-plans.
         */
        val scope: CoroutineScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> },
            )

        private val mutex = Mutex()

        @OptIn(ExperimentalCoroutinesApi::class)
        fun start() {
            scope.launch {
                settings.uiPrefs
                    .map { it.onboardingCompleted }
                    .distinctUntilChanged()
                    .flatMapLatest { completed ->
                        if (completed) {
                            // Delete-all closes the database under this observer; it resumes on the new
                            // database once onboarding completes again.
                            combine(
                                trackerData.observe(),
                                settings.observeReminders(),
                                ::Pair,
                            ).catch { }
                        } else {
                            flowOf(null)
                        }
                    }.collectLatest { state ->
                        // One save can touch several rows; plan once they have settled.
                        delay(SETTLE_MILLIS)
                        mutex.withLock {
                            if (state == null) scheduler.cancelAll() else apply(state.first, state.second)
                        }
                    }
            }
            ReminderRefreshWorker.enqueue(context)
        }

        suspend fun refresh() {
            mutex.withLock {
                if (!settings.uiPrefs.first().onboardingCompleted) {
                    scheduler.cancelAll()
                    return
                }
                apply(trackerData.observe().first(), settings.reminders())
            }
        }

        /** An alarm went off for the reminder planned at [planned]: post it if the data still lead there. */
        suspend fun fire(
            type: ReminderType,
            planned: LocalDateTime,
        ) {
            mutex.withLock {
                if (!settings.uiPrefs.first().onboardingCompleted) return@withLock
                val data = trackerData.observe().first()
                val config = settings.reminders()
                val due = ReminderPlanner.due(type, data.inputFor(planned.toLocalDate()), config, planned)
                if (due != null) notifications.post(due, config)
            }
            refresh()
        }

        // The zone is read on each call: the injected clock keeps the zone it was created with.
        private fun apply(
            data: TrackerData,
            config: ReminderConfig,
        ) {
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.ofInstant(Instant.now(clock), zone)
            scheduler.apply(ReminderPlanner.plan(data.inputFor(now.toLocalDate()), config, now), zone)
        }

        private companion object {
            const val SETTLE_MILLIS = 500L
        }
    }
