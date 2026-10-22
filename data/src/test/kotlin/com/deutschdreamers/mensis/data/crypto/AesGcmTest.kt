package com.deutschdreamers.mensis.data.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.experimental.xor

class AesGcmTest {
    private val key = ByteArray(AesGcm.KEY_BYTES) { it.toByte() }
    private val otherKey = ByteArray(AesGcm.KEY_BYTES) { (it + 1).toByte() }
    private val plaintext = "synthetic payload, not health data".toByteArray()

    @Test
    fun `round trip restores the plaintext`() {
        val blob = AesGcm.encrypt(key, plaintext)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, blob))
    }

    @Test
    fun `empty plaintext round trips`() {
        val blob = AesGcm.encrypt(key, ByteArray(0))
        assertEquals(AesGcm.NONCE_BYTES + AesGcm.TAG_BITS / Byte.SIZE_BITS, blob.size)
        assertArrayEquals(ByteArray(0), AesGcm.decrypt(key, blob))
    }

    @Test
    fun `round trip with associated data`() {
        val aad = "header".toByteArray()
        val blob = AesGcm.encrypt(key, plaintext, aad)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, blob, aad))
    }

    @Test
    fun `every encryption uses a fresh nonce`() {
        val first = AesGcm.encrypt(key, plaintext)
        val second = AesGcm.encrypt(key, plaintext)
        assertFalse(first.contentEquals(second))
        assertFalse(first.copyOf(AesGcm.NONCE_BYTES).contentEquals(second.copyOf(AesGcm.NONCE_BYTES)))
    }

    @Test
    fun `a flipped ciphertext byte fails authentication`() {
        val blob = AesGcm.encrypt(key, plaintext)
        blob[AesGcm.NONCE_BYTES] = blob[AesGcm.NONCE_BYTES] xor 0x01
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, blob) }
    }

    @Test
    fun `a flipped tag byte fails authentication`() {
        val blob = AesGcm.encrypt(key, plaintext)
        blob[blob.lastIndex] = blob[blob.lastIndex] xor 0x01
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, blob) }
    }

    @Test
    fun `a flipped nonce byte fails authentication`() {
        val blob = AesGcm.encrypt(key, plaintext)
        blob[0] = blob[0] xor 0x01
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, blob) }
    }

    @Test
    fun `the wrong key fails authentication`() {
        val blob = AesGcm.encrypt(key, plaintext)
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(otherKey, blob) }
    }

    @Test
    fun `mismatched associated data fails authentication`() {
        val blob = AesGcm.encrypt(key, plaintext, "v1".toByteArray())
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, blob, "v2".toByteArray()) }
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, blob) }
    }

    @Test
    fun `a truncated payload is rejected`() {
        assertThrows(
            AuthenticationFailedException::class.java,
        ) { AesGcm.decrypt(key, ByteArray(AesGcm.NONCE_BYTES + 3)) }
        assertThrows(AuthenticationFailedException::class.java) { AesGcm.decrypt(key, ByteArray(0)) }
    }

    @Test
    fun `keys must be 32 bytes`() {
        assertThrows(IllegalArgumentException::class.java) { AesGcm.encrypt(ByteArray(16), plaintext) }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { AesGcm.decrypt(ByteArray(31), AesGcm.encrypt(key, plaintext)) }
    }
}
