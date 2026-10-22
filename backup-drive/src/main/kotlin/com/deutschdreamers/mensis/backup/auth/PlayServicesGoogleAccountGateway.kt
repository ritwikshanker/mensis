package com.deutschdreamers.mensis.backup.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.deutschdreamers.mensis.backup.BuildConfig
import com.deutschdreamers.mensis.backup.drive.DriveAppDataClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Credential Manager's "Sign in with Google" for the account, Play services' AuthorizationClient for
 * the `drive.appdata` token. The ID token Credential Manager returns is read for the e-mail address
 * only and never sent anywhere.
 */
@Singleton
class PlayServicesGoogleAccountGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : GoogleAccountGateway {
        private val authorizationClient by lazy { Identity.getAuthorizationClient(context) }
        private val scope = Scope(DriveAppDataClient.SCOPE_APP_DATA)

        override fun availability(): BackupAvailability =
            when {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                    context,
                ) != ConnectionResult.SUCCESS ->
                    BackupAvailability.NO_PLAY_SERVICES
                BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() -> BackupAvailability.NOT_CONFIGURED
                else -> BackupAvailability.AVAILABLE
            }

        override suspend fun signIn(activity: Activity): SignInResult {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            return try {
                val credential = CredentialManager.create(activity).getCredential(activity, request).credential
                if (credential is CustomCredential && credential.type in GOOGLE_CREDENTIAL_TYPES) {
                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                    SignInResult.SignedIn(google.email.takeUnless { it.isNullOrBlank() } ?: google.id)
                } else {
                    SignInResult.Failed
                }
            } catch (expected: GetCredentialCancellationException) {
                SignInResult.Cancelled
            } catch (expected: NoCredentialException) {
                SignInResult.NoAccount
            } catch (expected: GetCredentialException) {
                SignInResult.Failed
            } catch (expected: GoogleIdTokenParsingException) {
                SignInResult.Failed
            }
        }

        override suspend fun authorize(email: String): AuthorizationOutcome {
            val request =
                AuthorizationRequest
                    .builder()
                    .setRequestedScopes(listOf(scope))
                    .setAccount(Account(email, ACCOUNT_TYPE))
                    .build()
            val result =
                try {
                    authorizationClient.authorize(request).await()
                } catch (e: ApiException) {
                    if (e.statusCode == CommonStatusCodes.NETWORK_ERROR) throw IOException("no connection", e)
                    throw GoogleAuthException("authorization failed (${e.statusCode})", e)
                }
            val pending = result.pendingIntent
            return when {
                result.hasResolution() && pending != null -> AuthorizationOutcome.NeedsConsent(pending)
                else ->
                    AuthorizationOutcome.Granted(
                        result.accessToken ?: throw GoogleAuthException("no access token"),
                    )
            }
        }

        override fun tokenFromConsent(data: Intent?): String? =
            try {
                data?.let { authorizationClient.getAuthorizationResultFromIntent(it).accessToken }
            } catch (expected: ApiException) {
                null
            }

        override suspend fun clearToken(token: String) {
            try {
                authorizationClient.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
            } catch (expected: ApiException) {
                // A token Play services no longer knows needs no clearing.
            }
        }

        override suspend fun disconnect(email: String) {
            try {
                val request =
                    RevokeAccessRequest
                        .builder()
                        .setAccount(Account(email, ACCOUNT_TYPE))
                        .setScopes(listOf(scope))
                        .build()
                authorizationClient.revokeAccess(request).await()
            } catch (expected: ApiException) {
                // Offline or already revoked: the user can still remove access at myaccount.google.com.
            }
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (expected: ClearCredentialException) {
                // Nothing cached to clear.
            }
        }

        private companion object {
            const val ACCOUNT_TYPE = "com.google"
            val GOOGLE_CREDENTIAL_TYPES =
                setOf(
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
                    GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL,
                )
        }
    }
