package com.deutschdreamers.mensis.ui.screens.export

import com.deutschdreamers.mensis.data.export.ExportFormat
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import java.util.Locale

/** The export screen's computed lines, as plain Kotlin so they are unit-tested. */
object ExportTexts {
    /** "243 daily entries · 8 cycles · settings"; CSV leaves the settings out. */
    fun included(
        summary: ExportSummary,
        format: ExportFormat,
    ): String {
        val entries = if (summary.entries == 1) "1 daily entry" else "${summary.entries} daily entries"
        val settings = if (format == ExportFormat.JSON) " $MIDDLE_DOT settings" else ""
        return "$entries $MIDDLE_DOT ${Formats.cycles(summary.cycles)}$settings"
    }

    /** "6 Mar – 21 Oct 2026 · about 52 KB" */
    fun rangeAndSize(
        summary: ExportSummary,
        bytes: Long,
    ): String {
        val size = "about ${fileSize(bytes)}"
        val first = summary.firstDate
        val last = summary.lastDate
        return if (first != null && last != null) "${Formats.rangeWithYear(first, last)} $MIDDLE_DOT $size" else size
    }

    /** "mensis-export-2026-10-21.json · 52 KB · encrypted" */
    fun saved(
        name: String,
        bytes: Long,
        encrypted: Boolean,
    ): String = "$name $MIDDLE_DOT ${fileSize(bytes)}" + if (encrypted) " $MIDDLE_DOT encrypted" else ""

    /** "52 KB", "1.4 MB"; anything under a kilobyte reads "1 KB". */
    fun fileSize(bytes: Long): String =
        if (bytes < BYTES_PER_MB) {
            "${maxOf(1L, (bytes + BYTES_PER_KB / 2) / BYTES_PER_KB)} KB"
        } else {
            String.format(Locale.ENGLISH, "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
        }

    private const val BYTES_PER_KB = 1_024L
    private const val BYTES_PER_MB = BYTES_PER_KB * BYTES_PER_KB
}
