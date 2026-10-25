package com.deutschdreamers.mensis.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.db.DatabaseFactory
import com.deutschdreamers.mensis.data.db.DatabaseHolder
import com.deutschdreamers.mensis.data.export.ExportedApp
import com.deutschdreamers.mensis.data.export.ExportedContraceptionPeriod
import com.deutschdreamers.mensis.data.export.ExportedDayEntry
import com.deutschdreamers.mensis.data.export.ExportedSettings
import com.deutschdreamers.mensis.data.export.MensisExport
import com.deutschdreamers.mensis.data.export.MensisJson
import com.deutschdreamers.mensis.data.importer.ConflictStrategy
import com.deutschdreamers.mensis.data.importer.ImportApplier
import com.deutschdreamers.mensis.data.importer.ImportReader
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.data.importer.ReadOutcome
import com.deutschdreamers.mensis.data.importer.RoomImportStore
import com.deutschdreamers.mensis.data.keys.DatabaseKeyProvider
import com.deutschdreamers.mensis.data.repo.ContraceptionRepository
import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.repo.TrackerDataRepository
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Goal
import com.deutschdreamers.mensis.domain.model.MethodKind
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import com.deutschdreamers.mensis.domain.today.TodayState
import com.deutschdreamers.mensis.domain.today.TodayStateResolver
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingAnswers
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Onboarding step 1's "Import from a file" on a real encrypted database: what the import leaves on the
 * phone decides which questions are skipped, and finishing onboarding keeps the imported settings.
 * Synthetic data only.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today: LocalDate = LocalDate.of(2026, 10, 25)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC)

    private lateinit var holder: DatabaseHolder
    private lateinit var dayEntries: DayEntryRepository
    private lateinit var settings: SettingsRepository
    private lateinit var trackerData: TrackerDataRepository
    private lateinit var answers: OnboardingAnswers

    @Before
    fun setUp() {
        DatabaseFactory.databaseFiles(context).forEach { it.delete() }
        holder = DatabaseHolder(context, DatabaseKeyProvider(context))
        dayEntries = DayEntryRepository(holder, clock)
        settings = SettingsRepository(holder, UiPrefsStore(context), clock)
        trackerData = TrackerDataRepository(dayEntries, settings, ContraceptionRepository(holder, clock))
        answers = OnboardingAnswers(settings, dayEntries)
    }

    @After
    fun tearDown() {
        holder.close()
        DatabaseFactory.databaseFiles(context).forEach { it.delete() }
    }

    /** What the view model observes: stored settings and whether any day is logged. */
    private suspend fun draftAfterImport(): OnboardingDraft =
        OnboardingDraft(
            hasSettings = settings.observeStored().first(),
            hasHistory = dayEntries.observeAll().first().isNotEmpty(),
        )

    private fun stamp(date: LocalDate): String = "${date}T08:00:00Z"

    private fun uuidOf(name: String): String = UUID.nameUUIDFromBytes(name.toByteArray()).toString()

    /** Four periods 29 days apart, the last one 6 days ago, with a hormonal IUD for the last three. */
    private fun mensisExport(): ByteArray {
        val starts = (3 downTo 0).map { today.minusDays(6L + it * 29L) }
        val days =
            starts.flatMap { start ->
                listOf("medium", "light", "light").mapIndexed { i, flow ->
                    val date = start.plusDays(i.toLong())
                    ExportedDayEntry(
                        id = uuidOf("day $date"),
                        date = date.toString(),
                        flow = flow,
                        createdAt = stamp(date),
                        updatedAt = stamp(date),
                    )
                }
            }
        val inserted = starts[1].minusDays(3)
        val export =
            MensisExport(
                schemaVersion = 1,
                exportedAt = stamp(today),
                app = ExportedApp("0.1.1", 2),
                settings =
                    ExportedSettings(
                        trackingLevel = "full",
                        goal = "trying_to_conceive",
                        mode = "cycle",
                        typicalCycleLength = 31,
                        typicalPeriodLength = 4,
                        temperatureUnit = "celsius",
                        weightUnit = "kilogram",
                        createdAt = stamp(starts[0]),
                        updatedAt = stamp(starts[0]),
                    ),
                contraceptionPeriods =
                    listOf(
                        ExportedContraceptionPeriod(
                            id = uuidOf("iud"),
                            method = "hormonal_iud",
                            startDate = inserted.toString(),
                            createdAt = stamp(inserted),
                            updatedAt = stamp(inserted),
                        ),
                    ),
                dayEntries = days,
            )
        return MensisJson.encode(export)
    }

    private suspend fun import(bytes: ByteArray) {
        val parsed = (ImportReader.read(ImportSource.CSV, bytes, today) as ReadOutcome.Parsed).parsed
        ImportApplier(RoomImportStore(holder), clock).apply(parsed, ConflictStrategy.MERGE)
    }

    @Test
    fun freshInstall_asksEveryQuestion_andFinishingWritesTheAnswers() =
        runBlocking<Unit> {
            val draft = draftAfterImport()
            assertFalse(draft.hasSettings)
            assertFalse(draft.hasHistory)
            assertEquals(1, draft.nextStep())

            val lastPeriod = today.minusDays(3)
            answers.save(
                draft.copy(cycleLength = 26, goal = Goal.AVOIDING_PREGNANCY, lastPeriodStart = lastPeriod),
                remindersAllowed = true,
            )
            val stored = settings.tracker()
            assertEquals(26, stored.typicalCycleLength)
            assertEquals(Goal.AVOIDING_PREGNANCY, stored.goal)
            assertTrue(settings.reminders().setting(ReminderType.PERIOD).enabled)
            assertEquals(Flow.MEDIUM, dayEntries.get(lastPeriod)?.flow)
        }

    @Test
    fun mensisExport_skipsToTheAppLock_andKeepsItsSettingsAndDays() =
        runBlocking<Unit> {
            import(mensisExport())
            val draft = draftAfterImport()
            assertTrue(draft.hasSettings)
            assertTrue(draft.hasHistory)
            assertEquals(OnboardingDraft.LOCK_STEP, draft.nextStep())

            // Answers left over from steps the import skipped must not reach the database.
            val days = dayEntries.all().size
            answers.save(
                draft.copy(cycleLength = 26, goal = Goal.AVOIDING_PREGNANCY, lastPeriodStart = today),
                remindersAllowed = true,
            )
            val stored = settings.tracker()
            assertEquals(31, stored.typicalCycleLength)
            assertEquals(4, stored.typicalPeriodLength)
            assertEquals(Goal.TRYING_TO_CONCEIVE, stored.goal)
            assertEquals(TrackingLevel.FULL, stored.trackingLevel)
            assertFalse(settings.reminders().setting(ReminderType.PERIOD).enabled)
            assertEquals(days, dayEntries.all().size)
            assertNull(dayEntries.get(today))

            val model = TodayStateResolver.resolve(trackerData.observe().first().inputFor(today))
            assertEquals(MethodKind.LOCAL_HORMONAL, model.methodKind)
            assertEquals(TodayState.NORMAL, model.state)
            assertEquals(7, model.numeral)
        }

    @Test
    fun daysWithoutSettings_skipOnlyTheLastPeriod_andWriteOnboardingsSettings() =
        runBlocking<Unit> {
            // A CSV, drip. or Clue import leaves days but no settings.
            val logged = today.minusDays(12)
            dayEntries.save(DayEntry(logged, flow = Flow.HEAVY))
            val draft = draftAfterImport()
            assertFalse(draft.hasSettings)
            assertTrue(draft.hasHistory)
            assertEquals(3, draft.copy(step = OnboardingDraft.HOW_IT_WORKS_STEP).nextStep())

            answers.save(draft.copy(cycleLength = 33, lastPeriodStart = today), remindersAllowed = false)
            assertEquals(33, settings.tracker().typicalCycleLength)
            assertFalse(settings.reminders().setting(ReminderType.PERIOD).enabled)
            assertNull(dayEntries.get(today))
            assertEquals(listOf(logged), dayEntries.all().map { it.date })
        }
}
