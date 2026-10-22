package com.deutschdreamers.mensis.data.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class EncryptedContainerTest {
    /** Cheap parameters so the suite stays fast; the header carries them, so opening needs nothing else. */
    private val fast = KdfParams(memoryKib = 1_024, iterations = 1, parallelism = 1)
    private val password = "correct horse battery staple".toCharArray()
    private val payload = "{\"format\":\"mensis-export\",\"dayEntries\":[]}".repeat(50).toByteArray()

    @Test
    fun `seal then open returns the payload`() {
        val sealed = EncryptedContainer.seal(payload, password, fast)
        assertTrue(EncryptedContainer.isContainer(sealed))
        assertArrayEquals(payload, EncryptedContainer.open(sealed, password))
    }

    @Test
    fun `header layout carries magic, version, Argon2id and its parameters`() {
        val sealed = EncryptedContainer.seal(payload, password, fast)
        assertArrayEquals("MENSISBK".toByteArray(), sealed.copyOfRange(0, 8))
        val header = ByteBuffer.wrap(sealed, 8, EncryptedContainer.HEADER_BYTES - 8)
        assertEquals(1.toByte(), header.get())
        assertEquals(1.toByte(), header.get())
        assertEquals(1_024, header.int)
        assertEquals(1, header.int)
        assertEquals(1.toByte(), header.get())
        assertEquals(35, EncryptedContainer.HEADER_BYTES)
    }

    @Test
    fun `payload is compressed and never stored in clear`() {
        val sealed = EncryptedContainer.seal(payload, password, fast)
        assertTrue(sealed.size < payload.size)
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("mensis-export"))
    }

    @Test
    fun `two seals of the same payload differ (random salt and nonce)`() {
        assertFalse(
            EncryptedContainer
                .seal(
                    payload,
                    password,
                    fast,
                ).contentEquals(EncryptedContainer.seal(payload, password, fast)),
        )
    }

    @Test
    fun `wrong password fails authentication`() {
        val sealed = EncryptedContainer.seal(payload, password, fast)
        assertThrows<AuthenticationFailedException> {
            EncryptedContainer.open(sealed, "Correct horse battery staple".toCharArray())
        }
    }

    @Test
    fun `a flipped ciphertext byte fails authentication`() {
        val sealed = EncryptedContainer.seal(payload, password, fast)
        sealed[sealed.size - 20] = (sealed[sealed.size - 20].toInt() xor 1).toByte()
        assertThrows<AuthenticationFailedException> { EncryptedContainer.open(sealed, password) }
    }

    @Test
    fun `a changed salt in the header fails authentication`() {
        val sealed = EncryptedContainer.seal(payload, password, fast)
        sealed[EncryptedContainer.HEADER_BYTES - 1] =
            (sealed[EncryptedContainer.HEADER_BYTES - 1].toInt() xor 1).toByte()
        assertThrows<AuthenticationFailedException> { EncryptedContainer.open(sealed, password) }
    }

    @Test
    fun `truncated files and foreign files are reported as damaged`() {
        val sealed = EncryptedContainer.seal(payload, password, fast)
        assertThrows<DamagedContainerException> { EncryptedContainer.open(sealed.copyOfRange(0, 40), password) }
        assertThrows<DamagedContainerException> { EncryptedContainer.open("date,flow\n".toByteArray(), password) }
    }

    @Test
    fun `out-of-range parameters in a crafted header are rejected before deriving`() {
        val sealed = EncryptedContainer.seal(payload, password, fast)
        ByteBuffer.wrap(sealed).putInt(10, 4 * 1024 * 1024)
        assertThrows<DamagedContainerException> { EncryptedContainer.open(sealed, password) }
    }

    @Test
    fun `Argon2id is deterministic for the same salt and differs for another`() {
        val salt = ByteArray(16) { it.toByte() }
        val a = Argon2Kdf.derive(password, salt, fast)
        assertArrayEquals(a, Argon2Kdf.derive(password, salt, fast))
        assertNotEquals(a.toList(), Argon2Kdf.derive(password, ByteArray(16), fast).toList())
        assertEquals(32, a.size)
    }

    @Test
    fun `default parameters stay above the OWASP Argon2id floor`() {
        val d = KdfParams.DEFAULT
        assertTrue(d.memoryKib >= 19 * 1024 && d.iterations >= 2 && d.parallelism >= 1)
        assertTrue(d.isWithinLimits)
    }

    @Test
    fun `a stored key seals files the password opens`() {
        val key = EncryptedContainer.deriveKey(password, fast)
        val sealed = EncryptedContainer.seal(payload, key)
        assertArrayEquals(payload, EncryptedContainer.open(sealed, password))
        assertArrayEquals(payload, EncryptedContainer.open(sealed, key))
    }

    @Test
    fun `the key read back from a file with its password seals compatible files`() {
        val original = EncryptedContainer.seal(payload, password, fast)
        val key = EncryptedContainer.keyFor(original, password)
        val next = EncryptedContainer.seal("next".toByteArray(), key)
        assertArrayEquals("next".toByteArray(), EncryptedContainer.open(next, password))
        assertArrayEquals(
            original.copyOfRange(0, EncryptedContainer.HEADER_BYTES),
            next.copyOfRange(0, EncryptedContainer.HEADER_BYTES),
        )
    }

    @Test
    fun `a key derived from another password does not open the file`() {
        val sealed = EncryptedContainer.seal(payload, EncryptedContainer.deriveKey(password, fast))
        val other = EncryptedContainer.deriveKey("another password entirely".toCharArray(), fast)
        assertThrows<AuthenticationFailedException> { EncryptedContainer.open(sealed, other) }
    }

    @Test
    fun `destroy overwrites the key bytes`() {
        val key = EncryptedContainer.deriveKey(password, fast)
        key.destroy()
        assertTrue(key.bytes.all { it == 0.toByte() })
    }
}
