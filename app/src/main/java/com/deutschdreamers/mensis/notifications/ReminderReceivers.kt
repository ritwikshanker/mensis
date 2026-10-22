package com.deutschdreamers.mensis.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun reminderSync(): ReminderSync
}

private fun Context.reminderSync(): ReminderSync =
    EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java).reminderSync()

/** Runs [work] off the main thread while keeping the broadcast alive until it is done. */
private fun BroadcastReceiver.runAsync(
    sync: ReminderSync,
    work: suspend () -> Unit,
) {
    val pending = goAsync()
    sync.scope.launch {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}

/** A reminder alarm went off. The intent carries only the type and planned time, never health data. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_FIRE) return
        val type =
            intent.getStringExtra(EXTRA_TYPE)?.let { name ->
                ReminderType.entries.firstOrNull { it.name == name }
            }
        val planned = intent.getStringExtra(EXTRA_AT)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (type == null || planned == null) return
        val sync = context.reminderSync()
        runAsync(sync) { sync.fire(type, planned) }
    }

    companion object {
        const val ACTION_FIRE: String = "com.deutschdreamers.mensis.action.REMINDER"
        const val EXTRA_TYPE: String = "type"
        const val EXTRA_AT: String = "at"

        fun intent(
            context: Context,
            type: ReminderType,
        ): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_TYPE, type.name)
    }
}

/**
 * Alarms do not survive a reboot or an app update, and a time or time-zone change moves the local
 * time they were planned for; granting exact alarms lets them be set exactly. Each re-plans them.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in ACTIONS) return
        val sync = context.reminderSync()
        runAsync(sync) { sync.refresh() }
    }

    companion object {
        val ACTIONS: Set<String> =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                ACTION_EXACT_ALARM_PERMISSION_CHANGED,
            )

        /** `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, sent from Android 12 on. */
        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
