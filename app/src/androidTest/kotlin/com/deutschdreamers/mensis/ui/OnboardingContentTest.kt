package com.deutschdreamers.mensis.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingContent
import com.deutschdreamers.mensis.ui.screens.onboarding.OnboardingDraft
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OnboardingContentTest {
    @get:Rule
    val compose = createComposeRule()

    /** How many pixels of the text's capture are light, i.e. readable on the dark surface. */
    private fun lightPixelsOf(text: String): Int {
        val pixels = compose.onNodeWithText(text).captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) if (pixels[x, y].luminance() > LIGHT) count++
        }
        return count
    }

    @Test
    fun darkMode_drawsUncolouredTextInTheLightOnSurfaceColour() {
        var draft by mutableStateOf(OnboardingDraft())
        compose.setContent {
            MensisTheme(appearance = AppearanceMode.DARK) {
                OnboardingContent(
                    draft = draft,
                    today = LocalDate.of(2026, 10, 21),
                    onUpdate = {},
                    onBack = {},
                    onSkip = {},
                    onNext = {},
                    onRestore = {},
                    onImport = {},
                )
            }
        }
        // Text without its own colour used to fall back to black on the near-black surface.
        assertTrue(lightPixelsOf("A cycle tracker that keeps your data on your phone.") > 0)

        draft = OnboardingDraft(step = OnboardingDraft.BACKUP_STEP)
        assertTrue(lightPixelsOf("Back up to Google Drive?") > 0)
    }

    private companion object {
        const val LIGHT = 0.5f
    }
}
