package com.deutschdreamers.mensis.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.backup.BackupFileInfo
import com.deutschdreamers.mensis.backup.OpenedBackup
import com.deutschdreamers.mensis.backup.RestoreError
import com.deutschdreamers.mensis.backup.RestoreException
import com.deutschdreamers.mensis.backup.RestoreSource
import com.deutschdreamers.mensis.backup.auth.AuthorizationOutcome
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.backup.auth.SignInResult
import com.deutschdreamers.mensis.data.crypto.ContainerKey
import com.deutschdreamers.mensis.data.crypto.KdfParams
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.RestoreResult
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.lock.AppLockManager
import com.deutschdreamers.mensis.lock.DeviceCredentialChecker
import com.deutschdreamers.mensis.ui.screens.restore.RestoreScreen
import com.deutschdreamers.mensis.ui.screens.restore.RestoreViewModel
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Design §12 end to end on the real view model, with Google and Drive replaced by fakes. Synthetic data only. */
@RunWith(AndroidJUnit4::class)
class RestoreFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)

    private class FakeGateway : GoogleAccountGateway {
        var signIns = 0

        override fun availability(): BackupAvailability = BackupAvailability.AVAILABLE

        override suspend fun signIn(activity: Activity): SignInResult {
            signIns++
            return SignInResult.SignedIn("someone@example.com")
        }

        override suspend fun authorize(email: String): AuthorizationOutcome = AuthorizationOutcome.Granted("token")

        override fun tokenFromConsent(data: Intent?): String? = "token"

        override suspend fun clearToken(token: String) = Unit

        override suspend fun disconnect(email: String) = Unit
    }

    private class FakeSource(
        private val local: ExportSummary,
    ) : RestoreSource {
        val file =
            BackupFileInfo("f1", "mensis-backup.enc", 48 * 1_024L, Instant.parse("2026-10-20T23:14:00Z"), "Pixel 9")
        var restoredFor: String? = null

        override suspend fun latestBackup(email: String): BackupFileInfo = file

        override suspend fun download(
            email: String,
            file: BackupFileInfo,
        ): ByteArray = ByteArray(16)

        override suspend fun open(
            file: BackupFileInfo,
            bytes: ByteArray,
            password: CharArray,
        ): OpenedBackup {
            if (String(password) != PASSWORD) throw RestoreException(RestoreError.WRONG_PASSWORD)
            return OpenedBackup(
                file = file,
                parsed = ParsedImport(ImportSource.MENSIS, 0, emptyList(), emptyList(), emptyList()),
                uiPrefs = null,
                summary = ExportSummary(243, 8, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 10, 20)),
                key = ContainerKey(ByteArray(32), ByteArray(16), KdfParams.DEFAULT),
            )
        }

        override suspend fun localData(): ExportSummary = local

        override suspend fun restore(
            email: String,
            opened: OpenedBackup,
        ): RestoreResult {
            restoredFor = email
            return RestoreResult(243, 8, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 10, 20), true)
        }
    }

    private fun show(
        source: RestoreSource,
        gateway: FakeGateway = FakeGateway(),
        onClose: () -> Unit = {},
        onRestored: () -> Unit = {},
    ) {
        val lock = AppLockManager(UiPrefsStore(context), DeviceCredentialChecker(context))
        val viewModel = RestoreViewModel(source, gateway, lock, clock)
        compose.setContent {
            MensisTheme {
                RestoreScreen(onClose = onClose, onRestored = onRestored, fromOnboarding = true, viewModel = viewModel)
            }
        }
    }

    @Test
    fun signIn_findBackup_wrongThenRightPassword_restores() {
        val source = FakeSource(ExportSummary(0, 0, null, null))
        var opened = false
        show(source, onRestored = { opened = true })

        compose.onNodeWithText("Sign in to Google Drive").assertExists()
        compose
            .onNodeWithText(
                "Mensis only asks for access to its own app folder, not your other files.",
            ).assertExists()
        compose.onNodeWithText("Continue with Google").performClick()

        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodes(hasTextExactly("Backup found")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("mensis-backup.enc · 20 Oct 2026, 23:14 · 48 KB · from Pixel 9").assertExists()
        compose.onNodeWithText("This phone has no cycle data yet, so the backup will be loaded as-is.").assertExists()
        compose.onNodeWithText("Restore this backup").performClick()

        compose.onNodeWithText("Enter the backup password").assertExists()
        compose.onNodeWithText("Backup password").performTextInput("not it")
        compose.onNodeWithText("Decrypt and restore").performClick()
        compose.waitUntil(TIMEOUT) {
            compose
                .onAllNodes(
                    hasText(
                        "Wrong password. The backup can't be decrypted. Check for typos; the password is case-sensitive.",
                    ),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }

        compose.onNodeWithText("Backup password").performTextReplacement(PASSWORD)
        compose.onNodeWithText("Decrypt and restore").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasTextExactly("Restored")).fetchSemanticsNodes().isNotEmpty() }
        compose
            .onNodeWithText(
                "8 cycles and 243 daily entries, 6 Mar to 20 Oct 2026. Settings and reminders were restored too.",
            ).assertExists()
        assertEquals("someone@example.com", source.restoredFor)

        compose.onNodeWithText("Open Mensis").performClick()
        assertTrue(opened)
    }

    @Test
    fun restoringOverExistingData_needsConfirmation() {
        val source = FakeSource(ExportSummary(12, 2, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 10, 20)))
        show(source)

        compose.onNodeWithText("Continue with Google").performClick()
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodes(hasTextExactly("Backup found")).fetchSemanticsNodes().isNotEmpty() }
        compose
            .onNodeWithText(
                "This phone has 12 daily entries from 6 Mar to 20 Oct 2026. Restoring replaces them with the backup. " +
                    "Export them first if you want to keep a copy.",
            ).assertExists()
        compose.onNodeWithText("Restore this backup").assertIsNotEnabled()
        compose.onNodeWithText("Replace the data on this phone").performClick()
        compose.onNodeWithText("Restore this backup").performScrollTo().assertIsEnabled()
    }

    @Test
    fun lostPassword_explainsAndOffersAFreshStart() {
        var closed = false
        show(FakeSource(ExportSummary(0, 0, null, null)), onClose = { closed = true })
        compose.onNodeWithText("Continue with Google").performClick()
        compose.waitUntil(
            TIMEOUT,
        ) { compose.onAllNodes(hasTextExactly("Backup found")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Restore this backup").performClick()

        compose.onNodeWithText("I've lost the password").performClick()
        compose.onNodeWithText("The backup can't be opened without it").assertExists()
        compose.onNodeWithText("Start fresh").performClick()
        assertTrue(closed)
    }

    private companion object {
        const val PASSWORD = "correct horse battery staple"
        const val TIMEOUT = 5_000L
    }
}
