package com.deutschdreamers.mensis.ui.screens.backup

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.deutschdreamers.mensis.backup.auth.AuthorizationOutcome
import com.deutschdreamers.mensis.backup.auth.GoogleAccountGateway
import com.deutschdreamers.mensis.backup.auth.GoogleAuthException
import com.deutschdreamers.mensis.backup.auth.SignInResult
import com.deutschdreamers.mensis.lock.AppLockManager
import kotlinx.coroutines.launch
import java.io.IOException

/** How connecting a Google account ended. */
sealed interface ConnectResult {
    data class Connected(
        val email: String,
    ) : ConnectResult

    data object Cancelled : ConnectResult

    data object NoAccount : ConnectResult

    data object Failed : ConnectResult
}

/** Why connecting did not get as far as the account's Drive. */
enum class ConnectProblem { NO_ACCOUNT, FAILED }

/** Starts the connect flow; see [rememberGoogleConnect]. */
fun interface GoogleConnect {
    /** With [email], re-authorizes that account without the account sheet (Reconnect). */
    fun connect(email: String?)
}

/**
 * "Continue with Google": Credential Manager's account sheet, then Drive app-folder access with
 * Google's consent screen when it asks for one. The trips to Google's screens do not re-lock the app.
 */
@Composable
fun rememberGoogleConnect(
    gateway: GoogleAccountGateway,
    lock: AppLockManager,
    onResult: (ConnectResult) -> Unit,
): GoogleConnect {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onResult)
    var pendingEmail by rememberSaveable { mutableStateOf<String?>(null) }
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            lock.endExternalTrip()
            val email = pendingEmail
            pendingEmail = null
            val granted = result.resultCode == Activity.RESULT_OK && gateway.tokenFromConsent(result.data) != null
            report(if (granted && email != null) ConnectResult.Connected(email) else ConnectResult.Cancelled)
        }
    return remember(gateway, activity) {
        GoogleConnect { known ->
            val host = activity ?: return@GoogleConnect
            scope.launch {
                lock.allowExternalTrip()
                val email =
                    known ?: when (val signIn = gateway.signIn(host)) {
                        is SignInResult.SignedIn -> signIn.email
                        SignInResult.Cancelled -> null.also { report(ConnectResult.Cancelled) }
                        SignInResult.NoAccount -> null.also { report(ConnectResult.NoAccount) }
                        SignInResult.Failed -> null.also { report(ConnectResult.Failed) }
                    }
                if (email == null) {
                    lock.endExternalTrip()
                    return@launch
                }
                val outcome =
                    try {
                        gateway.authorize(email)
                    } catch (expected: IOException) {
                        null
                    } catch (expected: GoogleAuthException) {
                        null
                    }
                when (outcome) {
                    null -> {
                        lock.endExternalTrip()
                        report(ConnectResult.Failed)
                    }
                    is AuthorizationOutcome.Granted -> {
                        lock.endExternalTrip()
                        report(ConnectResult.Connected(email))
                    }
                    is AuthorizationOutcome.NeedsConsent -> {
                        pendingEmail = email
                        consent.launch(IntentSenderRequest.Builder(outcome.intent).build())
                    }
                }
            }
        }
    }
}
