package com.deutschdreamers.mensis.ui.screens.onboarding

import com.deutschdreamers.mensis.R

/** A coarse 0–4 strength score for the backup password preview; the real policy lands with Drive backup. */
object PasswordStrength {
    const val SEGMENTS: Int = 4
    private const val SCORE_STRONG = 4
    private const val SCORE_GOOD = 3
    private const val SCORE_FAIR = 2
    private const val SCORE_WEAK = 1
    private const val MIN_CLASSES = 2
    private const val FAIR_LENGTH = 8
    private const val GOOD_LENGTH = 12
    private const val STRONG_LENGTH = 14

    fun score(password: String): Int {
        if (password.isEmpty()) return 0
        val classes =
            listOf(
                password.any { it.isLowerCase() },
                password.any { it.isUpperCase() },
                password.any { it.isDigit() },
                password.any { !it.isLetterOrDigit() },
            ).count { it }
        return when {
            password.length >= STRONG_LENGTH && classes >= MIN_CLASSES -> SCORE_STRONG
            password.length >= GOOD_LENGTH && classes >= MIN_CLASSES -> SCORE_GOOD
            password.length >= FAIR_LENGTH -> SCORE_FAIR
            else -> SCORE_WEAK
        }
    }

    fun labelRes(score: Int): Int =
        when (score) {
            SCORE_STRONG -> R.string.password_strong
            SCORE_GOOD -> R.string.password_good
            SCORE_FAIR -> R.string.password_fair
            else -> R.string.password_weak
        }
}
