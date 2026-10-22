package com.deutschdreamers.mensis.ui

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.backup.NoBackupStatusSource
import com.deutschdreamers.mensis.data.db.DatabaseFactory
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.keys.DatabaseKeyProvider
import com.deutschdreamers.mensis.data.repo.ContraceptionRepository
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.TrackerSettings
import com.deutschdreamers.mensis.domain.model.WeightUnit
import com.deutschdreamers.mensis.domain.today.TodayState
import com.deutschdreamers.mensis.lock.DeviceCredentialChecker
import com.deutschdreamers.mensis.ui.screens.log.DailyLogViewModel
import com.deutschdreamers.mensis.ui.screens.log.LogRequest
import com.deutschdreamers.mensis.ui.screens.mode.ModeViewModel
import com.deutschdreamers.mensis.ui.screens.mode.PregnancyOffer
import com.deutschdreamers.mensis.ui.screens.settings.SettingsViewModel
import com.deutschdreamers.mensis.ui.screens.theme.ThemeViewModel
import com.deutschdreamers.mensis.ui.screens.today.TodayViewModel
import com.deutschdreamers.mensis.ui.screens.units.UnitsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Mode, Units and Theme write through their view models and come back in the Settings summaries,
 * the daily log and Today; a positive pregnancy test leads to pregnancy mode. Real encrypted
 * database, synthetic data only.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today: LocalDate = LocalDate.of(2026, 10, 21)
    private val lmp: LocalDate = LocalDate.of(2026, 8, 17)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)
    private val viewModels = TestViewModels()

    private lateinit var holder: DatabaseHolder
    private lateinit var dayEntries: DayEntryRepository
    private lateinit var settings: SettingsRepository
    private lateinit var trackerData: TrackerDataRepository

    @Before
    fun setUp() =
        runBlocking<Unit> {
            DatabaseFactory.databaseFiles(context).forEach { it.delete() }
            uiPrefs.clear()
            holder = DatabaseHolder(context, DatabaseKeyProvider(context))
            dayEntries = DayEntryRepository(holder, clock)
            val contraception = ContraceptionRepository(holder, clock)
            settings = SettingsRepository(holder, uiPrefs, clock)
            trackerData = TrackerDataRepository(dayEntries, settings, contraception)
        }

    @After
    fun tearDown() =
        runBlocking<Unit> {
            viewModels.cancelAndJoin()
            holder.close()
            DatabaseFactory.databaseFiles(context).forEach { it.delete() }
            uiPrefs.clear()
        }

    @Test
    fun modeUnitsAndTheme_showUpInTheSettingsSummaries() =
        runBlocking<Unit> {
            dayEntries.save(DayEntry(lmp, flow = Flow.MEDIUM))
            val settingsViewModel =
                viewModels.track(
                    SettingsViewModel(
                        settings,
                        ContraceptionRepository(holder, clock),
                        NoBackupStatusSource,
                        DeviceCredentialChecker(context),
                        clock,
                    ),
                )
            val first = settingsViewModel.uiState.awaitNotNull()
            assertEquals("Cycle tracking", first.modeSummary)
            assertEquals("°C · kg", first.unitsSummary)
            assertEquals("Rose · System", first.themeSummary)

            val mode = viewModels.track(ModeViewModel(trackerData, settings, clock))
            assertEquals(lmp, mode.uiState.awaitNotNull().suggestedLmp)
            mode.setPregnancy(lmp)
            val units = viewModels.track(UnitsViewModel(settings))
            units.setTemperature(TemperatureUnit.FAHRENHEIT)
            units.setWeight(WeightUnit.POUND)
            val theme = viewModels.track(ThemeViewModel(settings))
            theme.setScheme(ThemeScheme.SAGE)
            theme.setAppearance(AppearanceMode.DARK)

            val changed =
                settingsViewModel.uiState.awaitMatching {
                    it.pregnancyMode && it.unitsSummary == "°F · lb" && it.themeSummary == "Sage · Dark"
                }
            assertEquals("Pregnancy · predictions paused", changed.modeSummary)
            assertEquals(lmp, settings.tracker().pregnancyLmp)

            mode.setCycle()
            settingsViewModel.uiState.awaitMatching { it.modeSummary == "Cycle tracking" }
            assertNull(settings.tracker().pregnancyLmp)
        }

    @Test
    fun fahrenheitEntry_isStoredInCelsius() =
        runBlocking<Unit> {
            viewModels.track(UnitsViewModel(settings)).setTemperature(TemperatureUnit.FAHRENHEIT)
            awaitTracker { it.temperatureUnit == TemperatureUnit.FAHRENHEIT }

            val log = viewModels.track(DailyLogViewModel(trackerData, dayEntries))
            log.open(LogRequest(today))
            assertEquals(TemperatureUnit.FAHRENHEIT, log.uiState.awaitNotNull().temperatureUnit)
            log.update { it.copy(temperatureText = "97.70") }
            log.save()
            log.uiState.awaitMatching { it.saved }

            assertEquals(36.5, requireNotNull(dayEntries.get(today)?.temperatureC), 0.001)
        }

    @Test
    fun positivePregnancyTest_offersPregnancyMode_andTodayFollows() =
        runBlocking<Unit> {
            dayEntries.save(DayEntry(lmp, flow = Flow.MEDIUM))
            val log = viewModels.track(DailyLogViewModel(trackerData, dayEntries))
            log.open(LogRequest(today, testsFirst = true))
            log.uiState.awaitNotNull()
            log.update { it.copy(pregnancyTest = TestResult.POSITIVE) }
            log.save()
            val saved = log.uiState.awaitMatching { it.saved }
            assertEquals(PregnancyOffer(today, lmp), saved.pregnancyOffer)

            // Saving the same positive test again does not ask again.
            log.open(LogRequest(today))
            log.uiState.awaitNotNull()
            log.save()
            assertNull(log.uiState.awaitMatching { it.saved }.pregnancyOffer)

            viewModels.track(ModeViewModel(trackerData, settings, clock)).setPregnancy(lmp)
            awaitTracker { it.mode == TrackerMode.PREGNANCY }
            val todayViewModel = viewModels.track(TodayViewModel(trackerData, NoBackupStatusSource, {}, clock))
            val state = todayViewModel.uiState.awaitMatching { it.state == TodayState.PREGNANCY }
            assertEquals("10", state.numeral)
            assertEquals("Week 10, day 3", state.phase)
        }

    private suspend fun awaitTracker(predicate: (TrackerSettings) -> Boolean) {
        withTimeout(TIMEOUT_MS) { settings.observeTracker().first(predicate) }
    }

    private suspend fun <T : Any> StateFlow<T?>.awaitNotNull(): T =
        withTimeout(TIMEOUT_MS) { requireNotNull(first { it != null }) }

    private suspend fun <T : Any> StateFlow<T?>.awaitMatching(predicate: (T) -> Boolean): T =
        withTimeout(TIMEOUT_MS) { requireNotNull(first { it != null && predicate(it) }) }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        // A store of its own: the app's singletons keep the real preferences file open in this process.
        val uiPrefs: UiPrefsStore by lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            UiPrefsStore(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
                    context.preferencesDataStoreFile("test_settings_round_trip_prefs")
                },
            )
        }
    }
}
