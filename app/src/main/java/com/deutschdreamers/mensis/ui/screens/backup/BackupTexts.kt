package com.deutschdreamers.mensis.ui.screens.backup

import com.deutschdreamers.mensis.data.backup.BackupError
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.importer.RestoreResult
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.screens.export.ExportTexts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The backup and restore screens' computed lines, as plain Kotlin so they are unit-tested. */
object BackupTexts {
    /** Completes "Last backup failed: …" (design: "no connection"). */
    fun reason(error: BackupError): String =
        when (error) {
            BackupError.NETWORK -> "no connection"
            BackupError.SERVER -> "Google Drive did not respond"
            BackupError.DRIVE_FULL -> "Google Drive is full"
            BackupError.VERIFY_FAILED -> "the upload did not arrive intact"
            BackupError.NO_PASSWORD -> "the backup password needs to be set again"
            BackupError.OTHER -> "something went wrong"
        }

    /** "Last backup failed: no connection." */
    fun failed(error: BackupError): String = "Last backup failed: ${reason(error)}."

    /** "today, 09:12", "yesterday, 23:14", "20 Oct, 23:14", "20 Oct 2025, 23:14" */
    fun moment(
        at: Instant,
        today: LocalDate,
        zone: ZoneId,
    ): String {
        val local = at.atZone(zone)
        val date = local.toLocalDate()
        val day =
            when {
                date == today -> "today"
                date == today.minusDays(1) -> "yesterday"
                date.year == today.year -> Formats.dayMonth(date)
                else -> Formats.dayMonthYear(date)
            }
        return "$day, ${Formats.time(local.toLocalTime())}"
    }

    /** Design §11 idle: "Backed up yesterday, 23:14". */
    fun backedUp(
        at: Instant,
        today: LocalDate,
        zone: ZoneId,
    ): String = "Backed up ${moment(at, today, zone)}"

    /** "48 KB · encrypted · Pixel 9" */
    fun details(
        bytes: Long?,
        device: String?,
    ): String =
        listOfNotNull(bytes?.let(ExportTexts::fileSize), "encrypted", device?.takeIf { it.isNotBlank() })
            .joinToString(" $MIDDLE_DOT ")

    /** Design §12 step 2: "mensis-backup.enc · 20 Oct 2026, 23:14 · 48 KB · from Pixel 9" */
    fun backupFile(
        name: String,
        at: Instant,
        bytes: Long,
        device: String?,
        zone: ZoneId,
    ): String {
        val local = at.atZone(zone)
        val moment = "${Formats.dayMonthYear(local.toLocalDate())}, ${Formats.time(local.toLocalTime())}"
        return listOfNotNull(
            name,
            moment,
            ExportTexts.fileSize(bytes),
            device?.takeIf { it.isNotBlank() }?.let { "from $it" },
        ).joinToString(" $MIDDLE_DOT ")
    }

    /** "This phone has 12 daily entries from 6 Mar to 20 Oct 2026. Restoring replaces them …" */
    fun replaces(local: ExportSummary): String =
        "This phone has ${entries(local.entries)}${span(local.firstDate, local.lastDate, " from ")}. " +
            "Restoring replaces them with the backup. Export them first if you want to keep a copy."

    /** Design §12 step 4: "Decrypting 48 KB and rebuilding 8 cycles. Keep the app open." */
    fun restoring(
        bytes: Long,
        cycles: Int,
    ): String = "Decrypting ${ExportTexts.fileSize(bytes)} and rebuilding ${Formats.cycles(cycles)}. Keep the app open."

    /** Design §12 step 5: "8 cycles and 243 daily entries, 6 Mar to 20 Oct 2026. Settings and reminders …" */
    fun restored(result: RestoreResult): String {
        val settings = if (result.settingsRestored) " Settings and reminders were restored too." else ""
        return "${Formats.cycles(result.cycles)} and ${entries(result.entries)}" +
            "${span(result.firstDate, result.lastDate, ", ")}.$settings"
    }

    private fun entries(count: Int): String = if (count == 1) "1 daily entry" else "$count daily entries"

    /** ", 6 Mar to 20 Oct 2026"; nothing without dates. */
    private fun span(
        first: LocalDate?,
        last: LocalDate?,
        prefix: String,
    ): String {
        if (first == null || last == null) return ""
        val start = if (first.year == last.year) Formats.dayMonth(first) else Formats.dayMonthYear(first)
        return if (first ==
            last
        ) {
            "$prefix${Formats.dayMonthYear(last)}"
        } else {
            "$prefix$start to ${Formats.dayMonthYear(last)}"
        }
    }
}
