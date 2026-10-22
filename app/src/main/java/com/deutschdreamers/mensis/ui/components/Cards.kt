package com.deutschdreamers.mensis.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.theme.SerifStyles

/** `surfaceContainerHigh`, radius 12, 22 dp icon, text and an optional text button. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ms_info,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor, MaterialTheme.shapes.medium)
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MsIcon(
            resId = icon,
            contentDescription = null,
            size = 22.dp,
            tint = iconTint,
            modifier = Modifier.align(Alignment.Top),
        )
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = contentColor,
                )
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A plain content card: `surfaceContainerLow`, radius 16, padding 16. */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().background(containerColor, MaterialTheme.shapes.large).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

/** Onboarding step 4 stepper card: title, supporting, 40 dp round −/+ buttons and a serif value. */
@Composable
fun StepperCard(
    title: String,
    supporting: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 99,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(min)) },
            enabled = value > min,
            modifier = Modifier.size(40.dp),
        ) {
            MsIcon(resId = R.drawable.ms_remove, contentDescription = decreaseDescription)
        }
        Text(
            text = value.toString(),
            style = SerifStyles.statSmall,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { contentDescription = "$title $value" },
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceAtMost(max)) },
            enabled = value < max,
            modifier = Modifier.size(40.dp),
        ) {
            MsIcon(resId = R.drawable.ms_add, contentDescription = increaseDescription)
        }
    }
}

/** Onboarding progress: [total] segments, 4 dp tall with 4 dp gaps; the first [filled] in `primary`. */
@Composable
fun StepProgressBar(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Step $filled of $total" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (index <
                                filled
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            CircleShape,
                        ),
            )
        }
    }
}
