package com.deutschdreamers.mensis.data.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** The file is not a readable container: truncated, a different file type, or its header was altered. */
class DamagedContainerException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Password-encrypted file format shared by exports and (from build step 6) Drive backups:
 *
 * `magic "MENSISBK" (8) ‖ version (1) ‖ kdf = 1, Argon2id (1) ‖ memory KiB (4) ‖ iterations (4) ‖
 * parallelism (1) ‖ salt (16) ‖ nonce (12) ‖ AES-256-GCM(gzip(payload)) with its 16-byte tag`
 *
 * Integers are big-endian. The 35 header bytes before the nonce are the GCM associated data, so any
 * change to the KDF parameters or salt fails authentication. A wrong password also fails
 * authentication ([AuthenticationFailedException]); the two cannot be told apart, by design.
 */
object EncryptedContainer {
    val MAGIC: ByteArray = "MENSISBK".toByteArray(Charsets.US_ASCII)
    const val VERSION: Byte = 1
    const val KDF_ARGON2ID: Byte = 1
    const val SALT_BYTES: Int = 16
    const val HEADER_BYTES: Int = 8 + 1 + 1 + 4 + 4 + 1 + SALT_BYTES

    /** Decompressed payloads above this size are treated as damaged (guards against gzip bombs). */
    const val MAX_PAYLOAD_BYTES: Int = 64 * 1024 * 1024

    private val random = SecureRandom()

    fun isContainer(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun seal(
        payload: ByteArray,
        password: CharArray,
        params: KdfParams = KdfParams.DEFAULT,
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = header(params, salt)
        val key = Argon2Kdf.derive(password, salt, params)
        try {
            return header + AesGcm.encrypt(key, gzip(payload), header)
        } finally {
            key.fill(0)
        }
    }

    /** @throws AuthenticationFailedException wrong password or altered ciphertext. */
    fun open(
        container: ByteArray,
        password: CharArray,
    ): ByteArray {
        val (params, salt) = readHeader(container)
        val header = container.copyOfRange(0, HEADER_BYTES)
        val key = Argon2Kdf.derive(password, salt, params)
        val compressed =
            try {
                AesGcm.decrypt(key, container.copyOfRange(HEADER_BYTES, container.size), header)
            } finally {
                key.fill(0)
            }
        return gunzip(compressed)
    }

    /** The KDF parameters and salt, after checking everything that can be checked without the password. */
    private fun readHeader(container: ByteArray): Pair<KdfParams, ByteArray> {
        intact(isContainer(container)) { "not an encrypted Mensis file" }
        intact(
            container.size >= HEADER_BYTES + AesGcm.NONCE_BYTES + AesGcm.TAG_BITS / Byte.SIZE_BITS,
        ) { "file is truncated" }
        val buffer = ByteBuffer.wrap(container, MAGIC.size, HEADER_BYTES - MAGIC.size)
        val version = buffer.get()
        val kdf = buffer.get()
        val params = KdfParams(memoryKib = buffer.int, iterations = buffer.int, parallelism = buffer.get().toInt())
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        intact(version == VERSION) { "unsupported container version $version" }
        intact(kdf == KDF_ARGON2ID) { "unsupported key derivation $kdf" }
        intact(params.isWithinLimits) { "key derivation parameters out of range" }
        return params to salt
    }

    private inline fun intact(
        condition: Boolean,
        message: () -> String,
    ) {
        if (!condition) throw DamagedContainerException(message())
    }

    private fun header(
        params: KdfParams,
        salt: ByteArray,
    ): ByteArray =
        ByteBuffer
            .allocate(HEADER_BYTES)
            .put(MAGIC)
            .put(VERSION)
            .put(KDF_ARGON2ID)
            .putInt(params.memoryKib)
            .putInt(params.iterations)
            .put(params.parallelism.toByte())
            .put(salt)
            .array()

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray =
        try {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { input -> readCapped(input) }
        } catch (e: IOException) {
            throw DamagedContainerException("payload is not valid gzip", e)
        }

    private fun readCapped(input: GZIPInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_CHUNK)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            out.write(chunk, 0, read)
            if (out.size() > MAX_PAYLOAD_BYTES) throw DamagedContainerException("payload too large")
        }
        return out.toByteArray()
    }

    private const val DEFAULT_CHUNK = 8 * 1024
}
