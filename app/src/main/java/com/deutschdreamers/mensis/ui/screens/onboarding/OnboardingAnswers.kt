package com.deutschdreamers.mensis.ui.screens.onboarding

import com.deutschdreamers.mensis.data.repo.DayEntryRepository
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.domain.model.Flow
import com.deutschdreamers.mensis.domain.reminders.ReminderType
import javax.inject.Inject

/**
 * Writes what onboarding asked. Settings and days an import from step 1 brought along stay as the file
 * had them: the typical lengths, goal and reminders are written only without imported settings, the
 * last period start only without imported days.
 */
class OnboardingAnswers
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val dayEntries: DayEntryRepository,
    ) {
        /**
         * When notifications were allowed on the last step, the period reminder is switched on with its
         * defaults (2 days before, 09:00).
         */
        suspend fun save(
            draft: OnboardingDraft,
            remindersAllowed: Boolean,
        ) {
            if (!draft.hasHistory) draft.lastPeriodStart?.let { dayEntries.setFlow(it, Flow.MEDIUM) }
            if (draft.hasSettings) return
            settings.updateTracker {
                it.copy(
                    typicalCycleLength = draft.cycleLength,
                    typicalPeriodLength = draft.periodLength,
                    goal = draft.goal,
                )
            }
            if (remindersAllowed) {
                settings.updateReminders { it.update(ReminderType.PERIOD) { s -> s.copy(enabled = true) } }
            }
        }
    }
