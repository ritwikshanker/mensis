package com.deutschdreamers.mensis.data

import android.content.Context
import com.deutschdreamers.mensis.data.backup.BackupStatusSource
import com.deutschdreamers.mensis.data.backup.NoBackupStatusSource
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun uiPrefsStore(
        @ApplicationContext context: Context,
    ): UiPrefsStore = UiPrefsStore(context)

    /** Replaced by the backup module's real source when Drive backup lands; until then backup is reported as off. */
    @Provides
    @Singleton
    fun backupStatusSource(): BackupStatusSource = NoBackupStatusSource
}
