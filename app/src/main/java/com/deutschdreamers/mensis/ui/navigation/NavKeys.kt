package com.deutschdreamers.mensis.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 keys. Every key is serializable so the back stack survives process death. */
sealed interface MensisNavKey : NavKey

/** First-run flow (design §1). */
@Serializable
data object Onboarding : MensisNavKey

/** The tabbed root: Today, Calendar, Insights, Settings. */
@Serializable
data object Main : MensisNavKey

/** Settings → What to track (design §8). */
@Serializable
data object TrackLevel : MensisNavKey

/** Settings → Contraception (design §9). */
@Serializable
data object Contraception : MensisNavKey

/** Screens that arrive in later build steps; their Settings rows already navigate somewhere. */
@Serializable
enum class LaterScreen { MODE, UNITS, REMINDERS, BACKUP, RESTORE, EXPORT, IMPORT, REPORT, DELETE_ALL, THEME, ABOUT }

@Serializable
data class ComingSoon(
    val screen: LaterScreen,
) : MensisNavKey
