package com.deutschdreamers.mensis.lock

import com.deutschdreamers.mensis.data.settings.UiPrefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AppLockPolicyTest {
    @ParameterizedTest(name = "enabled={0} credential={1} timeout={2} min, away {3} ms → lock={4}")
    @CsvSource(
        // lock off: never locks, whatever the timeout
        "false, true, 0, 0, false",
        "false, true, 1, 3600000, false",
        // no fingerprint or PIN on the phone: the lock is unavailable
        "true, false, 0, 0, false",
        "true, false, 5, 3600000, false",
        // immediately (0): locks even after an instant
        "true, true, 0, 0, true",
        "true, true, 0, 1, true",
        // 1 minute: just under stays open, exactly one minute locks
        "true, true, 1, 59999, false",
        "true, true, 1, 60000, true",
        "true, true, 1, 60001, true",
        // 5 and 15 minutes
        "true, true, 5, 299999, false",
        "true, true, 5, 300000, true",
        "true, true, 15, 899999, false",
        "true, true, 15, 900000, true",
        // never (-1) and any other negative value
        "true, true, -1, 0, false",
        "true, true, -1, 86400000, false",
        "true, true, -7, 86400000, false",
    )
    fun `return from background`(
        lockEnabled: Boolean,
        credentialAvailable: Boolean,
        timeoutMinutes: Int,
        backgroundMillis: Long,
        expected: Boolean,
    ) {
        assertEquals(
            expected,
            AppLockPolicy.lockOnReturn(lockEnabled, credentialAvailable, timeoutMinutes, backgroundMillis),
        )
    }

    @Test
    fun `cold start locks only when enabled and a credential exists`() {
        assertTrue(AppLockPolicy.lockOnStart(lockEnabled = true, credentialAvailable = true))
        assertFalse(AppLockPolicy.lockOnStart(lockEnabled = false, credentialAvailable = true))
        assertFalse(AppLockPolicy.lockOnStart(lockEnabled = true, credentialAvailable = false))
    }

    @Test
    fun `the never constant is what the policy treats as never`() {
        assertFalse(AppLockPolicy.lockOnReturn(true, true, UiPrefs.RELOCK_NEVER, Long.MAX_VALUE))
    }

    @Test
    fun `the default timeout is one minute`() {
        assertFalse(AppLockPolicy.lockOnReturn(true, true, UiPrefs.DEFAULT_RELOCK_MINUTES, 30_000))
        assertTrue(AppLockPolicy.lockOnReturn(true, true, UiPrefs.DEFAULT_RELOCK_MINUTES, 60_000))
    }

    @Test
    fun `a short trip to a picker the app opened does not re-lock, a long one does`() {
        assertTrue(AppLockPolicy.externalTripExempt(0))
        assertTrue(AppLockPolicy.externalTripExempt(AppLockPolicy.EXTERNAL_TRIP_LIMIT_MILLIS - 1))
        assertFalse(AppLockPolicy.externalTripExempt(AppLockPolicy.EXTERNAL_TRIP_LIMIT_MILLIS))
    }
}
