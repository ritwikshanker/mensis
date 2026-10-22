package com.deutschdreamers.mensis.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.format.Formats
import com.deutschdreamers.mensis.ui.theme.MensisTheme

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

/** Design onboarding step 8: 4-segment teal strength bar and "Strong · 14 characters"; also used by export. */
@Composable
fun PasswordStrengthBar(password: String) {
    val score = PasswordStrength.score(password)
    val teal = MensisTheme.colors.fertile
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(PasswordStrength.SEGMENTS) { index ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (index <
                                    score
                                ) {
                                    teal
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                CircleShape,
                            ),
                )
            }
        }
        Text(
            text =
                if (password.isEmpty()) {
                    stringResource(R.string.onboarding_password_hint)
                } else {
                    "${stringResource(
                        PasswordStrength.labelRes(score),
                    )} ${Formats.MIDDLE_DOT} ${password.length} characters"
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
