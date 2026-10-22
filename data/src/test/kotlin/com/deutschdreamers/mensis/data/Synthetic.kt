package com.deutschdreamers.mensis.data

import com.deutschdreamers.mensis.data.db.ContraceptionPeriodEntity
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.db.TrackerSettingsEntity
import com.deutschdreamers.mensis.data.db.toEntity
import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.RingState
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TestResult
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.set
import java.time.LocalDate
import java.time.LocalTime

/** Synthetic rows and random generators for the export/import tests. No real data, ever. */
object Synthetic {
    const val NOW: String = "2026-10-21T12:00:00Z"
    val TODAY: LocalDate = LocalDate.of(2026, 10, 21)

    @Suppress("LongParameterList") // named defaults stand in for the row's columns
    fun day(
        date: String,
        flow: Flow? = Flow.MEDIUM,
        id: String = "id-$date",
        createdAt: String = "2026-01-01T08:00:00Z",
        updatedAt: String = createdAt,
        deletedAt: String? = null,
        build: DayEntry.() -> DayEntry = { this },
    ): DayEntryEntity =
        DayEntry(LocalDate.parse(date), flow = flow)
            .build()
            .toEntity(id, createdAt, updatedAt)
            .copy(deletedAt = deletedAt)

    @Suppress("LongParameterList") // named defaults stand in for the row's columns
    fun period(
        id: String,
        method: String,
        start: String,
        end: String? = null,
        regimen: String? = null,
        deletedAt: String? = null,
    ): ContraceptionPeriodEntity =
        ContraceptionPeriodEntity(
            id = id,
            method = method,
            regimen = regimen,
            startDate = start,
            endDate = end,
            createdAt = "2026-01-01T08:00:00Z",
            updatedAt = "2026-01-01T08:00:00Z",
            deletedAt = deletedAt,
        )

    fun settings(
        cycleLength: Int = 29,
        level: String = "SYMPTOMS",
        createdAt: String = "2026-01-01T08:00:00Z",
    ): TrackerSettingsEntity =
        TrackerSettingsEntity(
            trackingLevel = level,
            goal = "TRACK_CYCLE",
            mode = "CYCLE",
            typicalCycleLength = cycleLength,
            typicalPeriodLength = 5,
            temperatureUnit = "CELSIUS",
            weightUnit = "KILOGRAM",
            remindersJson = """{"period":{"enabled":true,"time":"09:00","option":"two_days_before"}}""",
            discreetWording = true,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    /** Free text with the characters CSV and JSON must survive: quotes, separators, line breaks, non-ASCII. */
    private val notes: Arb<String> =
        Arb
            .list(Arb.element("a", "B", "7", " ", "\"", ",", ";", "\n", "\r\n", "é", "ß", "🙂", "\t", "'", "|"), 1..24)
            .map { it.joinToString("").trim() }
            .map { it.ifEmpty { "note" } }
            .map { if (it.lowercase() in setOf("-", "na", "n/a", "null", "|")) "note $it" else it }

    /** Any entry Mensis could store for [date], every field independently present or absent. */
    fun entryArb(date: LocalDate): Arb<DayEntry> =
        arbitrary {
            DayEntry(
                date = date,
                flow = Arb.enum<Flow>().orNull(0.3).bind(),
                temperatureC =
                    Arb
                        .int(3500..3900)
                        .map { it / 100.0 }
                        .orNull(0.5)
                        .bind(),
                temperatureTime =
                    Arb
                        .int(0..(24 * 60 - 1))
                        .map { LocalTime.of(it / 60, it % 60) }
                        .orNull(0.6)
                        .bind(),
                symptoms = Arb.set(Arb.enum<Symptom>(), 0..3).bind(),
                moods = Arb.set(Arb.enum<Mood>(), 0..2).bind(),
                cervicalMucus = Arb.enum<CervicalMucus>().orNull(0.7).bind(),
                lhTest = Arb.enum<TestResult>().orNull(0.8).bind(),
                pregnancyTest = Arb.enum<TestResult>().orNull(0.9).bind(),
                sex = Arb.enum<SexType>().orNull(0.8).bind(),
                pillState = Arb.enum<PillState>().orNull(0.8).bind(),
                patchChanged = Arb.boolean().orNull(0.9).bind(),
                ringState = Arb.enum<RingState>().orNull(0.9).bind(),
                injectionGiven = Arb.boolean().orNull(0.9).bind(),
                emergencyContraception = Arb.boolean().orNull(0.9).bind(),
                sleepMinutes = Arb.int(0..900).orNull(0.7).bind(),
                weightKg =
                    Arb
                        .int(400..1200)
                        .map { it / 10.0 }
                        .orNull(0.8)
                        .bind(),
                notes = notes.orNull(0.6).bind(),
            )
        }

    /** A random history: distinct dates, non-empty entries, some rows soft-deleted. */
    val historyArb: Arb<List<DayEntryEntity>> =
        arbitrary {
            val dates = Arb.set(Arb.localDate(LocalDate.of(2024, 1, 1), TODAY), 0..40).bind().sorted()
            dates.mapIndexed { i, date ->
                val entry = entryArb(date).bind().let { if (it.isEmpty) it.copy(flow = Flow.LIGHT) else it }
                val deleted = Arb.boolean().bind() && i % 4 == 0
                entry
                    .toEntity(id = "row-$i-$date", createdAt = "2026-0${1 + i % 9}-01T08:00:00Z", updatedAt = NOW)
                    .copy(deletedAt = if (deleted) NOW else null)
            }
        }
}
