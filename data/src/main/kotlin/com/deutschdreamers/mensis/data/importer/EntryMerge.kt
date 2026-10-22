package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.domain.model.DayEntry

/** Field-level combination of two entries for the same day. */
object EntryMerge {
    /** Keeps every value of [kept] and takes from [incoming] only what [kept] leaves empty. */
    @Suppress("CyclomaticComplexMethod") // one elvis per DayEntry field; splitting would hide the field list
    fun fillEmpty(
        kept: DayEntry,
        incoming: DayEntry,
    ): DayEntry =
        kept.copy(
            flow = kept.flow ?: incoming.flow,
            temperatureC = kept.temperatureC ?: incoming.temperatureC,
            temperatureTime = kept.temperatureTime ?: incoming.temperatureTime.takeIf { kept.temperatureC == null },
            symptoms = kept.symptoms.ifEmpty { incoming.symptoms },
            moods = kept.moods.ifEmpty { incoming.moods },
            cervicalMucus = kept.cervicalMucus ?: incoming.cervicalMucus,
            lhTest = kept.lhTest ?: incoming.lhTest,
            pregnancyTest = kept.pregnancyTest ?: incoming.pregnancyTest,
            sex = kept.sex ?: incoming.sex,
            pillState = kept.pillState ?: incoming.pillState,
            patchChanged = kept.patchChanged ?: incoming.patchChanged,
            ringState = kept.ringState ?: incoming.ringState,
            injectionGiven = kept.injectionGiven ?: incoming.injectionGiven,
            emergencyContraception = kept.emergencyContraception ?: incoming.emergencyContraception,
            sleepMinutes = kept.sleepMinutes ?: incoming.sleepMinutes,
            weightKg = kept.weightKg ?: incoming.weightKg,
            notes = kept.notes?.takeIf { it.isNotBlank() } ?: incoming.notes,
        )
}
