package com.deutschdreamers.mensis.lock

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.deutschdreamers.mensis.R

/**
 * Shows the system unlock sheet. Biometrics with the device credential as fallback; "Use PIN" asks
 * for the credential directly where the platform supports that (Android 11+).
 */
class BiometricUnlock(
    private val activity: FragmentActivity,
    private val onUnlocked: () -> Unit,
    private val onError: (CharSequence) -> Unit,
) {
    private val prompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    if (errorCode !in DISMISSED_BY_USER) onError(errString)
                }
            },
        )

    fun authenticate(credentialOnly: Boolean = false) {
        val authenticators =
            if (credentialOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                DEVICE_CREDENTIAL
            } else {
                DeviceCredentialChecker.AUTHENTICATORS
            }
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(activity.getString(R.string.unlock_prompt_title))
                .setSubtitle(activity.getString(R.string.unlock_prompt_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build()
        prompt.authenticate(info)
    }

    private companion object {
        val DISMISSED_BY_USER =
            setOf(
                BiometricPrompt.ERROR_USER_CANCELED,
                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                BiometricPrompt.ERROR_CANCELED,
            )
    }
}
