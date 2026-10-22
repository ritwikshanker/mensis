package com.deutschdreamers.mensis.domain.reminders

import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.DateRange
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One row on the Reminders screen (design §10). Which rows exist depends on the method and tracking level. */
enum class ReminderType {
    /** "Period expected", or "Withdrawal bleed expected" for cyclic methods. Hidden for suppressing methods. */
    PERIOD,

    /** Only without a hormonal method (none or barrier, copper IUD). */
    FERTILE,

    /** Combined and progestin-only pill. */
    PILL,
    PATCH,
    RING,
    INJECTION,

    /** Implant and IUD replacement. */
    REPLACEMENT,

    /** Only when tracking everything, including temperature. */
    TEMPERATURE,
}

/** The chips of an expanded row; each type offers a few of them (see [ReminderCatalog.options]). */
enum class ReminderOption(
    val leadDays: Int = 0,
    val intervalWeeks: Int = 0,
    val leadMonths: Int = 0,
) {
    SAME_DAY,
    ONE_DAY_BEFORE(leadDays = 1),
    TWO_DAYS_BEFORE(leadDays = 2),
    DAILY,

    /** Combined pill on 21 + 7: no reminder in the break week. */
    ACTIVE_DAYS_ONLY,
    WEEKLY,

    /** Ring out on pack day 22, a new ring in on day 1 of the next pack. */
    THREE_WEEKS_IN_ONE_OUT,

    /** A new ring every 28 days without a break. */
    FOUR_WEEKLY,
    TWELVE_WEEKS(intervalWeeks = PredictionConstants.INJECTION_INTERVAL_WEEKS),
    THIRTEEN_WEEKS(intervalWeeks = PredictionConstants.INJECTION_INTERVAL_MAX_WEEKS),
    ONE_MONTH_BEFORE(leadMonths = 1),
    THREE_MONTHS_BEFORE(leadMonths = 3),

    /** Morning temperature until the shift is confirmed in the current cycle. */
    FOLLICULAR_ONLY,
}

/** What the user chose for one reminder. `null` falls back to the type's default. */
data class ReminderSetting(
    val enabled: Boolean = false,
    val time: LocalTime? = null,
    val option: ReminderOption? = null,
)

/**
 * Every reminder setting. These are raw facts like the daily logs; when each reminder fires next is
 * derived by [ReminderPlanner] and never stored.
 */
data class ReminderConfig(
    val settings: Map<ReminderType, ReminderSetting> = emptyMap(),
    /** Notifications show only the generic text. On by default. */
    val discreet: Boolean = true,
    /** The user's own generic title; `null` keeps the default ("Reminder"). */
    val discreetTitle: String? = null,
    /** The user's own generic text; `null` keeps the default ("Tap to open"). */
    val discreetBody: String? = null,
) {
    fun setting(type: ReminderType): ReminderSetting = settings[type] ?: ReminderSetting()

    fun update(
        type: ReminderType,
        transform: (ReminderSetting) -> ReminderSetting,
    ): ReminderConfig = copy(settings = settings + (type to transform(setting(type))))
}

/** A reminder as it applies now: an option valid for the current method and the defaults filled in. */
data class ResolvedReminder(
    val type: ReminderType,
    val enabled: Boolean,
    val time: LocalTime,
    val option: ReminderOption,
    val options: List<ReminderOption>,
)

enum class PatchAction { APPLY, CHANGE, REMOVE }

enum class RingAction { REMOVE, INSERT, REPLACE }

/** What a reminder is about. Only shown when discreet wording is off. */
sealed interface ReminderEvent {
    /** [daysAhead] counts from the reminder day to the first day of [range]. */
    data class PeriodExpected(
        val range: DateRange,
        val daysAhead: Int,
        val basisCycles: Int,
        val usesTypicalLength: Boolean,
    ) : ReminderEvent

    data class WithdrawalBleedExpected(
        val range: DateRange,
        val daysAhead: Int,
    ) : ReminderEvent

    data class FertileWindowStarts(
        val range: DateRange,
        val daysAhead: Int,
    ) : ReminderEvent

    /** [packDay] only on a 21 + 7 combined pill; [windowEnd] only for the progestin-only pill. */
    data class PillDue(
        val packDay: Int?,
        val windowEnd: LocalTime?,
    ) : ReminderEvent

    data class PatchDue(
        val action: PatchAction,
    ) : ReminderEvent

    data class RingDue(
        val action: RingAction,
    ) : ReminderEvent

    data class InjectionDue(
        val dueDate: LocalDate,
        val lastInjection: LocalDate,
    ) : ReminderEvent

    data class ReplacementDue(
        val method: ContraceptionMethod,
        val replaceBy: LocalDate,
        val monthsAhead: Int,
    ) : ReminderEvent

    data object TemperatureDue : ReminderEvent
}

/** The next time a reminder fires, in local wall-clock time. */
data class PlannedReminder(
    val type: ReminderType,
    val at: LocalDateTime,
    val event: ReminderEvent,
)
