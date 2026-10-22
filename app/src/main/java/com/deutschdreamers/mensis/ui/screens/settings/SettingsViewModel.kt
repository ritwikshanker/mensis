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
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.model.activeOn
import com.deutschdreamers.mensis.lock.DeviceCredentialChecker
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
    val modeSummary: String,
    val trackSummary: String,
    val contraceptionSummary: String,
    val unitsSummary: String,
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
            ) { prefs, tracker, periods, status ->
                val method = periods.activeOn(LocalDate.now(clock))
                SettingsUiState(
                    modeSummary = modeSummary(tracker),
                    trackSummary = trackSummary(tracker.trackingLevel),
                    contraceptionSummary =
                        MethodLabels.summary(
                            method?.method ?: ContraceptionMethod.NONE,
                            method?.regimen,
                        ),
                    unitsSummary = "${Units.temperatureSymbol(
                        tracker.temperatureUnit,
                    )} $MIDDLE_DOT ${Units.weightSymbol(tracker.weightUnit)}",
                    lockEnabled = prefs.lockEnabled,
                    lockAvailable = credentials.canAuthenticate(),
                    hideInRecents = prefs.hideInRecents,
                    relockTimeoutMinutes = prefs.relockTimeoutMinutes,
                    backupSummary = backupSummary(status),
                    themeSummary = themeSummary(prefs),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun setLockEnabled(enabled: Boolean) = updatePrefs { it.copy(lockEnabled = enabled) }

        fun setHideInRecents(enabled: Boolean) = updatePrefs { it.copy(hideInRecents = enabled) }

        fun setRelockTimeout(minutes: Int) = updatePrefs { it.copy(relockTimeoutMinutes = minutes) }

        private fun updatePrefs(transform: (UiPrefs) -> UiPrefs) {
            viewModelScope.launch { settings.updateUiPrefs(transform) }
        }

        private fun modeSummary(tracker: TrackerSettings): String =
            when (tracker.mode) {
                TrackerMode.CYCLE -> "Cycle tracking"
                TrackerMode.PREGNANCY -> "Pregnancy $MIDDLE_DOT predictions paused"
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

        private fun themeSummary(prefs: UiPrefs): String {
            val scheme =
                when (prefs.theme) {
                    ThemeScheme.ROSE -> "Rose"
                    ThemeScheme.SAGE -> "Sage"
                    ThemeScheme.DYNAMIC -> "System colours"
                }
            val appearance =
                when (prefs.appearance) {
                    AppearanceMode.LIGHT -> "Light"
                    AppearanceMode.DARK -> "Dark"
                    AppearanceMode.SYSTEM -> "System"
                }
            return "$scheme $MIDDLE_DOT $appearance"
        }

        companion object {
            private const val STOP_TIMEOUT_MS = 5_000L

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
