package com.deutschdreamers.mensis.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.ui.preview.FakeData
import com.deutschdreamers.mensis.ui.screens.insights.CycleLengthChart
import com.deutschdreamers.mensis.ui.screens.insights.CycleLengthChartUi
import com.deutschdreamers.mensis.ui.screens.insights.InsightsScreenContent
import com.deutschdreamers.mensis.ui.screens.insights.InsightsUiBuilder
import com.deutschdreamers.mensis.ui.screens.insights.InsightsUiState
import com.deutschdreamers.mensis.ui.screens.insights.TemperatureChart
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsightsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: InsightsUiState) {
        compose.setContent { MensisTheme(appearance = AppearanceMode.LIGHT) { InsightsScreenContent(state) } }
    }

    /** How many pixels of the node's capture are exactly [color]. */
    private fun pixelsOf(
        tag: String,
        color: Color,
    ): Int {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val argb = color.toArgb()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) if (pixels[x, y].toArgb() == argb) count++
        }
        return count
    }

    @Test
    fun cycleLengthChart_drawsBarsFor0_1And8Cycles() {
        val empty = CycleLengthChartUi(emptyList(), 8, 0, 10, null, InsightsUiBuilder.cycleChartDescription(null))
        val one = requireNotNull(FakeData.insights(cycles = 1).cycleLength).chart
        val eight = requireNotNull(FakeData.insights(cycles = 8).cycleLength).chart
        var primary = Color.Unspecified
        var container = Color.Unspecified
        compose.setContent {
            MensisTheme(appearance = AppearanceMode.LIGHT) {
                primary = MaterialTheme.colorScheme.primary
                container = MaterialTheme.colorScheme.primaryContainer
                Column {
                    CycleLengthChart(empty, Modifier.testTag("c0"))
                    CycleLengthChart(one, Modifier.testTag("c1"))
                    CycleLengthChart(eight, Modifier.testTag("c8"))
                }
            }
        }
        compose.onNode(hasContentDescription("Cycle length chart, no complete cycles yet.")).assertIsDisplayed()
        compose.onNode(hasContentDescription("oldest first: 6 Sep, 29 days.", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("oldest first: 12 Feb, 29 days;", substring = true)).assertIsDisplayed()
        assertEquals(0, pixelsOf("c0", primary))
        assertEquals(0, pixelsOf("c0", container))
        assertTrue(pixelsOf("c1", primary) > 0)
        assertEquals(0, pixelsOf("c1", container))
        assertTrue(pixelsOf("c8", primary) > 0)
        // Seven older bars in primaryContainer, each wider than the latest's share of primary.
        assertTrue(pixelsOf("c8", container) > 6 * pixelsOf("c8", primary))
    }

    @Test
    fun temperatureChart_drawsForTheOpenCycleWith0_1And8CompleteCycles() {
        val charts = listOf(0, 1, 8).map { requireNotNull(FakeData.insights(cycles = it).temperature?.chart) }
        var primary = Color.Unspecified
        compose.setContent {
            MensisTheme(appearance = AppearanceMode.LIGHT) {
                primary = MaterialTheme.colorScheme.primary
                Column { charts.forEachIndexed { i, chart -> TemperatureChart(chart, Modifier.testTag("t$i")) } }
            }
        }
        for (i in charts.indices) {
            compose.onNodeWithTag("t$i").assertIsDisplayed()
            assertTrue(pixelsOf("t$i", primary) > 0)
        }
        // The open cycle is the same in all three fixtures, so all three charts say the same.
        compose
            .onAllNodes(hasContentDescription("Coverline 36.45 °C. Shift from day 15 to day 17.", substring = true))
            .assertCountEquals(3)
    }

    @Test
    fun eightCycles_showDesignNumbersWithMergedSummaries() {
        show(FakeData.insights())
        compose.onNodeWithText("8 cycles").assertIsDisplayed()
        compose
            .onNode(
                hasText("Cycle length") and hasText("29") and hasText("SD 1.6 d", substring = true),
            ).assertIsDisplayed()
        compose.onNode(hasText("Period length") and hasText("Range 4–6 · heaviest on day 2")).assertIsDisplayed()
        compose
            .onNode(
                hasText("Luteal phase") and hasText("Range 13–15 · 6 cycles with a confirmed shift"),
            ).assertIsDisplayed()
        compose
            .onNodeWithText("Rise of 0.3 °C held for 3 days from day 15. Ovulation estimated day 14 (18 Oct).")
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNode(hasContentDescription("Waking temperature, day 1 to day 32: 17 readings", substring = true))
            .assertExists()
        compose
            .onNode(
                hasText("Cramps on period days 1–2") and hasText("8 of 8 cycles"),
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("Headache in the 3 days before a period") and hasText("6 of 8 cycles")).assertExists()
        compose.onNode(hasText("Fatigue in the late luteal phase") and hasText("5 of 8 cycles")).assertExists()
        compose
            .onNodeWithText(
                "Computed on this phone from your entries only. Means and ranges use the last 8 complete cycles.",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun noCompleteCycle_explainsWhatIsMissing() {
        show(FakeData.insights(cycles = 0))
        compose.onNodeWithText("8 cycles").assertDoesNotExist()
        compose
            .onNodeWithText("Means and ranges appear after your first complete cycle, when the next period starts.")
            .assertIsDisplayed()
        compose.onNodeWithText("Period length").assertDoesNotExist()
        compose.onNodeWithText("Luteal phase").assertDoesNotExist()
        compose.onNodeWithText("Waking temperature, this cycle").assertIsDisplayed()
        compose.onNodeWithText("Patterns appear once 2 cycles are complete.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun symptomsLevel_hasNoTemperatureCard() {
        show(FakeData.insights(trackingLevel = TrackingLevel.SYMPTOMS))
        compose.onNodeWithText("Waking temperature, this cycle").assertDoesNotExist()
        compose.onNode(hasText("Range 13–15 · 6 cycles with a confirmed shift")).assertIsDisplayed()
        compose.onNodeWithText("Recurring symptoms").performScrollTo().assertIsDisplayed()
    }
}
