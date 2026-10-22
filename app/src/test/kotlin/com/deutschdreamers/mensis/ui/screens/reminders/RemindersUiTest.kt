package com.deutschdreamers.mensis.ui.screens.reminders

import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import com.deutschdreamers.mensis.notifications.NotificationText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RemindersUiTest {
    private val now = LocalDateTime.of(2026, 10, 21, 12, 0)
    private val allowed = ReminderSystemState(notificationsAllowed = true, exactAlarms = false)

    /** Seven synthetic 28-day cycles ending with a period on 1 Oct 2026. */
    private val entries: List<DayEntry> =
        (0..6).flatMap { cycle ->
            val start = LocalDate.of(2026, 10, 1).minusDays(28L * (6 - cycle))
            (0..4).map { DayEntry(start.plusDays(it.toLong()), flow = Flow.MEDIUM) }
        }

    private fun data(
        method: ContraceptionPeriod? = null,
        level: TrackingLevel = TrackingLevel.SYMPTOMS,
    ) = TrackerData(entries, TrackerSettings(trackingLevel = level), listOfNotNull(method))

    @Test
    fun `rows follow the method and tracking level`() {
        val ring = ContraceptionPeriod(ContraceptionMethod.RING, LocalDate.of(2026, 1, 5), regimen = Regimen.CYCLIC)
        val state = RemindersUi.build(data(ring, TrackingLevel.FULL), ReminderConfig(), allowed, now)
        assertEquals(
            listOf("Withdrawal bleed expected", "Ring out, then in", "Morning temperature"),
            state.rows.map { it.title },
        )
        assertEquals(
            listOf("1 day before · 09:00", "Every 3 weeks · 09:00", "Daily · 06:30"),
            state.rows.map { it.subtitle },
        )
        assertTrue(state.rows.none { it.enabled })
    }

    @Test
    fun `the preview is generic while discreet and shows the first enabled reminder otherwise`() {
        val config = ReminderConfig().update(ReminderType.PERIOD) { it.copy(enabled = true) }
        val discreet = RemindersUi.build(data(), config, allowed, now)
        assertEquals(NotificationText("Reminder", "Tap to open"), discreet.preview)
        val open = RemindersUi.build(data(), config.copy(discreet = false), allowed, now)
        assertEquals("Period expected in 2 days", open.preview.title)
        assertTrue(open.preview.body.startsWith("Expected "), open.preview.body)
        assertEquals(LocalTime.of(9, 0), open.previewTime)
    }

    @Test
    fun `the injection row names the due date of the chosen interval`() {
        val shot = ContraceptionPeriod(ContraceptionMethod.INJECTION, LocalDate.of(2026, 9, 11))
        val state = RemindersUi.build(data(shot), ReminderConfig(), allowed, now)
        assertEquals("4 Dec 2026 · 09:00", state.rows.single().subtitle)
    }
}
