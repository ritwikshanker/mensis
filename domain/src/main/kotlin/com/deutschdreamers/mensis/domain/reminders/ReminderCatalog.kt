package com.deutschdreamers.mensis.domain.reminders

import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import java.time.LocalTime

/** Defaults from the design (§10). Times are local wall-clock times. */
object ReminderDefaults {
    /** Period, fertile window and method reminders. */
    val TIME: LocalTime = LocalTime.of(9, 0)

    /** Daily pill when the method has no time of its own. */
    val PILL_TIME: LocalTime = LocalTime.of(21, 0)

    /** Morning temperature, before getting up. */
    val TEMPERATURE_TIME: LocalTime = LocalTime.of(6, 30)
}

/** Which reminders apply (design §10 and the contraception table), with their options and defaults. */
object ReminderCatalog {
    /** In screen order: bleed, fertile window, method, temperature. */
    fun available(
        method: ContraceptionPeriod?,
        trackingLevel: TrackingLevel,
    ): List<ReminderType> {
        val kind = method?.kind ?: MethodKind.NONE
        return buildList {
            if (kind != MethodKind.SUPPRESS) add(ReminderType.PERIOD)
            if (kind == MethodKind.NONE || kind == MethodKind.COPPER) add(ReminderType.FERTILE)
            methodType(method?.method)?.let(::add)
            if (trackingLevel.showsTemperature) add(ReminderType.TEMPERATURE)
        }
    }

    fun methodType(method: ContraceptionMethod?): ReminderType? =
        when (method) {
            ContraceptionMethod.COMBINED_PILL, ContraceptionMethod.PROGESTIN_ONLY_PILL -> ReminderType.PILL
            ContraceptionMethod.PATCH -> ReminderType.PATCH
            ContraceptionMethod.RING -> ReminderType.RING
            ContraceptionMethod.INJECTION -> ReminderType.INJECTION
            ContraceptionMethod.IMPLANT, ContraceptionMethod.HORMONAL_IUD, ContraceptionMethod.COPPER_IUD ->
                ReminderType.REPLACEMENT
            ContraceptionMethod.NONE, null -> null
        }

    fun options(
        type: ReminderType,
        method: ContraceptionPeriod?,
    ): List<ReminderOption> =
        when (type) {
            ReminderType.PERIOD ->
                if (method?.kind == MethodKind.CYCLIC) {
                    listOf(ReminderOption.SAME_DAY, ReminderOption.ONE_DAY_BEFORE)
                } else {
                    listOf(ReminderOption.SAME_DAY, ReminderOption.ONE_DAY_BEFORE, ReminderOption.TWO_DAYS_BEFORE)
                }
            ReminderType.FERTILE -> listOf(ReminderOption.SAME_DAY, ReminderOption.ONE_DAY_BEFORE)
            ReminderType.PILL ->
                if (hasBreakWeek(method)) {
                    listOf(ReminderOption.DAILY, ReminderOption.ACTIVE_DAYS_ONLY)
                } else {
                    listOf(ReminderOption.DAILY)
                }
            ReminderType.PATCH -> listOf(ReminderOption.WEEKLY)
            ReminderType.RING -> listOf(ReminderOption.THREE_WEEKS_IN_ONE_OUT, ReminderOption.FOUR_WEEKLY)
            ReminderType.INJECTION -> listOf(ReminderOption.TWELVE_WEEKS, ReminderOption.THIRTEEN_WEEKS)
            ReminderType.REPLACEMENT -> listOf(ReminderOption.ONE_MONTH_BEFORE, ReminderOption.THREE_MONTHS_BEFORE)
            ReminderType.TEMPERATURE -> listOf(ReminderOption.DAILY, ReminderOption.FOLLICULAR_ONLY)
        }

    fun defaultOption(
        type: ReminderType,
        method: ContraceptionPeriod?,
    ): ReminderOption =
        when (type) {
            ReminderType.PERIOD ->
                if (method?.kind == MethodKind.CYCLIC) ReminderOption.ONE_DAY_BEFORE else ReminderOption.TWO_DAYS_BEFORE
            ReminderType.FERTILE -> ReminderOption.ONE_DAY_BEFORE
            ReminderType.PILL -> ReminderOption.DAILY
            ReminderType.PATCH -> ReminderOption.WEEKLY
            ReminderType.RING ->
                if (method?.regimen == Regimen.CONTINUOUS) {
                    ReminderOption.FOUR_WEEKLY
                } else {
                    ReminderOption.THREE_WEEKS_IN_ONE_OUT
                }
            ReminderType.INJECTION -> ReminderOption.TWELVE_WEEKS
            ReminderType.REPLACEMENT -> ReminderOption.THREE_MONTHS_BEFORE
            ReminderType.TEMPERATURE -> ReminderOption.DAILY
        }

    /** The pill reminder follows the pill time set under Contraception until the user picks another. */
    fun defaultTime(
        type: ReminderType,
        method: ContraceptionPeriod?,
    ): LocalTime =
        when (type) {
            ReminderType.PILL -> method?.dailyTime ?: ReminderDefaults.PILL_TIME
            ReminderType.TEMPERATURE -> ReminderDefaults.TEMPERATURE_TIME
            else -> ReminderDefaults.TIME
        }

    /** A stored option that no longer fits (the method changed) falls back to the default. */
    fun resolve(
        type: ReminderType,
        config: ReminderConfig,
        method: ContraceptionPeriod?,
    ): ResolvedReminder {
        val setting = config.setting(type)
        val options = options(type, method)
        return ResolvedReminder(
            type = type,
            enabled = setting.enabled,
            time = setting.time ?: defaultTime(type, method),
            option = setting.option?.takeIf { it in options } ?: defaultOption(type, method),
            options = options,
        )
    }

    /** A combined pill taken 21 + 7 has a break week the reminder can skip. */
    fun hasBreakWeek(method: ContraceptionPeriod?): Boolean =
        method?.method == ContraceptionMethod.COMBINED_PILL && method.kind == MethodKind.CYCLIC
}
