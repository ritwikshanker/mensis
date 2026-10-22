package com.deutschdreamers.mensis.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.report.ReportBuilder
import com.deutschdreamers.mensis.report.ReportOptions
import com.deutschdreamers.mensis.report.ReportRange
import com.deutschdreamers.mensis.report.ReportSection
import com.deutschdreamers.mensis.ui.preview.FakeData
import com.deutschdreamers.mensis.ui.screens.report.ReportActions
import com.deutschdreamers.mensis.ui.screens.report.ReportContent
import com.deutschdreamers.mensis.ui.screens.report.ReportPreview
import com.deutschdreamers.mensis.ui.screens.report.ReportStep
import com.deutschdreamers.mensis.ui.screens.report.ReportUiState
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(ReportUiState(hasEntries = true))
    private val calls = mutableListOf<String>()

    private val actions =
        ReportActions(
            onBack = { calls += "back" },
            onRange = { state = state.copy(options = state.options.copy(range = it)) },
            onToggle = { section ->
                val sections = state.options.sections
                state =
                    state.copy(
                        options =
                            state.options.copy(
                                sections =
                                    if (section in
                                        sections
                                    ) {
                                        sections - section
                                    } else {
                                        sections + section
                                    },
                            ),
                    )
            },
            onPreview = { calls += "preview" },
            onEdit = { calls += "edit" },
            onCreate = { calls += "create" },
            onShare = { calls += "share" },
            onSave = { calls += "save" },
            onDiscard = { calls += "discard" },
        )

    private fun show() = compose.setContent { MensisTheme { ReportContent(state, actions) } }

    private fun preview(range: ReportRange = ReportRange.TWELVE_MONTHS): ReportPreview {
        val model =
            ReportBuilder.build(
                FakeData.data(FakeData.insightsHistory()),
                ReportOptions(range, ReportSection.DEFAULTS),
                FakeData.insightsToday,
                appVersion = "1.0.0",
            )
        return ReportPreview(
            model,
            pageCount = 3,
            bytes = 140 * 1024L,
            footer = "Page 1 of 3 · BBT charts on pages 2–3",
        )
    }

    @Test
    fun setup_ticksTheFirstFourSections_andNeedsDataAndASection() {
        show()
        compose.onNodeWithText("Period covered").assertExists()
        compose.onNodeWithText("12 months").assertExists()
        for (label in listOf("Cycle summary", "Cycle and period lengths", "Symptoms", "BBT chart")) {
            compose.onNodeWithText(label).assertIsOn()
        }
        compose.onNodeWithText("Medication").assertIsOff()
        compose.onNodeWithText("Notes").assertIsOff()
        compose
            .onNodeWithText(
                "The PDF is built on the phone and only leaves it when you share it. Free-text notes are left out " +
                    "unless you tick them.",
            ).assertExists()

        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("Notes").assertIsOn()
        compose.onNodeWithText("Last 6 cycles").performClick()
        assertEquals(ReportRange.LAST_6_CYCLES, state.options.range)

        compose
            .onNodeWithText("Preview")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(listOf("preview"), calls)

        state = state.copy(options = state.options.copy(sections = emptySet()))
        compose.onNodeWithText("Preview").performScrollTo().assertIsNotEnabled()
        state = state.copy(hasEntries = false, options = ReportOptions())
        compose.onNodeWithText("Preview").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Nothing is logged yet, so there is nothing to report.").assertExists()
    }

    @Test
    fun preview_showsThePaperCardWithTheDesignNumbers() {
        state =
            ReportUiState(step = ReportStep.PREVIEW, hasEntries = true, preview = preview(ReportRange.LAST_6_CYCLES))
        show()
        compose.onNodeWithText("Cycle report").assertExists()
        compose.onNodeWithText("Prepared 21 Oct 2026").assertExists()
        compose.onNodeWithText("Apr – Oct 2026 · Logged with Mensis 1.0.0 · Not a medical diagnosis").assertExists()
        compose.onNodeWithText("Cycles logged").assertExists()
        compose.onNodeWithText("29.2 d (27–32)").assertExists()
        compose.onNodeWithText("Contraception").assertExists()
        compose.onNodeWithText("None recorded").assertExists()
        compose
            .onNode(
                hasContentDescription("Cycle lengths, oldest first: 28, 30, 27, 32, 29, 29 days.", substring = true),
            ).assertExists()
        compose
            .onNodeWithText("Cramps (days 1–2) 6/6 · Headache (pre-menstrual) 5/6 · Fatigue (late luteal) 3/6")
            .performScrollTo()
            .assertExists()
        compose.onNodeWithText("Page 1 of 3 · BBT charts on pages 2–3").performScrollTo().assertExists()
        compose.onNodeWithText("Create PDF").performScrollTo().performClick()
        compose.onNodeWithText("Edit").performClick()
        assertEquals(listOf("create", "edit"), calls)
    }

    @Test
    fun ready_offersShareSaveAndDiscard() {
        state = ReportUiState(step = ReportStep.READY, hasEntries = true, preview = preview())
        show()
        compose.onNodeWithText("PDF ready").assertExists()
        compose
            .onNodeWithText(
                "mensis-report-2026-10-21.pdf · 3 pages · 140 KB. It stays in the app until you save or share it.",
            ).assertExists()
        compose.onNodeWithText("Share").performScrollTo().performClick()
        compose.onNodeWithText("Save to Files").performScrollTo().performClick()
        compose.onNodeWithText("Discard").performScrollTo().performClick()
        assertEquals(listOf("share", "save", "discard"), calls)

        state = state.copy(savedName = "mensis-report-2026-10-21.pdf")
        compose.onNodeWithText("Saved as mensis-report-2026-10-21.pdf.").assertExists()
    }
}
