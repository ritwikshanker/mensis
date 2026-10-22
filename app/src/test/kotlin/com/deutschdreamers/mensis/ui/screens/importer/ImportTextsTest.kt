package com.deutschdreamers.mensis.ui.screens.importer

import com.deutschdreamers.mensis.data.export.ExportFormat
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.importer.DateOrder
import com.deutschdreamers.mensis.data.importer.ExtrasOutcome
import com.deutschdreamers.mensis.data.importer.ImportIssue
import com.deutschdreamers.mensis.data.importer.ImportResult
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.data.importer.IssueKind
import com.deutschdreamers.mensis.data.importer.LastImport
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.SkipReason
import com.deutschdreamers.mensis.data.importer.SkippedRow
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.screens.export.ExportTexts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ImportTextsTest {
    private fun parsed(
        rows: Int,
        skipped: List<SkipReason>,
    ) = ParsedImport(
        source = ImportSource.CSV,
        rowsRead = rows,
        days = emptyList(),
        skipped = skipped.mapIndexed { i, r -> SkippedRow(i + 2, r) },
        issues = emptyList(),
    )

    private fun result(
        entries: Int = 240,
        cycles: Int = 8,
        skipped: Int = 3,
        kept: Int = 0,
    ) = ImportResult(
        entries,
        entries,
        0,
        kept,
        cycles,
        skipped,
        ExtrasOutcome.NOT_IN_FILE,
        ExtrasOutcome.NOT_IN_FILE,
        true,
    )

    @Test
    fun `preview counts read like the design`() {
        assertEquals(
            "243 rows read · 240 valid · 3 skipped (unreadable dates)",
            ImportTexts.previewCounts(parsed(243, List(3) { SkipReason.UNREADABLE_DATE })),
        )
        assertEquals("1 row read · 1 valid", ImportTexts.previewCounts(parsed(1, emptyList())))
        assertEquals(
            "10 rows read · 7 valid · 3 skipped (2 unreadable dates, 1 empty row)",
            ImportTexts.previewCounts(
                parsed(10, listOf(SkipReason.UNREADABLE_DATE, SkipReason.NO_DATA, SkipReason.UNREADABLE_DATE)),
            ),
        )
    }

    @Test
    fun `summary follows the design and mentions only what happened`() {
        assertEquals(
            "240 daily entries added, 8 cycles recognised. 3 rows were skipped; the list is below.",
            ImportTexts.summary(result()),
        )
        assertEquals(
            "1 daily entry added, 1 cycle recognised.",
            ImportTexts.summary(result(entries = 1, cycles = 1, skipped = 0)),
        )
        assertEquals(
            "0 daily entries added, 0 cycles recognised. 4 days that already had data were kept as they were.",
            ImportTexts.summary(result(entries = 0, cycles = 0, skipped = 0, kept = 4)),
        )
    }

    @Test
    fun `mapping subtitle, button and skipped rows`() {
        assertEquals("cycle_export.csv · 6 columns · 243 rows", ImportTexts.mapSubtitle("cycle_export.csv", 6, 243))
        assertEquals("Import 240 entries", ImportTexts.importButton(240))
        assertEquals("Import 1 entry", ImportTexts.importButton(1))
        assertEquals(
            "Row 6 · unreadable date · 2026-13-40",
            ImportTexts.skippedRow(SkippedRow(6, SkipReason.UNREADABLE_DATE, "2026-13-40")),
        )
        assertEquals("Row 4 · empty row", ImportTexts.skippedRow(SkippedRow(4, SkipReason.NO_DATA)))
    }

    @Test
    fun `issues, conflicts and extras`() {
        assertNull(ImportTexts.issues(emptyList()))
        assertEquals(
            "Not imported: cervix.opening (12). Unreadable values: mood (2).",
            ImportTexts.issues(
                listOf(
                    ImportIssue("cervix.opening", IssueKind.NOT_TRACKED, 12),
                    ImportIssue("mood", IssueKind.UNKNOWN_VALUE, 2),
                ),
            ),
        )
        assertNull(ImportTexts.conflicts(0))
        assertEquals("1 day in the file already has data on this phone.", ImportTexts.conflicts(1))
        assertNull(ImportTexts.extras(ExtrasOutcome.NOT_IN_FILE, ExtrasOutcome.NOT_IN_FILE))
        assertEquals(
            "Method history and settings come from the file.",
            ImportTexts.extras(ExtrasOutcome.TAKEN_FROM_FILE, ExtrasOutcome.TAKEN_FROM_FILE),
        )
        assertEquals(
            "Method history comes from the file. The phone keeps its own settings.",
            ImportTexts.extras(ExtrasOutcome.TAKEN_FROM_FILE, ExtrasOutcome.KEPT),
        )
    }

    @Test
    fun `last import line and date readings`() {
        val last = LastImport(ImportSource.DRIP, Instant.parse("2026-10-21T15:00:00Z"), "drip.csv", 240)
        assertEquals(
            "drip. export · 21 Oct, 15:00 · 240 entries",
            ImportTexts.lastImport("drip. export", last, ZoneOffset.UTC),
        )
        assertEquals("3 Apr 2026", ImportTexts.dateReading("03/04/2026", DateOrder.DAY_FIRST))
        assertEquals("4 Mar 2026", ImportTexts.dateReading("03/04/2026", DateOrder.MONTH_FIRST))
    }

    @Test
    fun `export card lines follow the design`() {
        val summary = ExportSummary(243, 8, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 10, 21))
        assertEquals("243 daily entries · 8 cycles · settings", ExportTexts.included(summary, ExportFormat.JSON))
        assertEquals("243 daily entries · 8 cycles", ExportTexts.included(summary, ExportFormat.CSV))
        assertEquals("6 Mar – 21 Oct 2026 · about 52 KB", ExportTexts.rangeAndSize(summary, 52 * 1024))
        assertEquals("about 1 KB", ExportTexts.rangeAndSize(ExportSummary(0, 0, null, null), 300))
        assertEquals(
            "mensis-export-2026-10-21.json · 52 KB · encrypted",
            ExportTexts.saved("mensis-export-2026-10-21.json", 52 * 1024, encrypted = true),
        )
    }

    @Test
    fun `file sizes and ranges with years`() {
        assertEquals("1 KB", ExportTexts.fileSize(10))
        assertEquals("52 KB", ExportTexts.fileSize(53_000))
        assertEquals("1.5 MB", ExportTexts.fileSize(1_572_864))
        assertEquals("21 Oct 2026", Formats.rangeWithYear(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 21)))
        assertEquals(
            "6 Mar 2025 – 21 Oct 2026",
            Formats.rangeWithYear(LocalDate.of(2025, 3, 6), LocalDate.of(2026, 10, 21)),
        )
    }
}
