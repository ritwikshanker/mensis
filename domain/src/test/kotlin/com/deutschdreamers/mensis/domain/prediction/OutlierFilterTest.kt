package com.deutschdreamers.mensis.domain.prediction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OutlierFilterTest {
    @Test
    fun `a 60-day cycle is discarded by the absolute bounds`() {
        val result = OutlierFilter.filter(listOf(28, 29, 60, 28, 30))
        assertEquals(listOf(28, 29, 28, 30), result.kept)
        assertEquals(listOf(60), result.discarded)
    }

    @Test
    fun `a value far from the median is discarded by the MAD rule`() {
        val result = OutlierFilter.filter(listOf(28, 28, 29, 28, 45, 28))
        assertEquals(listOf(28, 28, 29, 28, 28), result.kept)
        assertEquals(listOf(45), result.discarded)
    }

    @Test
    fun `identical lengths are all kept thanks to the MAD floor`() {
        val result = OutlierFilter.filter(listOf(28, 28, 28, 28))
        assertEquals(4, result.kept.size)
    }

    @Test
    fun `irregular but plausible lengths are kept`() {
        val lengths = listOf(24, 33, 26, 35, 25, 34)
        assertEquals(lengths, OutlierFilter.filter(lengths).kept)
    }

    @Test
    fun `median of even and odd lists`() {
        assertEquals(28.5, OutlierFilter.median(listOf(28.0, 29.0)))
        assertEquals(29.0, OutlierFilter.median(listOf(30.0, 28.0, 29.0)))
        assertEquals(0.0, OutlierFilter.median(emptyList()))
    }
}
