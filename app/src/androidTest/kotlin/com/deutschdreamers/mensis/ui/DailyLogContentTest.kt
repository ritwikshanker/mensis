package com.deutschdreamers.mensis.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.model.WeightUnit
import com.deutschdreamers.mensis.ui.screens.log.DailyLogContent
import com.deutschdreamers.mensis.ui.screens.log.LogDraft
import com.deutschdreamers.mensis.ui.screens.log.LogMethodUi
import com.deutschdreamers.mensis.ui.screens.log.LogUiState
import com.deutschdreamers.mensis.ui.screens.log.MethodItem
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DailyLogContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val date = LocalDate.of(2026, 10, 21)

    private fun initialState(
        level: TrackingLevel = TrackingLevel.FULL,
        testsFirst: Boolean = false,
        method: LogMethodUi = LogMethodUi(MethodItem.NONE, "", "", showEmergencyContraception = true),
    ) = LogUiState(
        draft = LogDraft(date),
        dateLabel = "Wed 21 Oct",
        subtitle = "Cycle day 17 · Luteal",
        trackingLevel = level,
        temperatureUnit = TemperatureUnit.CELSIUS,
        weightUnit = WeightUnit.KILOGRAM,
        method = method,
        testsFirst = testsFirst,
    )

    private var state by mutableStateOf(initialState())
    private var saved = false
    private var cancelled = false

    private fun show(
        start: LogUiState,
        expanded: Boolean,
    ) {
        state = start
        compose.setContent {
            MensisTheme {
                DailyLogContent(
                    state = state,
                    expanded = expanded,
                    onExpand = {},
                    onCollapse = {},
                    onUpdate = { transform -> state = state.copy(draft = transform(state.draft)) },
                    onCancel = { cancelled = true },
                    onSave = { saved = true },
                )
            }
        }
    }

    @Test
    fun collapsedSheet_editsFlowAndSymptoms_andOffersMore() {
        show(initialState(), expanded = false)
        compose
            .onNodeWithText(
                "Nothing here is required. Save with as many or as few fields as you like.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Medium").performClick()
        assertEquals(Flow.MEDIUM, state.draft.flow)
        // The collapsed sheet scrolls; symptoms sit below the fold at the half-screen height.
        compose.onNodeWithText("Headache").performScrollTo().performClick()
        assertTrue(Symptom.HEADACHE in state.draft.symptoms)
        compose.onNodeWithText("Headache").performScrollTo().performClick()
        assertTrue(state.draft.symptoms.isEmpty())
        compose.onNodeWithText("Mucus, tests, sex, sleep, notes").assertExists()
        compose.onNodeWithText("Save").performClick()
        assertTrue(saved)
    }

    @Test
    fun periodOnlyLevel_hidesSymptomsTemperatureAndSleep() {
        show(initialState(level = TrackingLevel.PERIOD), expanded = true)
        compose.onNodeWithText("Flow").assertExists()
        compose.onNodeWithText("Symptoms").assertDoesNotExist()
        compose.onNodeWithText("Waking temperature").assertDoesNotExist()
        compose.onNodeWithText("Sleep").assertDoesNotExist()
        compose.onNodeWithText("Notes").performScrollTo().assertExists()
    }

    @Test
    fun expandedSheet_showsTestsSexAndMethodItem() {
        val pill =
            LogMethodUi(
                MethodItem.PILL,
                "Combined pill",
                "Late = more than 12 hours",
                showEmergencyContraception = true,
            )
        show(initialState(method = pill), expanded = true)
        compose.onNodeWithText("Cervical mucus").performScrollTo().assertExists()
        compose.onNodeWithText("Pregnancy test").performScrollTo().assertExists()
        compose.onNodeWithText("Combined pill").performScrollTo().assertExists()
        compose.onNodeWithText("Taken").performScrollTo().performClick()
        assertEquals(com.deutschdreamers.mensis.domain.model.PillState.TAKEN, state.draft.pillState)
        compose.onNodeWithText("Emergency contraception").performScrollTo().assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun lateState_putsThePregnancyTestFirst() {
        show(initialState(testsFirst = true), expanded = true)
        compose.onNodeWithText("A positive result pauses predictions and offers pregnancy mode.").assertIsDisplayed()
        // The highlighted pregnancy-test card comes first; the LH test further down has its own "Positive".
        compose.onAllNodesWithText("Positive").onFirst().performClick()
        assertEquals(com.deutschdreamers.mensis.domain.model.TestResult.POSITIVE, state.draft.pregnancyTest)
    }
}
