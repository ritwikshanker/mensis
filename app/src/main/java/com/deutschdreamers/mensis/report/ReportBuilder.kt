package com.deutschdreamers.mensis.report

import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.domain.cycle.Cycle
import com.deutschdreamers.mensis.domain.cycle.CycleDeriver
import com.deutschdreamers.mensis.domain.insights.CyclePhaseBucket
import com.deutschdreamers.mensis.domain.insights.InsightsCalculator
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.PillState
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.domain.prediction.OvulationSource
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.domain.prediction.PredictionEngine
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.EN_DASH
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.format.LoggedChips
import com.deutschdreamers.mensis.ui.format.MethodLabels
import com.deutschdreamers.mensis.ui.format.Units
import com.deutschdreamers.mensis.ui.screens.insights.InsightsUiBuilder
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Words the doctor report (design §15). Everything is descriptive: what was logged in the chosen
 * period and the user's own means and ranges, never a forecast or a diagnosis. Statistics use the
 * complete cycles that start inside the period; the open cycle only appears on the temperature page.
 */
object ReportBuilder {
    fun build(
        data: TrackerData,
        options: ReportOptions,
        today: LocalDate,
        appVersion: String,
    ): ReportModel {
        val scope = Scope.of(data, options.range, today)
        val sections = options.sections

        fun <T> section(
            section: ReportSection,
            build: () -> T,
        ): T? = if (section in sections) build() else null
        return ReportModel(
            title = ReportTexts.TITLE,
            prepared = "Prepared ${Formats.dayMonthYear(today)}",
            subtitle =
                "${ReportTexts.monthRange(scope.from, today)} $MIDDLE_DOT Logged with Mensis $appVersion " +
                    "$MIDDLE_DOT Not a medical diagnosis",
            fileName = ReportTexts.fileName(today),
            summary = section(ReportSection.SUMMARY) { summary(scope) },
            lengths = section(ReportSection.LENGTHS) { lengths(scope) },
            symptoms = section(ReportSection.SYMPTOMS) { symptoms(scope) },
            medication = section(ReportSection.MEDICATION) { medication(scope) },
            notes = section(ReportSection.NOTES) { notes(scope) },
            temperatures = section(ReportSection.TEMPERATURE) { temperatures(scope, data.settings) },
        )
    }

    /** What the chosen range covers: its first day, the complete cycles that start in it and its entries. */
    private class Scope(
        val from: LocalDate,
        val today: LocalDate,
        val entries: List<DayEntry>,
        val cycles: List<Cycle>,
        val contraception: List<ContraceptionPeriod>,
    ) {
        val complete: List<Cycle> = cycles.filter { it.isComplete && !it.startDate.isBefore(from) }

        val inRange: List<DayEntry> = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }

        /** Complete cycles without a hormonal method: the only ones where an ovulation can be looked for. */
        val natural: List<Cycle> = complete.filter { hormonalMethod(it) == null }

        /** A cyclic or suppressing method in effect on any day of [cycle]. */
        fun hormonalMethod(cycle: Cycle): ContraceptionPeriod? =
            contraception.firstOrNull {
                (it.kind == MethodKind.CYCLIC || it.kind == MethodKind.SUPPRESS) &&
                    overlaps(it, cycle.startDate, cycle.endDate ?: today)
            }

        companion object {
            fun of(
                data: TrackerData,
                range: ReportRange,
                today: LocalDate,
            ): Scope {
                val entries = data.entries.sortedBy { it.date }
                val cycles = CycleDeriver.derive(entries)
                val first = entries.firstOrNull()?.date ?: today
                val from =
                    when (range) {
                        ReportRange.LAST_6_CYCLES ->
                            cycles
                                .filter { it.isComplete }
                                .takeLast(RECENT_CYCLES)
                                .firstOrNull()
                                ?.startDate
                                ?: cycles.lastOrNull()?.startDate
                                ?: first
                        // Never before the first entry, so the header does not claim months nothing was logged in.
                        ReportRange.TWELVE_MONTHS -> maxOf(today.minusMonths(MONTHS_IN_YEAR).plusDays(1), first)
                        ReportRange.ALL -> first
                    }
                return Scope(minOf(from, today), today, entries, cycles, data.contraception)
            }
        }
    }

    private fun summary(scope: Scope): List<ReportRow> =
        buildList {
            add(ReportRow("Cycles logged", scope.complete.size.toString()))
            InsightsCalculator.cycleLengths(scope.complete, ALL_CYCLES)?.let {
                add(ReportRow("Cycle length, mean (range)", meanAndRange(it.mean, it.min, it.max)))
            }
            InsightsCalculator.periodLengths(scope.complete, scope.entries, ALL_CYCLES)?.let {
                add(ReportRow("Period length, mean (range)", meanAndRange(it.mean, it.min, it.max)))
            }
            addAll(ovulationRows(scope))
            add(ReportRow("Contraception", currentMethod(scope.contraception, scope.today)))
        }

    /** Luteal mean from temperature shifts (else positive LH tests), and how many cycles showed a shift. */
    private fun ovulationRows(scope: Scope): List<ReportRow> {
        val starts = scope.natural.map { it.startDate }.toSet()
        val observed =
            PredictionEngine.confirmedOvulations(scope.cycles, scope.entries).filter {
                it.cycleStart in
                    starts
            }
        val byTemperature = observed.filter { it.source == OvulationSource.TEMPERATURE_CONFIRMED }
        val byLh = observed.filter { it.source == OvulationSource.LH_TEST }
        val luteal =
            InsightsCalculator.luteal(byTemperature)?.let { "${Formats.oneDecimal(it.mean)} d" }
                ?: InsightsCalculator.luteal(byLh)?.let { "${Formats.oneDecimal(it.mean)} d (LH tests)" }
        // "0 of 8 cycles" would read as no ovulation when nobody measured; the row needs readings.
        val measured =
            scope.natural.any { cycle -> scope.entries.any { it.temperatureC != null && it.date in cycle } }
        return listOfNotNull(
            luteal?.let { ReportRow("Luteal phase, mean", it) },
            ReportRow(
                "Ovulation confirmed by BBT",
                "${byTemperature.size} of ${Formats.cycles(scope.natural.size)}",
            ).takeIf { measured },
        )
    }

    /** Design: "Combined pill, cyclic, since 3 Jan 2026" or "None recorded". */
    private fun currentMethod(
        contraception: List<ContraceptionPeriod>,
        today: LocalDate,
    ): String {
        val active = contraception.activeOn(today)
        if (active == null || active.method == ContraceptionMethod.NONE) return "None recorded"
        return "${methodLabel(active)}, since ${Formats.dayMonthYear(active.startDate)}"
    }

    private fun lengths(scope: Scope): ReportLengths {
        val complete = scope.complete
        val chart =
            InsightsCalculator.cycleLengths(complete, ALL_CYCLES)?.let { stats ->
                val axis = InsightsUiBuilder.cycleAxis(stats.min, stats.max)
                ReportBarChart(
                    lengths = stats.lengths,
                    axisMin = axis.first,
                    axisMax = axis.last,
                    mean = stats.mean,
                    meanLabel = "mean ${Formats.oneDecimal(stats.mean)} d",
                    startLabel = Formats.monthYear(complete.first().startDate),
                    endLabel = Formats.monthYear(requireNotNull(complete.last().endDate)),
                )
            }
        val rows =
            complete.map { cycle ->
                ReportLengthRow(
                    start = Formats.dayMonthYear(cycle.startDate),
                    cycleLength = Formats.days(requireNotNull(cycle.lengthDays)),
                    periodLength = Formats.days(cycle.periodLengthDays),
                )
            }
        return ReportLengths(chart, rows, emptyText = "No complete cycle in this period yet.")
    }

    /** Design: "Cramps (days 1–2) 12/12 · Headache (pre-menstrual) 9/12 · …". */
    private fun symptoms(scope: Scope): ReportList {
        val total = scope.complete.size
        val rows =
            InsightsCalculator.symptomFrequencies(scope.complete, scope.entries).map { frequency ->
                val phase = frequency.typicalPhase?.let { " (${phaseLabel(it)})" }.orEmpty()
                ReportRow(
                    "${LoggedChips.symptomLabel(frequency.symptom)}$phase",
                    "${frequency.cyclesWith}/${frequency.cyclesConsidered}",
                )
            }
        return ReportList(
            rows = rows,
            note =
                "Complete cycles with the symptom logged at least once, out of $total. In brackets, the " +
                    "phase it fell in for at least half of them.",
            emptyText =
                if (total == 0) {
                    "Symptom frequencies appear after the first complete cycle."
                } else {
                    "No symptoms logged in these cycles."
                },
        )
    }

    fun phaseLabel(phase: CyclePhaseBucket): String =
        when (phase) {
            CyclePhaseBucket.PERIOD_START -> "days 1${EN_DASH}2"
            CyclePhaseBucket.PRE_PERIOD -> "pre-menstrual"
            CyclePhaseBucket.LATE_LUTEAL -> "late luteal"
            CyclePhaseBucket.MID_CYCLE -> "mid-cycle"
        }

    /** Method history in the period, then what the daily log recorded about pills and injections. */
    private fun medication(scope: Scope): ReportList {
        val methods =
            scope.contraception
                .filter { overlaps(it, scope.from, scope.today) }
                .sortedBy { it.startDate }
                .map { ReportRow(methodLabel(it), methodDates(it)) }
        val pills =
            scope.inRange
                .mapNotNull { it.pillState }
                .groupingBy { it }
                .eachCount()
        val pillRow =
            ReportRow(
                "Pill days logged",
                "${pills[PillState.TAKEN] ?: 0} taken $MIDDLE_DOT ${pills[PillState.LATE] ?: 0} late $MIDDLE_DOT " +
                    "${pills[PillState.MISSED] ?: 0} missed",
            ).takeIf { pills.isNotEmpty() }
        val injections = dates(scope.inRange.filter { it.injectionGiven == true })
        val emergency = dates(scope.inRange.filter { it.emergencyContraception == true })
        return ReportList(
            rows =
                methods +
                    listOfNotNull(
                        pillRow,
                        injections?.let { ReportRow("Injections logged", it) },
                        emergency?.let { ReportRow("Emergency contraception", it) },
                    ),
            note = null,
            emptyText = "No contraception recorded for this period.",
        )
    }

    /** "Combined pill, cyclic", "Hormonal IUD". */
    private fun methodLabel(period: ContraceptionPeriod): String {
        val name = MethodLabels.name(period.method)
        if (period.method.baseKind != MethodKind.CYCLIC) return name
        return "$name, ${if (period.regimen == Regimen.CONTINUOUS) "continuous" else "cyclic"}"
    }

    /** "3 Jan – 2 May 2026", "since 11 Sep 2026 · replace by 11 Sep 2029". */
    private fun methodDates(period: ContraceptionPeriod): String {
        val end = period.endDate
        if (end != null) return Formats.rangeWithYear(period.startDate, end)
        val since = "since ${Formats.dayMonthYear(period.startDate)}"
        val due = period.replaceByDate ?: return since
        val what = if (period.method == ContraceptionMethod.INJECTION) "next injection" else "replace by"
        return "$since $MIDDLE_DOT $what ${Formats.dayMonthYear(due)}"
    }

    private fun dates(entries: List<DayEntry>): String? =
        entries.takeIf { it.isNotEmpty() }?.joinToString(", ") { Formats.dayMonthYear(it.date) }

    private fun notes(scope: Scope): ReportList =
        ReportList(
            rows =
                scope.inRange.mapNotNull { entry ->
                    entry.notes
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { ReportRow(Formats.dayMonthYear(entry.date), it) }
                },
            note = null,
            emptyText = "No notes in this period.",
        )

    private fun temperatures(
        scope: Scope,
        settings: TrackerSettings,
    ): ReportTemperatures {
        val unit = settings.temperatureUnit
        val typical =
            InsightsCalculator.cycleLengths(scope.complete, ALL_CYCLES)?.mean?.roundToInt()
                ?: settings.typicalCycleLength
        val charts =
            scope.cycles
                .filter { !it.startDate.isBefore(scope.from) && !it.startDate.isAfter(scope.today) }
                .mapNotNull { cycle -> temperatureChart(scope, cycle, typical, unit) }
        val cutOff = Formats.time(PredictionConstants.BBT_LATE_CUTOFF)
        return ReportTemperatures(
            note =
                "Waking temperature in ${Units.temperatureSymbol(unit)}, one chart per cycle. Shifts are found " +
                    "with the 3-over-6 rule on readings taken before $cutOff; ovulation dates are estimates.",
            charts = charts,
            emptyText = "No waking temperature logged in this period.",
        )
    }

    private fun temperatureChart(
        scope: Scope,
        cycle: Cycle,
        typicalLength: Int,
        unit: TemperatureUnit,
    ): ReportTemperatureChart? {
        val found = InsightsCalculator.temperatures(cycle, scope.entries)
        if (found.readings.isEmpty()) return null
        val method = scope.hormonalMethod(cycle)
        // A hormonal method suppresses ovulation: any "shift" would be noise, so none is drawn.
        val temps = if (method == null) found else found.copy(shift = null, heldDays = 0)
        val chart = InsightsUiBuilder.temperatureChart(temps, cycle.lengthDays ?: typicalLength, unit)
        val caption =
            when {
                method != null -> "${MethodLabels.name(
                    method.method,
                )} in use: readings shown without looking for a shift."
                cycle.isComplete ->
                    InsightsUiBuilder.temperatureCaption(
                        temps,
                        unit,
                        noShift = "No temperature shift found.",
                    )
                else -> InsightsUiBuilder.temperatureCaption(temps, unit, noShift = "No temperature shift found yet.")
            }
        val title =
            if (cycle.isComplete) {
                "Cycle from ${Formats.dayMonthYear(cycle.startDate)} $MIDDLE_DOT " +
                    Formats.days(requireNotNull(cycle.lengthDays))
            } else {
                "Current cycle from ${Formats.dayMonthYear(
                    cycle.startDate,
                )} $MIDDLE_DOT day ${cycle.dayOf(scope.today)}"
            }
        return ReportTemperatureChart(
            title = title,
            caption = caption,
            chart = chart,
            axisLow = Formats.oneDecimal(Units.toDisplayTemperature(chart.minC, unit)),
            axisHigh = Formats.oneDecimal(Units.toDisplayTemperature(chart.maxC, unit)),
        )
    }

    /** "29.4 d (27–32)", or "29 d" when every cycle had the same length. */
    private fun meanAndRange(
        mean: Double,
        min: Int,
        max: Int,
    ): String = if (min == max) "$min d" else "${Formats.oneDecimal(mean)} d ($min$EN_DASH$max)"

    private fun overlaps(
        period: ContraceptionPeriod,
        start: LocalDate,
        end: LocalDate,
    ): Boolean = !period.startDate.isAfter(end) && period.endDate?.isBefore(start) != true

    /** "Last 6 cycles". A range choice, not a prediction threshold. */
    private const val RECENT_CYCLES = 6
    private const val MONTHS_IN_YEAR = 12L

    /** The calculators' window argument meaning "every cycle passed in". */
    private const val ALL_CYCLES = Int.MAX_VALUE
}
