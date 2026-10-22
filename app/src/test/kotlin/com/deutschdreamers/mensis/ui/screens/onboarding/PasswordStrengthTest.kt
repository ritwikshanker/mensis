package com.deutschdreamers.mensis.ui.screens.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PasswordStrengthTest {
    @Test
    fun `score grows with length and character classes`() {
        assertEquals(0, PasswordStrength.score(""))
        assertEquals(1, PasswordStrength.score("short"))
        assertEquals(2, PasswordStrength.score("eightchr"))
        assertEquals(3, PasswordStrength.score("twelve-chars"))
        assertEquals(4, PasswordStrength.score("correct horse battery"))
        assertEquals(2, PasswordStrength.score("aaaaaaaaaaaaaaaaaa"))
    }
}
