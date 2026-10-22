package com.deutschdreamers.mensis.domain

import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/** Synthetic data builders. Nothing here comes from a real person. */
object Synthetic {
    val day1: LocalDate = LocalDate.of(2026, 1, 5)

    val defaultPeriodFlows: List<Flow> = listOf(Flow.MEDIUM, Flow.HEAVY, Flow.MEDIUM, Flow.LIGHT, Flow.LIGHT)

    /** Entries for one period starting on [start]; the number of days is `flows.size`. */
    fun period(
        start: LocalDate,
        flows: List<Flow> = defaultPeriodFlows,
    ): List<DayEntry> = flows.mapIndexed { index, flow -> DayEntry(start.plusDays(index.toLong()), flow = flow) }

    /** `count` period starts, each [cycleLength] days apart, with periods of [periodLength] days. */
    fun regularCycles(
        firstStart: LocalDate = day1,
        cycleLength: Int = 28,
        periodLength: Int = 5,
        count: Int = 7,
    ): List<DayEntry> = cyclesWithLengths(firstStart, List(count - 1) { cycleLength }, periodLength)

    /** `lengths.size + 1` period starts whose gaps are [lengths]. */
    fun cyclesWithLengths(
        firstStart: LocalDate = day1,
        lengths: List<Int>,
        periodLength: Int = 5,
    ): List<DayEntry> {
        val flows = defaultPeriodFlows.take(periodLength).let { it + List(periodLength - it.size) { Flow.LIGHT } }
        val starts = lengths.runningFold(firstStart) { date, length -> date.plusDays(length.toLong()) }
        return starts.flatMap { period(it, flows) }
    }

    /** Last period start of a synthetic history. */
    fun lastStart(entries: List<DayEntry>): LocalDate =
        com.deutschdreamers.mensis.domain.cycle.CycleDeriver
            .periodStarts(entries)
            .last()

    /** Temperature readings: [lowDays] days around 36.4 °C, then a rise to ~36.7 °C, with optional noise. */
    fun temperatures(
        start: LocalDate,
        lowDays: Int,
        highDays: Int,
        noise: Double = 0.0,
        seed: Int = 1,
    ): List<DayEntry> {
        val time = LocalTime.of(6, 40)
        val random = Random(seed)

        fun jitter() = if (noise == 0.0) 0.0 else (random.nextDouble() * 2 - 1) * noise
        val low =
            (0 until lowDays).map {
                DayEntry(start.plusDays(it.toLong()), temperatureC = round2(36.40 + jitter()), temperatureTime = time)
            }
        val high =
            (0 until highDays).map {
                DayEntry(
                    start.plusDays((lowDays + it).toLong()),
                    temperatureC = round2(36.72 + jitter()),
                    temperatureTime = time,
                )
            }
        return low + high
    }

    fun round2(value: Double): Double = Math.round(value * 100) / 100.0

    /** Merges entries that share a date (later wins per field). */
    fun merge(vararg lists: List<DayEntry>): List<DayEntry> =
        lists
            .flatMap { it }
            .groupBy { it.date }
            .map { (_, same) ->
                same.reduce { a, b ->
                    a.copy(
                        flow = b.flow ?: a.flow,
                        temperatureC = b.temperatureC ?: a.temperatureC,
                        temperatureTime = b.temperatureTime ?: a.temperatureTime,
                        symptoms = a.symptoms + b.symptoms,
                        moods = a.moods + b.moods,
                        lhTest = b.lhTest ?: a.lhTest,
                        pregnancyTest = b.pregnancyTest ?: a.pregnancyTest,
                        pillState = b.pillState ?: a.pillState,
                        emergencyContraception = b.emergencyContraception ?: a.emergencyContraception,
                        injectionGiven = b.injectionGiven ?: a.injectionGiven,
                    )
                }
            }.sortedBy { it.date }
}
