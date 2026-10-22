package com.deutschdreamers.mensis.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R

/**
 * Design chip: selected = `secondaryContainer` with a check icon, unselected = 1 dp `outlineVariant`
 * border. Used for single- and multi-select groups alike.
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        leadingIcon =
            if (selected) {
                { MsIcon(resId = R.drawable.ms_check, contentDescription = null, size = 18.dp) }
            } else {
                null
            },
        shape = MaterialTheme.shapes.small,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    )
}

/** A wrapping row of [SelectChip]s with one selected value (or none). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SingleChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (option in options) {
            val isSelected = option == selected
            SelectChip(
                label = label(option),
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else option) },
            )
        }
    }
}

/** A wrapping row of [SelectChip]s with any number selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChoiceChips(
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (option in options) {
            SelectChip(label = label(option), selected = option in selected, onClick = { onToggle(option) })
        }
    }
}

/**
 * Material 3 single-select segmented button row (design "segmented"). With [icon], each segment shows
 * its own icon, and the selected one a check in its place (design §16 Appearance).
 */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> Int)? = null,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val active = option == selected
            SegmentedButton(
                selected = active,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                icon = {
                    if (icon == null) {
                        SegmentedButtonDefaults.Icon(active)
                    } else {
                        MsIcon(
                            resId = if (active) R.drawable.ms_check else icon(option),
                            contentDescription = null,
                            size = SegmentedButtonDefaults.IconSize,
                        )
                    }
                },
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}

/** Design "logged value" chip: 32 dp, radius 8, 1 dp `outlineVariant` border, 18 dp icon. */
@Composable
fun ValueChip(
    @DrawableRes icon: Int?,
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick ?: {},
        enabled = onClick != null,
        label = { Text(text) },
        modifier = modifier,
        leadingIcon =
            if (icon != null) {
                {
                    MsIcon(
                        resId = icon,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        shape = MaterialTheme.shapes.small,
        colors =
            AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    )
}
