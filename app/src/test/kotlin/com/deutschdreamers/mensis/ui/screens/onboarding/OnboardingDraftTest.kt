package com.deutschdreamers.mensis.ui.screens.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OnboardingDraftTest {
    private fun walk(draft: OnboardingDraft): List<Int> =
        generateSequence(draft.step) { step ->
            if (step == OnboardingDraft.LAST_STEP) null else draft.copy(step = step).nextStep()
        }.toList()

    private fun walkBack(draft: OnboardingDraft): List<Int> =
        generateSequence(draft.step) { step ->
            draft.copy(step = step).previousStep().takeIf { it != step }
        }.toList()

    @Test
    fun `without an import every step is shown`() {
        val draft = OnboardingDraft()
        assertEquals((0..8).toList(), walk(draft))
        assertEquals((8 downTo 0).toList(), walkBack(draft.copy(step = 8)))
        assertFalse((0..8).any(draft::isAnswered))
    }

    @Test
    fun `imported days skip only the last period question`() {
        val draft = OnboardingDraft(hasHistory = true)
        assertEquals(listOf(0, 1, 3, 4, 5, 6, 7, 8), walk(draft))
        assertEquals(1, draft.copy(step = 3).previousStep())
    }

    @Test
    fun `imported settings go from the welcome step straight to the app lock`() {
        val draft = OnboardingDraft(hasHistory = true, hasSettings = true)
        assertEquals(OnboardingDraft.LOCK_STEP, draft.nextStep())
        assertEquals(listOf(0, 5, 6, 7, 8), walk(draft))
        assertEquals(listOf(5, 0), walkBack(draft.copy(step = OnboardingDraft.LOCK_STEP)))
    }

    @Test
    fun `settings without days skip the same steps`() {
        val draft = OnboardingDraft(hasSettings = true)
        assertEquals(listOf(0, 5, 6, 7, 8), walk(draft))
    }

    @Test
    fun `the welcome step and the device steps are never answered by a file`() {
        val draft = OnboardingDraft(hasHistory = true, hasSettings = true)
        for (step in listOf(0, OnboardingDraft.LOCK_STEP, OnboardingDraft.BACKUP_STEP, OnboardingDraft.LAST_STEP)) {
            assertFalse(draft.isAnswered(step), "step $step")
        }
        assertEquals(0, draft.previousStep())
    }
}
