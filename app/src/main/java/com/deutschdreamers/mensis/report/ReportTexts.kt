package com.deutschdreamers.mensis.report

import com.deutschdreamers.mensis.ui.format.Formats.EN_DASH
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.screens.export.ExportTexts
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The report's computed lines shared by the PDF and the screen, as plain Kotlin so they are unit-tested. */
object ReportTexts {
    private val month = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    const val TITLE: String = "Cycle report"
    const val LENGTHS_TITLE: String = "Cycle lengths"
    const val SYMPTOMS_TITLE: String = "Symptoms, by frequency"
    const val TEMPERATURE_TITLE: String = "Waking temperature"
    const val MEDICATION_TITLE: String = "Medication"
    const val NOTES_TITLE: String = "Notes"
    const val MIME_TYPE: String = "application/pdf"

    /** "mensis-report-2026-10-21.pdf" */
    fun fileName(date: LocalDate): String = "mensis-report-${date.format(isoDate)}.pdf"

    /** "Apr – Oct 2026", "Nov 2025 – Oct 2026", "Oct 2026" when both fall in one month. */
    fun monthRange(
        start: LocalDate,
        end: LocalDate,
    ): String =
        when {
            start.year == end.year && start.month == end.month -> end.format(monthYear)
            start.year == end.year -> "${start.format(month)} $EN_DASH ${end.format(monthYear)}"
            else -> "${start.format(monthYear)} $EN_DASH ${end.format(monthYear)}"
        }

    /**
     * Each page's footer: "Page 1 of 2 · BBT chart on page 2" on the first page, "Page 2 of 2" after
     * it. [temperaturePages] is null when the report has no temperature page.
     */
    fun footer(
        page: Int,
        pageCount: Int,
        temperaturePages: IntRange?,
    ): String {
        val base = "Page $page of $pageCount"
        if (page != 1 || temperaturePages == null) return base
        val where =
            if (temperaturePages.first == temperaturePages.last) {
                "BBT chart on page ${temperaturePages.first}"
            } else {
                "BBT charts on pages ${temperaturePages.first}$EN_DASH${temperaturePages.last}"
            }
        return "$base $MIDDLE_DOT $where"
    }

    /** The preview's line for the notes section: "3 notes included." */
    fun notesIncluded(count: Int): String = if (count == 1) "1 note included." else "$count notes included."

    /** "mensis-report-2026-10-21.pdf · 2 pages · 140 KB. It stays in the app until you save or share it." */
    fun ready(
        name: String,
        pages: Int,
        bytes: Long,
    ): String {
        val pageCount = if (pages == 1) "1 page" else "$pages pages"
        return "$name $MIDDLE_DOT $pageCount $MIDDLE_DOT ${ExportTexts.fileSize(bytes)}. " +
            "It stays in the app until you save or share it."
    }
}
