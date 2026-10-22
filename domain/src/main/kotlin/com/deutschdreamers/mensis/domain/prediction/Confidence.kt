package com.deutschdreamers.mensis.domain.prediction

/** The 3-pip confidence mark shown with every prediction. */
enum class Confidence(
    val pips: Int,
) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    ;

    companion object {
        /** Confidence from the number of kept cycles a prediction rests on. */
        fun forCycleCount(count: Int): Confidence =
            when {
                count >= PredictionConstants.PIPS_THREE_MIN_CYCLES -> HIGH
                count >= PredictionConstants.PIPS_TWO_MIN_CYCLES -> MEDIUM
                count >= PredictionConstants.PIPS_ONE_MIN_CYCLES -> LOW
                else -> NONE
            }
    }
}
