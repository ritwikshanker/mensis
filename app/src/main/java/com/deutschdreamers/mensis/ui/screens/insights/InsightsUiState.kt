package com.deutschdreamers.mensis.ui.screens.insights

import androidx.annotation.DrawableRes

/** Everything the Insights screen renders, already worded. Built by [InsightsUiBuilder] off the main thread. */
data class InsightsUiState(
    /** Top bar trailing text ("8 cycles"); null before the first complete cycle. */
    val cyclesLabel: String?,
    /** Null before the first complete cycle: the card then shows its empty text. */
    val cycleLength: CycleLengthCardUi?,
    val periodLength: StatCardUi?,
    /** Null when a hormonal method is active today: there is no luteal phase to describe. */
    val luteal: StatCardUi?,
    /** Only at tracking level Everything, outside pregnancy mode and without a hormonal method. */
    val temperature: TemperatureCardUi?,
    /** Null when symptoms are not tracked and none recur. */
    val symptoms: SymptomsCardUi?,
)

data class CycleLengthCardUi(
    val figure: String,
    val range: String,
    val standardDeviation: String?,
    val chart: CycleLengthChartUi,
)

/** Bars oldest first in [slots] equal columns, scaled from [axisMin] to [axisMax] days. */
data class CycleLengthChartUi(
    val bars: List<CycleBarUi>,
    val slots: Int,
    val axisMin: Int,
    val axisMax: Int,
    val mean: Double?,
    val description: String,
)

data class CycleBarUi(
    val days: Int,
    val label: String,
    val latest: Boolean,
)

/** A half card: serif figure, "days, mean" and one supporting line. */
data class StatCardUi(
    val figure: String,
    val supporting: String,
)

data class TemperatureCardUi(
    val unit: String,
    /** Null when nothing was logged this cycle; the caption then says so. */
    val chart: TemperatureChartUi?,
    val caption: String,
)

/**
 * Readings of the current cycle on a day axis from 1 to [dayCount]. Values stay in °C (the scale is
 * linear, so the shape is the same in °F); only the labels are converted.
 */
data class TemperatureChartUi(
    val points: List<TemperaturePointUi>,
    val dayCount: Int,
    val minC: Double,
    val maxC: Double,
    val coverlineC: Double?,
    val coverlineLabel: String?,
    val shiftDays: IntRange?,
    val startLabel: String,
    val endLabel: String,
    val description: String,
)

data class TemperaturePointUi(
    val day: Int,
    val celsius: Double,
    /** Taken at or after the late cut-off: drawn hollow and not joined to the line. */
    val late: Boolean,
)

data class SymptomsCardUi(
    val rows: List<SymptomRowUi>,
    val emptyText: String?,
)

data class SymptomRowUi(
    @param:DrawableRes val icon: Int,
    val title: String,
    val supporting: String,
)
