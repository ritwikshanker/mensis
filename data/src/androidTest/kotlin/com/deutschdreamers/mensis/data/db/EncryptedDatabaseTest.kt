package com.deutschdreamers.mensis.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.keys.DatabaseKeyProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "encrypted-test.db"
    private val keyProvider = DatabaseKeyProvider(context)

    /** Synthetic row; nothing here comes from a real person. */
    private val entry =
        DayEntryEntity(
            id = "e1",
            date = "2026-03-14",
            flow = "MEDIUM",
            temperatureC = 36.6,
            temperatureTime = "06:40",
            symptoms = """["CRAMPS"]""",
            moods = """["CALM"]""",
            notes = "synthetic",
            createdAt = "2026-03-14T06:45:00Z",
            updatedAt = "2026-03-14T06:45:00Z",
        )

    @Before
    fun clean() {
        DatabaseFactory.databaseFiles(context, name).forEach { it.delete() }
        keyProvider.delete()
    }

    @After
    fun tearDown() = clean()

    @Test
    fun insert_close_and_reopen_with_the_same_key_reads_the_row_back() =
        runBlocking {
            DatabaseFactory.open(context, keyProvider.getOrCreate(), name).apply {
                dayEntryDao().upsert(entry)
                close()
            }
            val reopened = DatabaseFactory.open(context, keyProvider.getOrCreate(), name)
            try {
                assertEquals(listOf(entry), reopened.dayEntryDao().active())
            } finally {
                reopened.close()
            }
        }

    @Test
    fun a_wrong_key_fails_to_open_and_leaves_the_data_intact() =
        runBlocking {
            DatabaseFactory.open(context, keyProvider.getOrCreate(), name).apply {
                dayEntryDao().upsert(entry)
                close()
            }
            val wrongKey = ByteArray(DatabaseFactory.KEY_BYTES).also { SecureRandom().nextBytes(it) }
            val wrong = DatabaseFactory.open(context, wrongKey, name)
            val attempt = runCatching { wrong.dayEntryDao().countActive() }
            wrong.close()
            assertTrue("opening with a wrong key must fail", attempt.isFailure)
            assertTrue(context.getDatabasePath(name).exists())

            val reopened = DatabaseFactory.open(context, keyProvider.getOrCreate(), name)
            try {
                assertEquals(1, reopened.dayEntryDao().countActive())
            } finally {
                reopened.close()
            }
        }

    @Test
    fun the_database_file_is_not_plaintext_sqlite() =
        runBlocking {
            DatabaseFactory.open(context, keyProvider.getOrCreate(), name).apply {
                dayEntryDao().upsert(entry)
                close()
            }
            val file = context.getDatabasePath(name)
            assertTrue(file.length() > 0)
            val header = file.inputStream().use { it.readNBytes(16) }
            val plaintextMagic = "SQLite format 3".toByteArray(Charsets.US_ASCII)
            assertFalse(header.copyOf(plaintextMagic.size).contentEquals(plaintextMagic))
            val text = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(text.contains("synthetic"))
            assertFalse(text.contains("day_entries"))
        }
}
