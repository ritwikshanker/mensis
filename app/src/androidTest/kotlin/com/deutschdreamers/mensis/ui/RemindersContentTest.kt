package com.deutschdreamers.mensis.ui

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.domain.reminders.ReminderOption
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import com.deutschdreamers.mensis.notifications.NotificationText
import com.deutschdreamers.mensis.ui.screens.reminders.ReminderRow
import com.deutschdreamers.mensis.ui.screens.reminders.RemindersActions
import com.deutschdreamers.mensis.ui.screens.reminders.RemindersContent
import com.deutschdreamers.mensis.ui.screens.reminders.RemindersUiState
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** Reminders screen (design §10) on the stateless content. Synthetic data only. */
@RunWith(AndroidJUnit4::class)
class RemindersContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val period =
        ReminderRow(
            type = ReminderType.PERIOD,
            title = "Period expected",
            subtitle = "2 days before · 09:00",
            enabled = false,
            option = ReminderOption.TWO_DAYS_BEFORE,
            options = listOf(ReminderOption.SAME_DAY, ReminderOption.ONE_DAY_BEFORE, ReminderOption.TWO_DAYS_BEFORE),
            time = LocalTime.of(9, 0),
        )

    private fun state(
        discreet: Boolean = true,
        notificationsAllowed: Boolean = true,
        exactAlarms: Boolean = true,
        enabled: Boolean = false,
    ) = RemindersUiState(
        rows = listOf(period.copy(enabled = enabled)),
        discreet = discreet,
        generic = NotificationText("Reminder", "Tap to open"),
        preview =
            if (discreet) {
                NotificationText("Reminder", "Tap to open")
            } else {
                NotificationText("Period expected in 2 days", "Expected 2–5 Nov · based on 8 cycles")
            },
        previewTime = LocalTime.of(9, 0),
        notificationsAllowed = notificationsAllowed,
        exactAlarms = exactAlarms,
    )

    private fun show(state: RemindersUiState) {
        compose.setContent {
            MensisTheme {
                RemindersContent(
                    state = state,
                    actions =
                        RemindersActions(
                            onBack = {},
                            onEnabled = { type, on -> events += "enabled $type $on" },
                            onOption = { type, option -> events += "option $type $option" },
                            onTime = { type, time -> events += "time $type $time" },
                            onDiscreet = { events += "discreet $it" },
                            onGenericText = { title, body -> events += "text $title / $body" },
                            onAllowNotifications = { events += "allow notifications" },
                            onAllowExactAlarms = { events += "allow alarms" },
                        ),
                )
            }
        }
    }

    @Test
    fun aRowExpandsToItsOptionsAndTime() {
        show(state())
        compose.onNodeWithText("1 day before").assertDoesNotExist()
        compose.onNodeWithText("Period expected").performClick()
        compose.onNodeWithText("1 day before").performClick()
        compose.onNodeWithText("Time").assertExists()
        assertEquals(listOf("option PERIOD ONE_DAY_BEFORE"), events)
    }

    @Test
    fun theSwitchEnablesTheReminder() {
        show(state())
        // The row switch comes before the discreet-wording switch.
        compose
            .onAllNodes(isToggleable())
            .onFirst()
            .assertIsOff()
            .performClick()
        assertEquals("enabled PERIOD true", events.single())
    }

    @Test
    fun theDiscreetPreviewIsGenericAndTheDetailedOneIsNot() {
        show(state(discreet = true))
        compose.onAllNodesWithText("Reminder").onFirst().assertExists()
        compose.onNodeWithText("Notifications say only \"Reminder\"").assertExists()
        compose.onNodeWithText("Reminder · Tap to open").assertExists()
    }

    @Test
    fun theDetailedPreviewShowsThePrediction() {
        show(state(discreet = false))
        compose.onNodeWithText("Period expected in 2 days").performScrollTo()
        compose.onNodeWithText("Expected 2–5 Nov · based on 8 cycles").assertExists()
        compose.onNodeWithText("Reminder · Tap to open").assertDoesNotExist()
    }

    @Test
    fun blockedNotificationsAndInexactAlarmsOfferAWayOut() {
        show(state(notificationsAllowed = false, enabled = true))
        compose.onNodeWithText("Allow").performClick()
        assertEquals("allow notifications", events.single())
    }

    @Test
    fun inexactAlarmsAreOfferedButNotRequired() {
        show(state(exactAlarms = false, enabled = true))
        compose.onNodeWithText("Allow alarms").performScrollTo().performClick()
        assertEquals("allow alarms", events.single())
        compose.onNodeWithText("Scheduled on the phone. No push service is involved.").performScrollTo()
    }
}
