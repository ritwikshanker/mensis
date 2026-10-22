package com.deutschdreamers.mensis.ui.screens.mode

import com.deutschdreamers.mensis.domain.model.TestResult
import com.deutschdreamers.mensis.domain.model.TrackerMode
import com.deutschdreamers.mensis.domain.pregnancy.PregnancyCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PregnancyOfferTest {
    private val testDate = LocalDate.of(2026, 10, 21)
    private val lmp = LocalDate.of(2026, 8, 17)

    @Test
    fun `a test that turns positive in cycle mode offers pregnancy mode from the last period`() {
        assertEquals(
            PregnancyOffer(testDate, lmp),
            PregnancyOffer.onPositive(TrackerMode.CYCLE, before = null, testDate = testDate, lmp = lmp),
        )
        assertEquals(
            PregnancyOffer(testDate, null),
            PregnancyOffer.onPositive(TrackerMode.CYCLE, TestResult.NEGATIVE, testDate, lmp = null),
        )
    }

    @Test
    fun `no offer in pregnancy mode or when the day already held a positive test`() {
        assertNull(PregnancyOffer.onPositive(TrackerMode.PREGNANCY, null, testDate, lmp))
        assertNull(PregnancyOffer.onPositive(TrackerMode.CYCLE, TestResult.POSITIVE, testDate, lmp))
    }

    @Test
    fun `the due line counts from the last period`() {
        assertEquals(
            "Estimated due date 24 May 2027 · week 10, day 3",
            ModeTexts.dueLine(PregnancyCalculator.calculate(lmp, testDate)),
        )
    }
}
