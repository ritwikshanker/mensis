package com.deutschdreamers.mensis.data.db

import android.content.Context
import com.deutschdreamers.mensis.data.keys.DatabaseKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single open database. Repositories go through [get] rather than holding DAOs, so the
 * database can be closed and reopened (delete-all-data creates a fresh key and file).
 */
@Singleton
class DatabaseHolder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val keyProvider: DatabaseKeyProvider,
    ) {
        @Volatile
        private var database: MensisDatabase? = null

        fun get(): MensisDatabase =
            database ?: synchronized(this) {
                database ?: DatabaseFactory.open(context, keyProvider.getOrCreate()).also { database = it }
            }

        fun close() {
            synchronized(this) {
                database?.close()
                database = null
            }
        }

        /** Closes the database and runs [block] before anything can open it again (delete-all-data). */
        fun closeAndThen(block: () -> Unit) {
            synchronized(this) {
                database?.close()
                database = null
                block()
            }
        }
    }
