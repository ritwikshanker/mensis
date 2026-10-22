package com.deutschdreamers.mensis.notifications

import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.reminders.PlannedReminder
import com.deutschdreamers.mensis.domain.reminders.ReminderCatalog
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.domain.reminders.ReminderEvent
import com.deutschdreamers.mensis.domain.reminders.ReminderOption
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderTextsTest {
    private val periodSoon =
        PlannedReminder(
            ReminderType.PERIOD,
            LocalDateTime.of(2026, 10, 31, 9, 0),
            ReminderEvent.PeriodExpected(DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5)), 2, 8, false),
        )

    @Test
    fun `notifications are generic by default`() {
        assertEquals(
            NotificationText("Reminder", "Tap to open"),
            ReminderTexts.notification(periodSoon, ReminderConfig()),
        )
    }

    @Test
    fun `the generic text can be the user's own, blank falls back to the default`() {
        val own = ReminderConfig(discreetTitle = "Water the plants", discreetBody = " ")
        assertEquals(NotificationText("Water the plants", "Tap to open"), ReminderTexts.notification(periodSoon, own))
    }

    @Test
    fun `with discreet wording off the period reminder reads like the design`() {
        assertEquals(
            NotificationText("Period expected in 2 days", "Expected 2–5 Nov · based on 8 cycles"),
            ReminderTexts.notification(periodSoon, ReminderConfig(discreet = false)),
        )
        val typical = periodSoon.event as ReminderEvent.PeriodExpected
        assertEquals(
            NotificationText("Period expected tomorrow", "Expected 2–5 Nov · based on your typical length"),
            ReminderTexts.detailed(typical.copy(daysAhead = 1, usesTypicalLength = true)),
        )
    }

    @Test
    fun `method reminders say what to do`() {
        assertEquals("Active day 5 of 21", ReminderTexts.detailed(ReminderEvent.PillDue(5, null)).body)
        assertEquals("Break week, day 2 of 7", ReminderTexts.detailed(ReminderEvent.PillDue(23, null)).body)
        assertEquals(
            "Take it by 00:00, within 3 hours",
            ReminderTexts.detailed(ReminderEvent.PillDue(null, LocalTime.MIDNIGHT)).body,
        )
        assertEquals(
            NotificationText("Implant replacement in 3 months", "Replace by 12 Mar 2029"),
            ReminderTexts.detailed(
                ReminderEvent.ReplacementDue(ContraceptionMethod.IMPLANT, LocalDate.of(2029, 3, 12), 3),
            ),
        )
    }

    @Test
    fun `row lines follow the design`() {
        val config = ReminderConfig()
        val patch = ContraceptionPeriod(ContraceptionMethod.PATCH, LocalDate.of(2026, 1, 5), regimen = Regimen.CYCLIC)
        val pop = ContraceptionPeriod(ContraceptionMethod.PROGESTIN_ONLY_PILL, LocalDate.of(2026, 1, 3))
        val implant = ContraceptionPeriod(ContraceptionMethod.IMPLANT, LocalDate.of(2026, 3, 12))

        fun line(
            type: ReminderType,
            method: ContraceptionPeriod?,
            date: LocalDate? = null,
        ) = ReminderTexts.subtitle(ReminderCatalog.resolve(type, config, method), method, date)
        assertEquals("2 days before · 09:00", line(ReminderType.PERIOD, null))
        assertEquals("1 day before · 09:00", line(ReminderType.PERIOD, patch))
        assertEquals("Day before it starts · 09:00", line(ReminderType.FERTILE, null))
        assertEquals("Daily · 21:00 · 3-hour window", line(ReminderType.PILL, pop))
        assertEquals("Weekly, Monday · 09:00", line(ReminderType.PATCH, patch))
        assertEquals("Mar 2029 · 3 months before", line(ReminderType.REPLACEMENT, implant, LocalDate.of(2029, 3, 12)))
        assertEquals("Daily · 06:30", line(ReminderType.TEMPERATURE, null))
        assertEquals("Withdrawal bleed expected", ReminderTexts.title(ReminderType.PERIOD, patch))
        assertEquals("3 weeks in, 1 out", ReminderTexts.option(ReminderOption.THREE_WEEKS_IN_ONE_OUT))
    }

    @Test
    fun `summaries name what is on`() {
        val pill =
            ContraceptionPeriod(ContraceptionMethod.COMBINED_PILL, LocalDate.of(2026, 1, 3), regimen = Regimen.CYCLIC)
        assertEquals("Off", ReminderTexts.summary(emptyList(), null))
        assertEquals(
            "Period, pill, temperature",
            ReminderTexts.summary(listOf(ReminderType.PERIOD, ReminderType.PILL, ReminderType.TEMPERATURE), null),
        )
        assertEquals("Bleed, pill", ReminderTexts.summary(listOf(ReminderType.PERIOD, ReminderType.PILL), pill))
        val off = ReminderCatalog.resolve(ReminderType.PILL, ReminderConfig(), pill)
        assertEquals("Pill · Off", ReminderTexts.methodSummary(off, pill, null))
        assertEquals("Pill · Daily · 21:00", ReminderTexts.methodSummary(off.copy(enabled = true), pill, null))
        assertEquals("No method reminder", ReminderTexts.methodSummary(null, null, null))
    }
}
