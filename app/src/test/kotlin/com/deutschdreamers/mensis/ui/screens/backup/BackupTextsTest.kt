package com.deutschdreamers.mensis.ui.screens.backup

import com.deutschdreamers.mensis.data.backup.BackupError
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.importer.RestoreResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BackupTextsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun `the idle line reads like the design`() {
        assertEquals(
            "Backed up yesterday, 23:14",
            BackupTexts.backedUp(Instant.parse("2026-10-20T23:14:00Z"), today, zone),
        )
        assertEquals("48 KB · encrypted · Pixel 9", BackupTexts.details(48 * 1_024L, "Pixel 9"))
        assertEquals("encrypted", BackupTexts.details(null, null))
    }

    @Test
    fun `moments name today, yesterday, a date and a date in another year`() {
        assertEquals("today, 09:05", BackupTexts.moment(Instant.parse("2026-10-21T09:05:00Z"), today, zone))
        assertEquals("3 Oct, 18:00", BackupTexts.moment(Instant.parse("2026-10-03T18:00:00Z"), today, zone))
        assertEquals("30 Dec 2025, 07:30", BackupTexts.moment(Instant.parse("2025-12-30T07:30:00Z"), today, zone))
    }

    @Test
    fun `the restore file card reads like the design`() {
        assertEquals(
            "mensis-backup.enc · 20 Oct 2026, 23:14 · 48 KB · from Pixel 9",
            BackupTexts.backupFile(
                "mensis-backup.enc",
                Instant.parse("2026-10-20T23:14:00Z"),
                48 * 1_024L,
                "Pixel 9",
                zone,
            ),
        )
    }

    @Test
    fun `restoring and restored read like the design`() {
        assertEquals(
            "Decrypting 48 KB and rebuilding 8 cycles. Keep the app open.",
            BackupTexts.restoring(48 * 1_024L, 8),
        )
        assertEquals(
            "8 cycles and 243 daily entries, 6 Mar to 20 Oct 2026. Settings and reminders were restored too.",
            BackupTexts.restored(RestoreResult(243, 8, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 10, 20), true)),
        )
        assertEquals(
            "1 cycle and 1 daily entry, 20 Oct 2026.",
            BackupTexts.restored(RestoreResult(1, 1, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 20), false)),
        )
    }

    @Test
    fun `restoring over existing data says what it replaces`() {
        assertEquals(
            "This phone has 12 daily entries from 6 Mar 2025 to 20 Oct 2026. " +
                "Restoring replaces them with the backup. Export them first if you want to keep a copy.",
            BackupTexts.replaces(ExportSummary(12, 2, LocalDate.of(2025, 3, 6), LocalDate.of(2026, 10, 20))),
        )
    }

    @Test
    fun `failure reasons complete the design's sentence`() {
        assertEquals("Last backup failed: no connection.", BackupTexts.failed(BackupError.NETWORK))
        BackupError.entries.forEach { assertEquals(true, BackupTexts.reason(it).isNotBlank()) }
    }
}
