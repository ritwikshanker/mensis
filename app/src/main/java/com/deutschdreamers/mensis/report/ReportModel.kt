package com.deutschdreamers.mensis.report

import com.deutschdreamers.mensis.ui.screens.insights.TemperatureChartUi

/** Design §15 step 1, "Period covered". */
enum class ReportRange { LAST_6_CYCLES, TWELVE_MONTHS, ALL }

/** Design §15 step 1 checkboxes, in the design's order. */
enum class ReportSection {
    SUMMARY,
    LENGTHS,
    SYMPTOMS,
    TEMPERATURE,
    MEDICATION,
    NOTES,
    ;

    companion object {
        /** The first four are ticked by default; free-text notes only go in when the user ticks them. */
        val DEFAULTS: Set<ReportSection> = setOf(SUMMARY, LENGTHS, SYMPTOMS, TEMPERATURE)
    }
}

data class ReportOptions(
    val range: ReportRange = ReportRange.TWELVE_MONTHS,
    val sections: Set<ReportSection> = ReportSection.DEFAULTS,
)

/**
 * Everything the PDF and its preview show, already worded. Built by [ReportBuilder]; a section that
 * was not ticked is null and left out of both.
 */
data class ReportModel(
    val title: String,
    /** "Prepared 21 Oct 2026" */
    val prepared: String,
    /** "Nov 2025 – Oct 2026 · Logged with Mensis 1.0.0 · Not a medical diagnosis" */
    val subtitle: String,
    /** "mensis-report-2026-10-21.pdf" */
    val fileName: String,
    val summary: List<ReportRow>?,
    val lengths: ReportLengths?,
    val symptoms: ReportList?,
    val medication: ReportList?,
    val notes: ReportList?,
    val temperatures: ReportTemperatures?,
)

/** A label on the left and its value on the right ("Cycles logged" · "12"). */
data class ReportRow(
    val label: String,
    val value: String,
)

/** Rows under a section title, an optional explanation above them and the text shown when there are none. */
data class ReportList(
    val rows: List<ReportRow>,
    val note: String?,
    val emptyText: String,
)

/** The cycle-length bars and one table row per complete cycle; [chart] is null before the first one. */
data class ReportLengths(
    val chart: ReportBarChart?,
    val rows: List<ReportLengthRow>,
    val emptyText: String,
)

/** Complete cycles oldest first, scaled from [axisMin] to [axisMax] days, with a dashed line at [mean]. */
data class ReportBarChart(
    val lengths: List<Int>,
    val axisMin: Int,
    val axisMax: Int,
    val mean: Double,
    val meanLabel: String,
    val startLabel: String,
    val endLabel: String,
)

data class ReportLengthRow(
    val start: String,
    val cycleLength: String,
    val periodLength: String,
)

/** The temperature page(s): one chart per cycle in the range that has readings. */
data class ReportTemperatures(
    val note: String,
    val charts: List<ReportTemperatureChart>,
    val emptyText: String,
)

data class ReportTemperatureChart(
    /** "Cycle from 6 Mar 2026 · 29 days" */
    val title: String,
    val caption: String,
    val chart: TemperatureChartUi,
    /** Axis labels in the user's unit: "36.20", "36.90". */
    val axisLow: String,
    val axisHigh: String,
)
