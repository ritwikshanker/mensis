package com.deutschdreamers.mensis.data.repo

import com.deutschdreamers.mensis.data.db.ContraceptionDao
import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.db.toDomain
import com.deutschdreamers.mensis.data.db.toEntity
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A stored method period with its row id (needed for editing). */
data class ContraceptionRecord(
    val id: String,
    val period: ContraceptionPeriod,
)

@Singleton
class ContraceptionRepository
    @Inject
    constructor(
        private val database: DatabaseHolder,
        private val clock: Clock,
    ) {
        private val dao: ContraceptionDao get() = database.get().contraceptionDao()

        fun observePeriods(): Flow<List<ContraceptionPeriod>> =
            observeRecords().map { records -> records.map { it.period } }

        fun observeRecords(): Flow<List<ContraceptionRecord>> = dao.observeActive().map { rows -> rows.toRecords() }

        suspend fun records(): List<ContraceptionRecord> = dao.active().toRecords()

        private fun List<ContraceptionPeriodEntity>.toRecords(): List<ContraceptionRecord> =
            mapNotNull { row -> row.toDomain()?.let { ContraceptionRecord(row.id, it) } }

        /** Inserts or updates a period and returns its id. */
        suspend fun save(
            period: ContraceptionPeriod,
            id: String? = null,
        ): String {
            val now = Instant.now(clock).toString()
            val existing = id?.let { dao.byId(it) }
            val rowId = existing?.id ?: UUID.randomUUID().toString()
            dao.upsert(period.toEntity(rowId, existing?.createdAt ?: now, now))
            return rowId
        }

        /** Ends every open period the day before [startOfNext] and starts the new one, keeping history intact. */
        suspend fun switchTo(
            period: ContraceptionPeriod,
            startOfNext: LocalDate = period.startDate,
        ): String {
            val now = Instant.now(clock).toString()
            for (record in records().filter { it.period.endDate == null }) {
                val row = dao.byId(record.id) ?: continue
                val lastDay = startOfNext.minusDays(1)
                if (lastDay.isBefore(record.period.startDate)) {
                    dao.upsert(row.copy(updatedAt = now, deletedAt = now))
                } else {
                    dao.upsert(row.copy(endDate = lastDay.toString(), updatedAt = now))
                }
            }
            return save(period)
        }

        suspend fun delete(id: String) {
            val now = Instant.now(clock).toString()
            dao.byId(id)?.let { dao.upsert(it.copy(updatedAt = now, deletedAt = now)) }
        }
    }
