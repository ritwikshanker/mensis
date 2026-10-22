package com.deutschdreamers.mensis

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.deutschdreamers.mensis.data.settings.UiPrefs
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import com.deutschdreamers.mensis.lock.AppLockManager
import com.deutschdreamers.mensis.lock.LockState
import com.deutschdreamers.mensis.lock.SecureFlagController
import com.deutschdreamers.mensis.ui.navigation.MensisNavDisplay
import com.deutschdreamers.mensis.ui.screens.lock.LockGate
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Single activity. A FragmentActivity because BiometricPrompt hosts its UI in a fragment. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var uiPrefsStore: UiPrefsStore

    private var prefs by mutableStateOf<UiPrefs?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Privacy rule 5: secure from the first frame; the preference can only relax it once it is read.
        SecureFlagController.apply(window, hideInRecents = true)
        splash.setKeepOnScreenCondition { prefs == null || appLockManager.state.value == LockState.UNKNOWN }
        enableEdgeToEdge()
        lifecycleScope.launch {
            uiPrefsStore.prefs.collect {
                prefs = it
                SecureFlagController.apply(window, it.hideInRecents)
            }
        }
        setContent {
            val current = prefs ?: return@setContent
            val lockState by appLockManager.state.collectAsStateWithLifecycle()
            MensisTheme(scheme = current.theme, appearance = current.appearance) {
                // The lock is a gate for existing data: a fresh install walks through onboarding first.
                LockGate(
                    locked = lockState == LockState.LOCKED && current.onboardingCompleted,
                    onUnlocked = appLockManager::unlock,
                ) {
                    MensisNavDisplay(startAtOnboarding = !current.onboardingCompleted)
                }
            }
        }
    }
}
