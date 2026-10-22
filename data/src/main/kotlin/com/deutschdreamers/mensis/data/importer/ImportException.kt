package com.deutschdreamers.mensis.data.importer

/** Why a file could not be read at all. Row-level problems are reported as skipped rows instead. */
enum class ImportError {
    /** Empty file, or nothing that looks like the chosen source. */
    NOT_THIS_FORMAT,

    /** A Mensis export written by a newer app version than this one. */
    NEWER_VERSION,

    /** Truncated or otherwise unreadable. */
    DAMAGED,

    /** An encrypted Mensis file whose password did not match (or whose ciphertext was altered). */
    WRONG_PASSWORD,

    /** Larger than [ImportLimits.MAX_FILE_BYTES]. */
    TOO_LARGE,

    /** A CSV without any column that holds dates. */
    NO_DATE_COLUMN,
}

class ImportException(
    val error: ImportError,
    message: String = error.name,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Throws [ImportException]; a function so parsers can refuse a file from several places. */
fun refuse(
    error: ImportError,
    message: String = error.name,
    cause: Throwable? = null,
): Nothing = throw ImportException(error, message, cause)

object ImportLimits {
    /** Ten years of daily entries in JSON are well under 10 MB; anything above this is not a cycle export. */
    const val MAX_FILE_BYTES: Int = 32 * 1024 * 1024
}
