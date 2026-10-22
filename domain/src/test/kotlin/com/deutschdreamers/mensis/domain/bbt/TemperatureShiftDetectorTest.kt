package com.deutschdreamers.mensis.domain.bbt

import com.deutschdreamers.mensis.domain.Synthetic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TemperatureShiftDetectorTest {
    private val start = Synthetic.day1

    private fun readings(entries: List<com.deutschdreamers.mensis.domain.model.DayEntry>) =
        entries.mapNotNull {
            TemperatureReading.from(it)
        }

    @Test
    fun `a clean rise is confirmed on the third elevated reading`() {
        val entries = Synthetic.temperatures(start, lowDays = 8, highDays = 4)
        val shift = checkNotNull(TemperatureShiftDetector.detect(readings(entries)))
        assertEquals(start.plusDays(8), shift.firstElevatedDate)
        assertEquals(start.plusDays(10), shift.confirmedDate)
        assertEquals(start.plusDays(7), shift.ovulationDate)
        assertEquals(36.40, shift.coverlineC, 1e-9)
    }

    @Test
    fun `noisy readings without a real rise never trigger`() {
        for (seed in 1..20) {
            val entries = Synthetic.temperatures(start, lowDays = 30, highDays = 0, noise = 0.12, seed = seed)
            assertNull(TemperatureShiftDetector.detect(readings(entries)), "false shift with seed $seed")
        }
    }

    @Test
    fun `a noisy but real rise is still found`() {
        val entries = Synthetic.temperatures(start, lowDays = 10, highDays = 6, noise = 0.05, seed = 3)
        assertNotNull(TemperatureShiftDetector.detect(readings(entries)))
    }

    @Test
    fun `only two elevated readings are not enough`() {
        val entries = Synthetic.temperatures(start, lowDays = 8, highDays = 2)
        assertNull(TemperatureShiftDetector.detect(readings(entries)))
    }

    @Test
    fun `fewer than six baseline readings are not enough`() {
        val entries = Synthetic.temperatures(start, lowDays = 5, highDays = 3)
        assertNull(TemperatureShiftDetector.detect(readings(entries)))
    }

    @Test
    fun `readings after nine o'clock are skipped by the rule`() {
        val late =
            Synthetic.temperatures(start, lowDays = 8, highDays = 3).map {
                it.copy(temperatureTime = LocalTime.of(9, 30))
            }
        assertNull(TemperatureShiftDetector.detect(readings(late)))
    }

    @Test
    fun `elevated readings spread over too many days do not count`() {
        val low = Synthetic.temperatures(start, lowDays = 8, highDays = 0)
        val high =
            listOf(start.plusDays(8), start.plusDays(11), start.plusDays(14)).map {
                com.deutschdreamers.mensis.domain.model.DayEntry(
                    it,
                    temperatureC = 36.75,
                    temperatureTime = LocalTime.of(6, 30),
                )
            }
        assertNull(TemperatureShiftDetector.detect(readings(low + high)))
    }
}
