package com.deutschdreamers.mensis.backup

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.deutschdreamers.mensis.backup.auth.GoogleAccessTokens
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.backup.auth.PlayServicesGoogleAccountGateway
import com.deutschdreamers.mensis.backup.drive.DriveAppDataClient
import com.deutschdreamers.mensis.data.backup.BackupStatusSource
import com.deutschdreamers.mensis.data.backup.BackupTrigger
import com.deutschdreamers.mensis.data.export.DataExporter
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.data.importer.ImportApplier
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt bindings for Drive backup. The app supplies [ExportedApp] (its version) and a
 * [ReconnectNotifier] (it owns the notification resources).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    abstract fun gateway(impl: PlayServicesGoogleAccountGateway): GoogleAccountGateway

    @Binds
    abstract fun keyStore(impl: KeystoreBackupKeyStore): BackupKeyStore

    @Binds
    abstract fun scheduler(impl: WorkManagerBackupScheduler): BackupScheduler

    @Binds
    abstract fun trigger(impl: WorkManagerBackupScheduler): BackupTrigger

    @Binds
    abstract fun statusSource(impl: BackupStatusStore): BackupStatusSource

    @Binds
    abstract fun restoreSource(impl: RestoreManager): RestoreSource

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 20L
        private const val TRANSFER_TIMEOUT_SECONDS = 60L

        @Provides
        @Singleton
        fun okHttpClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        @Provides
        @Singleton
        fun statusStore(
            @ApplicationContext context: Context,
            clock: Clock,
            gateway: GoogleAccountGateway,
        ): BackupStatusStore =
            BackupStatusStore(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                    context.preferencesDataStoreFile(BackupStatusStore.FILE_NAME)
                },
                clock,
                gateway::availability,
            )

        @Provides
        fun driveFactory(
            http: OkHttpClient,
            gateway: GoogleAccountGateway,
        ): DriveFactory = DriveFactory { email -> DriveAppDataClient(http, GoogleAccessTokens(gateway, email)) }

        @Provides
        fun snapshotSource(
            exporter: DataExporter,
            app: ExportedApp,
        ): SnapshotSource = SnapshotSource { exporter.snapshot(app) }

        @Provides
        @Singleton
        @Suppress("LongParameterList") // one argument per collaborator
        fun backupManager(
            snapshots: SnapshotSource,
            keys: BackupKeyStore,
            drives: DriveFactory,
            store: BackupStatusStore,
            notifier: ReconnectNotifier,
            clock: Clock,
        ): BackupManager = BackupManager(snapshots, keys, drives, store, notifier, clock, Build.MODEL.orEmpty())

        @Provides
        @Singleton
        @Suppress("LongParameterList") // one argument per collaborator
        fun backupController(
            store: BackupStatusStore,
            keys: BackupKeyStore,
            scheduler: BackupScheduler,
            manager: BackupManager,
            drives: DriveFactory,
            gateway: GoogleAccountGateway,
            notifier: ReconnectNotifier,
            clock: Clock,
        ): BackupController = BackupController(store, keys, scheduler, manager, drives, gateway, notifier, clock)

        @Provides
        @Singleton
        @Suppress("LongParameterList") // one argument per collaborator
        fun restoreManager(
            drives: DriveFactory,
            snapshots: SnapshotSource,
            applier: ImportApplier,
            uiPrefsStore: UiPrefsStore,
            keys: BackupKeyStore,
            store: BackupStatusStore,
            scheduler: BackupScheduler,
            clock: Clock,
        ): RestoreManager =
            RestoreManager(drives, snapshots, { applier.replaceAll(it) }, uiPrefsStore, keys, store, scheduler, clock)
    }
}
