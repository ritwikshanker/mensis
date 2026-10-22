package com.deutschdreamers.mensis.domain.prediction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ConfidenceTest {
    @ParameterizedTest(name = "{0} cycles → {1} pips")
    @CsvSource("0, 0", "1, 1", "2, 1", "3, 2", "5, 2", "6, 3", "8, 3")
    fun `pips follow the kept cycle count`(
        count: Int,
        pips: Int,
    ) {
        assertEquals(pips, Confidence.forCycleCount(count).pips)
    }
}
