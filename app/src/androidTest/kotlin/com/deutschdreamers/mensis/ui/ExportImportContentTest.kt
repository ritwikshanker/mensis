package com.deutschdreamers.mensis.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.importer.ConflictStrategy
import com.deutschdreamers.mensis.data.importer.ImportPreview
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.SkipReason
import com.deutschdreamers.mensis.data.importer.SkippedRow
import com.deutschdreamers.mensis.domain.model.DayEntry
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.ui.screens.export.ExportContent
import com.deutschdreamers.mensis.ui.screens.export.ExportUiState
import com.deutschdreamers.mensis.ui.screens.importer.ImportContent
import com.deutschdreamers.mensis.ui.screens.importer.ImportStep
import com.deutschdreamers.mensis.ui.screens.importer.ImportUiState
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Export options and import preview as the user sees them. Synthetic data only. */
@RunWith(AndroidJUnit4::class)
class ExportImportContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val summary = ExportSummary(243, 8, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 10, 21))

    @Test
    fun export_warns_without_encryption_and_asks_for_the_password_twice_with_it() {
        var state by mutableStateOf(ExportUiState(summary = summary, estimatedBytes = 52L * 1024))
        compose.setContent {
            MensisTheme {
                ExportContent(
                    state = state,
                    onBack = {},
                    onFormat = { state = state.copy(format = it) },
                    onEncrypt = { state = state.copy(encrypt = it) },
                    onPassword = { state = state.copy(password = it) },
                    onRepeat = { state = state.copy(repeat = it) },
                    onChooseLocation = {},
                    onShare = {},
                )
            }
        }
        compose
            .onNodeWithText(
                "The file holds your health data unencrypted. Anyone who gets a copy can read it.",
            ).assertExists()
        compose.onNodeWithText("243 daily entries · 8 cycles · settings").assertExists()
        compose.onNodeWithText("6 Mar – 21 Oct 2026 · about 52 KB").assertExists()
        compose.onNodeWithText("Choose where to save").performScrollTo().assertIsEnabled()

        compose.onNodeWithText("Encrypt with a password").performClick()
        compose
            .onNodeWithText(
                "The file holds your health data unencrypted. Anyone who gets a copy can read it.",
            ).assertDoesNotExist()
        compose.onNodeWithText("Choose where to save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("File password").performTextInput("a long synthetic passphrase")
        compose.onNodeWithText("Repeat the password").performTextInput("a long synthetic passphrasE")
        compose.onNodeWithText("The passwords don't match.").assertExists()
        compose.onNodeWithText("Choose where to save").performScrollTo().assertIsNotEnabled()

        state = state.copy(repeat = "a long synthetic passphrase")
        compose.onNodeWithText("Choose where to save").performScrollTo().assertIsEnabled()

        compose.onNodeWithText("CSV").performScrollTo().performClick()
        compose.onNodeWithText("243 daily entries · 8 cycles").assertExists()
    }

    @Test
    fun import_preview_shows_counts_rows_and_the_strategy_help() {
        val days =
            listOf(
                DayEntry(LocalDate.of(2026, 3, 6), flow = Flow.MEDIUM, temperatureC = 36.42, moods = setOf(Mood.CALM)),
                DayEntry(LocalDate.of(2026, 3, 9), flow = Flow.LIGHT, moods = setOf(Mood.LOW)),
            )
        val parsed =
            ParsedImport(
                source = ImportSource.CSV,
                rowsRead = 3,
                days = days,
                skipped = listOf(SkippedRow(4, SkipReason.UNREADABLE_DATE, "2026-13-40")),
                issues = emptyList(),
            )
        var state by mutableStateOf(
            ImportUiState(
                step = ImportStep.PREVIEW,
                parsed = parsed,
                preview = ImportPreview(1, localHasHistory = false, localHasSettings = true),
            ),
        )
        compose.setContent {
            MensisTheme {
                ImportContent(
                    state = state,
                    onBack = {},
                    onClose = {},
                    onSource = {},
                    onChooseFile = {},
                    onPassword = {},
                    onDecrypt = {},
                    onField = { _, _ -> },
                    onDateOrder = {},
                    onConfirmMapping = {},
                    onStrategy = { state = state.copy(strategy = it) },
                    onImport = {},
                    onUndo = {},
                )
            }
        }
        compose.onNodeWithText("Step 3 of 4").assertExists()
        compose.onNodeWithText("3 rows read · 2 valid · 1 skipped (unreadable date)").assertExists()
        compose.onNodeWithText("6 Mar 2026").assertExists()
        compose.onNodeWithText("36.42").assertExists()
        compose.onNodeWithText("Calm").assertExists()
        compose.onNodeWithText("Keeps what you logged; fills only empty fields from the file.").assertExists()
        compose.onNodeWithText("1 day in the file already has data on this phone.").assertExists()

        compose.onNodeWithText("Replace").performClick()
        compose.onNodeWithText("The file overwrites existing entries on the same day.").assertExists()
        assert(state.strategy == ConflictStrategy.REPLACE)
        compose.onNodeWithText("Import 2 entries").performScrollTo().assertIsEnabled()
    }

    @Test
    fun import_source_step_shows_the_problem_and_the_local_only_note() {
        compose.setContent {
            MensisTheme {
                ImportContent(
                    state =
                        ImportUiState(
                            source = ImportSource.DRIP,
                            problem = com.deutschdreamers.mensis.ui.screens.importer.ImportProblem.NOT_THIS_FORMAT,
                        ),
                    onBack = {},
                    onClose = {},
                    onSource = {},
                    onChooseFile = {},
                    onPassword = {},
                    onDecrypt = {},
                    onField = { _, _ -> },
                    onDateOrder = {},
                    onConfirmMapping = {},
                    onStrategy = {},
                    onImport = {},
                    onUndo = {},
                )
            }
        }
        compose.onNodeWithText("Step 1 of 4").assertExists()
        compose.onNodeWithText("The file is read on the phone. Nothing is uploaded.").assertExists()
        compose
            .onNodeWithText(
                "This file doesn't look like a drip. export. Check the source above or choose another file.",
            ).assertExists()
    }
}
