package com.deutschdreamers.mensis.data.db

import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Goal
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.RingState
import com.deutschdreamers.mensis.domain.model.SexType
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.model.WeightUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class MappersTest {
    private val now = "2026-03-14T06:45:00Z"

    /** Synthetic entry with every field set to a non-default value. */
    private val full =
        DayEntry(
            date = LocalDate.of(2026, 3, 14),
            flow = Flow.MEDIUM,
            temperatureC = 36.62,
            temperatureTime = LocalTime.of(6, 40),
            symptoms = setOf(Symptom.HEADACHE, Symptom.CRAMPS),
            moods = setOf(Mood.LOW, Mood.CALM),
            cervicalMucus = CervicalMucus.EGG_WHITE,
            lhTest = TestResult.POSITIVE,
            pregnancyTest = TestResult.NEGATIVE,
            sex = SexType.PROTECTED,
            pillState = PillState.LATE,
            patchChanged = true,
            ringState = RingState.OUT,
            injectionGiven = false,
            emergencyContraception = true,
            sleepMinutes = 455,
            weightKg = 61.3,
            notes = "synthetic note",
        )

    @Test
    fun `the synthetic entry leaves no field at its default, so the round trip covers all of them`() {
        val empty = DayEntry(full.date)
        DayEntry::class.java.declaredFields
            .filter { !it.isSynthetic && it.name != "date" }
            .forEach { field ->
                field.isAccessible = true
                assertNotEquals(field.get(empty), field.get(full), "field ${field.name} is still the default")
            }
    }

    @Test
    fun `every DayEntry field survives the entity round trip`() {
        val entity = full.toEntity(id = "e1", createdAt = now, updatedAt = now, importBatchId = "b1")
        assertEquals(full, entity.toDomain())
    }

    @Test
    fun `metadata columns are carried as given`() {
        val entity = full.toEntity(id = "e1", createdAt = "2026-01-01T00:00:00Z", updatedAt = now, importBatchId = "b1")
        assertEquals("e1", entity.id)
        assertEquals("2026-03-14", entity.date)
        assertEquals("2026-01-01T00:00:00Z", entity.createdAt)
        assertEquals(now, entity.updatedAt)
        assertEquals("b1", entity.importBatchId)
        assertNull(entity.deletedAt)
    }

    @Test
    fun `an empty entry survives the round trip`() {
        val empty = DayEntry(LocalDate.of(2026, 3, 15))
        assertEquals(empty, empty.toEntity(id = "e2", createdAt = now, updatedAt = now).toDomain())
    }

    @Test
    fun `set names are stored sorted as a JSON array`() {
        val entity = full.toEntity(id = "e1", createdAt = now, updatedAt = now)
        assertEquals("""["CRAMPS","HEADACHE"]""", entity.symptoms)
        assertEquals("""["CALM","LOW"]""", entity.moods)
        assertEquals("[]", DayEntry(full.date).toEntity(id = "e3", createdAt = now, updatedAt = now).symptoms)
    }

    @Test
    fun `blank notes are stored as null`() {
        val entity = full.copy(notes = "   ").toEntity(id = "e1", createdAt = now, updatedAt = now)
        assertNull(entity.notes)
    }

    @Test
    fun `unknown enum names from a newer app version map to null instead of crashing`() {
        val entity =
            full
                .toEntity(id = "e1", createdAt = now, updatedAt = now)
                .copy(flow = "GUSHING", symptoms = """["CRAMPS","TELEPATHY"]""", moods = "not json")
        val domain = entity.toDomain()
        assertNull(domain.flow)
        assertEquals(setOf(Symptom.CRAMPS), domain.symptoms)
        assertEquals(emptySet<Mood>(), domain.moods)
    }

    @Test
    fun `contraception periods round trip`() {
        val period =
            ContraceptionPeriod(
                method = ContraceptionMethod.COMBINED_PILL,
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 6, 30),
                regimen = Regimen.CYCLIC,
                dailyTime = LocalTime.of(8, 0),
                replaceByDate = LocalDate.of(2027, 1, 1),
                checkupDate = LocalDate.of(2026, 9, 1),
            )
        assertEquals(period, period.toEntity(id = "c1", createdAt = now, updatedAt = now).toDomain())
    }

    @Test
    fun `a contraception period with an unknown method is dropped`() {
        val entity =
            ContraceptionPeriodEntity(
                id = "c2",
                method = "FUTURE",
                startDate = "2026-01-01",
                createdAt = now,
                updatedAt = now,
            )
        assertNull(entity.toDomain())
    }

    @Test
    fun `tracker settings round trip and keep the columns the domain does not own`() {
        val settings =
            TrackerSettings(
                trackingLevel = TrackingLevel.entries.last(),
                goal = Goal.entries.last(),
                mode = TrackerMode.entries.last(),
                pregnancyLmp = LocalDate.of(2026, 2, 2),
                typicalCycleLength = 31,
                typicalPeriodLength = 4,
                temperatureUnit = TemperatureUnit.entries.last(),
                weightUnit = WeightUnit.entries.last(),
            )
        val existing =
            settings
                .toEntity(createdAt = "2025-12-31T00:00:00Z", updatedAt = "2025-12-31T00:00:00Z")
                .copy(remindersJson = """{"period":true}""", discreetWording = false)
        val updated = settings.toEntity(createdAt = now, updatedAt = now, existing = existing)
        assertEquals(settings, updated.toDomain())
        assertEquals("2025-12-31T00:00:00Z", updated.createdAt)
        assertEquals(now, updated.updatedAt)
        assertEquals("""{"period":true}""", updated.remindersJson)
        assertEquals(false, updated.discreetWording)
    }
}
