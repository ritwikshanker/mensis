package com.deutschdreamers.mensis.data.repo

import android.content.Context
import com.deutschdreamers.mensis.data.db.DatabaseFactory
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.keys.DatabaseKeyProvider
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Delete all data": closes and deletes the encrypted database and its journal files, destroys the
 * wrapped database key and its Keystore alias, and clears the UI preferences. The next database
 * access creates a fresh key and an empty database. Drive backups are handled by the backup module.
 */
@Singleton
class DataWipe
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: DatabaseHolder,
        private val keyProvider: DatabaseKeyProvider,
        private val uiPrefsStore: UiPrefsStore,
    ) {
        suspend fun wipeAll() {
            // Held closed until the files and the key are gone, so nothing reopens it with the old key.
            database.closeAndThen {
                DatabaseFactory.databaseFiles(context).forEach { it.delete() }
                keyProvider.delete()
            }
            uiPrefsStore.clear()
        }
    }
