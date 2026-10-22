package com.deutschdreamers.mensis.data.repo

import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.db.TrackerSettingsDao
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.db.toEntity
import com.deutschdreamers.mensis.data.settings.UiPrefs
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

        fun observeTracker(): Flow<TrackerSettings> = dao.observe().map { it?.toDomain() ?: TrackerSettings() }

        suspend fun tracker(): TrackerSettings = dao.get()?.toDomain() ?: TrackerSettings()

        suspend fun updateTracker(transform: (TrackerSettings) -> TrackerSettings) {
            val now = Instant.now(clock).toString()
            val existing = dao.get()
            val updated = transform(existing?.toDomain() ?: TrackerSettings())
            dao.upsert(updated.toEntity(createdAt = now, updatedAt = now, existing = existing))
        }

        val uiPrefs: Flow<UiPrefs> get() = uiPrefsStore.prefs

        suspend fun updateUiPrefs(transform: (UiPrefs) -> UiPrefs) = uiPrefsStore.update(transform)
    }
