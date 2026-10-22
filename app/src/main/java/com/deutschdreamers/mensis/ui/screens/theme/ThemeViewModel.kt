package com.deutschdreamers.mensis.ui.screens.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deutschdreamers.mensis.data.repo.SettingsRepository
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThemeUiState(
    val scheme: ThemeScheme,
    val appearance: AppearanceMode,
)

/** Theme and appearance live in the UI preferences, so the activity re-themes as soon as they change. */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<ThemeUiState?> =
            settings.uiPrefs
                .map { ThemeUiState(it.theme, it.appearance) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun setScheme(scheme: ThemeScheme) {
            viewModelScope.launch { settings.updateUiPrefs { it.copy(theme = scheme) } }
        }

        fun setAppearance(appearance: AppearanceMode) {
            viewModelScope.launch { settings.updateUiPrefs { it.copy(appearance = appearance) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
