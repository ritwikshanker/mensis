package com.deutschdreamers.mensis.notifications

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The real AlarmManager behind [ReminderAlarms], and the receivers the manifest declares. */
@RunWith(AndroidJUnit4::class)
class ReminderAlarmsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = AndroidReminderAlarms(context)
    private val inAWeek: Instant = Instant.now().plus(7, ChronoUnit.DAYS)
    private val planned: LocalDateTime = LocalDateTime.ofInstant(inAWeek, ZoneId.systemDefault())

    @After
    fun cancel() {
        ReminderType.entries.forEach(alarms::cancel)
    }

    @Test
    fun windowedAlarmsAreSetAndCancelledPerType() {
        alarms.setWindow(ReminderType.PERIOD, inAWeek, ReminderScheduler.WINDOW, planned)
        assertTrue(alarms.isScheduled(ReminderType.PERIOD))
        assertFalse(alarms.isScheduled(ReminderType.PILL))
        alarms.cancel(ReminderType.PERIOD)
        assertFalse(alarms.isScheduled(ReminderType.PERIOD))
    }

    @Test
    fun exactAlarmsFallBackToAWindowWhenNotAllowed() {
        // Works either way: exact when granted, a window (no SecurityException) when not.
        alarms.setExact(ReminderType.TEMPERATURE, inAWeek, planned)
        assertTrue(alarms.isScheduled(ReminderType.TEMPERATURE))
    }

    @Test
    fun theSchedulerCancelsWhatIsNoLongerPlanned() {
        alarms.setWindow(ReminderType.RING, inAWeek, ReminderScheduler.WINDOW, planned)
        ReminderScheduler(alarms).apply(emptyList(), ZoneId.systemDefault())
        assertFalse(alarms.isScheduled(ReminderType.RING))
    }

    @Test
    fun rebootsUpdatesAndClockChangesReachTheRescheduleReceiver() {
        val receivers =
            listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            ).map { action ->
                context.packageManager
                    .queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
                    .map { it.activityInfo.name }
            }
        // WorkManager registers its own receiver for some of these too.
        receivers.forEach { assertTrue(it.toString(), RescheduleReceiver::class.java.name in it) }
        assertEquals(RescheduleReceiver.ACTIONS.size, receivers.size)
    }

    @Test
    fun theAlarmIntentNamesOnlyTheReminderType() {
        val intent = ReminderReceiver.intent(context, ReminderType.PILL)
        assertEquals(setOf(ReminderReceiver.EXTRA_TYPE), intent.extras?.keySet())
        assertEquals(ReminderReceiver::class.java.name, intent.component?.className)
    }
}
