package com.deutschdreamers.mensis.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.deutschdreamers.mensis.data.settings.AppearanceMode
import com.deutschdreamers.mensis.data.settings.ThemeScheme

val LocalMensisColors = staticCompositionLocalOf { MensisColorsLight }

object MensisTheme {
    val colors: MensisColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMensisColors.current
}

@Composable
fun MensisTheme(
    scheme: ThemeScheme = ThemeScheme.ROSE,
    appearance: AppearanceMode = AppearanceMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (appearance) {
            AppearanceMode.LIGHT -> false
            AppearanceMode.DARK -> true
            AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        }
    val colorScheme = colorSchemeFor(scheme, darkTheme)
    val mensisColors = if (darkTheme) MensisColorsDark else MensisColorsLight
    CompositionLocalProvider(LocalMensisColors provides mensisColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MensisTypography,
            shapes = MensisShapes,
            content = content,
        )
    }
}

/** [ThemeScheme.DYNAMIC] falls back to Rose below Android 12. */
@Composable
private fun colorSchemeFor(
    scheme: ThemeScheme,
    darkTheme: Boolean,
): ColorScheme =
    when (scheme) {
        ThemeScheme.ROSE -> if (darkTheme) RoseDarkColorScheme else RoseLightColorScheme
        ThemeScheme.SAGE -> if (darkTheme) SageDarkColorScheme else SageLightColorScheme
        ThemeScheme.DYNAMIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) RoseDarkColorScheme else RoseLightColorScheme
            }
    }
