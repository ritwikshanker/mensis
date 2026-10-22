package com.deutschdreamers.mensis.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** The AlarmManager calls reminders need, behind an interface so the choice between them is unit-tested. */
interface ReminderAlarms {
    /** Android 12+ lets the user grant or refuse exact alarms; below that they are always allowed. */
    fun canScheduleExact(): Boolean

    /** [planned] is the reminder's local time; the receiver uses it to find the reminder again. */
    fun setExact(
        type: ReminderType,
        at: Instant,
        planned: LocalDateTime,
    )

    fun setWindow(
        type: ReminderType,
        start: Instant,
        length: Duration,
        planned: LocalDateTime,
    )

    fun cancel(type: ReminderType)
}

/** One alarm per reminder type, each a broadcast to [ReminderReceiver] that wakes the phone. */
@Singleton
class AndroidReminderAlarms
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReminderAlarms {
        private val manager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

        override fun canScheduleExact(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

        override fun setExact(
            type: ReminderType,
            at: Instant,
            planned: LocalDateTime,
        ) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation(type, planned))
            } catch (expected: SecurityException) {
                // Exact alarms were withdrawn between the check and the call.
                setWindow(type, at, ReminderScheduler.WINDOW, planned)
            }
        }

        override fun setWindow(
            type: ReminderType,
            start: Instant,
            length: Duration,
            planned: LocalDateTime,
        ) {
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                start.toEpochMilli(),
                length.toMillis(),
                operation(type, planned),
            )
        }

        override fun cancel(type: ReminderType) {
            val existing = existing(type) ?: return
            manager.cancel(existing)
            existing.cancel()
        }

        /** Whether an alarm is pending for [type]; for tests. */
        fun isScheduled(type: ReminderType): Boolean = existing(type) != null

        private fun operation(
            type: ReminderType,
            planned: LocalDateTime,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode(type),
                ReminderReceiver.intent(context, type).putExtra(ReminderReceiver.EXTRA_AT, planned.toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        // Extras do not take part in PendingIntent matching, so the bare intent finds the alarm.
        private fun existing(type: ReminderType): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                requestCode(type),
                ReminderReceiver.intent(context, type),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )

        private fun requestCode(type: ReminderType): Int = REQUEST_CODE_BASE + type.ordinal

        private companion object {
            const val REQUEST_CODE_BASE = 7000
        }
    }
