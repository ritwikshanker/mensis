package com.deutschdreamers.mensis

import com.deutschdreamers.mensis.backup.ReconnectNotifier
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.notifications.AndroidReminderAlarms
import com.deutschdreamers.mensis.notifications.BackupNotifications
import com.deutschdreamers.mensis.notifications.ReminderAlarms
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What the library modules need from the app (version, notifications), and the reminder alarms. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun reconnectNotifier(impl: BackupNotifications): ReconnectNotifier

    @Binds
    abstract fun reminderAlarms(impl: AndroidReminderAlarms): ReminderAlarms

    companion object {
        @Provides
        fun exportedApp(): ExportedApp = ExportedApp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }
}
