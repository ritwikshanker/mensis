package com.deutschdreamers.mensis.ui.screens.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LogParsingTest {
    @ParameterizedTest(name = "\"{0}\" → {1} min")
    @CsvSource(
        "7:20, 440",
        "7h 20, 440",
        "7h20min, 440",
        "7h, 420",
        "7.5, 450",
        "'7,5', 450",
        "0:45, 45",
    )
    fun `sleep accepts the usual spellings`(
        text: String,
        minutes: Int,
    ) {
        assertEquals(minutes, DailyLogViewModel.parseSleep(text))
    }

    @Test
    fun `sleep rejects nonsense`() {
        assertNull(DailyLogViewModel.parseSleep("7:75"))
        assertNull(DailyLogViewModel.parseSleep("lots"))
        assertNull(DailyLogViewModel.parseSleep(""))
    }

    @Test
    fun `decimals accept dot or comma with up to two places`() {
        assertEquals(36.71, DailyLogViewModel.parseDecimal("36.71"))
        assertEquals(36.7, DailyLogViewModel.parseDecimal("36,7"))
        assertEquals(61.0, DailyLogViewModel.parseDecimal(" 61 "))
        assertNull(DailyLogViewModel.parseDecimal("36.712"))
        assertNull(DailyLogViewModel.parseDecimal("abc"))
        assertEquals("7:05", DailyLogViewModel.formatSleepInput(425))
    }
}
