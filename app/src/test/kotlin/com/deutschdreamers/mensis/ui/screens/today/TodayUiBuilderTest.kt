package com.deutschdreamers.mensis.ui.screens.today

import com.deutschdreamers.mensis.data.backup.BackupStatus
import com.deutschdreamers.mensis.domain.model.ContraceptionMethod
import com.deutschdreamers.mensis.domain.model.ContraceptionPeriod
import com.deutschdreamers.mensis.domain.model.TrackingLevel
import com.deutschdreamers.mensis.domain.today.StripCellKind
import com.deutschdreamers.mensis.domain.today.StripLaneKind
import com.deutschdreamers.mensis.domain.today.TodayState
import com.deutschdreamers.mensis.ui.preview.FakeData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TodayUiBuilderTest {
    @Test
    fun `normal luteal day shows cycle day, phase and two range cards`() {
        val state = FakeData.normal()
        assertEquals(TodayState.NORMAL, state.state)
        assertEquals("17", state.numeral)
        assertEquals("Cycle day", state.numeralLabel)
        assertEquals("Luteal phase", state.phase)
        assertTrue(state.supporting.startsWith("Ovulation estimated"), state.supporting)
        assertEquals(listOf("Next period", "Fertile window"), state.cards.map { it.title })
        val next = state.cards[0]
        assertEquals("in 12–14 days", next.supporting)
        assertEquals("6 cycles · ±1 d", next.basis)
        assertEquals(3, next.pips)
        assertEquals("next cycle, estimated", state.cards[1].supporting)
        assertEquals(listOf("36.71 °C · 06:40", "Headache", "Calm", "7 h 20 min"), state.logged.map { it.text })
        assertEquals("Backup is off. Your data exists only on this phone.", state.banner?.text)
    }

    @Test
    fun `on period day two`() {
        val state = FakeData.onPeriod()
        assertEquals(TodayState.ON_PERIOD, state.state)
        assertEquals("Period, day 2", state.phase)
        assertEquals("Your periods usually last 5 days", state.supporting)
        assertEquals(StripCellKind.PERIOD, state.strip?.kindOf(1))
        assertEquals(StripCellKind.PERIOD, state.strip?.kindOf(2))
        assertEquals(StripCellKind.PERIOD_PREDICTED, state.strip?.kindOf(3))
    }

    @Test
    fun `fertile window with full tracking suggests an LH test`() {
        val state = FakeData.fertile()
        assertEquals(TodayState.FERTILE, state.state)
        assertEquals("Fertile window", state.phase)
        assertTrue(state.supporting.endsWith("An LH test can confirm it."), state.supporting)
        assertTrue(state.cards[1].supporting.startsWith("day "), state.cards[1].supporting)
    }

    @Test
    fun `fertile window without temperature tracking estimates from cycle lengths`() {
        val data =
            FakeData.data(
                FakeData.regularHistory(),
                settings = FakeData.settings.copy(trackingLevel = TrackingLevel.SYMPTOMS),
            )
        val state = FakeData.today(data, FakeData.lastStart.plusDays(12))
        assertTrue(state.supporting.endsWith("from your cycle lengths"), state.supporting)
    }

    @Test
    fun `late state hides the range cards and explains neutrally`() {
        val state = FakeData.late()
        assertEquals(TodayState.LATE, state.state)
        assertEquals("3 days later than expected", state.phase)
        assertTrue(state.cards.isEmpty())
        val body = requireNotNull(state.lateCard).body
        assertTrue(body.startsWith("Your period was expected between "), body)
        assertTrue(body.contains("Cycles of 29 days have been normal for you"), body)
        assertTrue(body.endsWith("a pregnancy test is reliable from today."), body)
        assertEquals("Expected", state.laneLabels[StripLaneKind.EXPECTED])
    }

    @Test
    fun `first launch`() {
        val state = FakeData.firstLaunch()
        assertEquals(TodayState.FIRST_LAUNCH, state.state)
        assertEquals("–", state.numeral)
        assertEquals("No cycle data yet", state.phase)
        assertTrue(state.firstLaunchCard)
        assertNotNull(state.stripHelper)
        assertTrue(state.cards.isEmpty())
        assertTrue(state.logged.isEmpty())
    }

    @Test
    fun `combined pill shows pack day, withdrawal bleed and next pack`() {
        val state = FakeData.cyclic()
        assertEquals(TodayState.CYCLIC, state.state)
        assertEquals("Pack day", state.numeralLabel)
        assertEquals("17", state.numeral)
        assertEquals("Active phase, day 17 of 21", state.phase)
        assertTrue(state.supporting.contains("Ovulation is suppressed"), state.supporting)
        assertEquals(listOf("Withdrawal bleed", "Next pack"), state.cards.map { it.title })
        assertEquals("after the 7-day break", state.cards[1].supporting)
        assertEquals("set by the regimen", state.cards[1].basis)
        assertEquals(StripCellKind.BREAK, state.strip?.kindOf(22))
        assertEquals(StripCellKind.PERIOD_PREDICTED, state.strip?.kindOf(23))
        assertEquals("Active", state.laneLabels[StripLaneKind.ACTIVE])
    }

    @Test
    fun `suppressing method pauses predictions and shows the method card`() {
        val state = FakeData.suppressed()
        assertEquals(TodayState.SUPPRESSED, state.state)
        assertEquals("Days since last bleed", state.numeralLabel)
        assertEquals("47", state.numeral)
        assertEquals("Predictions paused", state.phase)
        assertTrue(state.cards.isEmpty())
        val card = requireNotNull(state.methodCard)
        assertEquals("Hormonal IUD", card.title)
        assertEquals("Replace by", card.nextLabel)
        assertEquals("Mar 2031", card.next)
        assertEquals(
            "Bleeding you log stays on the strip. Last 90 days: 3 days of spotting, no full period.",
            card.body,
        )
        assertEquals("Last 32 days", state.laneLabels[StripLaneKind.LAST_DAYS])
    }

    @Test
    fun `copper IUD behaves like no method`() {
        val iud = ContraceptionPeriod(ContraceptionMethod.COPPER_IUD, LocalDate.of(2025, 3, 12))
        val state =
            FakeData.today(
                FakeData.data(FakeData.regularHistory(), contraception = listOf(iud)),
                FakeData.lastStart.plusDays(16),
            )
        assertEquals(TodayState.NORMAL, state.state)
        assertEquals(2, state.cards.size)
        assertNull(state.methodCard)
    }

    @Test
    fun `pregnancy mode swaps the header and hides the strip`() {
        val state = FakeData.pregnancy()
        assertEquals(TodayState.PREGNANCY, state.state)
        assertEquals("9", state.numeral)
        assertEquals("Weeks pregnant", state.numeralLabel)
        assertEquals("Week 9, day 2", state.phase)
        assertEquals("Counted from 17 Aug, first day of last period", state.supporting)
        assertNull(state.strip)
        val card = requireNotNull(state.pregnancyCard)
        assertEquals("24 May 2027", card.dueDate)
        assertEquals("1st", card.trimester)
        assertTrue(card.caption.startsWith("Week 9 of 40."))
    }

    @Test
    fun `backup banners carry the design copy`() {
        val data = FakeData.data(FakeData.regularHistory())
        val today = FakeData.lastStart.plusDays(16)
        assertEquals("Last backup was 23 days ago.", FakeData.today(data, today, BackupStatus.Overdue(23)).banner?.text)
        assertEquals("Reconnect", FakeData.today(data, today, BackupStatus.NeedsReconnect).banner?.action)
        assertNull(FakeData.today(data, today, BackupStatus.Idle(null)).banner)
        assertFalse(
            FakeData
                .today(data, today, BackupStatus.Failing("no connection"))
                .banner
                ?.text
                .isNullOrEmpty(),
        )
    }

    @Test
    fun `cell descriptions name the day, today and the fill`() {
        assertEquals(
            "cycle day 17, today, fertile window, estimated",
            TodayUiBuilder.cellDescription(17, StripCellKind.FERTILE_PREDICTED, true, "Cycle day"),
        )
        assertEquals("pack day 3, nothing", TodayUiBuilder.cellDescription(3, StripCellKind.NONE, false, "Pack day"))
    }

    @Test
    fun `no wording promises contraceptive reliability`() {
        val states =
            listOf(
                FakeData.normal(),
                FakeData.onPeriod(),
                FakeData.fertile(),
                FakeData.late(),
                FakeData.cyclic(),
                FakeData.suppressed(),
            )
        val banned =
            listOf("safe day", "safe days", "protected from pregnancy", "cannot get pregnant", "reliable contraception")
        for (state in states) {
            val text =
                listOf(state.phase, state.supporting, state.lateCard?.body ?: "", state.methodCard?.body ?: "") +
                    state.cards.map { it.supporting }
            for (sentence in text) {
                for (phrase in banned) {
                    assertFalse(
                        sentence.contains(phrase, ignoreCase = true),
                        "'$sentence' contains '$phrase'",
                    )
                }
            }
        }
    }
}
