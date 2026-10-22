package com.deutschdreamers.mensis.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.domain.contraception.MethodSchedule
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.domain.reminders.ReminderCatalog
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.domain.reminders.ReminderDefaults
import com.deutschdreamers.mensis.domain.reminders.ReminderOption
import com.deutschdreamers.mensis.domain.reminders.ReminderPlanner
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import com.deutschdreamers.mensis.domain.reminders.ResolvedReminder
import com.deutschdreamers.mensis.lock.AppLockManager
import com.deutschdreamers.mensis.notifications.NotificationText
import com.deutschdreamers.mensis.notifications.ReminderAlarms
import com.deutschdreamers.mensis.notifications.ReminderNotifications
import com.deutschdreamers.mensis.notifications.ReminderTexts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class ReminderRow(
    val type: ReminderType,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val option: ReminderOption,
    val options: List<ReminderOption>,
    val time: LocalTime,
)

data class RemindersUiState(
    val rows: List<ReminderRow>,
    val discreet: Boolean,
    /** The generic text, the user's own or the default. */
    val generic: NotificationText,
    /** What the first enabled reminder (or the first row) would say. */
    val preview: NotificationText,
    val previewTime: LocalTime,
    /** Permission granted and neither app nor channel blocked. */
    val notificationsAllowed: Boolean,
    val exactAlarms: Boolean,
) {
    val anyEnabled: Boolean get() = rows.any { it.enabled }
}

/** What the stateless content asks of its host. */
data class RemindersActions(
    val onBack: () -> Unit,
    val onEnabled: (ReminderType, Boolean) -> Unit,
    val onOption: (ReminderType, ReminderOption) -> Unit,
    val onTime: (ReminderType, LocalTime) -> Unit,
    val onDiscreet: (Boolean) -> Unit,
    val onGenericText: (String, String) -> Unit,
    val onAllowNotifications: () -> Unit,
    val onAllowExactAlarms: () -> Unit,
)

/** What Android currently allows; read again whenever the screen resumes. */
data class ReminderSystemState(
    val notificationsAllowed: Boolean,
    val exactAlarms: Boolean,
)

/** Builds the Reminders screen from data and settings; plain Kotlin for unit tests. */
object RemindersUi {
    fun build(
        data: TrackerData,
        config: ReminderConfig,
        system: ReminderSystemState,
        now: LocalDateTime,
    ): RemindersUiState {
        val today = now.toLocalDate()
        val method = data.contraception.activeOn(today)
        val resolved =
            ReminderCatalog
                .available(method, data.settings.trackingLevel)
                .map { ReminderCatalog.resolve(it, config, method) }
        val rows =
            resolved.map {
                ReminderRow(
                    type = it.type,
                    title = ReminderTexts.title(it.type, method),
                    subtitle = ReminderTexts.subtitle(it, method, scheduleDate(it, method, data)),
                    enabled = it.enabled,
                    option = it.option,
                    options = it.options,
                    time = it.time,
                )
            }
        val shown = rows.firstOrNull { it.enabled } ?: rows.firstOrNull()
        val planned =
            shown?.let { row ->
                val only = ReminderConfig(mapOf(row.type to config.setting(row.type).copy(enabled = true)))
                ReminderPlanner.plan(data.inputFor(today), only, now).firstOrNull()
            }
        val preview =
            when {
                config.discreet -> ReminderTexts.generic(config)
                planned != null -> ReminderTexts.detailed(planned.event)
                else -> NotificationText(shown?.title ?: ReminderTexts.GENERIC_TITLE, ReminderTexts.GENERIC_BODY)
            }
        return RemindersUiState(
            rows = rows,
            discreet = config.discreet,
            generic = ReminderTexts.generic(config),
            preview = preview,
            previewTime = planned?.at?.toLocalTime() ?: shown?.time ?: ReminderDefaults.TIME,
            notificationsAllowed = system.notificationsAllowed,
            exactAlarms = system.exactAlarms,
        )
    }

    /** The injection or replacement date a row names, if any. */
    fun scheduleDate(
        reminder: ResolvedReminder,
        method: ContraceptionPeriod?,
        data: TrackerData,
    ): LocalDate? =
        when {
            method == null -> null
            reminder.type == ReminderType.INJECTION ->
                MethodSchedule.nextInjectionDate(method, data.entries, reminder.option.intervalWeeks)
            reminder.type == ReminderType.REPLACEMENT -> MethodSchedule.replaceByDate(method)
            else -> null
        }
}

@HiltViewModel
class RemindersViewModel
    @Inject
    constructor(
        trackerData: TrackerDataRepository,
        private val settings: SettingsRepository,
        private val notifications: ReminderNotifications,
        private val alarms: ReminderAlarms,
        val lock: AppLockManager,
        private val clock: Clock,
    ) : ViewModel() {
        private val system = MutableStateFlow(currentSystemState())

        val uiState: StateFlow<RemindersUiState?> =
            combine(trackerData.observe(), settings.observeReminders(), system) { data, config, state ->
                RemindersUi.build(data, config, state, LocalDateTime.now(clock))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** After a permission dialog or a trip to Android's settings. */
        fun refreshSystemState() {
            system.value = currentSystemState()
        }

        fun setEnabled(
            type: ReminderType,
            enabled: Boolean,
        ) = update { it.update(type) { setting -> setting.copy(enabled = enabled) } }

        fun setOption(
            type: ReminderType,
            option: ReminderOption,
        ) = update { it.update(type) { setting -> setting.copy(option = option) } }

        fun setTime(
            type: ReminderType,
            time: LocalTime,
        ) = update { it.update(type) { setting -> setting.copy(time = time) } }

        fun setDiscreet(discreet: Boolean) = update { it.copy(discreet = discreet) }

        /** Empty or default text is stored as `null`, so the default follows future wording changes. */
        fun setGenericText(
            title: String,
            body: String,
        ) = update {
            it.copy(
                discreetTitle = title.trim().takeIf { t -> t.isNotEmpty() && t != ReminderTexts.GENERIC_TITLE },
                discreetBody = body.trim().takeIf { b -> b.isNotEmpty() && b != ReminderTexts.GENERIC_BODY },
            )
        }

        private fun update(transform: (ReminderConfig) -> ReminderConfig) {
            viewModelScope.launch { settings.updateReminders(transform) }
        }

        private fun currentSystemState() = ReminderSystemState(notifications.canPost(), alarms.canScheduleExact())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
