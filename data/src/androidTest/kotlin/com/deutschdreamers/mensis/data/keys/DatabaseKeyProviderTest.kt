package com.deutschdreamers.mensis.data.keys

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.db.DatabaseFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class DatabaseKeyProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val provider = DatabaseKeyProvider(context)
    private val keyFile = File(context.filesDir, DatabaseKeyProvider.FILE_NAME)

    private fun aliasExists(): Boolean =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(DatabaseKeyProvider.ALIAS)

    @Before
    fun clean() = provider.delete()

    @After
    fun tearDown() = provider.delete()

    @Test
    fun the_key_is_created_once_and_unwraps_to_the_same_bytes() {
        val first = provider.getOrCreate()
        assertEquals(DatabaseFactory.KEY_BYTES, first.size)
        assertTrue(provider.exists())
        assertTrue(keyFile.exists())
        assertTrue(aliasExists())
        assertArrayEquals(first, provider.getOrCreate())
        assertArrayEquals(first, DatabaseKeyProvider(context).getOrCreate())
    }

    @Test
    fun the_stored_file_is_versioned_and_does_not_hold_the_key_in_the_clear() {
        val key = provider.getOrCreate()
        val stored = keyFile.readBytes()
        assertEquals(1.toByte(), stored[0])
        assertTrue(stored.size > key.size)
        val window = key.copyOf(8)
        val found =
            (0..stored.size - window.size).any {
                stored
                    .copyOfRange(
                        it,
                        it + window.size,
                    ).contentEquals(window)
            }
        assertFalse("key bytes must not appear in the wrapped file", found)
    }

    @Test
    fun delete_removes_the_file_and_the_keystore_alias_and_a_new_key_differs() {
        val old = provider.getOrCreate()
        provider.delete()
        assertFalse(provider.exists())
        assertFalse(keyFile.exists())
        assertFalse(aliasExists())
        val fresh = provider.getOrCreate()
        assertFalse(old.contentEquals(fresh))
    }
}
