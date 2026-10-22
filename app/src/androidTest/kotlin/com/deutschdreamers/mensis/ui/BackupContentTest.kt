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
import com.deutschdreamers.mensis.backup.BackupConfig
import com.deutschdreamers.mensis.backup.BackupFrequency
import com.deutschdreamers.mensis.backup.BackupPhase
import com.deutschdreamers.mensis.backup.ManualBackupState
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.data.backup.BackupError
import com.deutschdreamers.mensis.ui.screens.backup.BackupActions
import com.deutschdreamers.mensis.ui.screens.backup.BackupContent
import com.deutschdreamers.mensis.ui.screens.backup.BackupDraft
import com.deutschdreamers.mensis.ui.screens.backup.BackupUiState
import com.deutschdreamers.mensis.ui.screens.backup.PasswordMode
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Backup screen states (design §11) on the stateless content. Synthetic data only. */
@RunWith(AndroidJUnit4::class)
class BackupContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val on =
        BackupConfig(
            enabled = true,
            accountEmail = "someone@example.com",
            lastSuccessAt = Instant.parse("2026-10-20T23:14:00Z"),
            lastSuccessBytes = 48 * 1_024L,
            lastSuccessDevice = "Pixel 9",
        )

    private fun state(
        config: BackupConfig = on,
        manual: ManualBackupState = ManualBackupState.None,
        draft: BackupDraft = BackupDraft(),
        availability: BackupAvailability = BackupAvailability.AVAILABLE,
    ) = BackupUiState(availability, config, manual, draft, LocalDate.of(2026, 10, 21), ZoneOffset.UTC)

    @Test
    fun idle_showsTheLastBackupRowsAndFooter() {
        var frequency: BackupFrequency? = null
        compose.setContent {
            MensisTheme { BackupContent(state(), BackupActions(onFrequency = { frequency = it })) }
        }
        compose.onNodeWithText("Backed up yesterday, 23:14").assertExists()
        compose.onNodeWithText("48 KB · encrypted · Pixel 9").assertExists()
        compose.onNodeWithText("Back up now").assertIsEnabled()
        compose.onNodeWithText("someone@example.com").assertExists()
        compose.onNodeWithText("Daily").performClick()
        assertEquals(BackupFrequency.DAILY, frequency)
        compose
            .onNodeWithText(
                "The file is encrypted with AES-256-GCM using a key derived from your password (Argon2id) before it " +
                    "leaves the phone. Google sees only ciphertext and the file size.",
            ).performScrollTo()
            .assertExists()
    }

    @Test
    fun running_thenDone_followTheDesign() {
        var current by mutableStateOf(state(manual = ManualBackupState.Running(BackupPhase.ENCRYPTING)))
        compose.setContent { MensisTheme { BackupContent(current, BackupActions()) } }
        compose.onNodeWithText("Backing up…").assertExists()
        compose.onNodeWithText("Encrypting on the phone, then uploading").assertExists()
        compose.onNodeWithText("Back up now").assertIsNotEnabled()

        current = state(manual = ManualBackupState.Succeeded, draft = BackupDraft(sawManualRun = true))
        compose.onNodeWithText("Backed up just now").assertExists()
    }

    @Test
    fun failingAndReconnect_sayWhatIsWrong() {
        var current by mutableStateOf(state(config = on.copy(lastError = BackupError.NETWORK)))
        var reconnectedWith: String? = null
        compose.setContent {
            MensisTheme { BackupContent(current, BackupActions(onConnect = { reconnectedWith = it })) }
        }
        compose.onNodeWithText("Last backup failed: no connection.").assertExists()

        current = state(config = on.copy(needsReconnect = true))
        compose.onNodeWithText("Google account needs reconnecting before the next backup.").assertExists()
        compose.onNodeWithText("Reconnect").performClick()
        assertEquals("someone@example.com", reconnectedWith)
    }

    @Test
    fun off_offersTurnOnAndRestore() {
        compose.setContent { MensisTheme { BackupContent(state(config = BackupConfig()), BackupActions()) } }
        compose.onNodeWithText("Backup is off").assertExists()
        compose.onNodeWithText("Turn on backup").assertExists()
        compose.onNodeWithText("Restore from a backup").assertExists()
    }

    @Test
    fun unavailable_explainsWithoutOptions() {
        compose.setContent {
            MensisTheme {
                BackupContent(
                    state(config = BackupConfig(), availability = BackupAvailability.NOT_CONFIGURED),
                    BackupActions(),
                )
            }
        }
        compose
            .onNodeWithText(
                "Drive backup is not configured in this build. Export and import still work.",
            ).assertExists()
        compose.onNodeWithText("Turn on backup").assertDoesNotExist()
    }

    @Test
    fun setup_needsTheSamePasswordTwice() {
        var current by mutableStateOf(
            state(
                config = BackupConfig(),
                draft = BackupDraft(setupEmail = "someone@example.com", passwordMode = PasswordMode.SETUP),
            ),
        )
        compose.setContent {
            MensisTheme {
                BackupContent(
                    current,
                    BackupActions(
                        onPassword = { current = current.copy(draft = current.draft.copy(password = it)) },
                        onRepeat = { current = current.copy(draft = current.draft.copy(repeat = it)) },
                    ),
                )
            }
        }
        compose.onNodeWithText("Choose a backup password").assertExists()
        compose.onNodeWithText("Turn on").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Backup password").performTextInput("a long synthetic passphrase")
        compose.onNodeWithText("Repeat the password").performTextInput("a long synthetic passphrase")
        compose.onNodeWithText("Turn on").performScrollTo().assertIsEnabled()
    }
}
