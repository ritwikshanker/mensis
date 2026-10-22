package com.deutschdreamers.mensis.ui.format

import com.deutschdreamers.mensis.domain.model.TemperatureUnit
import com.deutschdreamers.mensis.domain.model.WeightUnit
import kotlin.math.roundToInt

/** Unit conversion happens only at entry and display; storage, engine and export stay in °C and kg. */
object Units {
    private const val F_SCALE = 9.0 / 5.0
    private const val F_OFFSET = 32.0
    private const val LB_PER_KG = 2.20462262
    private const val TWO_DECIMALS = 100.0
    private const val ONE_DECIMAL = 10.0

    fun toDisplayTemperature(
        celsius: Double,
        unit: TemperatureUnit,
    ): Double =
        when (unit) {
            TemperatureUnit.CELSIUS -> celsius
            TemperatureUnit.FAHRENHEIT -> round2(celsius * F_SCALE + F_OFFSET)
        }

    fun toCelsius(
        display: Double,
        unit: TemperatureUnit,
    ): Double =
        when (unit) {
            TemperatureUnit.CELSIUS -> round2(display)
            TemperatureUnit.FAHRENHEIT -> round2((display - F_OFFSET) / F_SCALE)
        }

    fun toDisplayWeight(
        kg: Double,
        unit: WeightUnit,
    ): Double =
        when (unit) {
            WeightUnit.KILOGRAM -> kg
            WeightUnit.POUND -> round1(kg * LB_PER_KG)
        }

    fun toKilograms(
        display: Double,
        unit: WeightUnit,
    ): Double =
        when (unit) {
            WeightUnit.KILOGRAM -> round1(display)
            WeightUnit.POUND -> round1(display / LB_PER_KG)
        }

    fun temperatureSymbol(unit: TemperatureUnit): String =
        when (unit) {
            TemperatureUnit.CELSIUS -> "°C"
            TemperatureUnit.FAHRENHEIT -> "°F"
        }

    fun weightSymbol(unit: WeightUnit): String =
        when (unit) {
            WeightUnit.KILOGRAM -> "kg"
            WeightUnit.POUND -> "lb"
        }

    private fun round2(value: Double) = (value * TWO_DECIMALS).roundToInt() / TWO_DECIMALS

    private fun round1(value: Double) = (value * ONE_DECIMAL).roundToInt() / ONE_DECIMAL
}
