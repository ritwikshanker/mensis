package com.deutschdreamers.mensis.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.ui.screens.theme.ThemeContent
import com.deutschdreamers.mensis.ui.screens.theme.ThemeUiState
import com.deutschdreamers.mensis.ui.theme.LocalMensisColors
import com.deutschdreamers.mensis.ui.theme.MensisColors
import com.deutschdreamers.mensis.ui.theme.MensisColorsDark
import com.deutschdreamers.mensis.ui.theme.MensisColorsLight
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Design principle 4: period, fertile, ovulation and the hatch never change with the colour scheme. */
@RunWith(AndroidJUnit4::class)
class ThemeColorsTest {
    @get:Rule
    val compose = createComposeRule()

    private data class Seen(
        val scheme: ThemeScheme,
        val appearance: AppearanceMode,
        val primary: Color,
        val semantic: MensisColors,
    )

    @Test
    fun switchingSchemes_keepsTheSemanticColoursFixed() {
        var scheme by mutableStateOf(ThemeScheme.ROSE)
        var appearance by mutableStateOf(AppearanceMode.LIGHT)
        val seen = mutableListOf<Seen>()
        compose.setContent {
            MensisTheme(scheme = scheme, appearance = appearance) {
                val semantic = LocalMensisColors.current
                val primary = MaterialTheme.colorScheme.primary
                SideEffect { seen += Seen(scheme, appearance, primary, semantic) }
            }
        }

        for (mode in listOf(AppearanceMode.LIGHT, AppearanceMode.DARK)) {
            for (next in ThemeScheme.entries) {
                appearance = mode
                scheme = next
                compose.waitForIdle()
            }
        }

        val expected = mapOf(AppearanceMode.LIGHT to MensisColorsLight, AppearanceMode.DARK to MensisColorsDark)
        for (mode in expected.keys) {
            val inMode = seen.filter { it.appearance == mode }
            assertEquals(ThemeScheme.entries.toSet(), inMode.map { it.scheme }.toSet())
            inMode.forEach { assertEquals("${it.scheme} ${it.appearance}", expected[mode], it.semantic) }
            // The schemes themselves do change.
            assertNotEquals(
                inMode.last { it.scheme == ThemeScheme.ROSE }.primary,
                inMode.last { it.scheme == ThemeScheme.SAGE }.primary,
            )
        }
    }

    @Test
    fun themeScreen_selectsSchemeAndAppearance() {
        var picked: ThemeScheme? = null
        var appearance: AppearanceMode? = null
        compose.setContent {
            MensisTheme {
                ThemeContent(
                    state = ThemeUiState(ThemeScheme.ROSE, AppearanceMode.SYSTEM),
                    onScheme = { picked = it },
                    onAppearance = { appearance = it },
                    onBack = {},
                    dynamicAvailable = true,
                )
            }
        }

        compose.onNodeWithText("Warm neutrals, green accent").performClick()
        assertEquals(ThemeScheme.SAGE, picked)
        compose.onNodeWithText("From your wallpaper (Material You)").performClick()
        assertEquals(ThemeScheme.DYNAMIC, picked)
        compose.onNodeWithText("Dark").performClick()
        assertEquals(AppearanceMode.DARK, appearance)
        compose
            .onNodeWithText(
                "Period red, fertile teal and the predicted hatch stay the same in every theme, so the calendar " +
                    "reads the same way whichever colour you choose.",
            ).assertExists()
    }

    @Test
    fun systemColours_needAndroid12() {
        var picked: ThemeScheme? = null
        compose.setContent {
            MensisTheme {
                ThemeContent(
                    state = ThemeUiState(ThemeScheme.ROSE, AppearanceMode.LIGHT),
                    onScheme = { picked = it },
                    onAppearance = {},
                    onBack = {},
                    dynamicAvailable = false,
                )
            }
        }

        compose.onNodeWithText("Needs Android 12 or later").performClick()
        assertEquals(null, picked)
        compose.onNodeWithText("System colours").assertExists()
        compose.onNodeWithText("Rose").assertExists()
        compose.onNodeWithText("Needs Android 12 or later").assertIsNotEnabled()
    }
}
