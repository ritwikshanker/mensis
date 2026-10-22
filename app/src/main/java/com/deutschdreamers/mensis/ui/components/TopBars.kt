package com.deutschdreamers.mensis.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R

/** The design's 64 dp top bar: title 22/28, optional back arrow, trailing actions. */
@Composable
fun MensisTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backIcon: Int = R.drawable.ms_arrow_back,
    backDescription: String = stringResource(R.string.action_back),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = containerColor, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    MsIcon(resId = backIcon, contentDescription = backDescription)
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1,
            )
            trailing()
        }
    }
}
