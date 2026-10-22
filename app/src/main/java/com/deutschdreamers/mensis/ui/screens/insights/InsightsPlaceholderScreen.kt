package com.deutschdreamers.mensis.ui.screens.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.components.MensisTopBar

/** Insights land in build step 8; the tab exists so the bottom navigation is complete. */
@Composable
fun InsightsPlaceholderScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        MensisTopBar(title = stringResource(R.string.tab_insights))
        Text(
            text = stringResource(R.string.coming_soon_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
