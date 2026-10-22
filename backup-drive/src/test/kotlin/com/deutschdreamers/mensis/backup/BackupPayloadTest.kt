package com.deutschdreamers.mensis.backup

import com.deutschdreamers.mensis.data.crypto.ContainerKey
import com.deutschdreamers.mensis.domain.model.Flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupPayloadTest {
    private val key = Fixtures.key()
    private val export = Fixtures.export("2026-10-01" to Flow.HEAVY)

    @Test
    fun `the fingerprint ignores the export time`() {
        assertEquals(
            BackupPayload.fingerprint(export, key),
            BackupPayload.fingerprint(export.copy(exportedAt = "2027-01-01T00:00:00Z"), key),
        )
    }

    @Test
    fun `the fingerprint changes with the data and with the password`() {
        val changed = Fixtures.export("2026-10-01" to Flow.LIGHT)
        assertNotEquals(BackupPayload.fingerprint(export, key), BackupPayload.fingerprint(changed, key))
        assertNotEquals(BackupPayload.fingerprint(export, key), BackupPayload.fingerprint(export, Fixtures.key()))
    }

    @Test
    fun `the key id depends on the salt alone`() {
        val sameSalt = ContainerKey(ByteArray(32) { 7 }, key.salt.copyOf(), key.params)
        assertEquals(BackupPayload.keyId(key), BackupPayload.keyId(sameSalt))
        assertNotEquals(BackupPayload.keyId(key), BackupPayload.keyId(Fixtures.key()))
        assertEquals(16, BackupPayload.keyId(key).length)
    }

    @Test
    fun `app properties carry no health data and fit Drive's 124-byte limit`() {
        val props =
            BackupPayload.appProperties(
                export,
                BackupPayload.fingerprint(export, key),
                BackupPayload.keyId(key),
                "A".repeat(200),
            )
        assertEquals(
            setOf("formatVersion", "schemaVersion", "device", "fingerprint", "keyId"),
            props.keys,
        )
        assertTrue(props.all { (k, v) -> (k + v).toByteArray().size <= 124 })
        assertTrue(props.values.none { it.contains("2026-10-01") })
    }

    @Test
    fun `the key file codec round trips and refuses odd sizes`() {
        val decoded = requireNotNull(KeystoreBackupKeyStore.decode(KeystoreBackupKeyStore.encode(key)))
        assertTrue(decoded.bytes.contentEquals(key.bytes))
        assertTrue(decoded.salt.contentEquals(key.salt))
        assertEquals(key.params, decoded.params)
        assertNull(KeystoreBackupKeyStore.decode(ByteArray(10)))
    }
}
