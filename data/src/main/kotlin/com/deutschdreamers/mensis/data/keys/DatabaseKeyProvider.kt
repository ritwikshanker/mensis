package com.deutschdreamers.mensis.data.keys

import android.content.Context
import com.deutschdreamers.mensis.data.db.DatabaseFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The database key: 32 random bytes, generated once, stored wrapped by [KeystoreWrappingKey] in the
 * app's private files directory. Without this device's Keystore the stored blob is useless, which
 * is why Android backup and device transfer are disabled for the app.
 */
@Singleton
class DatabaseKeyProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val file = File(context.filesDir, FILE_NAME)
        private val wrappingKey = KeystoreWrappingKey(ALIAS)

        @Synchronized
        fun getOrCreate(): ByteArray {
            if (file.exists()) return unwrapFile()
            val key = ByteArray(DatabaseFactory.KEY_BYTES).also { SecureRandom().nextBytes(it) }
            writeAtomically(byteArrayOf(FORMAT_VERSION) + wrappingKey.wrap(key))
            return key
        }

        fun exists(): Boolean = file.exists()

        @Synchronized
        fun delete() {
            file.delete()
            wrappingKey.delete()
        }

        private fun unwrapFile(): ByteArray {
            val bytes = file.readBytes()
            check(bytes.isNotEmpty() && bytes[0] == FORMAT_VERSION) { "unknown key file format" }
            return wrappingKey.unwrap(bytes.copyOfRange(1, bytes.size))
        }

        private fun writeAtomically(bytes: ByteArray) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(bytes)
            check(tmp.renameTo(file)) { "could not store the database key" }
        }

        internal companion object {
            const val ALIAS = "mensis/db-wrap/v1"
            const val FILE_NAME = "keys/db.key"
            const val FORMAT_VERSION: Byte = 1
        }
    }
