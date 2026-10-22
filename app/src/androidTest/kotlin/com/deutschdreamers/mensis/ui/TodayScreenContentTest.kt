package com.deutschdreamers.mensis.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.ui.preview.FakeData
import com.deutschdreamers.mensis.ui.screens.log.LogRequest
import com.deutschdreamers.mensis.ui.screens.today.TodayScreenContent
import com.deutschdreamers.mensis.ui.screens.today.TodayUiState
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayScreenContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var logged: LogRequest? = null

    private fun show(state: TodayUiState) {
        compose.setContent {
            MensisTheme {
                TodayScreenContent(
                    state = state,
                    onLog = { logged = it },
                    onImport = {},
                    onBackupAction = {},
                    onMethodSettings = {},
                )
            }
        }
    }

    @Test
    fun normalState_showsHeaderCardsAndAnnouncesStripCells() {
        show(FakeData.normal())
        compose.onNodeWithText("Luteal phase").assertIsDisplayed()
        compose.onNodeWithText("Next period").assertIsDisplayed()
        compose.onNodeWithText("Fertile window").assertIsDisplayed()
        // The extended FAB keeps its label in an animated child, so it is matched on the unmerged tree.
        compose.onNodeWithText("Log today", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("cycle day 17, today", substring = true)).assertExists()
        compose.onNode(hasContentDescription("cycle day 1, period", substring = true)).assertExists()
        compose.onNodeWithText("36.71 °C · 06:40").assertExists()
    }

    @Test
    fun lateState_offersPregnancyTestFirst() {
        show(FakeData.late())
        compose.onNodeWithText("Period not logged yet").assertIsDisplayed()
        compose.onNodeWithText("Log a pregnancy test").performClick()
        assertEquals(true, logged?.testsFirst)
        compose.onNodeWithText("Period started").performClick()
        assertEquals(Flow.MEDIUM, logged?.presetFlow)
    }

    @Test
    fun firstLaunch_offersPeriodStartedToday() {
        show(FakeData.firstLaunch())
        compose.onNodeWithText("No cycle history yet").assertIsDisplayed()
        compose.onNodeWithText("Nothing logged yet. A typical entry takes about ten seconds.").assertExists()
        compose.onNodeWithText("Period started today").performClick()
        assertEquals(Flow.MEDIUM, logged?.presetFlow)
        assertEquals(FakeData.firstStart, logged?.date)
    }

    @Test
    fun suppressedState_showsMethodCard() {
        show(FakeData.suppressed())
        compose.onNodeWithText("Predictions paused").assertIsDisplayed()
        compose.onNodeWithText("Hormonal IUD").assertIsDisplayed()
        compose.onNodeWithText("Method settings").assertIsDisplayed()
    }
}
