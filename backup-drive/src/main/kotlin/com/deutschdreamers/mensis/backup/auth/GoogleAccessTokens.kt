package com.deutschdreamers.mensis.backup.auth

import com.deutschdreamers.mensis.backup.drive.AccessTokenSource
import com.deutschdreamers.mensis.backup.drive.ReconnectRequiredException

/**
 * Tokens for one account without user interaction: AuthorizationClient hands out a cached or
 * refreshed token while the grant stands. When it asks for consent instead, the grant was revoked
 * or expired and only the user can renew it ([ReconnectRequiredException]).
 */
class GoogleAccessTokens(
    private val gateway: GoogleAccountGateway,
    private val email: String,
) : AccessTokenSource {
    override suspend fun token(): String =
        when (val outcome = gateway.authorize(email)) {
            is AuthorizationOutcome.Granted -> outcome.accessToken
            is AuthorizationOutcome.NeedsConsent -> throw ReconnectRequiredException()
        }

    override suspend fun invalidate(token: String) = gateway.clearToken(token)
}
