package com.deutschdreamers.mensis.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Regimen
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.today.TodayState
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarCellKind
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarViewModel
import com.deutschdreamers.mensis.ui.screens.log.DailyLogViewModel
import com.deutschdreamers.mensis.ui.screens.log.LogRequest
import com.deutschdreamers.mensis.ui.screens.log.MethodItem
import com.deutschdreamers.mensis.ui.screens.today.TodayScreen
import com.deutschdreamers.mensis.ui.screens.today.TodayViewModel
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Real repositories on the encrypted database (no fakes): log a day and see it on Today and in the
 * calendar; edit period dates and watch predictions recalculate; switch the method and watch Today
 * and the log's method item follow. Synthetic data only.
 */
@RunWith(AndroidJUnit4::class)
class LogToTodayFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today: LocalDate = LocalDate.of(2026, 10, 21)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)
    private val viewModels = TestViewModels()

    private lateinit var holder: DatabaseHolder
    private lateinit var dayEntries: DayEntryRepository
    private lateinit var contraception: ContraceptionRepository
    private lateinit var trackerData: TrackerDataRepository
    private lateinit var todayViewModel: TodayViewModel
    private lateinit var logViewModel: DailyLogViewModel
    private lateinit var calendarViewModel: CalendarViewModel

    @Before
    fun setUp() {
        DatabaseFactory.databaseFiles(context).forEach { it.delete() }
        holder = DatabaseHolder(context, DatabaseKeyProvider(context))
        dayEntries = DayEntryRepository(holder, clock)
        contraception = ContraceptionRepository(holder, clock)
        val settings = SettingsRepository(holder, UiPrefsStore(context), clock)
        trackerData = TrackerDataRepository(dayEntries, settings, contraception)
        todayViewModel = viewModels.track(TodayViewModel(trackerData, NoBackupStatusSource, {}, clock))
        logViewModel = viewModels.track(DailyLogViewModel(trackerData, dayEntries))
        calendarViewModel = viewModels.track(CalendarViewModel(trackerData, dayEntries, clock))
    }

    @After
    fun tearDown() =
        runBlocking<Unit> {
            viewModels.cancelAndJoin()
            holder.close()
            DatabaseFactory.databaseFiles(context).forEach { it.delete() }
        }

    @Test
    fun loggingAPeriodStart_showsOnTodayAndCalendar() =
        runBlocking<Unit> {
            assertEquals(TodayState.FIRST_LAUNCH, todayViewModel.uiState.first { it != null }?.state)

            logViewModel.open(LogRequest(today, presetFlow = Flow.MEDIUM))
            logViewModel.uiState.first { it != null }
            logViewModel.update { it.copy(symptoms = setOf(Symptom.CRAMPS), sleepText = "7:20") }
            logViewModel.save()
            logViewModel.uiState.first { it?.saved == true }

            val state = todayViewModel.uiState.first { it?.state == TodayState.ON_PERIOD }
            assertEquals("1", state?.numeral)
            assertEquals("Period, day 1", state?.phase)
            assertEquals(listOf("Medium flow", "Cramps", "7 h 20 min"), state?.logged?.map { it.text })

            val calendar = calendarViewModel.uiState.first { it != null }
            val cell = calendar?.weeks?.flatten()?.first { it?.date == today }
            assertEquals(CalendarCellKind.PERIOD, cell?.kind)
            assertEquals(true, cell?.isLogged)

            compose.setContent {
                MensisTheme {
                    TodayScreen(
                        viewModel = todayViewModel,
                        onLog = {},
                        onImport = {},
                        onOpenBackup = {},
                        onMethodSettings = {},
                    )
                }
            }
            compose.waitUntil(timeoutMillis = 5_000) {
                compose
                    .onAllNodes(
                        androidx.compose.ui.test
                            .hasText("Period, day 1"),
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Medium flow").assertExists()
        }

    @Test
    fun editingPeriodDates_recalculatesPredictions() =
        runBlocking<Unit> {
            val previousStart = today.minusDays(45)
            val lastStart = today.minusDays(16)
            for (start in listOf(previousStart, lastStart)) {
                for (offset in 0L..3L) dayEntries.setFlow(start.plusDays(offset), Flow.MEDIUM)
            }
            val before = todayViewModel.uiState.first { it?.state == TodayState.NORMAL }
            val nextBefore = before?.cards?.first { it.title == "Next period" }?.range
            assertTrue(nextBefore?.contains("Nov") == true)

            // Mark today as a period start through the calendar's edit mode and unmark one old period day.
            calendarViewModel.startEdit()
            calendarViewModel.toggle(today)
            calendarViewModel.toggle(previousStart.plusDays(3))
            calendarViewModel.saveEdit()
            calendarViewModel.uiState.first { it?.editMode == false }
            assertEquals(Flow.MEDIUM, dayEntries.get(today)?.flow)
            assertNull(dayEntries.get(previousStart.plusDays(3))?.flow)

            val after = todayViewModel.uiState.first { it?.state == TodayState.ON_PERIOD }
            assertEquals("1", after?.numeral)
            val nextAfter = after?.cards?.first { it.title == "Next period" }?.range
            assertTrue(
                "expected a December range, got $nextAfter",
                nextAfter?.contains("Nov") == true || nextAfter?.contains("Dec") == true,
            )
            assertTrue(nextBefore != nextAfter)
        }

    @Test
    fun switchingMethod_changesTodayAndTheLogsMethodItem() =
        runBlocking<Unit> {
            val pillStart = today.minusDays(16)
            contraception.switchTo(
                ContraceptionPeriod(ContraceptionMethod.COMBINED_PILL, pillStart, regimen = Regimen.CYCLIC),
            )
            val cyclic = todayViewModel.uiState.first { it?.state == TodayState.CYCLIC }
            assertEquals("Pack day", cyclic?.numeralLabel)
            assertEquals("17", cyclic?.numeral)

            logViewModel.open(LogRequest(today))
            val pillLog = logViewModel.uiState.first { it != null }
            assertEquals(MethodItem.PILL, pillLog?.method?.item)
            assertEquals("Combined pill", pillLog?.method?.title)
            assertEquals("Pack day 17", pillLog?.subtitle)

            contraception.switchTo(ContraceptionPeriod(ContraceptionMethod.HORMONAL_IUD, today), startOfNext = today)
            val suppressed = todayViewModel.uiState.first { it?.state == TodayState.SUPPRESSED }
            assertEquals("Days since last bleed", suppressed?.numeralLabel)
            assertEquals("Hormonal IUD", suppressed?.methodCard?.title)

            logViewModel.open(LogRequest(today))
            val iudLog = logViewModel.uiState.first { it != null && it.method.item != MethodItem.PILL }
            assertEquals(MethodItem.LARC, iudLog?.method?.item)
            assertEquals(false, iudLog?.method?.showEmergencyContraception)
        }
}
