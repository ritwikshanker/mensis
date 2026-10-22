package com.deutschdreamers.mensis.domain.contraception

import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate

/** Replacement and next-injection dates for long-acting methods. */
object MethodSchedule {
    /** The user's own date if set, else the method's default interval from the start date. */
    fun replaceByDate(period: ContraceptionPeriod): LocalDate? {
        period.replaceByDate?.let { return it }
        val years =
            when (period.method) {
                ContraceptionMethod.IMPLANT -> PredictionConstants.IMPLANT_YEARS
                ContraceptionMethod.HORMONAL_IUD -> PredictionConstants.HORMONAL_IUD_YEARS
                ContraceptionMethod.COPPER_IUD -> PredictionConstants.COPPER_IUD_YEARS
                else -> return null
            }
        return period.startDate.plusYears(years.toLong())
    }

    /** Last injection (logged or the method start) plus the injection interval. */
    fun nextInjectionDate(
        period: ContraceptionPeriod,
        entries: List<DayEntry>,
    ): LocalDate? {
        if (period.method != ContraceptionMethod.INJECTION) return null
        val last = lastInjectionDate(period, entries)
        return last.plusWeeks(PredictionConstants.INJECTION_INTERVAL_WEEKS.toLong())
    }

    fun lastInjectionDate(
        period: ContraceptionPeriod,
        entries: List<DayEntry>,
    ): LocalDate =
        entries.filter { it.injectionGiven == true && !it.date.isBefore(period.startDate) }.maxOfOrNull { it.date }
            ?: period.startDate
}
