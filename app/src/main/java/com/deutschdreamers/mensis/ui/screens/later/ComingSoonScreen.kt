package com.deutschdreamers.mensis.ui.screens.later

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.navigation.LaterScreen

/** Placeholder for screens that arrive in later build steps, so every Settings row already leads somewhere. */
@Composable
fun ComingSoonScreen(
    screen: LaterScreen,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { MensisTopBar(title = stringResource(screen.titleRes), onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                text = stringResource(R.string.coming_soon_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

val LaterScreen.titleRes: Int
    get() =
        when (this) {
            LaterScreen.MODE -> R.string.settings_mode
            LaterScreen.UNITS -> R.string.settings_units
            LaterScreen.REPORT -> R.string.settings_report
            LaterScreen.DELETE_ALL -> R.string.settings_delete_all
            LaterScreen.THEME -> R.string.settings_theme
            LaterScreen.ABOUT -> R.string.settings_about
        }
