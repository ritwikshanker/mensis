package com.deutschdreamers.mensis.data.repo

import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.db.TrackerSettingsDao
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.db.toEntity
import com.deutschdreamers.mensis.data.settings.ReminderConfigJson
import com.deutschdreamers.mensis.data.settings.UiPrefs
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val database: DatabaseHolder,
        private val uiPrefsStore: UiPrefsStore,
        private val clock: Clock,
    ) {
        private val dao: TrackerSettingsDao get() = database.get().trackerSettingsDao()

        // Updates read the row, change it and write it back; two at once must not lose one another's change.
        private val writes = Mutex()

        fun observeTracker(): Flow<TrackerSettings> = dao.observe().map { it?.toDomain() ?: TrackerSettings() }

        suspend fun tracker(): TrackerSettings = dao.get()?.toDomain() ?: TrackerSettings()

        suspend fun updateTracker(transform: (TrackerSettings) -> TrackerSettings) =
            writes.withLock {
                val now = Instant.now(clock).toString()
                val existing = dao.get()
                val updated = transform(existing?.toDomain() ?: TrackerSettings())
                dao.upsert(updated.toEntity(createdAt = now, updatedAt = now, existing = existing))
            }

        fun observeReminders(): Flow<ReminderConfig> = dao.observe().map { it.reminderConfig() }

        suspend fun reminders(): ReminderConfig = dao.get().reminderConfig()

        /** Reminders live in the settings row too; the row is created with default settings if needed. */
        suspend fun updateReminders(transform: (ReminderConfig) -> ReminderConfig) =
            writes.withLock {
                val now = Instant.now(clock).toString()
                val existing = dao.get() ?: TrackerSettings().toEntity(createdAt = now, updatedAt = now)
                val updated = transform(existing.reminderConfig())
                dao.upsert(
                    existing.copy(
                        remindersJson = ReminderConfigJson.encode(updated),
                        discreetWording = updated.discreet,
                        updatedAt = now,
                    ),
                )
            }

        private fun TrackerSettingsEntity?.reminderConfig(): ReminderConfig =
            ReminderConfigJson.decode(this?.remindersJson ?: "{}", discreet = this?.discreetWording ?: true)

        val uiPrefs: Flow<UiPrefs> get() = uiPrefsStore.prefs

        suspend fun updateUiPrefs(transform: (UiPrefs) -> UiPrefs) = uiPrefsStore.update(transform)
    }
