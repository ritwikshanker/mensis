package com.deutschdreamers.mensis.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether the phone can show an unlock prompt at all. Without an enrolled fingerprint, face or
 * screen lock the app lock is unavailable and the app stays unlocked rather than locking the user out.
 */
class DeviceCredentialChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun canAuthenticate(): Boolean =
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

        companion object {
            /** Weak biometrics or the device PIN/pattern/password: the combination every API level supports. */
            const val AUTHENTICATORS: Int = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }
    }
