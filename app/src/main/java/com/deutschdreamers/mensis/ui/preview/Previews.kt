package com.deutschdreamers.mensis.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.deutschdreamers.mensis.backup.BackupConfig
import com.deutschdreamers.mensis.backup.BackupFileInfo
import com.deutschdreamers.mensis.backup.BackupPhase
import com.deutschdreamers.mensis.backup.ManualBackupState
import com.deutschdreamers.mensis.backup.auth.BackupAvailability
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.importer.ExtrasOutcome
import com.deutschdreamers.mensis.data.importer.ImportPreview
import com.deutschdreamers.mensis.data.importer.ImportResult
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.RestoreResult
import com.deutschdreamers.mensis.data.importer.SkipReason
import com.deutschdreamers.mensis.data.importer.SkippedRow
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.ui.screens.backup.BackupActions
import com.deutschdreamers.mensis.ui.screens.backup.BackupContent
import com.deutschdreamers.mensis.ui.screens.backup.BackupDraft
import com.deutschdreamers.mensis.ui.screens.backup.BackupUiState
import com.deutschdreamers.mensis.ui.screens.export.ExportContent
import com.deutschdreamers.mensis.ui.screens.export.ExportUiState
import com.deutschdreamers.mensis.ui.screens.importer.ImportContent
import com.deutschdreamers.mensis.ui.screens.importer.ImportStep
import com.deutschdreamers.mensis.ui.screens.importer.ImportUiState
import com.deutschdreamers.mensis.ui.screens.insights.InsightsScreenContent
import com.deutschdreamers.mensis.ui.screens.insights.InsightsUiState
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingContent
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingDraft
import com.deutschdreamers.mensis.ui.screens.restore.RestoreActions
import com.deutschdreamers.mensis.ui.screens.restore.RestoreContent
import com.deutschdreamers.mensis.ui.screens.restore.RestoreStep
import com.deutschdreamers.mensis.ui.screens.restore.RestoreUiState
import com.deutschdreamers.mensis.ui.screens.today.TodayScreenContent
import com.deutschdreamers.mensis.ui.screens.today.TodayUiState
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
private fun TodayPreview(state: TodayUiState) {
    MensisTheme {
        TodayScreenContent(state = state, onLog = {}, onImport = {}, onBackupAction = {}, onMethodSettings = {})
    }
}

@PreviewLightDark
@Composable
private fun TodayNormalPreview() = TodayPreview(FakeData.normal())

@Preview(fontScale = 2.0f)
@Composable
private fun TodayNormalLargeTextPreview() = TodayPreview(FakeData.normal())

@PreviewLightDark
@Composable
private fun TodayOnPeriodPreview() = TodayPreview(FakeData.onPeriod())

@PreviewLightDark
@Composable
private fun TodayFertilePreview() = TodayPreview(FakeData.fertile())

@PreviewLightDark
@Composable
private fun TodayLatePreview() = TodayPreview(FakeData.late())

@PreviewLightDark
@Composable
private fun TodayFirstLaunchPreview() = TodayPreview(FakeData.firstLaunch())

@PreviewLightDark
@Composable
private fun TodayCyclicPreview() = TodayPreview(FakeData.cyclic())

@PreviewLightDark
@Composable
private fun TodaySuppressedPreview() = TodayPreview(FakeData.suppressed())

@PreviewLightDark
@Composable
private fun TodayPregnancyPreview() = TodayPreview(FakeData.pregnancy())

@Composable
private fun InsightsPreview(state: InsightsUiState) {
    MensisTheme { InsightsScreenContent(state) }
}

@PreviewLightDark
@Composable
private fun InsightsEightCyclesPreview() = InsightsPreview(FakeData.insights())

@Preview(fontScale = 2.0f)
@Composable
private fun InsightsLargeTextPreview() = InsightsPreview(FakeData.insights())

@PreviewLightDark
@Composable
private fun InsightsOneCyclePreview() = InsightsPreview(FakeData.insights(cycles = 1))

@PreviewLightDark
@Composable
private fun InsightsNoCyclePreview() = InsightsPreview(FakeData.insights(cycles = 0))

@PreviewLightDark
@Composable
private fun InsightsWithoutTemperaturePreview() =
    InsightsPreview(FakeData.insights(trackingLevel = TrackingLevel.SYMPTOMS))

@PreviewLightDark
@Composable
private fun OnboardingWelcomePreview() {
    MensisTheme {
        OnboardingContent(
            draft = OnboardingDraft(),
            today = FakeData.lastStart,
            onUpdate = {},
            onBack = {},
            onSkip = {},
            onNext = {},
            onRestore = {},
        )
    }
}

@Preview(fontScale = 2.0f)
@Composable
private fun OnboardingLengthsLargeTextPreview() {
    MensisTheme {
        OnboardingContent(
            draft = OnboardingDraft(step = 3),
            today = FakeData.lastStart,
            onUpdate = {},
            onBack = {},
            onSkip = {},
            onNext = {},
            onRestore = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ExportOptionsPreview() {
    MensisTheme {
        ExportContent(
            state =
                ExportUiState(
                    summary = ExportSummary(243, 8, FakeData.firstStart, FakeData.lastStart),
                    estimatedBytes = 52L * 1024,
                ),
            onBack = {},
            onFormat = {},
            onEncrypt = {},
            onPassword = {},
            onRepeat = {},
            onChooseLocation = {},
            onShare = {},
        )
    }
}

@Composable
private fun ImportPreviewFor(state: ImportUiState) {
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
            onStrategy = {},
            onImport = {},
            onUndo = {},
        )
    }
}

private val previewImport =
    ParsedImport(
        source = ImportSource.CSV,
        rowsRead = 243,
        days = FakeData.regularHistory(),
        skipped = List(3) { SkippedRow(it + 40, SkipReason.UNREADABLE_DATE, "2026-13-40") },
        issues = emptyList(),
    )

@PreviewLightDark
@Composable
private fun ImportPreviewStepPreview() =
    ImportPreviewFor(
        ImportUiState(step = ImportStep.PREVIEW, parsed = previewImport, preview = ImportPreview(12, false, true)),
    )

@PreviewLightDark
@Composable
private fun ImportDonePreview() =
    ImportPreviewFor(
        ImportUiState(
            step = ImportStep.DONE,
            parsed = previewImport,
            result =
                ImportResult(
                    240,
                    240,
                    0,
                    0,
                    8,
                    3,
                    ExtrasOutcome.NOT_IN_FILE,
                    ExtrasOutcome.NOT_IN_FILE,
                    undoable = true,
                ),
        ),
    )

private val previewBackup =
    BackupConfig(
        enabled = true,
        accountEmail = "someone@example.com",
        lastSuccessAt = Instant.parse("2026-10-20T23:14:00Z"),
        lastSuccessBytes = 48 * 1_024L,
        lastSuccessDevice = "Pixel 9",
    )

@Composable
private fun BackupPreviewFor(
    config: BackupConfig,
    manual: ManualBackupState = ManualBackupState.None,
) {
    MensisTheme {
        BackupContent(
            state =
                BackupUiState(
                    BackupAvailability.AVAILABLE,
                    config,
                    manual,
                    BackupDraft(),
                    LocalDate.of(2026, 10, 21),
                    ZoneOffset.UTC,
                ),
            actions = BackupActions(),
        )
    }
}

@PreviewLightDark
@Composable
private fun BackupIdlePreview() = BackupPreviewFor(previewBackup)

@PreviewLightDark
@Composable
private fun BackupRunningPreview() = BackupPreviewFor(previewBackup, ManualBackupState.Running(BackupPhase.UPLOADING))

@PreviewLightDark
@Composable
private fun BackupOffPreview() = BackupPreviewFor(BackupConfig())

@Composable
private fun RestorePreviewFor(state: RestoreUiState) {
    MensisTheme { RestoreContent(state = state, actions = RestoreActions()) }
}

private val previewFile =
    BackupFileInfo("1", "mensis-backup.enc", 48 * 1_024L, Instant.parse("2026-10-20T23:14:00Z"), "Pixel 9")

@PreviewLightDark
@Composable
private fun RestoreFoundPreview() =
    RestorePreviewFor(
        RestoreUiState(
            availability = BackupAvailability.AVAILABLE,
            step = RestoreStep.FOUND,
            email = "someone@example.com",
            file = previewFile,
            local = ExportSummary(0, 0, null, null),
            zone = ZoneOffset.UTC,
        ),
    )

@PreviewLightDark
@Composable
private fun RestoreWrongPasswordPreview() =
    RestorePreviewFor(
        RestoreUiState(
            availability = BackupAvailability.AVAILABLE,
            step = RestoreStep.PASSWORD,
            file = previewFile,
            password = "not it",
            wrongPassword = true,
        ),
    )

@PreviewLightDark
@Composable
private fun RestoreDonePreview() =
    RestorePreviewFor(
        RestoreUiState(
            availability = BackupAvailability.AVAILABLE,
            step = RestoreStep.DONE,
            result = RestoreResult(243, 8, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 10, 20), true),
        ),
    )
