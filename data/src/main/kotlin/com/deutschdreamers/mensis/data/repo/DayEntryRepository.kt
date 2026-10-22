package com.deutschdreamers.mensis.data.repo

import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.db.DayEntryDao
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.db.toEntity
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow as KFlow

/** Raw daily facts. Saving an empty entry removes the day (tombstone); re-logging a date revives the same row id. */
@Singleton
class DayEntryRepository
    @Inject
    constructor(
        private val database: DatabaseHolder,
        private val clock: Clock,
    ) {
        private val dao: DayEntryDao get() = database.get().dayEntryDao()

        fun observeAll(): KFlow<List<DayEntry>> = dao.observeActive().map { rows -> rows.map { it.toDomain() } }

        suspend fun all(): List<DayEntry> = dao.active().map { it.toDomain() }

        suspend fun get(date: LocalDate): DayEntry? =
            dao.byDate(date.toString())?.takeIf { it.deletedAt == null }?.toDomain()

        suspend fun save(entry: DayEntry) {
            val now = Instant.now(clock).toString()
            val existing = dao.byDate(entry.date.toString())
            if (entry.isEmpty) {
                if (existing != null &&
                    existing.deletedAt == null
                ) {
                    dao.upsert(existing.copy(updatedAt = now, deletedAt = now))
                }
                return
            }
            dao.upsert(
                entry.toEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    importBatchId = existing?.importBatchId,
                ),
            )
        }

        /** Calendar "edit period dates": set or clear the flow of a day, keeping everything else logged. */
        suspend fun setFlow(
            date: LocalDate,
            flow: Flow?,
        ) {
            val current = get(date) ?: DayEntry(date)
            save(current.copy(flow = flow))
        }

        suspend fun delete(date: LocalDate) = save(DayEntry(date))

        suspend fun countActive(): Int = dao.countActive()
    }
