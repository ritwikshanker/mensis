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

/** Colour scheme choice from the Theme screen. [DYNAMIC] falls back to [ROSE] below Android 12. */
enum class ColorSchemeChoice { ROSE, SAGE, DYNAMIC }

/** Light / dark / follow system. */
enum class Appearance { LIGHT, DARK, SYSTEM }

val LocalMensisColors = staticCompositionLocalOf { MensisColorsLight }

object MensisTheme {
    val colors: MensisColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMensisColors.current
}

@Composable
fun MensisTheme(
    scheme: ColorSchemeChoice = ColorSchemeChoice.ROSE,
    appearance: Appearance = Appearance.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (appearance) {
            Appearance.LIGHT -> false
            Appearance.DARK -> true
            Appearance.SYSTEM -> isSystemInDarkTheme()
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

@Composable
private fun colorSchemeFor(
    scheme: ColorSchemeChoice,
    darkTheme: Boolean,
): ColorScheme =
    when (scheme) {
        ColorSchemeChoice.ROSE -> if (darkTheme) RoseDarkColorScheme else RoseLightColorScheme
        ColorSchemeChoice.SAGE -> if (darkTheme) SageDarkColorScheme else SageLightColorScheme
        ColorSchemeChoice.DYNAMIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) RoseDarkColorScheme else RoseLightColorScheme
            }
    }
