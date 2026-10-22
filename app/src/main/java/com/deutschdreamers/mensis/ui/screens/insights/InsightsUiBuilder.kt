package com.deutschdreamers.mensis.ui.screens.insights

import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.domain.cycle.Cycle
import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import com.deutschdreamers.mensis.domain.insights.CycleLengthStats
import com.deutschdreamers.mensis.domain.insights.CyclePhaseBucket
import com.deutschdreamers.mensis.domain.insights.CycleTemperatures
import com.deutschdreamers.mensis.domain.insights.InsightsCalculator
import com.deutschdreamers.mensis.domain.insights.SymptomPattern
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.domain.prediction.ConfirmedOvulation
import com.deutschdreamers.mensis.domain.prediction.OvulationSource
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.domain.prediction.PredictionEngine
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.EN_DASH
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.format.LoggedChips
import com.deutschdreamers.mensis.ui.format.Units
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Words the insights statistics (design §6). Everything is descriptive: means and ranges of the
 * user's own last complete cycles, never a forecast.
 */
object InsightsUiBuilder {
    /** Month of a cycle's start under its bar: "Mar". */
    private val barMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    fun build(
        data: TrackerData,
        today: LocalDate,
    ): InsightsUiState {
        val entries = data.entries.sortedBy { it.date }
        val cycles = CycleDeriver.derive(entries)
        val window = InsightsCalculator.windowCycles(cycles)
        val method = data.contraception.activeOn(today)?.kind
        val hormonal = method == MethodKind.CYCLIC || method == MethodKind.SUPPRESS
        val lengths = InsightsCalculator.cycleLengths(cycles)
        return InsightsUiState(
            cyclesLabel = window.takeIf { it.isNotEmpty() }?.let { Formats.cycles(it.size) },
            cycleLength = lengths?.let { cycleLength(it) },
            periodLength = periodLength(cycles, entries),
            luteal = if (hormonal || window.isEmpty()) null else luteal(cycles, window, entries),
            temperature =
                cycles.lastOrNull()?.takeIf { showsTemperature(data.settings, hormonal) }?.let { current ->
                    temperature(InsightsCalculator.temperatures(current, entries), lengths, data.settings)
                },
            symptoms = symptoms(cycles, window, entries, data.settings),
        )
    }

    private fun showsTemperature(
        settings: TrackerSettings,
        hormonal: Boolean,
    ): Boolean = settings.trackingLevel.showsTemperature && settings.mode == TrackerMode.CYCLE && !hormonal

    /** The bar chart's day axis for cycles from [min] to [max] days; the doctor report uses the same scale. */
    fun cycleAxis(
        min: Int,
        max: Int,
    ): IntRange {
        val axisMin = min - AXIS_DAYS_BELOW_MIN
        return axisMin..maxOf(max + AXIS_DAYS_ABOVE_MAX, axisMin + AXIS_MIN_SPAN_DAYS)
    }

    private fun cycleLength(stats: CycleLengthStats): CycleLengthCardUi {
        val axis = cycleAxis(stats.min, stats.max)
        val bars =
            stats.startDates.zip(stats.lengths).mapIndexed { index, (start, days) ->
                CycleBarUi(days, start.format(barMonth), latest = index == stats.count - 1)
            }
        return CycleLengthCardUi(
            figure = stats.mean.roundToInt().toString(),
            range = "Range ${range(stats.min, stats.max)}",
            standardDeviation = if (stats.count < 2) null else "SD ${Formats.oneDecimal(stats.standardDeviation)} d",
            chart =
                CycleLengthChartUi(
                    bars = bars,
                    slots = PredictionConstants.INSIGHTS_WINDOW_CYCLES,
                    axisMin = axis.first,
                    axisMax = axis.last,
                    mean = stats.mean,
                    description = cycleChartDescription(stats),
                ),
        )
    }

    /** One spoken summary for the whole bar chart; the bars themselves are not focusable. */
    fun cycleChartDescription(stats: CycleLengthStats?): String {
        if (stats == null || stats.count == 0) return "Cycle length chart, no complete cycles yet."
        val bars =
            stats.startDates.zip(stats.lengths).joinToString("; ") { (start, days) ->
                "${Formats.dayMonth(start)}, ${Formats.days(days)}"
            }
        val mean = Formats.oneDecimal(stats.mean)
        return "Cycle lengths, oldest first: $bars. Mean $mean days, shown as a dashed line."
    }

    private fun periodLength(
        cycles: List<Cycle>,
        entries: List<DayEntry>,
    ): StatCardUi? {
        val stats = InsightsCalculator.periodLengths(cycles, entries) ?: return null
        val peak = stats.peakFlowDay?.let { " $MIDDLE_DOT heaviest on day $it" }.orEmpty()
        return StatCardUi(stats.mean.roundToInt().toString(), "Range ${range(stats.min, stats.max)}$peak")
    }

    private fun luteal(
        cycles: List<Cycle>,
        window: List<Cycle>,
        entries: List<DayEntry>,
    ): StatCardUi {
        val starts = window.map { it.startDate }.toSet()
        val observed = PredictionEngine.confirmedOvulations(cycles, entries).filter { it.cycleStart in starts }
        val byTemperature = observed.filter { it.source == OvulationSource.TEMPERATURE_CONFIRMED }
        val byLh = observed.filter { it.source == OvulationSource.LH_TEST }
        return when {
            byTemperature.isNotEmpty() -> observedLuteal(byTemperature, "with a confirmed shift")
            byLh.isNotEmpty() -> observedLuteal(byLh, "with a positive LH test")
            else -> {
                val anyTemperature =
                    entries.any { entry ->
                        entry.temperatureC != null &&
                            window.any { entry.date in it }
                    }
                val reason = if (anyTemperature) "no confirmed shift yet" else "no temperature data"
                StatCardUi(
                    PredictionConstants.DEFAULT_LUTEAL_DAYS.toString(),
                    "Estimated from cycle lengths $MIDDLE_DOT $reason",
                )
            }
        }
    }

    private fun observedLuteal(
        ovulations: List<ConfirmedOvulation>,
        basis: String,
    ): StatCardUi {
        val stats = requireNotNull(InsightsCalculator.luteal(ovulations))
        return StatCardUi(
            stats.mean.roundToInt().toString(),
            "Range ${range(stats.min, stats.max)} $MIDDLE_DOT ${Formats.cycles(stats.confirmedCount)} $basis",
        )
    }

    private fun temperature(
        temps: CycleTemperatures,
        lengths: CycleLengthStats?,
        settings: TrackerSettings,
    ): TemperatureCardUi {
        val unit = settings.temperatureUnit
        val symbol = Units.temperatureSymbol(unit)
        if (temps.readings.isEmpty()) {
            return TemperatureCardUi(symbol, chart = null, caption = "No waking temperature logged this cycle yet.")
        }
        val dayCount = maxOf(PredictionConstants.STRIP_MIN_DAYS, lengths?.mean?.roundToInt() ?: 0)
        return TemperatureCardUi(symbol, temperatureChart(temps, dayCount, unit), temperatureCaption(temps, unit))
    }

    /**
     * The readings of [temps] (at least one) on a day axis from 1 to [minDays], or to the last reading
     * when that is later. Also drawn on the doctor report's temperature page.
     */
    fun temperatureChart(
        temps: CycleTemperatures,
        minDays: Int,
        unit: TemperatureUnit,
    ): TemperatureChartUi {
        val cycle = temps.cycle
        val points = temps.readings.map { TemperaturePointUi(cycle.dayOf(it.date), it.temperatureC, it.isLate) }
        val dayCount = maxOf(minDays, points.maxOf { it.day })
        val shift = temps.shift
        val values = points.map { it.celsius } + listOfNotNull(shift?.coverlineC)
        val minC = floor((values.min() - SCALE_MARGIN_C) * TENTHS + EPSILON) / TENTHS
        val maxC = maxOf(minC + SCALE_MIN_SPAN_C, ceil((values.max() + SCALE_MARGIN_C) * TENTHS - EPSILON) / TENTHS)
        val shiftDays = shift?.let { cycle.dayOf(it.firstElevatedDate) }?.let { it until it + temps.heldDays }
        return TemperatureChartUi(
            points = points,
            dayCount = dayCount,
            minC = minC,
            maxC = maxC,
            coverlineC = shift?.coverlineC,
            coverlineLabel = shift?.let { "coverline ${display(it.coverlineC, unit)}" },
            shiftDays = shiftDays,
            startLabel = "day 1",
            endLabel = "day $dayCount",
            description = temperatureDescription(points, dayCount, shift?.coverlineC, shiftDays, unit),
        )
    }

    /** "Rise of 0.3 °C held for 7 days from day 15. Ovulation estimated day 14 (20 Oct)." and the late note. */
    fun temperatureCaption(
        temps: CycleTemperatures,
        unit: TemperatureUnit,
        noShift: String = "No temperature shift found this cycle yet.",
    ): String {
        val cycle = temps.cycle
        val shift = temps.shift
        val lateNote =
            if (temps.readings.none { it.isLate }) {
                ""
            } else {
                " Readings in grey were taken at ${Formats.time(
                    PredictionConstants.BBT_LATE_CUTOFF,
                )} or later and are not used."
            }
        val caption =
            if (shift != null) {
                val rise = Formats.oneDecimal(Units.toDisplayTemperatureDelta(shift.riseC, unit))
                "Rise of $rise ${Units.temperatureSymbol(unit)} held for ${Formats.days(temps.heldDays)} from day " +
                    "${cycle.dayOf(shift.firstElevatedDate)}. " +
                    "Ovulation estimated day ${cycle.dayOf(
                        shift.ovulationDate,
                    )} (${Formats.dayMonth(shift.ovulationDate)})."
            } else {
                noShift
            }
        return caption + lateNote
    }

    private fun temperatureDescription(
        points: List<TemperaturePointUi>,
        dayCount: Int,
        coverlineC: Double?,
        shiftDays: IntRange?,
        unit: TemperatureUnit,
    ): String {
        val symbol = Units.temperatureSymbol(unit)
        val count = if (points.size == 1) "1 reading" else "${points.size} readings"
        val low = display(points.minOf { it.celsius }, unit)
        val high = display(points.maxOf { it.celsius }, unit)
        val spread = if (low == high) "of $low $symbol" else "between $low and $high $symbol"
        val shift =
            if (coverlineC != null && shiftDays != null) {
                " Coverline ${display(
                    coverlineC,
                    unit,
                )} $symbol. Shift from day ${shiftDays.first} to day ${shiftDays.last}."
            } else {
                " No shift found."
            }
        val late = points.count { it.late }
        val lateNote =
            when (late) {
                0 -> ""
                1 -> " 1 reading taken late is not used."
                else -> " $late readings taken late are not used."
            }
        return "Waking temperature, day 1 to day $dayCount: $count $spread.$shift$lateNote"
    }

    private fun symptoms(
        cycles: List<Cycle>,
        window: List<Cycle>,
        entries: List<DayEntry>,
        settings: TrackerSettings,
    ): SymptomsCardUi? {
        val patterns = InsightsCalculator.symptomPatterns(cycles, entries)
        if (patterns.isEmpty() && !settings.trackingLevel.showsSymptoms) return null
        val emptyText =
            when {
                patterns.isNotEmpty() -> null
                window.size < PredictionConstants.SYMPTOM_PATTERN_MIN_CYCLES ->
                    "Patterns appear once ${Formats.cycles(
                        PredictionConstants.SYMPTOM_PATTERN_MIN_CYCLES,
                    )} are complete."
                else -> "No symptom came back in the same phase in at least half of your cycles."
            }
        val rows =
            patterns.map { pattern ->
                SymptomRowUi(
                    icon = LoggedChips.symptomIcon(pattern.symptom),
                    title = symptomTitle(pattern),
                    supporting = "${pattern.cyclesWith} of ${Formats.cycles(pattern.cyclesConsidered)}",
                )
            }
        return SymptomsCardUi(rows, emptyText)
    }

    /** "Cramps on period days 1–2", "Headache in the 3 days before a period", … */
    fun symptomTitle(pattern: SymptomPattern): String {
        val name = LoggedChips.symptomLabel(pattern.symptom)
        return when (pattern.phase) {
            CyclePhaseBucket.PERIOD_START -> "$name on period days 1${EN_DASH}2"
            CyclePhaseBucket.PRE_PERIOD ->
                "$name in the ${PredictionConstants.SYMPTOM_PRE_PERIOD_DAYS} days before a period"
            CyclePhaseBucket.LATE_LUTEAL -> "$name in the late luteal phase"
            CyclePhaseBucket.MID_CYCLE -> "$name around mid-cycle"
        }
    }

    private fun range(
        min: Int,
        max: Int,
    ): String = if (min == max) "$min" else "$min$EN_DASH$max"

    private fun display(
        celsius: Double,
        unit: TemperatureUnit,
    ): String = Formats.temperature(Units.toDisplayTemperature(celsius, unit))

    // Chart scales: presentation only, not prediction thresholds. The bar axis runs from 3 days below
    // the shortest cycle to 2 above the longest, at least 10 days, as in the design (27–32 on 24–34).
    private const val AXIS_DAYS_BELOW_MIN = 3
    private const val AXIS_DAYS_ABOVE_MAX = 2
    private const val AXIS_MIN_SPAN_DAYS = 10

    // The temperature axis leaves 0.15 °C around the readings, rounded out to tenths, and spans at
    // least 0.7 °C so day-to-day noise does not look like a shift (design: 36.2–36.9).
    private const val SCALE_MARGIN_C = 0.15
    private const val SCALE_MIN_SPAN_C = 0.7
    private const val TENTHS = 10.0

    /** Keeps 36.2 from rounding down to 36.1 through binary floating-point noise. */
    private const val EPSILON = 1e-6
}
