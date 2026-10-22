package com.deutschdreamers.mensis.wipe

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.backup.BackupConfig
import com.deutschdreamers.mensis.backup.BackupController
import com.deutschdreamers.mensis.backup.BackupFrequency
import com.deutschdreamers.mensis.backup.BackupManager
import com.deutschdreamers.mensis.backup.BackupPayload
import com.deutschdreamers.mensis.backup.BackupScheduler
import com.deutschdreamers.mensis.backup.BackupStatusStore
import com.deutschdreamers.mensis.backup.DriveFactory
import com.deutschdreamers.mensis.backup.KeystoreBackupKeyStore
import com.deutschdreamers.mensis.backup.ManualBackupState
import com.deutschdreamers.mensis.backup.ReconnectNotifier
import com.deutschdreamers.mensis.backup.auth.AuthorizationOutcome
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.backup.auth.SignInResult
import com.deutschdreamers.mensis.backup.drive.BackupDrive
import com.deutschdreamers.mensis.backup.drive.DriveFile
import com.deutschdreamers.mensis.backup.drive.ReconnectRequiredException
import com.deutschdreamers.mensis.data.crypto.ContainerKey
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.db.DatabaseFactory
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.keys.DatabaseKeyProvider
import com.deutschdreamers.mensis.data.repo.DataWipe
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.data.settings.UiPrefs
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import com.deutschdreamers.mensis.files.DocumentFiles
import com.deutschdreamers.mensis.notifications.AndroidReminderAlarms
import com.deutschdreamers.mensis.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow as KFlow

/**
 * Delete all data end to end on the real encrypted database, Keystore keys, AlarmManager and backup
 * controller; only Google and Drive are fakes. Synthetic data only.
 */
@RunWith(AndroidJUnit4::class)
class DeleteAllDataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)

    private class FakeDrive : BackupDrive {
        val files = mutableListOf<DriveFile>()
        var failure: Exception? = null

        fun put(name: String) {
            files += DriveFile("f${files.size}", name, 48L, Instant.parse("2026-10-20T23:14:00Z"), null, emptyMap())
        }

        override suspend fun list(): List<DriveFile> {
            failure?.let { throw it }
            return files.toList()
        }

        override suspend fun upload(
            name: String,
            bytes: ByteArray,
            appProperties: Map<String, String>,
        ): DriveFile = error("not used")

        override suspend fun download(
            fileId: String,
            maxBytes: Long,
        ): ByteArray = error("not used")

        override suspend fun delete(fileId: String) {
            files.removeAll { it.id == fileId }
        }
    }

    private class FakeGateway : GoogleAccountGateway {
        val disconnected = mutableListOf<String>()

        override fun availability(): BackupAvailability = BackupAvailability.AVAILABLE

        override suspend fun signIn(activity: Activity): SignInResult = error("not used")

        override suspend fun authorize(email: String): AuthorizationOutcome = AuthorizationOutcome.Granted("token")

        override fun tokenFromConsent(data: Intent?): String? = null

        override suspend fun clearToken(token: String) = Unit

        override suspend fun disconnect(email: String) {
            disconnected += email
        }
    }

    private class FakeScheduler : BackupScheduler {
        var cancelled = 0

        override fun apply(config: BackupConfig) = Unit

        override fun backUpNow() = Unit

        override fun manualRuns(): KFlow<ManualBackupState> = flowOf(ManualBackupState.None)

        override fun cancelAll() {
            cancelled++
        }
    }

    private object NoNotifier : ReconnectNotifier {
        override fun notifyReconnect() = Unit

        override fun cancel() = Unit
    }

    private val keyProvider = DatabaseKeyProvider(context)
    private val holder = DatabaseHolder(context, keyProvider)
    private val dayEntries = DayEntryRepository(holder, clock)
    private val settings = SettingsRepository(holder, uiPrefs, clock)
    private val backupKeys = KeystoreBackupKeyStore(context)
    private val drive = FakeDrive()
    private val gateway = FakeGateway()
    private val backupScheduler = FakeScheduler()
    private val alarms = AndroidReminderAlarms(context)
    private val sharedFile = File(context.cacheDir, "shared/mensis-report-2026-10-21.pdf")
    private lateinit var deleteAll: DeleteAllData

    @Before
    fun setUp() =
        runBlocking<Unit> {
            DatabaseFactory.databaseFiles(context).forEach { it.delete() }
            val drives = DriveFactory { drive }
            val manager =
                BackupManager(
                    { error("no backup runs here") },
                    backupKeys,
                    drives,
                    backupStore,
                    NoNotifier,
                    clock,
                    "Pixel 9",
                )
            val controller =
                BackupController(backupStore, backupKeys, backupScheduler, manager, drives, gateway, NoNotifier, clock)
            deleteAll =
                DeleteAllData(
                    context,
                    controller,
                    ReminderScheduler(alarms),
                    DataWipe(context, holder, keyProvider, uiPrefs),
                    DocumentFiles(context),
                )

            // A phone in use: logged days, pregnancy mode, a reminder alarm, backup on with a key, a share copy.
            dayEntries.save(DayEntry(LocalDate.of(2026, 8, 17), flow = Flow.MEDIUM))
            dayEntries.save(DayEntry(LocalDate.of(2026, 10, 21), notes = "synthetic"))
            settings.updateTracker { it.copy(mode = TrackerMode.PREGNANCY, pregnancyLmp = LocalDate.of(2026, 8, 17)) }
            uiPrefs.update { it.copy(onboardingCompleted = true, theme = ThemeScheme.SAGE, relockTimeoutMinutes = 15) }
            backupKeys.store(ContainerKey(ByteArray(32) { 7 }, ByteArray(16) { 3 }, KdfParams.DEFAULT))
            backupStore.update {
                it.copy(enabled = true, accountEmail = EMAIL, frequency = BackupFrequency.DAILY, wifiOnly = false)
            }
            drive.put(BackupPayload.FILE_NAME)
            drive.put(BackupPayload.FILE_NAME)
            drive.put("not-ours.bin")
            val inAWeek = Instant.now().plus(7, ChronoUnit.DAYS)
            alarms.setWindow(
                ReminderType.PERIOD,
                inAWeek,
                ReminderScheduler.WINDOW,
                LocalDateTime.ofInstant(inAWeek, ZoneId.systemDefault()),
            )
            sharedFile.parentFile?.mkdirs()
            sharedFile.writeText("synthetic")
        }

    @After
    fun tearDown() =
        runBlocking<Unit> {
            ReminderType.entries.forEach(alarms::cancel)
            holder.close()
            DatabaseFactory.databaseFiles(context).forEach { it.delete() }
            keyProvider.delete()
            backupKeys.delete()
            backupStore.clear()
            uiPrefs.clear()
            sharedFile.delete()
        }

    @Test
    fun deletingWithDrive_removesEverythingAndTheNextStartIsEmpty() =
        runBlocking<Unit> {
            assertEquals(DeleteAllResult.Deleted, deleteAll.deleteAll(deleteDriveBackups = true))

            assertEquals(listOf("not-ours.bin"), drive.files.map { it.name })
            assertEquals(listOf(EMAIL), gateway.disconnected)
            assertEquals(1, backupScheduler.cancelled)
            assertNull(backupKeys.load())
            assertEquals(BackupConfig(), backupStore.current())
            assertFalse(alarms.isScheduled(ReminderType.PERIOD))
            assertFalse(DatabaseFactory.databaseFiles(context).any { it.exists() })
            assertFalse(keyProvider.exists())
            assertEquals(UiPrefs(), uiPrefs.current())
            assertFalse(sharedFile.exists())

            // The next access creates a fresh key and an empty database.
            assertEquals(0, dayEntries.countActive())
            assertEquals(TrackerSettings(), settings.tracker())
        }

    @Test
    fun deletingWithoutDrive_keepsTheDriveBackups() =
        runBlocking<Unit> {
            assertEquals(DeleteAllResult.Deleted, deleteAll.deleteAll(deleteDriveBackups = false))

            assertEquals(3, drive.files.size)
            assertEquals(BackupConfig(), backupStore.current())
            assertFalse(keyProvider.exists())
            assertEquals(0, dayEntries.countActive())
        }

    @Test
    fun aDriveFailure_deletesNothing() =
        runBlocking<Unit> {
            drive.failure = IOException("offline")
            assertEquals(
                DeleteAllResult.DriveFailed(DriveDeleteFailure.NO_CONNECTION),
                deleteAll.deleteAll(deleteDriveBackups = true),
            )
            drive.failure = ReconnectRequiredException()
            assertEquals(
                DeleteAllResult.DriveFailed(DriveDeleteFailure.RECONNECT),
                deleteAll.deleteAll(deleteDriveBackups = true),
            )

            assertEquals(3, drive.files.size)
            assertTrue(gateway.disconnected.isEmpty())
            assertNotNull(backupKeys.load())
            assertTrue(backupStore.current().enabled)
            assertTrue(alarms.isScheduled(ReminderType.PERIOD))
            assertTrue(uiPrefs.current().onboardingCompleted)
            assertTrue(keyProvider.exists())
            assertEquals(2, dayEntries.countActive())
            assertTrue(sharedFile.exists())
        }

    private companion object {
        const val EMAIL = "someone@example.com"

        // Stores of their own: the app's singletons keep the real preference files open in this process.
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val uiPrefs: UiPrefsStore by lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            UiPrefsStore(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                ) { context.preferencesDataStoreFile("test_wipe_ui_prefs") },
            )
        }

        val backupStore: BackupStatusStore by lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            BackupStatusStore(
                PreferenceDataStoreFactory.create(scope = scope) {
                    context.preferencesDataStoreFile("test_wipe_backup_prefs")
                },
                Clock.systemUTC(),
            ) { BackupAvailability.AVAILABLE }
        }
    }
}
