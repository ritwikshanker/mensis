package com.deutschdreamers.mensis.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeScheme { ROSE, SAGE, DYNAMIC }

enum class AppearanceMode { LIGHT, DARK, SYSTEM }

/**
 * Preferences that must be readable before the encrypted database is unlocked: theme, app lock and
 * screenshot blocking. They contain no health data and live in an unencrypted DataStore.
 */
data class UiPrefs(
    val theme: ThemeScheme = ThemeScheme.ROSE,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val lockEnabled: Boolean = true,
    val hideInRecents: Boolean = true,
    /** Minutes in the background before the app locks again; 0 = immediately, [RELOCK_NEVER] = never. */
    val relockTimeoutMinutes: Int = DEFAULT_RELOCK_MINUTES,
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        const val DEFAULT_RELOCK_MINUTES: Int = 1
        const val RELOCK_NEVER: Int = -1
    }
}

class UiPrefsStore(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            context.preferencesDataStoreFile(FILE_NAME)
        },
    )

    /** An unreadable preferences file falls back to the defaults rather than blocking the app behind the splash. */
    val prefs: Flow<UiPrefs> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it.toUiPrefs() }

    suspend fun current(): UiPrefs = prefs.first()

    suspend fun update(transform: (UiPrefs) -> UiPrefs) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toUiPrefs())
            prefs[THEME] = updated.theme.name
            prefs[APPEARANCE] = updated.appearance.name
            prefs[LOCK_ENABLED] = updated.lockEnabled
            prefs[HIDE_IN_RECENTS] = updated.hideInRecents
            prefs[RELOCK_MINUTES] = updated.relockTimeoutMinutes
            prefs[ONBOARDING_COMPLETED] = updated.onboardingCompleted
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toUiPrefs(): UiPrefs {
        val defaults = UiPrefs()
        return UiPrefs(
            theme = ThemeScheme.entries.firstOrNull { it.name == this[THEME] } ?: defaults.theme,
            appearance = AppearanceMode.entries.firstOrNull { it.name == this[APPEARANCE] } ?: defaults.appearance,
            lockEnabled = this[LOCK_ENABLED] ?: defaults.lockEnabled,
            hideInRecents = this[HIDE_IN_RECENTS] ?: defaults.hideInRecents,
            relockTimeoutMinutes = this[RELOCK_MINUTES] ?: defaults.relockTimeoutMinutes,
            onboardingCompleted = this[ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
        )
    }

    private companion object {
        const val FILE_NAME = "ui_prefs"
        val THEME = stringPreferencesKey("theme")
        val APPEARANCE = stringPreferencesKey("appearance")
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
        val RELOCK_MINUTES = intPreferencesKey("relock_timeout_minutes")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
