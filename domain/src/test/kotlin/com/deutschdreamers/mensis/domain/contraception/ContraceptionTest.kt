package com.deutschdreamers.mensis.domain.contraception

import com.deutschdreamers.mensis.domain.Synthetic
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.prediction.Confidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class ContraceptionTest {
    private val methodStart: LocalDate = LocalDate.of(2026, 1, 3)
    private val pill = ContraceptionPeriod(ContraceptionMethod.COMBINED_PILL, methodStart, regimen = Regimen.CYCLIC)

    @ParameterizedTest(name = "{0} + {1} → kind {2}")
    @CsvSource(
        "NONE, , NONE",
        "COPPER_IUD, , COPPER",
        "COMBINED_PILL, CYCLIC, CYCLIC",
        "COMBINED_PILL, CONTINUOUS, SUPPRESS",
        "PATCH, CYCLIC, CYCLIC",
        "RING, CONTINUOUS, SUPPRESS",
        "PROGESTIN_ONLY_PILL, , SUPPRESS",
        "INJECTION, , SUPPRESS",
        "IMPLANT, , SUPPRESS",
        "HORMONAL_IUD, , SUPPRESS",
    )
    fun `method kind follows the handoff table`(
        method: ContraceptionMethod,
        regimen: Regimen?,
        kind: MethodKind,
    ) {
        assertEquals(kind, ContraceptionPeriod(method, methodStart, regimen = regimen).kind)
    }

    @Test
    fun `pack position counts 28-day packs from the method start`() {
        val day17 = PackCalendar.position(methodStart, methodStart.plusDays(28 * 9 + 16L))
        assertEquals(9, day17.packIndex)
        assertEquals(17, day17.packDay)
        assertEquals(3, day17.weekOfPack)
        assertEquals(false, day17.isBreak)
        assertEquals(day17.packStart.plusDays(21), day17.breakStart)
        assertEquals(day17.packStart.plusDays(28), day17.nextPackStart)
        val day25 = PackCalendar.position(methodStart, day17.packStart.plusDays(24))
        assertTrue(day25.isBreak)
        assertEquals(4, day25.weekOfPack)
    }

    @Test
    fun `patch change days are pack days 1, 8 and 15`() {
        assertEquals(listOf(1, 8, 15), (1..28).filter { PackCalendar.isPatchChangeDay(it) })
    }

    @Test
    fun `withdrawal bleed defaults to break days 2 to 5 with one day of spread`() {
        val today = methodStart.plusDays(28 * 6 + 16L)
        val bleed = WithdrawalBleedPredictor.predict(pill, emptyList(), today)
        val breakStart = bleed.position.breakStart
        assertEquals(6, bleed.position.packIndex)
        assertEquals(breakStart.plusDays(1), bleed.mostLikelyStart)
        assertEquals(breakStart.plusDays(1), bleed.range.start)
        assertEquals(breakStart.plusDays(4), bleed.range.end)
        assertEquals(1, bleed.spreadDays)
        assertEquals(Confidence.HIGH, bleed.confidence)
        assertEquals(0, bleed.packsUsed)
    }

    @Test
    fun `logged bleeding in earlier breaks shifts the predicted start`() {
        // Bleeding started on break day 3 in each of the first six packs.
        val entries =
            (0 until 6).flatMap { pack ->
                val breakStart = methodStart.plusDays(pack * 28L + 21)
                listOf(
                    DayEntry(breakStart.plusDays(2), flow = Flow.MEDIUM),
                    DayEntry(breakStart.plusDays(3), flow = Flow.LIGHT),
                )
            }
        val today = methodStart.plusDays(28 * 6 + 16L)
        val bleed = WithdrawalBleedPredictor.predict(pill, entries, today)
        assertEquals(6, bleed.packsUsed)
        assertEquals(bleed.position.breakStart.plusDays(2), bleed.mostLikelyStart)
    }

    @Test
    fun `after the bleed range the prediction moves to the next pack`() {
        val packStart = methodStart.plusDays(28 * 6L)
        assertEquals(6, WithdrawalBleedPredictor.predict(pill, emptyList(), packStart.plusDays(25)).position.packIndex)
        assertEquals(7, WithdrawalBleedPredictor.predict(pill, emptyList(), packStart.plusDays(26)).position.packIndex)
    }

    @Test
    fun `missed pills and emergency contraception widen the bleed range`() {
        val packStart = methodStart.plusDays(28 * 6L)
        val entries =
            listOf(
                DayEntry(packStart.plusDays(3), pillState = PillState.MISSED),
                DayEntry(packStart.plusDays(4), pillState = PillState.MISSED),
                DayEntry(packStart.plusDays(5), emergencyContraception = true),
            )
        val bleed = WithdrawalBleedPredictor.predict(pill, entries, packStart.plusDays(16))
        assertEquals(1 + 2 + 2, bleed.spreadDays)
        assertEquals(packStart.plusDays(22 - 4L), bleed.range.start)
        assertEquals(packStart.plusDays(25 + 4L), bleed.range.end)
    }

    @Test
    fun `suppressed summary counts days since the last bleed and bleeding days in the window`() {
        val today = LocalDate.of(2026, 10, 21)
        val entries =
            listOf(
                DayEntry(today.minusDays(47), flow = Flow.SPOTTING),
                DayEntry(today.minusDays(60), flow = Flow.SPOTTING),
                DayEntry(today.minusDays(61), flow = Flow.LIGHT),
                DayEntry(today.minusDays(120), flow = Flow.SPOTTING),
            )
        val summary = SuppressedSummaryCalculator.summarize(entries, today)
        assertEquals(47, summary.daysSinceLastBleed)
        assertEquals(3, summary.bleedingDaysInWindow)
        assertEquals(1, summary.fullPeriodsInWindow)
        assertEquals(90, summary.windowDays)
    }

    @Test
    fun `suppressed summary without any bleeding`() {
        val summary = SuppressedSummaryCalculator.summarize(emptyList(), Synthetic.day1)
        assertNull(summary.daysSinceLastBleed)
        assertEquals(0, summary.bleedingDaysInWindow)
    }

    @Test
    fun `replacement dates use the method default unless set`() {
        val implant = ContraceptionPeriod(ContraceptionMethod.IMPLANT, LocalDate.of(2026, 3, 12))
        assertEquals(LocalDate.of(2029, 3, 12), MethodSchedule.replaceByDate(implant))
        val hormonalIud =
            implant.copy(
                method = ContraceptionMethod.HORMONAL_IUD,
                replaceByDate = LocalDate.of(2031, 3, 1),
            )
        assertEquals(LocalDate.of(2031, 3, 1), MethodSchedule.replaceByDate(hormonalIud))
        assertEquals(
            LocalDate.of(2036, 3, 12),
            MethodSchedule.replaceByDate(implant.copy(method = ContraceptionMethod.COPPER_IUD)),
        )
        assertNull(MethodSchedule.replaceByDate(pill))
    }

    @Test
    fun `next injection is twelve weeks after the last logged injection`() {
        val injection = ContraceptionPeriod(ContraceptionMethod.INJECTION, LocalDate.of(2026, 6, 19))
        val entries = listOf(DayEntry(LocalDate.of(2026, 9, 11), injectionGiven = true))
        assertEquals(LocalDate.of(2026, 12, 4), MethodSchedule.nextInjectionDate(injection, entries))
        assertEquals(LocalDate.of(2026, 9, 11), MethodSchedule.nextInjectionDate(injection, emptyList()))
        assertNull(MethodSchedule.nextInjectionDate(pill, entries))
    }
}
