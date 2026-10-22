package com.deutschdreamers.mensis.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.deutschdreamers.mensis.data.importer.ImportError
import com.deutschdreamers.mensis.data.importer.ImportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reading and writing the files the user picks through the system file picker (Storage Access
 * Framework), and the short-lived copy that "Share file" hands to another app. Nothing here touches
 * the network; the user chooses every location.
 */
@Singleton
class DocumentFiles
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val sharedDir: File get() = File(context.cacheDir, SHARED_DIR)

        suspend fun write(
            uri: Uri,
            bytes: ByteArray,
        ) = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "w") ?: throw IOException("no output stream")
            stream.use { it.write(bytes) }
        }

        /** @throws ImportException with [ImportError.TOO_LARGE] above [maxBytes]. */
        suspend fun read(
            uri: Uri,
            maxBytes: Int,
        ): ByteArray =
            withContext(Dispatchers.IO) {
                val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("no input stream")
                stream.use { input ->
                    val out = ByteArrayOutputStream()
                    val chunk = ByteArray(CHUNK_BYTES)
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        out.write(chunk, 0, read)
                        if (out.size() > maxBytes) throw ImportException(ImportError.TOO_LARGE)
                    }
                    out.toByteArray()
                }
            }

        /** The name the provider shows for [uri] ("mensis-export-2026-10-21.json"), when it reports one. */
        suspend fun displayName(uri: Uri): String? =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }.getOrNull()
            }

        /**
         * Copies [bytes] into the app's private cache and returns a content URI another app may read
         * once the share intent grants it. Earlier copies are removed first; [clearShared] removes the
         * rest when the app starts.
         */
        suspend fun shareableCopy(
            name: String,
            bytes: ByteArray,
        ): Uri =
            withContext(Dispatchers.IO) {
                clearShared()
                val file = File(sharedDir.apply { mkdirs() }, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                file.writeBytes(bytes)
                FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
            }

        fun clearShared() {
            sharedDir.listFiles()?.forEach { it.delete() }
        }

        private companion object {
            const val SHARED_DIR = "shared"
            const val AUTHORITY_SUFFIX = ".files"
            const val CHUNK_BYTES = 8 * 1024
        }
    }
