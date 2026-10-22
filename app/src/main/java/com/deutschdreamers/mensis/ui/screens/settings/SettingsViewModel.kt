package com.deutschdreamers.mensis.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.backup.BackupStatus
import com.deutschdreamers.mensis.data.backup.BackupStatusSource
import com.deutschdreamers.mensis.data.repo.ContraceptionRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.data.settings.UiPrefs
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.model.WeightUnit
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.domain.reminders.ReminderCatalog
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.lock.DeviceCredentialChecker
import com.deutschdreamers.mensis.notifications.ReminderTexts
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.format.MethodLabels
import com.deutschdreamers.mensis.ui.format.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class SettingsUiState(
    val pregnancyMode: Boolean,
    val modeSummary: String,
    val trackSummary: String,
    val contraceptionSummary: String,
    val unitsSummary: String,
    val remindersSummary: String,
    val lockEnabled: Boolean,
    val lockAvailable: Boolean,
    val hideInRecents: Boolean,
    val relockTimeoutMinutes: Int,
    val backupSummary: String,
    val themeSummary: String,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
        contraception: ContraceptionRepository,
        backup: BackupStatusSource,
        private val credentials: DeviceCredentialChecker,
        private val clock: Clock,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState?> =
            combine(
                settings.uiPrefs,
                settings.observeTracker(),
                contraception.observePeriods(),
                backup.status,
                settings.observeReminders(),
            ) { prefs, tracker, periods, status, reminders ->
                val method = periods.activeOn(LocalDate.now(clock))
                SettingsUiState(
                    pregnancyMode = tracker.mode == TrackerMode.PREGNANCY,
                    modeSummary = modeSummary(tracker.mode),
                    trackSummary = trackSummary(tracker.trackingLevel),
                    contraceptionSummary =
                        MethodLabels.summary(
                            method?.method ?: ContraceptionMethod.NONE,
                            method?.regimen,
                        ),
                    unitsSummary = unitsSummary(tracker.temperatureUnit, tracker.weightUnit),
                    remindersSummary = remindersSummary(reminders, method, tracker.trackingLevel),
                    lockEnabled = prefs.lockEnabled,
                    lockAvailable = credentials.canAuthenticate(),
                    hideInRecents = prefs.hideInRecents,
                    relockTimeoutMinutes = prefs.relockTimeoutMinutes,
                    backupSummary = backupSummary(status),
                    themeSummary = themeSummary(prefs.theme, prefs.appearance),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun setLockEnabled(enabled: Boolean) = updatePrefs { it.copy(lockEnabled = enabled) }

        fun setHideInRecents(enabled: Boolean) = updatePrefs { it.copy(hideInRecents = enabled) }

        fun setRelockTimeout(minutes: Int) = updatePrefs { it.copy(relockTimeoutMinutes = minutes) }

        private fun updatePrefs(transform: (UiPrefs) -> UiPrefs) {
            viewModelScope.launch { settings.updateUiPrefs(transform) }
        }

        /** Only the reminders that apply to the current method and tracking level count. */
        private fun remindersSummary(
            config: ReminderConfig,
            method: ContraceptionPeriod?,
            level: TrackingLevel,
        ): String {
            val enabled = ReminderCatalog.available(method, level).filter { config.setting(it).enabled }
            return ReminderTexts.summary(enabled, method)
        }

        private fun backupSummary(status: BackupStatus): String =
            when (status) {
                BackupStatus.Off -> "Off"
                BackupStatus.Unavailable -> "Not available"
                is BackupStatus.Idle ->
                    "Encrypted $MIDDLE_DOT Google Drive" +
                        (
                            status.lastSuccessAt?.let {
                                " $MIDDLE_DOT last ${Formats.dayMonth(
                                    it.atZone(ZoneId.systemDefault()).toLocalDate(),
                                )}"
                            }
                                ?: ""
                        )
                is BackupStatus.Failing -> "Last backup failed"
                is BackupStatus.Overdue -> "Overdue"
                BackupStatus.NeedsReconnect -> "Needs reconnecting"
            }

        companion object {
            private const val STOP_TIMEOUT_MS = 5_000L

            fun modeSummary(mode: TrackerMode): String =
                when (mode) {
                    TrackerMode.CYCLE -> "Cycle tracking"
                    TrackerMode.PREGNANCY -> "Pregnancy $MIDDLE_DOT predictions paused"
                }

            fun unitsSummary(
                temperature: TemperatureUnit,
                weight: WeightUnit,
            ): String = "${Units.temperatureSymbol(temperature)} $MIDDLE_DOT ${Units.weightSymbol(weight)}"

            /** Design §7: "Rose · Light". */
            fun themeSummary(
                scheme: ThemeScheme,
                appearance: AppearanceMode,
            ): String {
                val name =
                    when (scheme) {
                        ThemeScheme.ROSE -> "Rose"
                        ThemeScheme.SAGE -> "Sage"
                        ThemeScheme.DYNAMIC -> "System colours"
                    }
                val mode =
                    when (appearance) {
                        AppearanceMode.LIGHT -> "Light"
                        AppearanceMode.DARK -> "Dark"
                        AppearanceMode.SYSTEM -> "System"
                    }
                return "$name $MIDDLE_DOT $mode"
            }

            fun trackSummary(level: TrackingLevel): String =
                when (level) {
                    TrackingLevel.PERIOD -> "Period only"
                    TrackingLevel.SYMPTOMS -> "Period and symptoms"
                    TrackingLevel.FULL -> "Everything, incl. temperature"
                }

            /** Design: 0 / 1 / 5 / 15 minutes / never. */
            val RELOCK_OPTIONS: List<Int> = listOf(0, 1, 5, 15, UiPrefs.RELOCK_NEVER)

            fun relockLabel(minutes: Int): String =
                when {
                    minutes == 0 -> "Immediately"
                    minutes < 0 -> "Never"
                    minutes == 1 -> "After 1 minute"
                    else -> "After $minutes minutes"
                }
        }
    }
