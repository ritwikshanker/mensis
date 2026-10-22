package com.deutschdreamers.mensis.notifications

import com.deutschdreamers.mensis.domain.reminders.PlannedReminder
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns planned reminders into alarms: exact (and allowed while the phone is idle) when the user has
 * granted exact alarms, otherwise a 15-minute window starting at the reminder time. Exact alarms are
 * offered on the Reminders screen but never required. Types without a planned reminder are cancelled.
 */
@Singleton
class ReminderScheduler
    @Inject
    constructor(
        private val alarms: ReminderAlarms,
    ) {
        fun apply(
            planned: List<PlannedReminder>,
            zone: ZoneId,
        ) {
            val exact = alarms.canScheduleExact()
            val byType = planned.associateBy { it.type }
            for (type in ReminderType.entries) {
                val reminder = byType[type]
                if (reminder == null) {
                    alarms.cancel(type)
                    continue
                }
                val at = reminder.at.atZone(zone).toInstant()
                if (exact) {
                    alarms.setExact(type, at, reminder.at)
                } else {
                    alarms.setWindow(type, at, WINDOW, reminder.at)
                }
            }
        }

        fun cancelAll() {
            ReminderType.entries.forEach(alarms::cancel)
        }

        companion object {
            /** Without exact alarms a reminder arrives within this long after its time (Doze can add more). */
            val WINDOW: Duration = Duration.ofMinutes(15)
        }
    }
