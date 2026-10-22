package com.deutschdreamers.mensis.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.db.DatabaseFactory
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.db.DayEntryEntity
import com.deutschdreamers.mensis.data.keys.DatabaseKeyProvider
import com.deutschdreamers.mensis.data.settings.UiPrefs
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class DataWipeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun wipeAll_removes_database_files_key_and_preferences_and_the_next_open_starts_fresh() =
        runBlocking {
            val keyProvider = DatabaseKeyProvider(context)
            val holder = DatabaseHolder(context, keyProvider)
            val wipe = DataWipe(context, holder, keyProvider, prefsStore)

            holder.get().dayEntryDao().upsert(
                DayEntryEntity(
                    id = "e1",
                    date = "2026-03-14",
                    flow = "LIGHT",
                    createdAt = "2026-03-14T06:45:00Z",
                    updatedAt = "2026-03-14T06:45:00Z",
                ),
            )
            prefsStore.update { it.copy(hideInRecents = false, relockTimeoutMinutes = 15) }
            val oldKey = keyProvider.getOrCreate()

            wipe.wipeAll()

            assertFalse(DatabaseFactory.databaseFiles(context).any { it.exists() })
            assertFalse(keyProvider.exists())
            assertFalse(keyStore().containsAlias(DatabaseKeyProvider.ALIAS))
            assertEquals(UiPrefs(), prefsStore.current())

            assertEquals(0, holder.get().dayEntryDao().countActive())
            assertFalse(oldKey.contentEquals(keyProvider.getOrCreate()))
            wipe.wipeAll()
        }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private companion object {
        /** DataStore allows one instance per file and process, so the store is shared by the whole test run. */
        val prefsStore: UiPrefsStore by lazy { UiPrefsStore(ApplicationProvider.getApplicationContext<Context>()) }
    }
}
