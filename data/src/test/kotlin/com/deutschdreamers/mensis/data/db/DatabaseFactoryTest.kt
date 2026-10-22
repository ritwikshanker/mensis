package com.deutschdreamers.mensis.data.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DatabaseFactoryTest {
    @Test
    fun `raw key passphrase is the SQLCipher x-quoted hex form`() {
        val key = ByteArray(DatabaseFactory.KEY_BYTES) { (it * 8).toByte() }
        val passphrase = DatabaseFactory.rawKeyPassphrase(key).toString(Charsets.US_ASCII)
        assertEquals(2 + 64 + 1, passphrase.length)
        assertEquals("x'", passphrase.take(2))
        assertEquals("'", passphrase.takeLast(1))
        val hex = passphrase.substring(2, passphrase.length - 1)
        assertEquals(key.joinToString("") { "%02x".format(it) }, hex)
        assertEquals("0008101820283038", hex.take(16))
    }

    @Test
    fun `negative bytes are rendered as two unsigned hex digits`() {
        val key = ByteArray(DatabaseFactory.KEY_BYTES) { 0xFF.toByte() }
        val passphrase = DatabaseFactory.rawKeyPassphrase(key).toString(Charsets.US_ASCII)
        assertEquals("x'" + "ff".repeat(DatabaseFactory.KEY_BYTES) + "'", passphrase)
    }

    @Test
    fun `keys of the wrong length are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DatabaseFactory.rawKeyPassphrase(ByteArray(31)) }
        assertThrows(IllegalArgumentException::class.java) { DatabaseFactory.rawKeyPassphrase(ByteArray(33)) }
    }

    @Test
    fun `every database file name is covered by the delete list`() {
        val names = listOf("mensis.db", "mensis.db-wal", "mensis.db-shm", "mensis.db-journal")
        assertEquals(names, DatabaseFactory.databaseFiles(FakeContext(), "mensis.db").map { it.name })
    }
}
