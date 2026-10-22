package com.deutschdreamers.mensis.data.repo

import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.prediction.PredictionInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the engine reads, as one stream. Cycles and predictions are derived from it, never stored. */
data class TrackerData(
    val entries: List<DayEntry>,
    val settings: TrackerSettings,
    val contraception: List<ContraceptionPeriod>,
) {
    fun inputFor(today: LocalDate): PredictionInput = PredictionInput(entries, settings, today, contraception)
}

@Singleton
class TrackerDataRepository
    @Inject
    constructor(
        private val dayEntries: DayEntryRepository,
        private val settings: SettingsRepository,
        private val contraception: ContraceptionRepository,
    ) {
        fun observe(): Flow<TrackerData> =
            combine(dayEntries.observeAll(), settings.observeTracker(), contraception.observePeriods()) { e, s, c ->
                TrackerData(e, s, c)
            }
    }
