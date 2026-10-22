package com.deutschdreamers.mensis.ui.screens.contraception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.ContraceptionRecord
import com.deutschdreamers.mensis.data.repo.ContraceptionRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.repo.TrackerData
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.domain.contraception.MethodSchedule
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.reminders.ReminderCatalog
import com.deutschdreamers.mensis.domain.reminders.ReminderConfig
import com.deutschdreamers.mensis.notifications.ReminderTexts
import com.deutschdreamers.mensis.ui.format.MethodLabels
import com.deutschdreamers.mensis.ui.screens.reminders.RemindersUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ContraceptionUiState(
    val method: ContraceptionMethod,
    val kind: MethodKind,
    val regimen: Regimen,
    val startDate: LocalDate,
    val dailyTime: LocalTime?,
    val isDailyPill: Boolean,
    val isLongActing: Boolean,
    val isCyclic: Boolean,
    val nextLabel: String,
    val nextDate: LocalDate?,
    val checkupDate: LocalDate?,
    val effect: String,
    val reminderSummary: String,
)

@HiltViewModel
class ContraceptionViewModel
    @Inject
    constructor(
        private val repository: ContraceptionRepository,
        trackerData: TrackerDataRepository,
        settings: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private var current: ContraceptionRecord? = null

        val uiState: StateFlow<ContraceptionUiState?> =
            combine(
                repository.observeRecords(),
                trackerData.observe(),
                settings.observeReminders(),
            ) { records, data, reminders ->
                val record = records.filter { it.period.endDate == null }.maxByOrNull { it.period.startDate }
                current = record
                val period = record?.period ?: ContraceptionPeriod(ContraceptionMethod.NONE, LocalDate.now(clock))
                val method = period.method
                val next =
                    when {
                        method == ContraceptionMethod.INJECTION ->
                            "Next injection" to
                                MethodSchedule.nextInjectionDate(period, data.entries)
                        method.isLongActing -> "Replace by" to MethodSchedule.replaceByDate(period)
                        else -> "" to null
                    }
                ContraceptionUiState(
                    method = method,
                    kind = period.kind,
                    regimen = period.regimen ?: Regimen.CYCLIC,
                    startDate = period.startDate,
                    dailyTime = period.dailyTime,
                    isDailyPill = method.isDailyPill,
                    isLongActing = method.isLongActing,
                    isCyclic = method.baseKind == MethodKind.CYCLIC,
                    nextLabel = next.first,
                    nextDate = next.second,
                    checkupDate = period.checkupDate,
                    effect = MethodLabels.effect(period.kind),
                    reminderSummary = reminderSummary(record?.period, reminders, data),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** Switching methods ends the current period yesterday and starts the new one today; history is kept. */
        fun selectMethod(method: ContraceptionMethod) {
            val existing = current?.period
            if (existing?.method == method) return
            val today = LocalDate.now(clock)
            val period =
                ContraceptionPeriod(
                    method = method,
                    startDate = today,
                    regimen = if (method.baseKind == MethodKind.CYCLIC) Regimen.CYCLIC else null,
                )
            viewModelScope.launch { repository.switchTo(period, startOfNext = today) }
        }

        fun setRegimen(regimen: Regimen) = edit { it.copy(regimen = regimen) }

        fun setStartDate(date: LocalDate) = edit { it.copy(startDate = date) }

        fun setDailyTime(time: LocalTime?) = edit { it.copy(dailyTime = time) }

        fun setReplaceByDate(date: LocalDate?) = edit { it.copy(replaceByDate = date) }

        fun setCheckupDate(date: LocalDate?) = edit { it.copy(checkupDate = date) }

        /** "Pill · Daily · 21:00", "Pill · Off" or "No method reminder". */
        private fun reminderSummary(
            period: ContraceptionPeriod?,
            reminders: ReminderConfig,
            data: TrackerData,
        ): String {
            val resolved =
                ReminderCatalog
                    .methodType(
                        period?.method,
                    )?.let { ReminderCatalog.resolve(it, reminders, period) }
            return ReminderTexts.methodSummary(
                resolved,
                period,
                resolved?.let { RemindersUi.scheduleDate(it, period, data) },
            )
        }

        private fun edit(transform: (ContraceptionPeriod) -> ContraceptionPeriod) {
            val record = current ?: return
            viewModelScope.launch { repository.save(transform(record.period), record.id) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
