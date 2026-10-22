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
import com.deutschdreamers.mensis.backup.ReconnectNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Reconnect your Google account to continue backups", posted when a scheduled backup finds the
 * Drive grant revoked. Tapping it opens the app, where Today shows the Reconnect banner. No health
 * data in the text; private on the lock screen like every Mensis notification.
 */
@Singleton
class BackupNotifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ReconnectNotifier {
        override fun notifyReconnect() {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            ensureChannel()
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
                    .setSmallIcon(R.drawable.ms_manage_accounts)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.backup_notification_public))
                    .build()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ms_manage_accounts)
                    .setContentTitle(context.getString(R.string.backup_notification_title))
                    .setContentText(context.getString(R.string.backup_notification_body))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(public)
                    .build()
            try {
                manager.notify(NOTIFICATION_ID, notification)
            } catch (expected: SecurityException) {
                // Permission withdrawn between the check and the post; the Today banner still shows it.
            }
        }

        override fun cancel() {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun ensureChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.backup_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.backup_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private companion object {
            const val CHANNEL_ID = "backup"
            const val NOTIFICATION_ID = 6001
        }
    }
