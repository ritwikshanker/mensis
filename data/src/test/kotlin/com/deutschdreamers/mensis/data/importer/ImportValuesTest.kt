package com.deutschdreamers.mensis.data.importer

import com.deutschdreamers.mensis.domain.model.CervicalMucus
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.model.Mood
import com.deutschdreamers.mensis.domain.model.Symptom
import com.deutschdreamers.mensis.domain.model.TestResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalTime

class ImportValuesTest {
    @ParameterizedTest(name = "flow \"{0}\" → {1}")
    @CsvSource(
        "none, NONE",
        "0, NONE",
        "Spotting, SPOTTING",
        "spot, SPOTTING",
        "light, LIGHT",
        "1, LIGHT",
        "Low, LIGHT",
        "medium, MEDIUM",
        "moderate, MEDIUM",
        "2, MEDIUM",
        "HEAVY, HEAVY",
        "very heavy, HEAVY",
        "3, HEAVY",
        "yes, MEDIUM",
        "x, MEDIUM",
    )
    fun `flow words and numbers`(
        raw: String,
        expected: Flow,
    ) {
        assertEquals(expected, ImportValues.flow(raw))
    }

    @Test
    fun `unknown flow words are not guessed`() {
        assertEquals(null, ImportValues.flow("gushing"))
        assertEquals(null, ImportValues.flow("5"))
    }

    @ParameterizedTest(name = "temperature \"{0}\" (°F default {1}) → {2}")
    @CsvSource(
        "36.62, false, 36.62",
        "'36,62', false, 36.62",
        "36.6 °C, false, 36.6",
        "97.9, true, 36.61",
        "97.9F, false, 36.61",
        "98.6 °F, false, 37.0",
        "36.5C, true, 36.5",
    )
    fun `temperatures end up in Celsius with two decimals`(
        raw: String,
        fahrenheit: Boolean,
        expected: Double,
    ) {
        assertEquals(expected, ImportValues.temperatureC(raw, fahrenheit))
    }

    @Test
    fun `implausible temperatures are rejected rather than stored`() {
        assertEquals(null, ImportValues.temperatureC("97.9"))
        assertEquals(null, ImportValues.temperatureC("3.66"))
        assertEquals(null, ImportValues.temperatureC("36.6", defaultFahrenheit = true))
        assertEquals(null, ImportValues.temperatureC("warm"))
    }

    @Test
    fun `weights in kg or lb end up in kg with one decimal`() {
        assertEquals(61.8, ImportValues.weightKg("61.8"))
        assertEquals(61.8, ImportValues.weightKg("136.2", pounds = true))
        assertEquals(61.8, ImportValues.weightKg("136.2 lb"))
        assertEquals(null, ImportValues.weightKg("6.18"))
    }

    @ParameterizedTest(name = "date \"{0}\" ({1}) → {2}")
    @CsvSource(
        "2026-03-06, DAY_FIRST, 2026-03-06",
        "2026-3-6, DAY_FIRST, 2026-03-06",
        "2026/03/06, DAY_FIRST, 2026-03-06",
        "20260306, DAY_FIRST, 2026-03-06",
        "6.3.2026, MONTH_FIRST, 2026-03-06",
        "06.03.26, DAY_FIRST, 2026-03-06",
        "06/03/2026, DAY_FIRST, 2026-03-06",
        "03/06/2026, MONTH_FIRST, 2026-03-06",
        "6-3-2026, DAY_FIRST, 2026-03-06",
        "6 Mar 2026, DAY_FIRST, 2026-03-06",
        "'March 6, 2026', DAY_FIRST, 2026-03-06",
        "6 September 2026, DAY_FIRST, 2026-09-06",
        "2026-03-06T00:00:00Z, DAY_FIRST, 2026-03-06",
        "2026-03-06 23:59:59, DAY_FIRST, 2026-03-06",
        "06/03/2026 08:15, DAY_FIRST, 2026-03-06",
    )
    fun `date formats`(
        raw: String,
        order: DateOrder,
        expected: String,
    ) {
        assertEquals(LocalDate.parse(expected), ImportValues.date(raw, order))
    }

    @Test
    fun `impossible dates are unreadable`() {
        assertEquals(null, ImportValues.date("2026-02-30"))
        assertEquals(null, ImportValues.date("31/31/2026"))
        assertEquals(null, ImportValues.date("yesterday"))
        assertEquals(null, ImportValues.date("6 Smarch 2026"))
    }

    @Test
    fun `date order is inferred only when a sample decides it`() {
        assertEquals(DateOrder.DAY_FIRST, ImportValues.inferDateOrder(listOf("03/04/2026", "25/04/2026")))
        assertEquals(DateOrder.MONTH_FIRST, ImportValues.inferDateOrder(listOf("03/04/2026", "04/25/2026")))
        assertEquals(null, ImportValues.inferDateOrder(listOf("03/04/2026", "05/06/2026")))
        assertEquals(null, ImportValues.inferDateOrder(listOf("2026-04-25")))
        assertTrue(ImportValues.isSlashedDate("03/04/2026"))
        assertFalse(ImportValues.isSlashedDate("03.04.2026"))
    }

    @Test
    fun `symptom, mood and mucus synonyms`() {
        assertEquals(Symptom.TENDER_BREASTS, ImportValues.symptom("Breast tenderness"))
        assertEquals(Symptom.BACK_PAIN, ImportValues.symptom("back-pain"))
        assertEquals(Symptom.HEADACHE, ImportValues.symptom("Migraine"))
        assertEquals(Mood.LOW, ImportValues.mood("sad"))
        assertEquals(Mood.ANXIOUS, ImportValues.mood("Stressed"))
        assertEquals(CervicalMucus.EGG_WHITE, ImportValues.mucus("Egg white"))
        assertEquals(TestResult.POSITIVE, ImportValues.testResult("+"))
        assertEquals(listOf("cramps", "headache", "bloating"), ImportValues.listItems("cramps; headache,bloating|-"))
    }

    @Test
    fun `times and sleep durations`() {
        assertEquals(LocalTime.of(6, 40), ImportValues.time("6:40"))
        assertEquals(LocalTime.of(6, 40), ImportValues.time("06.40"))
        assertEquals(LocalTime.of(6, 40), ImportValues.time("06:40:00"))
        assertEquals(null, ImportValues.time("25:00"))
        assertEquals(440, ImportValues.sleepMinutes("7:20"))
        assertEquals(440, ImportValues.sleepMinutes("7 h 20 min"))
        assertEquals(450, ImportValues.sleepMinutes("7.5", hours = true))
        assertEquals(7, ImportValues.sleepMinutes("7"))
        assertEquals(null, ImportValues.sleepMinutes("30", hours = true))
    }

    @Test
    fun `blank markers`() {
        listOf("", " ", "-", "—", "n/a", "NA", "null").forEach { assertTrue(ImportValues.isBlankValue(it), it) }
        assertFalse(ImportValues.isBlankValue("0"))
    }
}
