package com.deutschdreamers.mensis.data.crypto

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The authenticated payload could not be decrypted: wrong key, truncated or tampered data. */
class AuthenticationFailedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * AES-256-GCM with a random 96-bit nonce prefixed to the ciphertext. Pure JCA, so it runs in JVM
 * unit tests; the Keystore-backed variant for wrapping lives in the keys package.
 */
object AesGcm {
    const val KEY_BYTES: Int = 32
    const val NONCE_BYTES: Int = 12
    const val TAG_BITS: Int = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray = ByteArray(0),
    ): ByteArray {
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        return nonce + cipher.doFinal(plaintext)
    }

    fun decrypt(
        key: ByteArray,
        blob: ByteArray,
        associatedData: ByteArray = ByteArray(0),
    ): ByteArray {
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes" }
        if (blob.size < NONCE_BYTES + TAG_BITS / Byte.SIZE_BITS) {
            throw AuthenticationFailedException("payload too short")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, blob, 0, NONCE_BYTES))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        return try {
            cipher.doFinal(blob, NONCE_BYTES, blob.size - NONCE_BYTES)
        } catch (e: AEADBadTagException) {
            throw AuthenticationFailedException("authentication tag mismatch", e)
        }
    }
}
