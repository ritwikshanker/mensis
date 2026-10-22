package com.deutschdreamers.mensis.notifications

import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.prediction.PredictionConstants
import com.deutschdreamers.mensis.domain.reminders.PatchAction
import com.deutschdreamers.mensis.domain.reminders.PlannedReminder
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.domain.reminders.ReminderEvent
import com.deutschdreamers.mensis.domain.reminders.ReminderOption
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import com.deutschdreamers.mensis.domain.reminders.ResolvedReminder
import com.deutschdreamers.mensis.domain.reminders.RingAction
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class NotificationText(
    val title: String,
    val body: String,
)

/** Reminder wording for notifications and the Reminders screen (design §10), as plain Kotlin for unit tests. */
object ReminderTexts {
    const val GENERIC_TITLE: String = "Reminder"
    const val GENERIC_BODY: String = "Tap to open"

    /** The discreet text: the user's own, or "Reminder / Tap to open". */
    fun generic(config: ReminderConfig): NotificationText =
        NotificationText(
            title = config.discreetTitle?.takeIf { it.isNotBlank() } ?: GENERIC_TITLE,
            body = config.discreetBody?.takeIf { it.isNotBlank() } ?: GENERIC_BODY,
        )

    /** What a notification says: the generic text unless discreet wording is off. */
    fun notification(
        reminder: PlannedReminder,
        config: ReminderConfig,
    ): NotificationText = if (config.discreet) generic(config) else detailed(reminder.event)

    /** Design: "Period expected in 2 days / Expected 2–5 Nov · based on 8 cycles". */
    fun detailed(event: ReminderEvent): NotificationText =
        when (event) {
            is ReminderEvent.PeriodExpected ->
                NotificationText(
                    "Period expected ${relative(event.daysAhead)}",
                    "Expected ${Formats.range(event.range)} $MIDDLE_DOT ${basis(event)}",
                )
            is ReminderEvent.WithdrawalBleedExpected ->
                NotificationText(
                    "Withdrawal bleed expected ${relative(event.daysAhead)}",
                    "Expected ${Formats.range(event.range)} $MIDDLE_DOT break week",
                )
            is ReminderEvent.FertileWindowStarts ->
                NotificationText(
                    "Fertile window starts ${relative(event.daysAhead)}",
                    "Estimated ${Formats.range(event.range)}",
                )
            is ReminderEvent.PillDue -> NotificationText("Time for your pill", pillBody(event))
            is ReminderEvent.PatchDue -> patch(event.action)
            is ReminderEvent.RingDue -> ring(event.action)
            is ReminderEvent.InjectionDue ->
                NotificationText(
                    "Next injection due today",
                    "Last injection ${Formats.dayMonthYear(event.lastInjection)}",
                )
            is ReminderEvent.ReplacementDue ->
                NotificationText(
                    "${deviceName(event.method)} replacement in ${months(event.monthsAhead)}",
                    "Replace by ${Formats.dayMonthYear(event.replaceBy)}",
                )
            ReminderEvent.TemperatureDue ->
                NotificationText("Take your temperature", "Before getting up, at your usual time")
        }

    private fun patch(action: PatchAction): NotificationText =
        when (action) {
            PatchAction.APPLY -> NotificationText("Apply a new patch", "A new patch week starts today")
            PatchAction.CHANGE -> NotificationText("Change your patch", "Weekly patch change")
            PatchAction.REMOVE -> NotificationText("Take the patch off", "The patch-free week starts today")
        }

    private fun ring(action: RingAction): NotificationText =
        when (action) {
            RingAction.REMOVE -> NotificationText("Take the ring out", "The ring-free week starts today")
            RingAction.INSERT -> NotificationText("Put a new ring in", "The ring-free week is over")
            RingAction.REPLACE -> NotificationText("Replace your ring", "Take it out and put a new one in")
        }

    private fun relative(days: Int): String =
        when (days) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in $days days"
        }

    private fun basis(event: ReminderEvent.PeriodExpected): String =
        if (event.usesTypicalLength) "based on your typical length" else "based on ${Formats.cycles(event.basisCycles)}"

    private fun pillBody(event: ReminderEvent.PillDue): String {
        val packDay = event.packDay
        val windowEnd = event.windowEnd
        val active = PredictionConstants.PACK_ACTIVE_DAYS
        val breakDays = PredictionConstants.PACK_LENGTH_DAYS - active
        return when {
            windowEnd != null ->
                "Take it by ${Formats.time(windowEnd)}, within ${PredictionConstants.POP_WINDOW_HOURS} hours"
            packDay == null -> "Your daily pill"
            packDay <= active -> "Active day $packDay of $active"
            else -> "Break week, day ${packDay - active} of $breakDays"
        }
    }

    private fun months(count: Int): String = if (count == 1) "1 month" else "$count months"

    private fun deviceName(method: ContraceptionMethod): String =
        if (method == ContraceptionMethod.IMPLANT) "Implant" else "IUD"

    // ---- Reminders screen ----------------------------------------------------------------------------

    fun title(
        type: ReminderType,
        method: ContraceptionPeriod?,
    ): String =
        when (type) {
            ReminderType.PERIOD ->
                if (method?.kind == MethodKind.CYCLIC) "Withdrawal bleed expected" else "Period expected"
            ReminderType.FERTILE -> "Fertile window"
            ReminderType.PILL ->
                if (method?.method == ContraceptionMethod.PROGESTIN_ONLY_PILL) "Progestin-only pill" else "Pill"
            ReminderType.PATCH -> "Change patch"
            ReminderType.RING -> "Ring out, then in"
            ReminderType.INJECTION -> "Next injection"
            ReminderType.REPLACEMENT ->
                "${method?.method?.let(::deviceName) ?: "IUD"} replacement"
            ReminderType.TEMPERATURE -> "Morning temperature"
        }

    fun option(option: ReminderOption): String =
        when (option) {
            ReminderOption.SAME_DAY -> "Same day"
            ReminderOption.ONE_DAY_BEFORE -> "1 day before"
            ReminderOption.TWO_DAYS_BEFORE -> "2 days before"
            ReminderOption.DAILY -> "Daily"
            ReminderOption.ACTIVE_DAYS_ONLY -> "Active days only"
            ReminderOption.WEEKLY -> "Weekly"
            ReminderOption.THREE_WEEKS_IN_ONE_OUT -> "3 weeks in, 1 out"
            ReminderOption.FOUR_WEEKLY -> "4-weekly, continuous"
            ReminderOption.TWELVE_WEEKS -> "12 weeks"
            ReminderOption.THIRTEEN_WEEKS -> "13 weeks"
            ReminderOption.ONE_MONTH_BEFORE -> "1 month before"
            ReminderOption.THREE_MONTHS_BEFORE -> "3 months before"
            ReminderOption.FOLLICULAR_ONLY -> "Follicular phase only"
        }

    /**
     * Design §10 row lines: "2 days before · 09:00", "Daily · 21:00 · 3-hour window", "Weekly, Monday · 09:00",
     * "4 Dec 2026 · 09:00", "Mar 2029 · 3 months before". [date] is the injection or replacement date.
     */
    fun subtitle(
        reminder: ResolvedReminder,
        method: ContraceptionPeriod?,
        date: LocalDate?,
    ): String {
        val time = Formats.time(reminder.time)
        return when (reminder.type) {
            ReminderType.PERIOD -> "${option(reminder.option)} $MIDDLE_DOT $time"
            ReminderType.FERTILE ->
                if (reminder.option == ReminderOption.SAME_DAY) {
                    "Day it starts $MIDDLE_DOT $time"
                } else {
                    "Day before it starts $MIDDLE_DOT $time"
                }
            ReminderType.PILL ->
                if (method?.method == ContraceptionMethod.PROGESTIN_ONLY_PILL) {
                    "${option(reminder.option)} $MIDDLE_DOT $time $MIDDLE_DOT " +
                        "${PredictionConstants.POP_WINDOW_HOURS}-hour window"
                } else {
                    "${option(reminder.option)} $MIDDLE_DOT $time"
                }
            ReminderType.PATCH -> {
                val weekday = method?.startDate?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                "Weekly${weekday?.let { ", $it" } ?: ""} $MIDDLE_DOT $time"
            }
            ReminderType.RING ->
                if (reminder.option == ReminderOption.FOUR_WEEKLY) {
                    "Every 4 weeks $MIDDLE_DOT $time"
                } else {
                    "Every 3 weeks $MIDDLE_DOT $time"
                }
            ReminderType.INJECTION ->
                listOfNotNull(
                    date?.let(Formats::dayMonthYear),
                    time,
                ).joinToString(" $MIDDLE_DOT ")
            ReminderType.REPLACEMENT ->
                listOfNotNull(date?.let(Formats::monthYear), option(reminder.option)).joinToString(" $MIDDLE_DOT ")
            ReminderType.TEMPERATURE -> "${option(reminder.option)} $MIDDLE_DOT $time"
        }
    }

    /** Settings row: "Period, pill, temperature", or "Off". */
    fun summary(
        enabled: List<ReminderType>,
        method: ContraceptionPeriod?,
    ): String {
        if (enabled.isEmpty()) return "Off"
        val names =
            enabled.map { type ->
                when (type) {
                    ReminderType.PERIOD -> if (method?.kind == MethodKind.CYCLIC) "bleed" else "period"
                    ReminderType.FERTILE -> "fertile window"
                    ReminderType.PILL -> "pill"
                    ReminderType.PATCH -> "patch"
                    ReminderType.RING -> "ring"
                    ReminderType.INJECTION -> "injection"
                    ReminderType.REPLACEMENT -> "replacement"
                    ReminderType.TEMPERATURE -> "temperature"
                }
            }
        return names.joinToString(", ").replaceFirstChar { it.uppercase() }
    }

    /** Contraception screen row: "Pill · Daily · 21:00", "Pill · Off", or "No method reminder". */
    fun methodSummary(
        reminder: ResolvedReminder?,
        method: ContraceptionPeriod?,
        date: LocalDate?,
    ): String {
        if (reminder == null) return "No method reminder"
        val state = if (reminder.enabled) subtitle(reminder, method, date) else "Off"
        return "${title(reminder.type, method)} $MIDDLE_DOT $state"
    }
}
