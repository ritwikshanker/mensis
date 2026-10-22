package com.deutschdreamers.mensis.wipe

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.deutschdreamers.mensis.backup.BackupController
import com.deutschdreamers.mensis.backup.auth.GoogleAuthException
import com.deutschdreamers.mensis.backup.drive.DriveHttpException
import com.deutschdreamers.mensis.backup.drive.ReconnectRequiredException
import com.deutschdreamers.mensis.data.repo.DataWipe
import com.deutschdreamers.mensis.files.DocumentFiles
import com.deutschdreamers.mensis.notifications.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Why the Drive backup could not be deleted; nothing else was deleted either. */
enum class DriveDeleteFailure { NO_CONNECTION, RECONNECT, OTHER }

sealed interface DeleteAllResult {
    data object Deleted : DeleteAllResult

    data class DriveFailed(
        val failure: DriveDeleteFailure,
    ) : DeleteAllResult
}

/**
 * Design §18 "Delete all data". The Drive backups go first (optional): if that fails, nothing on the
 * phone is touched, so the user can retry or untick the option. Then backup is turned off and its
 * key and settings forgotten, reminder alarms and posted notifications are cancelled, and the
 * database, its key and the UI preferences are wiped. Share copies in the cache go too.
 */
@Singleton
class DeleteAllData
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val backup: BackupController,
        private val reminders: ReminderScheduler,
        private val wipe: DataWipe,
        private val files: DocumentFiles,
    ) {
        suspend fun deleteAll(deleteDriveBackups: Boolean): DeleteAllResult =
            withContext(NonCancellable) {
                val failure =
                    try {
                        backup.erase(deleteDriveBackups)
                        null
                    } catch (expected: ReconnectRequiredException) {
                        DriveDeleteFailure.RECONNECT
                    } catch (expected: DriveHttpException) {
                        DriveDeleteFailure.OTHER
                    } catch (expected: IOException) {
                        DriveDeleteFailure.NO_CONNECTION
                    } catch (expected: GoogleAuthException) {
                        DriveDeleteFailure.OTHER
                    }
                if (failure != null) return@withContext DeleteAllResult.DriveFailed(failure)
                // Alarms and notifications first: the reminder observer stops once the preferences are cleared.
                reminders.cancelAll()
                NotificationManagerCompat.from(context).cancelAll()
                wipe.wipeAll()
                files.clearShared()
                DeleteAllResult.Deleted
            }
    }
