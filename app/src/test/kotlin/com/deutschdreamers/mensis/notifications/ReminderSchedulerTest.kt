package com.deutschdreamers.mensis.notifications

import com.deutschdreamers.mensis.domain.reminders.PlannedReminder
import com.deutschdreamers.mensis.domain.reminders.ReminderEvent
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderSchedulerTest {
    private class FakeAlarms(
        var exactAllowed: Boolean,
    ) : ReminderAlarms {
        val calls = mutableListOf<String>()

        override fun canScheduleExact(): Boolean = exactAllowed

        override fun setExact(
            type: ReminderType,
            at: Instant,
            planned: LocalDateTime,
        ) {
            calls += "exact $type $at $planned"
        }

        override fun setWindow(
            type: ReminderType,
            start: Instant,
            length: Duration,
            planned: LocalDateTime,
        ) {
            calls += "window $type $start ${length.toMinutes()}min $planned"
        }

        override fun cancel(type: ReminderType) {
            calls += "cancel $type"
        }
    }

    private val berlin = ZoneId.of("Europe/Berlin")
    private val pill =
        PlannedReminder(ReminderType.PILL, LocalDateTime.of(2026, 10, 24, 21, 0), ReminderEvent.PillDue(5, null))

    @Test
    fun `exact alarms are used when the user has granted them`() {
        val alarms = FakeAlarms(exactAllowed = true)
        ReminderScheduler(alarms).apply(listOf(pill), berlin)
        assertEquals("exact PILL 2026-10-24T19:00:00Z 2026-10-24T21:00", alarms.calls.single { it.startsWith("exact") })
    }

    @Test
    fun `without exact alarms a 15-minute window starts at the reminder time`() {
        val alarms = FakeAlarms(exactAllowed = false)
        ReminderScheduler(alarms).apply(listOf(pill), berlin)
        assertEquals(
            "window PILL 2026-10-24T19:00:00Z 15min 2026-10-24T21:00",
            alarms.calls.single { it.startsWith("window") },
        )
    }

    @Test
    fun `every type without a planned reminder is cancelled`() {
        val alarms = FakeAlarms(exactAllowed = true)
        ReminderScheduler(alarms).apply(listOf(pill), berlin)
        val cancelled =
            alarms.calls
                .filter {
                    it.startsWith(
                        "cancel",
                    )
                }.map { ReminderType.valueOf(it.removePrefix("cancel ")) }
        assertEquals(ReminderType.entries - ReminderType.PILL, cancelled)
        alarms.calls.clear()
        ReminderScheduler(alarms).cancelAll()
        assertEquals(ReminderType.entries.map { "cancel $it" }, alarms.calls)
    }

    @Test
    fun `local reminder times follow the zone across a daylight saving change`() {
        val alarms = FakeAlarms(exactAllowed = true)
        val afterChange = pill.copy(at = LocalDateTime.of(2026, 10, 25, 21, 0))
        ReminderScheduler(alarms).apply(listOf(afterChange), berlin)
        assertEquals("exact PILL 2026-10-25T20:00:00Z 2026-10-25T21:00", alarms.calls.single { it.startsWith("exact") })
    }
}
