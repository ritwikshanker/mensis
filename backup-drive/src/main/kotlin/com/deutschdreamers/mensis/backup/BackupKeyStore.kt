package com.deutschdreamers.mensis.backup

import android.content.Context
import com.deutschdreamers.mensis.data.crypto.Argon2Kdf
import com.deutschdreamers.mensis.data.crypto.ContainerKey
import com.deutschdreamers.mensis.data.crypto.EncryptedContainer
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.keys.KeystoreWrappingKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the Argon2-derived backup key so scheduled backups never ask for the password. */
interface BackupKeyStore {
    /** The stored key, or null when there is none or it can no longer be unwrapped. */
    fun load(): ContainerKey?

    fun store(key: ContainerKey)

    fun delete()
}

/**
 * The backup key, wrapped by its own Keystore key (`mensis/backup-wrap/v1`, not bound to user
 * authentication so the worker can run while the phone is locked), in `files/keys/backup.key`:
 * `version (1) ‖ wrap(memory KiB (4) ‖ iterations (4) ‖ parallelism (1) ‖ salt (16) ‖ key (32))`.
 * The password itself is never stored.
 */
@Singleton
class KeystoreBackupKeyStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : BackupKeyStore {
        private val file = File(context.filesDir, FILE_NAME)
        private val wrappingKey = KeystoreWrappingKey(ALIAS)

        @Synchronized
        override fun load(): ContainerKey? {
            if (!file.exists()) return null
            return try {
                val bytes = file.readBytes()
                if (bytes.isEmpty() || bytes[0] != FORMAT_VERSION) return null
                decode(wrappingKey.unwrap(bytes.copyOfRange(1, bytes.size)))
            } catch (expected: GeneralSecurityException) {
                // The Keystore key is gone (device reset, lock screen removed on some devices).
                null
            } catch (expected: IllegalArgumentException) {
                null
            }
        }

        @Synchronized
        override fun store(key: ContainerKey) {
            val plain = encode(key)
            try {
                val tmp = File(file.parentFile, "${file.name}.tmp")
                file.parentFile?.mkdirs()
                tmp.writeBytes(byteArrayOf(FORMAT_VERSION) + wrappingKey.wrap(plain))
                check(tmp.renameTo(file)) { "could not store the backup key" }
            } finally {
                plain.fill(0)
            }
        }

        @Synchronized
        override fun delete() {
            file.delete()
            wrappingKey.delete()
        }

        internal companion object {
            const val ALIAS = "mensis/backup-wrap/v1"
            const val FILE_NAME = "keys/backup.key"
            const val FORMAT_VERSION: Byte = 1
            private const val PARAMS_BYTES = 4 + 4 + 1

            fun encode(key: ContainerKey): ByteArray =
                ByteBuffer
                    .allocate(PARAMS_BYTES + EncryptedContainer.SALT_BYTES + Argon2Kdf.KEY_BYTES)
                    .putInt(key.params.memoryKib)
                    .putInt(key.params.iterations)
                    .put(key.params.parallelism.toByte())
                    .put(key.salt)
                    .put(key.bytes)
                    .array()

            fun decode(plain: ByteArray): ContainerKey? {
                if (plain.size != PARAMS_BYTES + EncryptedContainer.SALT_BYTES + Argon2Kdf.KEY_BYTES) return null
                val buffer = ByteBuffer.wrap(plain)
                val params = KdfParams(buffer.int, buffer.int, buffer.get().toInt())
                val salt = ByteArray(EncryptedContainer.SALT_BYTES).also { buffer.get(it) }
                val key = ByteArray(Argon2Kdf.KEY_BYTES).also { buffer.get(it) }
                plain.fill(0)
                return ContainerKey(key, salt, params)
            }
        }
    }
