package com.deutschdreamers.mensis.domain.reminders

import com.deutschdreamers.mensis.domain.Synthetic
import com.deutschdreamers.mensis.domain.contraception.MethodSchedule
import com.deutschdreamers.mensis.domain.contraception.PackCalendar
import com.deutschdreamers.mensis.domain.contraception.WithdrawalBleedPredictor
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.prediction.PredictionEngine
import com.deutschdreamers.mensis.domain.prediction.PredictionInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {
    private val cycles = Synthetic.regularCycles(count = 7)
    private val lastStart = Synthetic.lastStart(cycles)
    private val nine = LocalTime.of(9, 0)

    private fun input(
        entries: List<DayEntry> = cycles,
        method: ContraceptionPeriod? = null,
        settings: TrackerSettings = TrackerSettings(),
    ) = PredictionInput(entries, settings, lastStart, listOfNotNull(method))

    private fun only(
        type: ReminderType,
        option: ReminderOption? = null,
        time: LocalTime? = null,
    ) = ReminderConfig(mapOf(type to ReminderSetting(enabled = true, time = time, option = option)))

    private fun plan(
        config: ReminderConfig,
        now: LocalDateTime,
        input: PredictionInput = input(),
    ): PlannedReminder? = ReminderPlanner.plan(input, config, now).singleOrNull()

    // ---- Catalog -------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} {1} at {2} → {3}")
    @CsvSource(
        "NONE, , SYMPTOMS, PERIOD FERTILE",
        "NONE, , FULL, PERIOD FERTILE TEMPERATURE",
        "COPPER_IUD, , PERIOD, PERIOD FERTILE REPLACEMENT",
        "COMBINED_PILL, CYCLIC, SYMPTOMS, PERIOD PILL",
        "COMBINED_PILL, CONTINUOUS, SYMPTOMS, PILL",
        "PROGESTIN_ONLY_PILL, , FULL, PILL TEMPERATURE",
        "PATCH, CYCLIC, SYMPTOMS, PERIOD PATCH",
        "RING, CYCLIC, SYMPTOMS, PERIOD RING",
        "INJECTION, , SYMPTOMS, INJECTION",
        "IMPLANT, , SYMPTOMS, REPLACEMENT",
        "HORMONAL_IUD, , FULL, REPLACEMENT TEMPERATURE",
    )
    fun `the rows follow method kind and tracking level`(
        method: ContraceptionMethod,
        regimen: Regimen?,
        level: TrackingLevel,
        expected: String,
    ) {
        val period = ContraceptionPeriod(method, Synthetic.day1, regimen = regimen)
        val types = ReminderCatalog.available(period, level).joinToString(" ")
        assertEquals(expected, types)
    }

    @Test
    fun `defaults follow the design`() {
        val cyclic = ContraceptionPeriod(ContraceptionMethod.COMBINED_PILL, Synthetic.day1, regimen = Regimen.CYCLIC)
        val config = ReminderConfig()
        val period = ReminderCatalog.resolve(ReminderType.PERIOD, config, null)
        assertEquals(ReminderOption.TWO_DAYS_BEFORE, period.option)
        assertEquals(nine, period.time)
        assertEquals(false, period.enabled)
        val bleed = ReminderCatalog.resolve(ReminderType.PERIOD, config, cyclic)
        assertEquals(ReminderOption.ONE_DAY_BEFORE, bleed.option)
        assertEquals(listOf(ReminderOption.SAME_DAY, ReminderOption.ONE_DAY_BEFORE), bleed.options)
        assertEquals(LocalTime.of(21, 0), ReminderCatalog.resolve(ReminderType.PILL, config, cyclic).time)
        assertEquals(LocalTime.of(6, 30), ReminderCatalog.resolve(ReminderType.TEMPERATURE, config, null).time)
        val ring = ContraceptionPeriod(ContraceptionMethod.RING, Synthetic.day1, regimen = Regimen.CONTINUOUS)
        assertEquals(ReminderOption.FOUR_WEEKLY, ReminderCatalog.resolve(ReminderType.RING, config, ring).option)
    }

    @Test
    fun `the pill reminder follows the method time until the user picks one`() {
        val pill =
            ContraceptionPeriod(
                ContraceptionMethod.PROGESTIN_ONLY_PILL,
                Synthetic.day1,
                dailyTime = LocalTime.of(7, 45),
            )
        assertEquals(LocalTime.of(7, 45), ReminderCatalog.resolve(ReminderType.PILL, ReminderConfig(), pill).time)
        val own = only(ReminderType.PILL, time = LocalTime.of(22, 0))
        assertEquals(LocalTime.of(22, 0), ReminderCatalog.resolve(ReminderType.PILL, own, pill).time)
    }

    @Test
    fun `an option the current method does not offer falls back to the default`() {
        val pop = ContraceptionPeriod(ContraceptionMethod.PROGESTIN_ONLY_PILL, Synthetic.day1)
        val config = only(ReminderType.PILL, ReminderOption.ACTIVE_DAYS_ONLY)
        assertEquals(ReminderOption.DAILY, ReminderCatalog.resolve(ReminderType.PILL, config, pop).option)
    }

    // ---- Period and fertile window -------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → {1} days before the range")
    @CsvSource("SAME_DAY, 0", "ONE_DAY_BEFORE, 1", "TWO_DAYS_BEFORE, 2")
    fun `the period reminder fires the lead days before the predicted range`(
        option: ReminderOption,
        lead: Long,
    ) {
        val next = requireNotNull(PredictionEngine.predict(input()).nextPeriod)
        val planned = requireNotNull(plan(only(ReminderType.PERIOD, option), lastStart.atTime(10, 0)))
        assertEquals(next.earliest.minusDays(lead).atTime(nine), planned.at)
        val expected = ReminderEvent.PeriodExpected(next.range, lead.toInt(), next.basisCycles, next.usesTypicalLength)
        assertEquals(expected, planned.event)
    }

    @Test
    fun `a period reminder whose time has passed is not sent late`() {
        val next = requireNotNull(PredictionEngine.predict(input()).nextPeriod)
        val at = next.earliest.minusDays(2).atTime(nine)
        assertNull(plan(only(ReminderType.PERIOD), at))
        assertNull(plan(only(ReminderType.PERIOD), at.plusDays(10)))
    }

    @Test
    fun `there is no period reminder without a logged period or in pregnancy mode`() {
        val now = lastStart.atTime(10, 0)
        assertNull(plan(only(ReminderType.PERIOD), now, input(entries = emptyList())))
        val pregnant = TrackerSettings(mode = TrackerMode.PREGNANCY, pregnancyLmp = lastStart)
        assertNull(plan(only(ReminderType.PERIOD), now, input(settings = pregnant)))
        assertNull(plan(only(ReminderType.FERTILE), now, input(settings = pregnant)))
    }

    @Test
    fun `the fertile reminder moves to the next cycle once this cycle's has passed`() {
        val prediction = PredictionEngine.predict(input())
        val window = requireNotNull(prediction.fertileWindow).range
        val first = plan(only(ReminderType.FERTILE), lastStart.atTime(10, 0))
        assertEquals(window.start.minusDays(1).atTime(nine), first?.at)
        assertEquals(ReminderEvent.FertileWindowStarts(window, 1), first?.event)
        val nextWindow = requireNotNull(prediction.nextCycleFertileWindow).range
        val second = plan(only(ReminderType.FERTILE), window.start.atTime(nine))
        assertEquals(nextWindow.start.minusDays(1).atTime(nine), second?.at)
    }

    // ---- Hormonal methods -----------------------------------------------------------------------------

    private val packStart: LocalDate = Synthetic.day1
    private val combined = ContraceptionPeriod(ContraceptionMethod.COMBINED_PILL, packStart, regimen = Regimen.CYCLIC)

    @Test
    fun `the withdrawal bleed reminder uses the break-week prediction and then the next pack`() {
        val today = packStart.plusDays(28 * 3 + 9L)
        val methodInput = PredictionInput(emptyList(), TrackerSettings(), today, listOf(combined))
        val bleed = WithdrawalBleedPredictor.predict(combined, emptyList(), today)
        val first = plan(only(ReminderType.PERIOD), today.atTime(10, 0), methodInput)
        assertEquals(
            bleed.range.start
                .minusDays(1)
                .atTime(nine),
            first?.at,
        )
        assertEquals(ReminderEvent.WithdrawalBleedExpected(bleed.range, 1), first?.event)
        val second = plan(only(ReminderType.PERIOD), requireNotNull(first).at, methodInput)
        assertEquals(
            bleed.range.start
                .plusDays(28 - 1L)
                .atTime(nine),
            second?.at,
        )
    }

    @ParameterizedTest(name = "{0} after pack day {1} {2} → pack day {3}, {4} days later")
    @CsvSource(
        "DAILY, 10, 20:00, 10, 0",
        "DAILY, 10, 21:00, 11, 1",
        "DAILY, 21, 22:00, 22, 1",
        "ACTIVE_DAYS_ONLY, 21, 22:00, 1, 8",
        "ACTIVE_DAYS_ONLY, 25, 08:00, 1, 4",
    )
    fun `the combined pill reminder is daily or on active days only`(
        option: ReminderOption,
        packDay: Long,
        now: LocalTime,
        expectedPackDay: Int,
        daysLater: Long,
    ) {
        val today = packStart.plusDays(28 * 2 + packDay - 1)
        val methodInput = PredictionInput(emptyList(), TrackerSettings(), today, listOf(combined))
        val planned = plan(only(ReminderType.PILL, option), today.atTime(now), methodInput)
        assertEquals(today.plusDays(daysLater).atTime(21, 0), planned?.at)
        assertEquals(ReminderEvent.PillDue(expectedPackDay, null), planned?.event)
    }

    @Test
    fun `the progestin-only pill reminder carries the end of its 3-hour window`() {
        val pop = ContraceptionPeriod(ContraceptionMethod.PROGESTIN_ONLY_PILL, packStart)
        val today = packStart.plusDays(40)
        val methodInput = PredictionInput(emptyList(), TrackerSettings(), today, listOf(pop))
        val planned = plan(only(ReminderType.PILL), today.atTime(12, 0), methodInput)
        assertEquals(today.atTime(21, 0), planned?.at)
        assertEquals(ReminderEvent.PillDue(null, LocalTime.MIDNIGHT), planned?.event)
    }

    @ParameterizedTest(name = "patch from pack day {0} → pack day {1}: {2}")
    @CsvSource("1, 8, CHANGE", "9, 15, CHANGE", "16, 22, REMOVE", "23, 1, APPLY")
    fun `the patch reminder is weekly on the start weekday`(
        fromPackDay: Long,
        expectedPackDay: Int,
        action: PatchAction,
    ) {
        val patch = ContraceptionPeriod(ContraceptionMethod.PATCH, packStart, regimen = Regimen.CYCLIC)
        val today = packStart.plusDays(28 + fromPackDay - 1)
        val methodInput = PredictionInput(emptyList(), TrackerSettings(), today, listOf(patch))
        val planned = requireNotNull(plan(only(ReminderType.PATCH), today.atTime(10, 0), methodInput))
        assertEquals(DayOfWeek.MONDAY, planned.at.dayOfWeek)
        assertEquals(expectedPackDay, PackCalendar.position(packStart, planned.at.toLocalDate()).packDay)
        assertEquals(ReminderEvent.PatchDue(action), planned.event)
    }

    @ParameterizedTest(name = "ring {0} from pack day {1} → {2} days later: {3}")
    @CsvSource(
        "THREE_WEEKS_IN_ONE_OUT, 10, 12, REMOVE",
        "THREE_WEEKS_IN_ONE_OUT, 23, 6, INSERT",
        "FOUR_WEEKLY, 10, 19, REPLACE",
    )
    fun `the ring reminder follows its schedule`(
        option: ReminderOption,
        fromPackDay: Long,
        daysLater: Long,
        action: RingAction,
    ) {
        val ring = ContraceptionPeriod(ContraceptionMethod.RING, packStart, regimen = Regimen.CYCLIC)
        val today = packStart.plusDays(28 + fromPackDay - 1)
        val methodInput = PredictionInput(emptyList(), TrackerSettings(), today, listOf(ring))
        val planned = plan(only(ReminderType.RING, option), today.atTime(10, 0), methodInput)
        assertEquals(today.plusDays(daysLater).atTime(nine), planned?.at)
        assertEquals(ReminderEvent.RingDue(action), planned?.event)
    }

    @ParameterizedTest(name = "injection {0} → {1} weeks after the last one")
    @CsvSource("TWELVE_WEEKS, 12", "THIRTEEN_WEEKS, 13")
    fun `the injection reminder fires on the due date`(
        option: ReminderOption,
        weeks: Long,
    ) {
        val shot = ContraceptionPeriod(ContraceptionMethod.INJECTION, packStart)
        val lastShot = packStart.plusWeeks(12)
        val entries = listOf(DayEntry(lastShot, injectionGiven = true))
        val today = lastShot.plusDays(3)
        val methodInput = PredictionInput(entries, TrackerSettings(), today, listOf(shot))
        val planned = plan(only(ReminderType.INJECTION, option), today.atTime(10, 0), methodInput)
        assertEquals(lastShot.plusWeeks(weeks).atTime(nine), planned?.at)
        assertEquals(ReminderEvent.InjectionDue(lastShot.plusWeeks(weeks), lastShot), planned?.event)
    }

    @Test
    fun `the injection reminder uses the date the user set`() {
        val own = packStart.plusWeeks(11)
        val shot = ContraceptionPeriod(ContraceptionMethod.INJECTION, packStart, replaceByDate = own)
        val methodInput = PredictionInput(emptyList(), TrackerSettings(), packStart.plusDays(1), listOf(shot))
        val planned = plan(only(ReminderType.INJECTION), packStart.plusDays(1).atTime(10, 0), methodInput)
        assertEquals(own.atTime(nine), planned?.at)
    }

    @ParameterizedTest(name = "{0} {1} → {2} months before the replacement date")
    @CsvSource(
        "IMPLANT, THREE_MONTHS_BEFORE, 3",
        "IMPLANT, ONE_MONTH_BEFORE, 1",
        "HORMONAL_IUD, THREE_MONTHS_BEFORE, 3",
        "COPPER_IUD, ONE_MONTH_BEFORE, 1",
    )
    fun `the replacement reminder fires months before the replace-by date`(
        method: ContraceptionMethod,
        option: ReminderOption,
        months: Long,
    ) {
        val inserted = ContraceptionPeriod(method, packStart)
        val replaceBy = requireNotNull(MethodSchedule.replaceByDate(inserted))
        val methodInput = PredictionInput(emptyList(), TrackerSettings(), packStart.plusDays(1), listOf(inserted))
        val planned = plan(only(ReminderType.REPLACEMENT, option), packStart.plusDays(1).atTime(10, 0), methodInput)
        assertEquals(replaceBy.minusMonths(months).atTime(nine), planned?.at)
        assertEquals(ReminderEvent.ReplacementDue(method, replaceBy, months.toInt()), planned?.event)
    }

    // ---- Temperature -----------------------------------------------------------------------------------

    private val full = TrackerSettings(trackingLevel = TrackingLevel.FULL)

    @Test
    fun `the temperature reminder is daily and needs the full tracking level`() {
        val now = lastStart.atTime(7, 0)
        assertNull(plan(only(ReminderType.TEMPERATURE), now))
        val planned = plan(only(ReminderType.TEMPERATURE), now, input(settings = full))
        assertEquals(lastStart.plusDays(1).atTime(6, 30), planned?.at)
        assertEquals(ReminderEvent.TemperatureDue, planned?.event)
    }

    @Test
    fun `follicular only stops once a shift confirms ovulation and restarts with the next period`() {
        val temps = Synthetic.temperatures(lastStart.plusDays(5), lowDays = 9, highDays = 4)
        val entries = Synthetic.merge(cycles, temps)
        val afterShift = lastStart.plusDays(5 + 9 + 4L)
        val shifted = PredictionInput(entries, full, afterShift)
        val config = only(ReminderType.TEMPERATURE, ReminderOption.FOLLICULAR_ONLY)
        assertNull(ReminderPlanner.plan(shifted, config, afterShift.atTime(7, 0)).singleOrNull())
        val beforeShift = lastStart.plusDays(8)
        val early = PredictionInput(entries.filter { !it.date.isAfter(beforeShift) }, full, beforeShift)
        assertNotNull(ReminderPlanner.plan(early, config, beforeShift.atTime(7, 0)).singleOrNull())
        val nextPeriod = Synthetic.period(lastStart.plusDays(28))
        val restarted = PredictionInput(entries + nextPeriod, full, lastStart.plusDays(28))
        assertNotNull(ReminderPlanner.plan(restarted, config, lastStart.plusDays(28).atTime(7, 0)).singleOrNull())
    }

    // ---- Planning as a whole ----------------------------------------------------------------------------

    @Test
    fun `disabled and inapplicable reminders are not planned`() {
        val config =
            ReminderConfig(
                mapOf(
                    ReminderType.PERIOD to ReminderSetting(enabled = false),
                    ReminderType.PILL to ReminderSetting(enabled = true),
                    ReminderType.TEMPERATURE to ReminderSetting(enabled = true),
                ),
            )
        assertTrue(ReminderPlanner.plan(input(), config, lastStart.atTime(10, 0)).isEmpty())
    }

    @Test
    fun `due finds a reminder at its planned time even when the alarm arrives late`() {
        val pill = ContraceptionPeriod(ContraceptionMethod.PROGESTIN_ONLY_PILL, packStart)
        val today = packStart.plusDays(40)
        val methodInput = PredictionInput(emptyList(), TrackerSettings(), today, listOf(pill))
        val config = only(ReminderType.PILL)
        val planned = requireNotNull(plan(config, today.atTime(12, 0), methodInput))
        assertEquals(planned, ReminderPlanner.due(ReminderType.PILL, methodInput, config, planned.at))
        assertNull(ReminderPlanner.due(ReminderType.PILL, methodInput, config, planned.at.plusMinutes(5)))
        val switchedOff = config.update(ReminderType.PILL) { it.copy(enabled = false) }
        assertNull(ReminderPlanner.due(ReminderType.PILL, methodInput, switchedOff, planned.at))
    }
}
