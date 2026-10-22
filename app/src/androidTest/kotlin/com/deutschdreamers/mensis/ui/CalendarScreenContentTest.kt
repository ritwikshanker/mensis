package com.deutschdreamers.mensis.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.ui.preview.FakeData
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarMonthBuilder
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarScreenContent
import com.deutschdreamers.mensis.ui.screens.calendar.CalendarSelection
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class CalendarScreenContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = FakeData.lastStart.plusDays(16)
    private val data = FakeData.data(FakeData.regularHistory() + FakeData.todayEntry(today))
    private var tapped: LocalDate? = null
    private var saved = false
    private var editStarted = false

    private fun show(selection: CalendarSelection) {
        val state = CalendarMonthBuilder.build(data, today, selection)
        compose.setContent {
            MensisTheme {
                CalendarScreenContent(
                    state = state,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDayTap = { tapped = it },
                    onStartEdit = { editStarted = true },
                    onCancelEdit = {},
                    onSaveEdit = { saved = true },
                    onEditDay = {},
                )
            }
        }
    }

    @Test
    fun monthView_showsGridLegendAndDayCard() {
        show(CalendarSelection(YearMonth.from(FakeData.lastStart), today))
        compose.onNodeWithText("August 2026").assertIsDisplayed()
        compose.onNodeWithText("Thu 10 Sep · Cycle day 17").assertIsDisplayed()
        compose.onNodeWithText("Cycle started 25 Aug · 29 d before: 27 Jul").assertIsDisplayed()
        compose.onNode(hasContentDescription("Tuesday 25 August, period, logged")).performClick()
        assertEquals(FakeData.lastStart, tapped)
        compose.onNodeWithContentDescription("Edit period dates").performClick()
        assertTrue(editStarted)
    }

    @Test
    fun editMode_announcesMarksAndSaves() {
        show(CalendarSelection(YearMonth.from(today), today, editMode = true, toggles = setOf(today)))
        compose.onNodeWithText("Edit period").assertIsDisplayed()
        compose
            .onNodeWithText(
                "Tap days to mark or unmark bleeding. Predictions recalculate when you save.",
            ).assertIsDisplayed()
        compose.onNode(hasContentDescription("Thursday 10 September, today, bleeding, tap to unmark")).assertExists()
        compose.onNode(hasContentDescription("Friday 11 September, not bleeding, tap to mark")).performClick()
        assertEquals(today.plusDays(1), tapped)
        compose.onNodeWithText("Save").performClick()
        assertTrue(saved)
    }
}
