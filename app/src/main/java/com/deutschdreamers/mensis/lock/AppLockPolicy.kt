package com.deutschdreamers.mensis.lock

import com.deutschdreamers.mensis.data.settings.UiPrefs

/**
 * Pure decision logic for the app lock. The lock is a UI gate: it never affects whether the database
 * can be read, only whether the Locked screen covers the UI.
 */
object AppLockPolicy {
    private const val MILLIS_PER_MINUTE = 60_000L

    /**
     * A trip to a system file picker or share sheet that the app started itself does not count as
     * leaving the app, as long as it returns within this time.
     */
    const val EXTERNAL_TRIP_LIMIT_MILLIS: Long = 5 * MILLIS_PER_MINUTE

    /** A cold start locks whenever the lock is enabled and the phone has a credential to unlock with. */
    fun lockOnStart(
        lockEnabled: Boolean,
        credentialAvailable: Boolean,
    ): Boolean = lockEnabled && credentialAvailable

    /**
     * A return from the background locks when the app was away for at least the re-lock timeout.
     * A timeout of 0 locks immediately, [UiPrefs.RELOCK_NEVER] (or any negative value) never re-locks.
     */
    fun lockOnReturn(
        lockEnabled: Boolean,
        credentialAvailable: Boolean,
        relockTimeoutMinutes: Int,
        backgroundMillis: Long,
    ): Boolean {
        if (!lockEnabled || !credentialAvailable) return false
        if (relockTimeoutMinutes < 0) return false
        return backgroundMillis >= relockTimeoutMinutes * MILLIS_PER_MINUTE
    }

    /** True when the round trip to a picker the app opened was short enough to skip the re-lock. */
    fun externalTripExempt(backgroundMillis: Long): Boolean = backgroundMillis < EXTERNAL_TRIP_LIMIT_MILLIS
}
