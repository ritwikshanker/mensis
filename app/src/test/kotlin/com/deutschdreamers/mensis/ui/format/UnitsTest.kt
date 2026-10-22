package com.deutschdreamers.mensis.ui.format

import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.WeightUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnitsTest {
    @Test
    fun `temperature converts both ways and storage stays celsius`() {
        assertEquals(98.08, Units.toDisplayTemperature(36.71, TemperatureUnit.FAHRENHEIT), 0.0001)
        assertEquals(36.71, Units.toCelsius(98.08, TemperatureUnit.FAHRENHEIT), 0.0001)
        assertEquals(36.71, Units.toDisplayTemperature(36.71, TemperatureUnit.CELSIUS), 0.0)
        assertEquals(36.71, Units.toCelsius(36.712, TemperatureUnit.CELSIUS), 0.0)
    }

    @Test
    fun `weight converts both ways and storage stays kilograms`() {
        assertEquals(136.2, Units.toDisplayWeight(61.8, WeightUnit.POUND), 0.0001)
        assertEquals(61.8, Units.toKilograms(136.2, WeightUnit.POUND), 0.0001)
        assertEquals("kg", Units.weightSymbol(WeightUnit.KILOGRAM))
        assertEquals("°F", Units.temperatureSymbol(TemperatureUnit.FAHRENHEIT))
    }
}
