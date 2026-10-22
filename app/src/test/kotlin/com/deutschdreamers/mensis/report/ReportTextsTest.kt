package com.deutschdreamers.mensis.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class ReportTextsTest {
    @ParameterizedTest(name = "{0} to {1}: {2}")
    @CsvSource(
        delimiter = '|',
        value = [
            "2026-04-13 | 2026-10-21 | Apr – Oct 2026",
            "2025-10-22 | 2026-10-21 | Oct 2025 – Oct 2026",
            "2026-10-05 | 2026-10-21 | Oct 2026",
        ],
    )
    fun `month ranges`(
        start: LocalDate,
        end: LocalDate,
        expected: String,
    ) = assertEquals(expected, ReportTexts.monthRange(start, end))

    @ParameterizedTest(name = "page {0} of {1}: {4}")
    @CsvSource(
        delimiter = '|',
        nullValues = ["-"],
        value = [
            "1 | 2 | 2 | 2 | Page 1 of 2 · BBT chart on page 2",
            "1 | 3 | 2 | 3 | Page 1 of 3 · BBT charts on pages 2–3",
            "2 | 3 | 2 | 3 | Page 2 of 3",
            "1 | 1 | - | - | Page 1 of 1",
        ],
    )
    fun footers(
        page: Int,
        count: Int,
        first: Int?,
        last: Int?,
        expected: String,
    ) {
        val pages = if (first != null && last != null) first..last else null
        assertEquals(expected, ReportTexts.footer(page, count, pages))
    }

    @Test
    fun `ready line as in the design`() {
        assertEquals(
            "mensis-report-2026-10-21.pdf · 2 pages · 140 KB. It stays in the app until you save or share it.",
            ReportTexts.ready("mensis-report-2026-10-21.pdf", 2, 140 * 1024L),
        )
        assertEquals(
            "a.pdf · 1 page · 1 KB. It stays in the app until you save or share it.",
            ReportTexts.ready("a.pdf", 1, 300),
        )
    }

    @Test
    fun `notes count`() {
        assertEquals("1 note included.", ReportTexts.notesIncluded(1))
        assertEquals("3 notes included.", ReportTexts.notesIncluded(3))
    }
}
