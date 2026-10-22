package com.deutschdreamers.mensis.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation 3 keys. Every key is serializable so the back stack survives process death. */
sealed interface MensisNavKey : NavKey

/** Placeholder root entry until the Today screen lands in build step 4. */
@Serializable
data object Home : MensisNavKey
