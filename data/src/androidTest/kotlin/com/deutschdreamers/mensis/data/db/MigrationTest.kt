package com.deutschdreamers.mensis.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test that the exported schema assets are present and usable; real migrations arrive with schema version 2. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MensisDatabase::class.java)

    @Test
    fun schema_version_1_creates_every_table() {
        helper.createDatabase("migration-test.db", MensisDatabase.VERSION).use { db ->
            val tables = mutableSetOf<String>()
            db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            val expected =
                setOf("day_entries", "contraception_periods", "tracker_settings", "import_batches", "import_undo_rows")
            assertTrue("missing tables: ${expected - tables}", tables.containsAll(expected))
        }
    }
}
