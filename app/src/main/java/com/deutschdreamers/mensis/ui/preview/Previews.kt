package com.deutschdreamers.mensis.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
