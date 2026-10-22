package com.deutschdreamers.mensis.domain.model

import java.time.LocalDate

/** Which daily-log fields are shown. Hidden fields never delete data. */
enum class TrackingLevel {
    PERIOD,
    SYMPTOMS,
    FULL,
    ;

    val showsSymptoms: Boolean get() = this != PERIOD
    val showsTemperature: Boolean get() = this == FULL
}

/** Onboarding step 5: what the user wants from Mensis. Changes what Today puts first. */
enum class Goal { TRACK_CYCLE, TRYING_TO_CONCEIVE, AVOIDING_PREGNANCY, UNDERSTAND_SYMPTOMS }

enum class TrackerMode { CYCLE, PREGNANCY }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

enum class WeightUnit { KILOGRAM, POUND }

/**
 * The health-relevant settings the engine needs. Typical lengths come from onboarding and are used
 * only until the user's own cycles exist.
 */
data class TrackerSettings(
    val trackingLevel: TrackingLevel = TrackingLevel.SYMPTOMS,
    val goal: Goal = Goal.TRACK_CYCLE,
    val mode: TrackerMode = TrackerMode.CYCLE,
    val pregnancyLmp: LocalDate? = null,
    val typicalCycleLength: Int = DEFAULT_TYPICAL_CYCLE_LENGTH,
    val typicalPeriodLength: Int = DEFAULT_TYPICAL_PERIOD_LENGTH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val weightUnit: WeightUnit = WeightUnit.KILOGRAM,
) {
    companion object {
        /** Onboarding step 4 default. */
        const val DEFAULT_TYPICAL_CYCLE_LENGTH: Int = 29

        /** Onboarding step 4 default. */
        const val DEFAULT_TYPICAL_PERIOD_LENGTH: Int = 5
    }
}
