package com.deutschdreamers.mensis.lock

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.deutschdreamers.mensis.data.settings.UiPrefs
import com.deutschdreamers.mensis.data.settings.UiPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class LockState { UNKNOWN, LOCKED, UNLOCKED }

/**
 * Keeps the lock state for the whole process. Observes the process lifecycle: a cold start locks
 * when the lock is enabled, a return from the background locks after the re-lock timeout from
 * [UiPrefs]. The database stays readable throughout so reminders and backups keep working.
 */
@Singleton
class AppLockManager
    @Inject
    constructor(
        private val uiPrefsStore: UiPrefsStore,
        private val credentials: DeviceCredentialChecker,
    ) : DefaultLifecycleObserver {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mutableState = MutableStateFlow(LockState.UNKNOWN)
        val state: StateFlow<LockState> = mutableState.asStateFlow()

        private var latestPrefs: UiPrefs? = null
        private var backgroundedAt: Long? = null
        private var pendingDecision: Job? = null

        /** Called once from the Application on the main thread. */
        fun start() {
            scope.launch { uiPrefsStore.prefs.collect { latestPrefs = it } }
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

        fun unlock() {
            mutableState.value = LockState.UNLOCKED
        }

        override fun onStart(owner: LifecycleOwner) {
            val backgroundMillis = backgroundedAt?.let { SystemClock.elapsedRealtime() - it }
            backgroundedAt = null
            val cached = latestPrefs
            if (cached != null) {
                decide(cached, backgroundMillis)
            } else {
                pendingDecision?.cancel()
                pendingDecision = scope.launch { decide(uiPrefsStore.current(), backgroundMillis) }
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            backgroundedAt = SystemClock.elapsedRealtime()
            // "Immediately" takes effect on the way out, so nothing shows on the way back in.
            latestPrefs?.let { decide(it, backgroundMillis = 0) }
        }

        private fun decide(
            prefs: UiPrefs,
            backgroundMillis: Long?,
        ) {
            val available = credentials.canAuthenticate()
            // Before onboarding finishes there is nothing to protect and no way to turn the lock off.
            val enabled = prefs.lockEnabled && prefs.onboardingCompleted
            val lock =
                if (backgroundMillis == null) {
                    AppLockPolicy.lockOnStart(enabled, available)
                } else {
                    AppLockPolicy.lockOnReturn(
                        enabled,
                        available,
                        prefs.relockTimeoutMinutes,
                        backgroundMillis,
                    )
                }
            mutableState.value =
                when {
                    lock -> LockState.LOCKED
                    mutableState.value == LockState.UNKNOWN -> LockState.UNLOCKED
                    else -> mutableState.value
                }
        }
    }
