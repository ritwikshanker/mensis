package com.deutschdreamers.mensis.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.ui.navigation.About
import com.deutschdreamers.mensis.ui.navigation.MeasurementUnits
import com.deutschdreamers.mensis.ui.navigation.MensisNavKey
import com.deutschdreamers.mensis.ui.navigation.Mode
import com.deutschdreamers.mensis.ui.navigation.NoticeDocument
import com.deutschdreamers.mensis.ui.navigation.Theme
import com.deutschdreamers.mensis.ui.screens.about.AboutContent
import com.deutschdreamers.mensis.ui.screens.about.AboutLinks
import com.deutschdreamers.mensis.ui.screens.about.NoticesScreen
import com.deutschdreamers.mensis.ui.screens.deleteall.DeleteAllDialog
import com.deutschdreamers.mensis.ui.screens.deleteall.DeleteAllUiState
import com.deutschdreamers.mensis.ui.screens.mode.ModeContent
import com.deutschdreamers.mensis.ui.screens.mode.ModeUiState
import com.deutschdreamers.mensis.ui.screens.mode.PregnancyOffer
import com.deutschdreamers.mensis.ui.screens.mode.PregnancyOfferDialog
import com.deutschdreamers.mensis.ui.screens.settings.SettingsScreenContent
import com.deutschdreamers.mensis.ui.screens.settings.SettingsUiState
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import com.deutschdreamers.mensis.wipe.DriveDeleteFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The step-10 screens: Settings rows, delete-all dialog, Mode, pregnancy offer, About and notices. */
@RunWith(AndroidJUnit4::class)
class SettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 21)
    private val lmp = LocalDate.of(2026, 8, 17)

    private val settings =
        SettingsUiState(
            pregnancyMode = false,
            modeSummary = "Cycle tracking",
            trackSummary = "Period and symptoms",
            contraceptionSummary = "None or barrier",
            unitsSummary = "°C · kg",
            remindersSummary = "Off",
            lockEnabled = true,
            lockAvailable = true,
            hideInRecents = true,
            relockTimeoutMinutes = 1,
            backupSummary = "Off",
            themeSummary = "Rose · Light",
        )

    @Test
    fun settingsRows_openTheirScreens_andDeleteOpensTheDialog() {
        val opened = mutableListOf<MensisNavKey>()
        var deleteRequested = false
        compose.setContent {
            MensisTheme {
                SettingsScreenContent(
                    state = settings,
                    onNavigate = { opened += it },
                    onLockEnabled = {},
                    onHideInRecents = {},
                    onRelockTimeout = {},
                    onDeleteAll = { deleteRequested = true },
                )
            }
        }

        compose.onNodeWithText("Mode").performClick()
        compose.onNodeWithText("Units").performClick()
        compose.onNodeWithText("Delete all data").performScrollTo().performClick()
        compose.onNodeWithText("Theme").performScrollTo().performClick()
        compose.onNodeWithText("About and privacy").performScrollTo().performClick()

        assertEquals(listOf(Mode, MeasurementUnits, Theme, About), opened)
        assertTrue(deleteRequested)
    }

    @Test
    fun deleteDialog_offersDriveOnlyWithBackup_andReportsFailures() {
        var state by mutableStateOf(DeleteAllUiState())
        var ticked: Boolean? = null
        var confirmed = 0
        compose.setContent {
            MensisTheme {
                DeleteAllDialog(
                    state = state,
                    onDeleteDriveChange = { ticked = it },
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Delete all data?").assertExists()
        compose
            .onNodeWithText(
                "Removes every entry, cycle and setting from this phone. This cannot be undone. " +
                    "Exports you saved elsewhere are not affected.",
            ).assertExists()
        compose.onNodeWithText("Also delete the backup in Google Drive").assertDoesNotExist()

        state = DeleteAllUiState(driveBackupOn = true)
        compose.onNodeWithText("Also delete the backup in Google Drive").performClick()
        assertEquals(true, ticked)

        state = state.copy(failure = DriveDeleteFailure.RECONNECT)
        compose
            .onNodeWithText(
                "The Google account needs reconnecting before its backup can be deleted, so nothing was deleted. " +
                    "Reconnect under Backup, or untick the Drive option.",
            ).assertExists()
        compose.onNodeWithText("Delete").assertIsEnabled().performClick()
        assertEquals(1, confirmed)

        state = state.copy(failure = null, busy = true)
        compose.onNodeWithText("Deleting…").assertExists()
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun mode_pregnancyCountsFromTheLastPeriod() {
        var state by mutableStateOf(ModeUiState(TrackerMode.CYCLE, null, today, suggestedLmp = lmp))
        var chosen: LocalDate? = null
        compose.setContent {
            MensisTheme {
                ModeContent(state = state, onCycle = {}, onPregnancy = { chosen = it }, onBack = {})
            }
        }

        compose.onNodeWithText("Pregnancy").performClick()
        assertEquals(lmp, chosen)

        state = ModeUiState(TrackerMode.PREGNANCY, lmp, today, suggestedLmp = lmp)
        compose.onNodeWithText("17 Aug 2026").assertExists()
        compose.onNodeWithText("Estimated due date 24 May 2027 · week 10, day 3").assertExists()
        compose
            .onNodeWithText(
                "Due dates are estimates; most births happen within two weeks either side.",
            ).assertExists()
    }

    @Test
    fun pregnancyOffer_withoutALoggedPeriod_needsADate() {
        compose.setContent {
            MensisTheme {
                PregnancyOfferDialog(
                    offer = PregnancyOffer(today, lmp = null),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Switch to pregnancy mode?").assertExists()
        compose.onNodeWithText("Choose a date").assertExists()
        compose.onNodeWithText("Switch").assertIsNotEnabled()
        compose.onNodeWithText("Not now").assertIsEnabled()
    }

    @Test
    fun about_rowsOpenTheNoticesAndTheLinks() {
        val links = mutableListOf<String>()
        val notices = mutableListOf<NoticeDocument>()
        compose.setContent {
            MensisTheme {
                AboutContent(
                    version = "0.1.0-dev",
                    onOpenLink = { links += it },
                    onNotices = { notices += it },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("VERSION 0.1.0-dev · MIT").assertExists()
        compose.onNodeWithText("github.com/ritwikshanker/mensis").performScrollTo().performClick()
        compose.onNodeWithText("Licence").performScrollTo().performClick()
        compose.onNodeWithText("Open-source libraries").performScrollTo().performClick()
        compose.onNodeWithText("Report a problem").performScrollTo().performClick()
        compose
            .onNodeWithText("Mensis is not a medical device and does not provide medical advice or contraception.")
            .performScrollTo()
            .assertExists()

        assertEquals(listOf(AboutLinks.SOURCE, AboutLinks.ISSUES), links)
        assertEquals(listOf(NoticeDocument.LICENCE, NoticeDocument.LIBRARIES), notices)
    }

    @Test
    fun libraries_showTheNoticesAndTheBundledLicenceTexts() {
        compose.setContent { MensisTheme { NoticesScreen(document = NoticeDocument.LIBRARIES, onBack = {}) } }

        compose.waitUntil(TIMEOUT) {
            compose.onAllNodes(hasText("Third-party notices")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Apache License 2.0"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("SIL Open Font License 1.1 · Source Serif 4"))
    }

    @Test
    fun licence_showsTheMitText() {
        compose.setContent { MensisTheme { NoticesScreen(document = NoticeDocument.LICENCE, onBack = {}) } }

        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText("MIT License")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Copyright (c) 2026 Ritwik Shanker").assertExists()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
