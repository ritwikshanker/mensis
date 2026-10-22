package com.deutschdreamers.mensis.backup.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent

/** Whether Drive backup can be offered on this phone and in this build. */
enum class BackupAvailability {
    AVAILABLE,

    /** Google Play services are missing or unusable; export and import still work. */
    NO_PLAY_SERVICES,

    /** This build has no OAuth web client ID (GOOGLE_CLOUD_SETUP.md). */
    NOT_CONFIGURED,
}

sealed interface SignInResult {
    data class SignedIn(
        val email: String,
    ) : SignInResult

    data object Cancelled : SignInResult

    /** No Google account on the phone that can sign in. */
    data object NoAccount : SignInResult

    data object Failed : SignInResult
}

sealed interface AuthorizationOutcome {
    data class Granted(
        val accessToken: String,
    ) : AuthorizationOutcome

    /** Google needs the user's consent (or an account choice) first: launch [intent] from an activity. */
    data class NeedsConsent(
        val intent: PendingIntent,
    ) : AuthorizationOutcome
}

/** Authorization failed for a reason other than the network (which is an [java.io.IOException]). */
class GoogleAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Google sign-in and Drive authorization. Sign-in (Credential Manager) only identifies the account;
 * authorization grants the `drive.appdata` scope and is the only thing later runs repeat.
 */
interface GoogleAccountGateway {
    fun availability(): BackupAvailability

    /** "Continue with Google": the system account sheet. Needs an activity. */
    suspend fun signIn(activity: Activity): SignInResult

    /**
     * An access token for the app folder of [email], or the consent screen to show first.
     * @throws java.io.IOException no connection.
     * @throws GoogleAuthException anything else.
     */
    suspend fun authorize(email: String): AuthorizationOutcome

    /** The access token the consent screen returned, or null when the user declined. */
    fun tokenFromConsent(data: Intent?): String?

    /** Drops a token Drive rejected, so the next [authorize] fetches a fresh one. */
    suspend fun clearToken(token: String)

    /** Revokes Mensis's Drive access for [email] and forgets the sign-in on this phone. Best effort. */
    suspend fun disconnect(email: String)
}
