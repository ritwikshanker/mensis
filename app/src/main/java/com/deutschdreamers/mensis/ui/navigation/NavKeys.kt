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

/** Settings → Reminders and the Contraception reminder row (design §10). */
@Serializable
data object Reminders : MensisNavKey

/** Settings → Export data (design §13). */
@Serializable
data object Export : MensisNavKey

/** Settings → Import data, Today's "Import history" and onboarding step 1 (design §14). */
@Serializable
data object Import : MensisNavKey

/** Settings → Report for a doctor (design §15). */
@Serializable
data object Report : MensisNavKey

/** Settings → Backup (design §11); [reconnect] opens Google's reconnect screen at once (Today's banner). */
@Serializable
data class Backup(
    val reconnect: Boolean = false,
) : MensisNavKey

/** Restore from a Drive backup (design §12), from onboarding step 1 or from Backup. */
@Serializable
data class Restore(
    val fromOnboarding: Boolean,
) : MensisNavKey

/** Settings → Mode: cycle tracking or pregnancy. */
@Serializable
data object Mode : MensisNavKey

/** Settings → Units: °C/°F and kg/lb for entry and display. */
@Serializable
data object MeasurementUnits : MensisNavKey

/** Settings → Theme (design §16). */
@Serializable
data object Theme : MensisNavKey

/** Settings → About and privacy (design §17). */
@Serializable
data object About : MensisNavKey

/** The texts behind About's Licence and Open-source libraries rows. */
@Serializable
enum class NoticeDocument { LICENCE, LIBRARIES }

@Serializable
data class Notices(
    val document: NoticeDocument,
) : MensisNavKey
