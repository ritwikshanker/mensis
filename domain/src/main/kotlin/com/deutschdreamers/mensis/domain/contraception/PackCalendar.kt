package com.deutschdreamers.mensis.domain.contraception

import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate

/** Where a date falls in a 21+7 pack (combined pill, patch 3+1, ring 3+1) counted from the method start. */
data class PackPosition(
    val packIndex: Int,
    val packDay: Int,
    val packStart: LocalDate,
) {
    val isBreak: Boolean get() = packDay > PredictionConstants.PACK_ACTIVE_DAYS

    val breakStart: LocalDate get() = packStart.plusDays(PredictionConstants.PACK_ACTIVE_DAYS.toLong())

    val breakRange: DateRange get() = DateRange(breakStart, nextPackStart.minusDays(1))

    val nextPackStart: LocalDate get() = packStart.plusDays(PredictionConstants.PACK_LENGTH_DAYS.toLong())

    /** 1-based week of the pack (1–3 active, 4 = break) for patch and ring wording. */
    val weekOfPack: Int get() = (packDay - 1) / DAYS_PER_WEEK + 1

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}

object PackCalendar {
    fun position(
        methodStart: LocalDate,
        date: LocalDate,
    ): PackPosition {
        val days = methodStart.daysUntil(date)
        val packLength = PredictionConstants.PACK_LENGTH_DAYS
        val packIndex = Math.floorDiv(days, packLength)
        val packDay = Math.floorMod(days, packLength) + 1
        return PackPosition(packIndex, packDay, methodStart.plusDays((packIndex * packLength).toLong()))
    }

    /** Pack days on which a weekly patch is changed (days 1, 8, 15). */
    fun isPatchChangeDay(packDay: Int): Boolean =
        packDay <= PredictionConstants.PACK_ACTIVE_DAYS && (packDay - 1) % PATCH_INTERVAL_DAYS == 0

    private const val PATCH_INTERVAL_DAYS = 7
}
