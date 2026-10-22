package com.deutschdreamers.mensis.domain.reminders

import com.deutschdreamers.mensis.domain.contraception.MethodSchedule
import com.deutschdreamers.mensis.domain.contraception.PackCalendar
import com.deutschdreamers.mensis.domain.contraception.WithdrawalBleedPredictor
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.domain.model.daysUntil
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.domain.prediction.PredictionEngine
import com.deutschdreamers.mensis.domain.prediction.PredictionInput
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Derives when each enabled reminder fires next. Each type has at most one pending reminder; once it
 * has fired, planning again from a later `now` gives the following one. A reminder whose time has
 * passed is skipped, never fired late: a missed "period expected" reminder is not sent after the fact.
 *
 * Bleed and fertile reminders are paused in pregnancy mode, as their predictions are.
 */
object ReminderPlanner {
    /** How far ahead daily and weekly reminders are searched; the longest gap (ring, 4-weekly) is 28 days. */
    private const val SEARCH_DAYS = 62

    /** The next reminder strictly after [now] for every enabled type that applies today. */
    fun plan(
        data: PredictionInput,
        config: ReminderConfig,
        now: LocalDateTime,
    ): List<PlannedReminder> {
        val input = data.copy(today = now.toLocalDate())
        val method = input.contraception.activeOn(input.today)
        return ReminderCatalog
            .available(method, input.settings.trackingLevel)
            .map { ReminderCatalog.resolve(it, config, method) }
            .filter { it.enabled }
            .mapNotNull { next(it, input, method, now) }
    }

    /**
     * The reminder of [type] planned for exactly [at], if the data still lead to it. Used when an alarm
     * goes off, possibly late, to decide whether to post it.
     */
    fun due(
        type: ReminderType,
        data: PredictionInput,
        config: ReminderConfig,
        at: LocalDateTime,
    ): PlannedReminder? = plan(data, config, at.minusNanos(1)).firstOrNull { it.type == type && it.at == at }

    private fun next(
        reminder: ResolvedReminder,
        input: PredictionInput,
        method: ContraceptionPeriod?,
        now: LocalDateTime,
    ): PlannedReminder? {
        val pregnant = input.settings.mode == TrackerMode.PREGNANCY
        return when (reminder.type) {
            ReminderType.PERIOD ->
                when {
                    pregnant -> null
                    method != null && method.kind == MethodKind.CYCLIC -> withdrawalBleed(reminder, input, method, now)
                    else -> period(reminder, input, now)
                }
            ReminderType.FERTILE -> if (pregnant) null else fertile(reminder, input, now)
            ReminderType.TEMPERATURE -> temperature(reminder, input, now, pregnant)
            else -> method?.let { methodReminder(reminder, it, input, now) }
        }
    }

    private fun methodReminder(
        reminder: ResolvedReminder,
        method: ContraceptionPeriod,
        input: PredictionInput,
        now: LocalDateTime,
    ): PlannedReminder? =
        when (reminder.type) {
            ReminderType.PILL -> pill(reminder, method, now)
            ReminderType.PATCH -> patch(reminder, method, now)
            ReminderType.RING -> ring(reminder, method, now)
            ReminderType.INJECTION -> injection(reminder, method, input, now)
            ReminderType.REPLACEMENT -> replacement(reminder, method, now)
            else -> null
        }

    private fun period(
        reminder: ResolvedReminder,
        input: PredictionInput,
        now: LocalDateTime,
    ): PlannedReminder? {
        val next = PredictionEngine.predict(input).nextPeriod ?: return null
        val lead = reminder.option.leadDays
        val event = ReminderEvent.PeriodExpected(next.range, lead, next.basisCycles, next.usesTypicalLength)
        return onDay(reminder, next.earliest.minusDays(lead.toLong()), now, event)
    }

    /** This pack's bleed, or the next pack's once this one's reminder has passed. */
    private fun withdrawalBleed(
        reminder: ResolvedReminder,
        input: PredictionInput,
        method: ContraceptionPeriod,
        now: LocalDateTime,
    ): PlannedReminder? {
        val current = WithdrawalBleedPredictor.predict(method, input.entries, input.today)
        val following = WithdrawalBleedPredictor.predict(method, input.entries, current.nextPackStart)
        val lead = reminder.option.leadDays
        return listOf(current, following).firstNotNullOfOrNull { bleed ->
            val event = ReminderEvent.WithdrawalBleedExpected(bleed.range, lead)
            onDay(reminder, bleed.range.start.minusDays(lead.toLong()), now, event)
        }
    }

    /** This cycle's window, or the next cycle's once this one's reminder has passed. */
    private fun fertile(
        reminder: ResolvedReminder,
        input: PredictionInput,
        now: LocalDateTime,
    ): PlannedReminder? {
        val prediction = PredictionEngine.predict(input)
        val lead = reminder.option.leadDays
        return listOfNotNull(prediction.fertileWindow, prediction.nextCycleFertileWindow).firstNotNullOfOrNull {
            val event = ReminderEvent.FertileWindowStarts(it.range, lead)
            onDay(reminder, it.range.start.minusDays(lead.toLong()), now, event)
        }
    }

    private fun pill(
        reminder: ResolvedReminder,
        method: ContraceptionPeriod,
        now: LocalDateTime,
    ): PlannedReminder? {
        val packs = ReminderCatalog.hasBreakWeek(method)
        val windowEnd =
            if (method.method == ContraceptionMethod.PROGESTIN_ONLY_PILL) {
                reminder.time.plusHours(PredictionConstants.POP_WINDOW_HOURS.toLong())
            } else {
                null
            }
        return firstDay(reminder, method.startDate, now) { day ->
            val packDay = if (packs) PackCalendar.position(method.startDate, day).packDay else null
            val inBreak = packDay != null && packDay > PredictionConstants.PACK_ACTIVE_DAYS
            if (reminder.option == ReminderOption.ACTIVE_DAYS_ONLY && inBreak) {
                null
            } else {
                ReminderEvent.PillDue(packDay, windowEnd)
            }
        }
    }

    /** Weekly on the start weekday. On 21 + 7 the change on pack day 22 takes the patch off for the break. */
    private fun patch(
        reminder: ResolvedReminder,
        method: ContraceptionPeriod,
        now: LocalDateTime,
    ): PlannedReminder? =
        firstDay(reminder, method.startDate, now) { day ->
            if (method.startDate.daysUntil(day) % DAYS_PER_WEEK != 0) return@firstDay null
            val action =
                if (method.kind != MethodKind.CYCLIC) {
                    PatchAction.CHANGE
                } else {
                    when (PackCalendar.position(method.startDate, day).packDay) {
                        1 -> PatchAction.APPLY
                        PredictionConstants.PACK_ACTIVE_DAYS + 1 -> PatchAction.REMOVE
                        else -> PatchAction.CHANGE
                    }
                }
            ReminderEvent.PatchDue(action)
        }

    private fun ring(
        reminder: ResolvedReminder,
        method: ContraceptionPeriod,
        now: LocalDateTime,
    ): PlannedReminder? =
        firstDay(reminder, method.startDate, now) { day ->
            val days = method.startDate.daysUntil(day)
            val packDay = PackCalendar.position(method.startDate, day).packDay
            when {
                reminder.option == ReminderOption.FOUR_WEEKLY ->
                    if (days > 0 && packDay == 1) ReminderEvent.RingDue(RingAction.REPLACE) else null
                packDay == PredictionConstants.PACK_ACTIVE_DAYS + 1 -> ReminderEvent.RingDue(RingAction.REMOVE)
                days > 0 && packDay == 1 -> ReminderEvent.RingDue(RingAction.INSERT)
                else -> null
            }
        }

    private fun injection(
        reminder: ResolvedReminder,
        method: ContraceptionPeriod,
        input: PredictionInput,
        now: LocalDateTime,
    ): PlannedReminder? {
        val due = MethodSchedule.nextInjectionDate(method, input.entries, reminder.option.intervalWeeks) ?: return null
        val last = MethodSchedule.lastInjectionDate(method, input.entries)
        return onDay(reminder, due, now, ReminderEvent.InjectionDue(due, last))
    }

    private fun replacement(
        reminder: ResolvedReminder,
        method: ContraceptionPeriod,
        now: LocalDateTime,
    ): PlannedReminder? {
        val replaceBy = MethodSchedule.replaceByDate(method) ?: return null
        val months = reminder.option.leadMonths
        val event = ReminderEvent.ReplacementDue(method.method, replaceBy, months)
        return onDay(reminder, replaceBy.minusMonths(months.toLong()), now, event)
    }

    /**
     * Daily, or with "follicular phase only" until a temperature shift confirms ovulation in the current
     * cycle; logging the next period starts it again.
     */
    private fun temperature(
        reminder: ResolvedReminder,
        input: PredictionInput,
        now: LocalDateTime,
        pregnant: Boolean,
    ): PlannedReminder? {
        if (reminder.option == ReminderOption.FOLLICULAR_ONLY) {
            if (pregnant) return null
            val prediction = PredictionEngine.predict(input)
            if (prediction.currentCycle != null && prediction.ovulation?.isConfirmed == true) return null
        }
        return firstDay(reminder, input.today, now) { ReminderEvent.TemperatureDue }
    }

    private fun onDay(
        reminder: ResolvedReminder,
        day: LocalDate,
        now: LocalDateTime,
        event: ReminderEvent,
    ): PlannedReminder? {
        val at = day.atTime(reminder.time)
        return if (at.isAfter(now)) PlannedReminder(reminder.type, at, event) else null
    }

    /** The first day from [from] (or today) whose reminder time is after [now] and that has an event. */
    private fun firstDay(
        reminder: ResolvedReminder,
        from: LocalDate,
        now: LocalDateTime,
        eventOn: (LocalDate) -> ReminderEvent?,
    ): PlannedReminder? {
        val start = maxOf(from, now.toLocalDate())
        return (0..SEARCH_DAYS)
            .asSequence()
            .map { start.plusDays(it.toLong()).atTime(reminder.time) }
            .filter { it.isAfter(now) }
            .firstNotNullOfOrNull { at -> eventOn(at.toLocalDate())?.let { PlannedReminder(reminder.type, at, it) } }
    }

    private const val DAYS_PER_WEEK = 7
}
