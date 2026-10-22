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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.ui.theme.SerifStyles

/** Three 10×4 dp pips: filled `onSurfaceVariant`, empty `outlineVariant`. */
@Composable
fun ConfidencePips(
    pips: Int,
    modifier: Modifier = Modifier,
) {
    val filled = MaterialTheme.colorScheme.onSurfaceVariant
    val empty = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.semantics { contentDescription = "Confidence $pips of 3" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(3) { index ->
            Box(
                modifier =
                    Modifier
                        .width(10.dp)
                        .height(4.dp)
                        .background(if (index < pips) filled else empty, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Design §3 range card: icon + title in a semantic colour, serif range, supporting line, pips and basis. */
@Composable
fun RangeCard(
    @DrawableRes icon: Int,
    title: String,
    range: String,
    supporting: String,
    pips: Int,
    basis: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MsIcon(resId = icon, contentDescription = null, size = 20.dp, tint = accent)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = accent,
            )
        }
        Text(text = range, style = SerifStyles.range, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConfidencePips(pips = pips)
            Text(
                text = basis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
