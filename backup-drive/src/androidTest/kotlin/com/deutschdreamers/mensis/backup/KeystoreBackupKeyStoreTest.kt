package com.deutschdreamers.mensis.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.keys.KeystoreWrappingKey
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The backup key wrapped in the real Android Keystore. Synthetic password only. */
@RunWith(AndroidJUnit4::class)
class KeystoreBackupKeyStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = KeystoreBackupKeyStore(context)
    private val file = File(context.filesDir, KeystoreBackupKeyStore.FILE_NAME)
    private val fast = KdfParams(memoryKib = 1_024, iterations = 1, parallelism = 1)

    @Before
    @After
    fun clean() {
        store.delete()
    }

    @Test
    fun storedKeySurvivesANewInstanceAndSealsForThePassword() {
        val key = EncryptedContainer.deriveKey("synthetic backup password".toCharArray(), fast)
        store.store(key)

        val loaded = requireNotNull(KeystoreBackupKeyStore(context).load())
        assertArrayEquals(key.bytes, loaded.bytes)
        assertArrayEquals(key.salt, loaded.salt)
        assertEquals(fast, loaded.params)
        val sealed = EncryptedContainer.seal("probe".toByteArray(), loaded)
        assertEquals("probe", String(EncryptedContainer.open(sealed, "synthetic backup password".toCharArray())))
    }

    @Test
    fun theFileHoldsNoPlainKey() {
        val key = EncryptedContainer.deriveKey("synthetic backup password".toCharArray(), fast)
        store.store(key)
        val bytes = file.readBytes()
        assertFalse(bytes.toList().windowed(key.bytes.size).any { it == key.bytes.toList() })
    }

    @Test
    fun deleteRemovesFileAndKeystoreAlias() {
        store.store(EncryptedContainer.deriveKey("synthetic backup password".toCharArray(), fast))
        store.delete()
        assertFalse(file.exists())
        assertFalse(KeystoreWrappingKey(KeystoreBackupKeyStore.ALIAS).exists())
        assertNull(store.load())
    }

    @Test
    fun aKeyWrappedByALostKeystoreKeyReadsAsMissing() {
        store.store(EncryptedContainer.deriveKey("synthetic backup password".toCharArray(), fast))
        KeystoreWrappingKey(KeystoreBackupKeyStore.ALIAS).delete()
        assertNull(store.load())
    }
}
