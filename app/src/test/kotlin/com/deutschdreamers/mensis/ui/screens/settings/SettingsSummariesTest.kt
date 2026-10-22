package com.deutschdreamers.mensis.ui.screens.settings

import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.model.WeightUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsSummariesTest {
    @Test
    fun `row summaries read like the design`() {
        assertEquals("Cycle tracking", SettingsViewModel.modeSummary(TrackerMode.CYCLE))
        assertEquals("Pregnancy · predictions paused", SettingsViewModel.modeSummary(TrackerMode.PREGNANCY))
        assertEquals("°C · kg", SettingsViewModel.unitsSummary(TemperatureUnit.CELSIUS, WeightUnit.KILOGRAM))
        assertEquals("°F · lb", SettingsViewModel.unitsSummary(TemperatureUnit.FAHRENHEIT, WeightUnit.POUND))
        assertEquals("Rose · Light", SettingsViewModel.themeSummary(ThemeScheme.ROSE, AppearanceMode.LIGHT))
        assertEquals("Sage · Dark", SettingsViewModel.themeSummary(ThemeScheme.SAGE, AppearanceMode.DARK))
        assertEquals(
            "System colours · System",
            SettingsViewModel.themeSummary(ThemeScheme.DYNAMIC, AppearanceMode.SYSTEM),
        )
    }
}
