package com.deutschdreamers.mensis.data.db

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * Opens the SQLCipher-encrypted Room database. The 32-byte random key is passed in SQLCipher's raw
 * key form (`x'<hex>'`), which skips SQLCipher's own password derivation: a random key gains nothing
 * from a KDF and the open stays fast. A new [SupportOpenHelperFactory] is built for every open with
 * its own copy of the key bytes, because the factory may clear its passphrase after first use.
 */
object DatabaseFactory {
    const val DEFAULT_NAME: String = "mensis.db"
    const val KEY_BYTES: Int = 32

    @Volatile
    private var nativeLoaded = false

    fun open(
        context: Context,
        key: ByteArray,
        name: String = DEFAULT_NAME,
    ): MensisDatabase {
        loadNative()
        return Room
            .databaseBuilder(context, MensisDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(rawKeyPassphrase(key)))
            .build()
    }

    /** `x'<64 hex chars>'` as ASCII bytes; SQLCipher treats this as a raw 256-bit key. */
    fun rawKeyPassphrase(key: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "database key must be $KEY_BYTES bytes, was ${key.size}" }
        val hex = key.joinToString("") { "%02x".format(it) }
        return "x'$hex'".toByteArray(Charsets.US_ASCII)
    }

    /** Every file the database may leave on disk (main file, WAL, shared memory, rollback journal). */
    fun databaseFiles(
        context: Context,
        name: String = DEFAULT_NAME,
    ): List<File> = listOf(name, "$name-wal", "$name-shm", "$name-journal").map { context.getDatabasePath(it) }

    private fun loadNative() {
        if (!nativeLoaded) {
            synchronized(this) {
                if (!nativeLoaded) {
                    System.loadLibrary("sqlcipher")
                    nativeLoaded = true
                }
            }
        }
    }
}
