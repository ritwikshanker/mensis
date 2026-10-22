package com.deutschdreamers.mensis.data.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Argon2id cost parameters. They are written into every encrypted file's header, so they can be
 * raised later without breaking older files.
 */
data class KdfParams(
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
) {
    /** True when a file's header asks for no more than a phone can reasonably spend on one derivation. */
    val isWithinLimits: Boolean
        get() =
            memoryKib in MIN_MEMORY_KIB..MAX_MEMORY_KIB &&
                iterations in 1..MAX_ITERATIONS &&
                parallelism in 1..MAX_PARALLELISM

    companion object {
        /** 64 MiB, 3 passes, 1 lane: above the OWASP floor of 19 MiB / 2 / 1, under a second or two on a Pixel. */
        val DEFAULT: KdfParams = KdfParams(memoryKib = 65_536, iterations = 3, parallelism = 1)

        /** Argon2 requires at least 8 KiB per lane. */
        const val MIN_MEMORY_KIB: Int = 8

        /** Files asking for more memory than this are rejected instead of risking an out-of-memory crash. */
        const val MAX_MEMORY_KIB: Int = 262_144
        const val MAX_ITERATIONS: Int = 10
        const val MAX_PARALLELISM: Int = 4
    }
}

/** Password → 256-bit key with Argon2id (BouncyCastle, pure Java, so it also runs in JVM unit tests). */
object Argon2Kdf {
    const val KEY_BYTES: Int = 32

    fun derive(
        password: CharArray,
        salt: ByteArray,
        params: KdfParams,
    ): ByteArray {
        val parameters =
            Argon2Parameters
                .Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKib)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        return ByteArray(KEY_BYTES).also { generator.generateBytes(password, it) }
    }
}
