package com.deutschdreamers.mensis.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.deutschdreamers.mensis.MainActivity
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.domain.reminders.PlannedReminder
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts reminders on the "Reminders" channel. The text is generic unless discreet wording is off
 * (privacy rule 6), and the lock screen always shows only the generic title (`VISIBILITY_PRIVATE`
 * with a public version). Tapping opens the app behind its lock.
 */
@Singleton
class ReminderNotifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Whether Android will show them: permission granted and neither the app nor the channel blocked. */
        fun canPost(): Boolean {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
            return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        fun post(
            reminder: PlannedReminder,
            config: ReminderConfig,
        ) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            ensureChannel()
            val text = ReminderTexts.notification(reminder, config)
            val open =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val public =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ms_notifications)
                    .setContentTitle(ReminderTexts.generic(config).title)
                    .build()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ms_notifications)
                    .setContentTitle(text.title)
                    .setContentText(text.body)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(public)
                    .build()
            try {
                manager.notify(NOTIFICATION_ID_BASE + reminder.type.ordinal, notification)
            } catch (expected: SecurityException) {
                // Permission withdrawn between the check and the post; the Reminders screen shows it.
            }
        }

        private fun ensureChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminders_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.reminders_channel_description)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private companion object {
            const val CHANNEL_ID = "reminders"
            const val NOTIFICATION_ID_BASE = 7100
        }
    }
