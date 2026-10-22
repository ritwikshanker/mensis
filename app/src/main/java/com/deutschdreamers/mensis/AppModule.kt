package com.deutschdreamers.mensis

import com.deutschdreamers.mensis.backup.ReconnectNotifier
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.notifications.BackupNotifications
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What the library modules need from the app: its version (export, backup) and its notifications. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun reconnectNotifier(impl: BackupNotifications): ReconnectNotifier

    companion object {
        @Provides
        fun exportedApp(): ExportedApp = ExportedApp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }
}
