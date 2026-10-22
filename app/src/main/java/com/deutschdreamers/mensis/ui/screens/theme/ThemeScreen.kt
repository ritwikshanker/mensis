package com.deutschdreamers.mensis.ui.screens.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme
import com.deutschdreamers.mensis.ui.components.ContentSectionTitle
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.SegmentedChoice
import com.deutschdreamers.mensis.ui.components.dashedBorder
import com.deutschdreamers.mensis.ui.components.hatched
import com.deutschdreamers.mensis.ui.theme.MensisColorsDark
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import com.deutschdreamers.mensis.ui.theme.RoseDarkColorScheme
import com.deutschdreamers.mensis.ui.theme.RoseLightColorScheme
import com.deutschdreamers.mensis.ui.theme.SageDarkColorScheme
import com.deutschdreamers.mensis.ui.theme.SageLightColorScheme

/** Design §16: colour scheme, appearance, and the semantic colours that never change. */
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ThemeContent(
        state = state,
        onScheme = viewModel::setScheme,
        onAppearance = viewModel::setAppearance,
        onBack = onBack,
    )
}

@Composable
fun ThemeContent(
    state: ThemeUiState?,
    onScheme: (ThemeScheme) -> Unit,
    onAppearance: (AppearanceMode) -> Unit,
    onBack: () -> Unit,
    dynamicAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    Scaffold(topBar = { MensisTopBar(title = stringResource(R.string.settings_theme), onBack = onBack) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentSectionTitle(stringResource(R.string.theme_colour))
                    for (scheme in ThemeScheme.entries) {
                        val enabled = scheme != ThemeScheme.DYNAMIC || dynamicAvailable
                        SchemeCard(
                            scheme = scheme,
                            selected = scheme == state.scheme,
                            enabled = enabled,
                            onSelect = { onScheme(scheme) },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentSectionTitle(stringResource(R.string.theme_appearance))
                    val labels = AppearanceMode.entries.associateWith { stringResource(it.labelRes) }
                    SegmentedChoice(
                        options = AppearanceMode.entries,
                        selected = state.appearance,
                        label = labels::getValue,
                        onSelect = onAppearance,
                        icon = { it.iconRes },
                    )
                }
            }
            FixedMeanings()
        }
    }
}

@Composable
private fun SchemeCard(
    scheme: ThemeScheme,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val (first, second) = swatches(scheme, available = enabled)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .border(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.large,
                ).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(width = 24.dp, height = 40.dp)
                        .background(first, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
            )
            Box(
                modifier =
                    Modifier
                        .size(width = 24.dp, height = 40.dp)
                        .background(second, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(scheme.nameRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(
                text =
                    stringResource(
                        if (enabled) scheme.descriptionRes else R.string.theme_dynamic_unavailable,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}

/**
 * Primary and secondary of each scheme as it would look in the current light or dark appearance;
 * System colours without Material You ([available] false) show the design's baseline stand-in.
 */
@Composable
private fun swatches(
    scheme: ThemeScheme,
    available: Boolean,
): Pair<Color, Color> {
    val dark = MensisTheme.colors == MensisColorsDark
    val colors =
        when (scheme) {
            ThemeScheme.ROSE -> if (dark) RoseDarkColorScheme else RoseLightColorScheme
            ThemeScheme.SAGE -> if (dark) SageDarkColorScheme else SageLightColorScheme
            ThemeScheme.DYNAMIC ->
                if (available && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val context = LocalContext.current
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    return BaselinePrimary to BaselineSecondary
                }
        }
    return colors.primary to colors.secondary
}

@Composable
private fun FixedMeanings() {
    val colors = MensisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ContentSectionTitle(stringResource(R.string.theme_fixed))
        Text(
            text = stringResource(R.string.theme_fixed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FixedSwatch(stringResource(R.string.legend_period), Modifier.background(colors.period, SwatchShape))
            FixedSwatch(stringResource(R.string.legend_fertile), Modifier.background(colors.fertile, SwatchShape))
            FixedSwatch(stringResource(R.string.legend_ovulation), Modifier.background(colors.ovulation, SwatchShape))
            FixedSwatch(
                stringResource(R.string.legend_predicted),
                Modifier
                    .hatched(line = colors.predHatch, tint = colors.predTint, cornerRadius = 8.dp)
                    .dashedBorder(color = colors.predHatch, width = 1.dp, cornerRadius = 8.dp),
            )
        }
    }
}

@Composable
private fun RowScope.FixedSwatch(
    description: String,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .weight(1f)
                .height(28.dp)
                .semantics { contentDescription = description },
    )
}

private val SwatchShape = RoundedCornerShape(8.dp)

// The design's stand-in for System colours where Material You is not available (M3 baseline).
private val BaselinePrimary = Color(0xFF6750A4)
private val BaselineSecondary = Color(0xFF625B71)

private val ThemeScheme.nameRes: Int
    get() =
        when (this) {
            ThemeScheme.ROSE -> R.string.theme_rose
            ThemeScheme.SAGE -> R.string.theme_sage
            ThemeScheme.DYNAMIC -> R.string.theme_dynamic
        }

private val ThemeScheme.descriptionRes: Int
    get() =
        when (this) {
            ThemeScheme.ROSE -> R.string.theme_rose_desc
            ThemeScheme.SAGE -> R.string.theme_sage_desc
            ThemeScheme.DYNAMIC -> R.string.theme_dynamic_desc
        }

private val AppearanceMode.labelRes: Int
    get() =
        when (this) {
            AppearanceMode.LIGHT -> R.string.theme_light
            AppearanceMode.DARK -> R.string.theme_dark
            AppearanceMode.SYSTEM -> R.string.theme_system
        }

private val AppearanceMode.iconRes: Int
    get() =
        when (this) {
            AppearanceMode.LIGHT -> R.drawable.ms_light_mode
            AppearanceMode.DARK -> R.drawable.ms_dark_mode
            AppearanceMode.SYSTEM -> R.drawable.ms_brightness_auto
        }
