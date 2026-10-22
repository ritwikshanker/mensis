package com.deutschdreamers.mensis.ui.screens.contraception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.ContraceptionRecord
import com.deutschdreamers.mensis.data.repo.ContraceptionRepository
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.domain.contraception.MethodSchedule
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.format.Formats.MIDDLE_DOT
import com.deutschdreamers.mensis.ui.format.MethodLabels
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
        dayEntries: DayEntryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private var current: ContraceptionRecord? = null

        val uiState: StateFlow<ContraceptionUiState?> =
            combine(repository.observeRecords(), dayEntries.observeAll()) { records, entries ->
                val record = records.filter { it.period.endDate == null }.maxByOrNull { it.period.startDate }
                current = record
                val period = record?.period ?: ContraceptionPeriod(ContraceptionMethod.NONE, LocalDate.now(clock))
                val method = period.method
                val next =
                    when {
                        method == ContraceptionMethod.INJECTION ->
                            "Next injection" to
                                MethodSchedule.nextInjectionDate(period, entries)
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
                    reminderSummary = reminderSummary(period),
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

        private fun edit(transform: (ContraceptionPeriod) -> ContraceptionPeriod) {
            val record = current ?: return
            viewModelScope.launch { repository.save(transform(record.period), record.id) }
        }

        private fun reminderSummary(period: ContraceptionPeriod): String =
            when (period.method) {
                ContraceptionMethod.NONE -> "No method reminder"
                ContraceptionMethod.COMBINED_PILL, ContraceptionMethod.PROGESTIN_ONLY_PILL ->
                    "Pill $MIDDLE_DOT daily" + (period.dailyTime?.let { ", ${Formats.time(it)}" } ?: "")
                ContraceptionMethod.PATCH -> "Change patch $MIDDLE_DOT weekly"
                ContraceptionMethod.RING -> "Ring out, then in $MIDDLE_DOT every 3 weeks"
                ContraceptionMethod.INJECTION -> "Next injection $MIDDLE_DOT 12 weeks"
                ContraceptionMethod.IMPLANT -> "Implant replacement $MIDDLE_DOT 3 months before"
                ContraceptionMethod.HORMONAL_IUD, ContraceptionMethod.COPPER_IUD ->
                    "IUD replacement $MIDDLE_DOT 3 months before"
            }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
