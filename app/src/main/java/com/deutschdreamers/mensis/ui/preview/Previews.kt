package com.deutschdreamers.mensis.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.deutschdreamers.mensis.data.export.ExportSummary
import com.deutschdreamers.mensis.data.importer.ExtrasOutcome
import com.deutschdreamers.mensis.data.importer.ImportPreview
import com.deutschdreamers.mensis.data.importer.ImportResult
import com.deutschdreamers.mensis.data.importer.ImportSource
import com.deutschdreamers.mensis.data.importer.ParsedImport
import com.deutschdreamers.mensis.data.importer.SkipReason
import com.deutschdreamers.mensis.data.importer.SkippedRow
import com.deutschdreamers.mensis.ui.screens.export.ExportContent
import com.deutschdreamers.mensis.ui.screens.export.ExportUiState
import com.deutschdreamers.mensis.ui.screens.importer.ImportContent
import com.deutschdreamers.mensis.ui.screens.importer.ImportStep
import com.deutschdreamers.mensis.ui.screens.importer.ImportUiState
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingContent
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingDraft
import com.deutschdreamers.mensis.ui.screens.today.TodayScreenContent
import com.deutschdreamers.mensis.ui.screens.today.TodayUiState
import com.deutschdreamers.mensis.ui.theme.MensisTheme

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
