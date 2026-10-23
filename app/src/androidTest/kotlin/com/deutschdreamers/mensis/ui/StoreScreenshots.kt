package com.deutschdreamers.mensis.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deutschdreamers.mensis.BuildConfig
import com.deutschdreamers.mensis.backup.BackupConfig
import com.deutschdreamers.mensis.backup.ManualBackupState
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.data.backup.BackupStatus
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.model.WeightUnit
import com.deutschdreamers.mensis.ui.preview.FakeData
import com.deutschdreamers.mensis.ui.screens.about.AboutContent
import com.deutschdreamers.mensis.ui.screens.backup.BackupActions
import com.deutschdreamers.mensis.ui.screens.backup.BackupContent
import com.deutschdreamers.mensis.ui.screens.backup.BackupDraft
import com.deutschdreamers.mensis.ui.screens.backup.BackupUiState
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarMonthBuilder
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarScreenContent
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarSelection
import com.deutschdreamers.mensis.ui.screens.insights.InsightsScreenContent
import com.deutschdreamers.mensis.ui.screens.log.DailyLogContent
import com.deutschdreamers.mensis.ui.screens.log.LogDraft
import com.deutschdreamers.mensis.ui.screens.log.LogMethodUi
import com.deutschdreamers.mensis.ui.screens.log.LogUiState
import com.deutschdreamers.mensis.ui.screens.log.MethodItem
import com.deutschdreamers.mensis.ui.screens.main.MainNavigationBar
import com.deutschdreamers.mensis.ui.screens.main.MainTab
import com.deutschdreamers.mensis.ui.screens.today.TodayScreenContent
import com.deutschdreamers.mensis.ui.screens.today.TodayUiState
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Draws the store and README screenshots from the real screens with the synthetic [FakeData], 1080 × 1920
 * pixels each. Skipped unless the instrumentation argument `screenshots` is `true`. Run it with `am instrument`
 * on one device (CONTRIBUTING.md, "Store screenshots"): `connectedDebugAndroidTest` uninstalls the app
 * afterwards, which deletes the PNGs, and runs on every attached device.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshots {
    @get:Rule
    val compose = createComposeRule()

    private val day = FakeData.lastStart.plusDays(16)

    @Before
    fun onlyWhenAsked() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("screenshots") == "true")
    }

    @Test
    fun today() = capture("1_today") { WithTabs(MainTab.TODAY) { Today(todayState()) } }

    @OptIn(ExperimentalMaterial3Api::class) // the sheet's drag handle
    @Test
    fun dailyLog() =
        capture("2_daily_log") {
            Box(modifier = Modifier.fillMaxSize()) {
                WithTabs(MainTab.TODAY) { Today(todayState()) }
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)))
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 560.dp),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                        DailyLogContent(
                            state = logState(),
                            expanded = false,
                            onExpand = {},
                            onCollapse = {},
                            onUpdate = {},
                            onCancel = {},
                            onSave = {},
                        )
                    }
                }
            }
        }

    @Test
    fun calendar() =
        capture("3_calendar") {
            val data = FakeData.data(FakeData.regularHistory() + FakeData.todayEntry(day))
            WithTabs(MainTab.CALENDAR) {
                CalendarScreenContent(
                    state = CalendarMonthBuilder.build(data, day, CalendarSelection(YearMonth.from(day), day)),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayTap = {},
                    onStartEdit = {},
                    onCancelEdit = {},
                    onSaveEdit = {},
                    onEditDay = {},
                )
            }
        }

    @Test
    fun insights() = capture("4_insights") { WithTabs(MainTab.INSIGHTS) { InsightsScreenContent(FakeData.insights()) } }

    @Test
    fun backup() =
        capture("5_backup") {
            BackupContent(
                state =
                    BackupUiState(
                        BackupAvailability.AVAILABLE,
                        BackupConfig(
                            enabled = true,
                            accountEmail = "someone@example.com",
                            lastSuccessAt = Instant.parse("2026-10-20T23:14:00Z"),
                            lastSuccessBytes = 48 * 1_024L,
                            lastSuccessDevice = "Pixel 9",
                        ),
                        ManualBackupState.None,
                        BackupDraft(),
                        LocalDate.of(2026, 10, 21),
                        ZoneOffset.UTC,
                    ),
                actions = BackupActions(),
            )
        }

    @Test
    fun about() =
        capture("6_about") {
            AboutContent(version = BuildConfig.VERSION_NAME, onOpenLink = {}, onNotices = {}, onBack = {})
        }

    @Test
    fun todayDark() =
        capture("7_today_dark", AppearanceMode.DARK) { WithTabs(MainTab.TODAY) { Today(onPeriodState()) } }

    // Backup on and healthy, so Today shows no banner.
    private val backedUp = BackupStatus.Idle(Instant.parse("2026-09-09T23:14:00Z"))

    private fun todayState() =
        FakeData.today(FakeData.data(FakeData.regularHistory() + FakeData.todayEntry(day)), day, backedUp)

    private fun onPeriodState() =
        FakeData.today(
            FakeData.data(FakeData.regularHistory(lastPeriodDays = 2)),
            FakeData.lastStart.plusDays(1),
            backedUp,
        )

    private fun logState() =
        LogUiState(
            draft =
                LogDraft(
                    date = day,
                    symptoms = setOf(Symptom.HEADACHE),
                    moods = setOf(Mood.CALM),
                    sleepText = "7:20",
                ),
            dateLabel = "Thu 10 Sep",
            subtitle = "Cycle day 17 · Luteal",
            trackingLevel = TrackingLevel.FULL,
            temperatureUnit = TemperatureUnit.CELSIUS,
            weightUnit = WeightUnit.KILOGRAM,
            method = LogMethodUi(MethodItem.NONE, "", "", showEmergencyContraception = true),
            testsFirst = false,
        )

    private fun capture(
        name: String,
        appearance: AppearanceMode = AppearanceMode.LIGHT,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            MensisTheme(appearance = appearance) {
                // Exactly 1080 × 1920 px whatever the device density; system bar insets are left out.
                Box(
                    modifier =
                        Modifier
                            .testTag(SHOT_TAG)
                            .layout { measurable, _ ->
                                val placeable = measurable.measure(Constraints.fixed(WIDTH_PX, HEIGHT_PX))
                                layout(WIDTH_PX, HEIGHT_PX) { placeable.place(0, 0) }
                            }.consumeWindowInsets(WindowInsets.systemBars)
                            .background(MaterialTheme.colorScheme.surface),
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        // Play wants screenshots without an alpha channel.
        val bitmap =
            compose
                .onNodeWithTag(SHOT_TAG)
                .captureToImage()
                .asAndroidBitmap()
                .copy(Bitmap.Config.ARGB_8888, true)
                .apply { setHasAlpha(false) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("screenshots"))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    private companion object {
        const val SHOT_TAG = "screenshot"
        const val WIDTH_PX = 1080
        const val HEIGHT_PX = 1920
        const val PNG_QUALITY = 100
        const val SCRIM_ALPHA = 0.32f
    }
}

/** Today with the same callbacks the previews use. */
@Composable
private fun Today(state: TodayUiState) {
    TodayScreenContent(state = state, onLog = {}, onImport = {}, onBackupAction = {}, onMethodSettings = {})
}

/** The tab screens as MainScreen lays them out: content above the bottom navigation bar. */
@Composable
private fun WithTabs(
    tab: MainTab,
    content: @Composable () -> Unit,
) {
    Scaffold(bottomBar = { MainNavigationBar(selected = tab, onSelect = {}) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) { content() }
    }
}
