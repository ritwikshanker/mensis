package com.deutschdreamers.mensis.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** How a method changes predictions and the UI. */
enum class MethodKind {
    /** None or barrier: period predictions from history. */
    NONE,

    /** Copper IUD: no hormonal effect, predictions unchanged. */
    COPPER,

    /**
     * Hormonal IUD: acts mainly in the uterus, so periods and ovulation often continue. Predictions
     * follow the logged periods; while none can be predicted (none logged, or the expected one has not
     * come), Today shows days since the last bleed instead of a late period.
     */
    LOCAL_HORMONAL,

    /** Combined pill 21+7, patch, ring: withdrawal bleed predicted in the break week; fertile lane hidden. */
    CYCLIC,

    /** Progestin-only pill, continuous regimen, injection, implant: predictions paused. */
    SUPPRESS,
}

enum class ContraceptionMethod(
    val baseKind: MethodKind,
    val isLongActing: Boolean = false,
    val isDailyPill: Boolean = false,
) {
    NONE(MethodKind.NONE),
    COMBINED_PILL(MethodKind.CYCLIC, isDailyPill = true),
    PROGESTIN_ONLY_PILL(MethodKind.SUPPRESS, isDailyPill = true),
    PATCH(MethodKind.CYCLIC),
    RING(MethodKind.CYCLIC),
    INJECTION(MethodKind.SUPPRESS, isLongActing = true),
    IMPLANT(MethodKind.SUPPRESS, isLongActing = true),
    HORMONAL_IUD(MethodKind.LOCAL_HORMONAL, isLongActing = true),
    COPPER_IUD(MethodKind.COPPER, isLongActing = true),
}

/** Only meaningful for cyclic methods. "Continuous" makes a cyclic method behave as suppressing. */
enum class Regimen { CYCLIC, CONTINUOUS }

/**
 * A contraception method in effect from [startDate] until [endDate] (null = current). History is kept
 * so earlier cycles keep the method that applied then.
 */
data class ContraceptionPeriod(
    val method: ContraceptionMethod,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val regimen: Regimen? = null,
    val dailyTime: LocalTime? = null,
    val replaceByDate: LocalDate? = null,
    val checkupDate: LocalDate? = null,
) {
    /** The effective kind, taking the regimen into account. */
    val kind: MethodKind
        get() =
            if (method.baseKind == MethodKind.CYCLIC &&
                regimen == Regimen.CONTINUOUS
            ) {
                MethodKind.SUPPRESS
            } else {
                method.baseKind
            }

    fun isActiveOn(date: LocalDate): Boolean = !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))
}

/** Picks the method in effect on [date], or `null` when none is recorded (treated as [MethodKind.NONE]). */
fun List<ContraceptionPeriod>.activeOn(date: LocalDate): ContraceptionPeriod? =
    filter { it.isActiveOn(date) }.maxByOrNull { it.startDate }
