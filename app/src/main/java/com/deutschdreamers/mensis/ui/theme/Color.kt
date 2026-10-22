package com.deutschdreamers.mensis.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Colour tokens for the Rose and Sage schemes. Both share the warm neutrals.

private val SurfaceLight = Color(0xFFFBF7F3)
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFF5F1ED)
private val SurfaceContainerLight = Color(0xFFEFEBE7)
private val SurfaceContainerHighLight = Color(0xFFE9E5E1)
private val SurfaceContainerHighestLight = Color(0xFFE3DFDB)
private val OnSurfaceLight = Color(0xFF1C1B1A)
private val OnSurfaceVariantLight = Color(0xFF514548)
private val OutlineLight = Color(0xFF837578)
private val OutlineVariantLight = Color(0xFFD5C3C6)

private val SurfaceDark = Color(0xFF141211)
private val SurfaceContainerLowestDark = Color(0xFF0F0D0C)
private val SurfaceContainerLowDark = Color(0xFF1C1A19)
private val SurfaceContainerDark = Color(0xFF201E1D)
private val SurfaceContainerHighDark = Color(0xFF2B2827)
private val SurfaceContainerHighestDark = Color(0xFF363231)
private val OnSurfaceDark = Color(0xFFEDE6E1)
private val OnSurfaceVariantDark = Color(0xFFD6C3C7)
private val OutlineDark = Color(0xFF9F8D91)
private val OutlineVariantDark = Color(0xFF524548)

private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF410002)
private val ErrorDark = Color(0xFFFFB4AB)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

private val TertiaryLight = Color(0xFF4E637A)
private val TertiaryContainerLight = Color(0xFFD2E4F9)
private val OnTertiaryContainerLight = Color(0xFF091C31)
private val TertiaryDark = Color(0xFFB5C8E1)
private val TertiaryContainerDark = Color(0xFF364A61)
private val OnTertiaryContainerDark = Color(0xFFD2E4F9)

val RoseLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF8B4A5A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9DF),
        onPrimaryContainer = Color(0xFF3A0818),
        secondary = Color(0xFF566653),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD9E6D3),
        onSecondaryContainer = Color(0xFF141E12),
        tertiary = TertiaryLight,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = TertiaryContainerLight,
        onTertiaryContainer = OnTertiaryContainerLight,
        error = ErrorLight,
        onError = Color(0xFFFFFFFF),
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceContainerHighestLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        surfaceContainerLowest = SurfaceContainerLowestLight,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
    )

val RoseDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFFFB1C1),
        onPrimary = Color(0xFF55202E),
        primaryContainer = Color(0xFF703244),
        onPrimaryContainer = Color(0xFFFFD9DF),
        secondary = Color(0xFFBECBB8),
        onSecondary = Color(0xFF283526),
        secondaryContainer = Color(0xFF3F4E3C),
        onSecondaryContainer = Color(0xFFD9E6D3),
        tertiary = TertiaryDark,
        onTertiary = Color(0xFF1F3349),
        tertiaryContainer = TertiaryContainerDark,
        onTertiaryContainer = OnTertiaryContainerDark,
        error = ErrorDark,
        onError = Color(0xFF690005),
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceContainerHighestDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        surfaceContainerLowest = SurfaceContainerLowestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
    )

val SageLightColorScheme =
    RoseLightColorScheme.copy(
        primary = Color(0xFF4F6B52),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD1E8D0),
        onPrimaryContainer = Color(0xFF0C2010),
        secondary = Color(0xFF7B5560),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9DF),
        onSecondaryContainer = Color(0xFF31101C),
    )

val SageDarkColorScheme =
    RoseDarkColorScheme.copy(
        primary = Color(0xFFB6CDB5),
        onPrimary = Color(0xFF22361F),
        primaryContainer = Color(0xFF38513A),
        onPrimaryContainer = Color(0xFFD1E8D0),
        secondary = Color(0xFFE8B8C4),
        onSecondary = Color(0xFF45262F),
        secondaryContainer = Color(0xFF5C3E48),
        onSecondaryContainer = Color(0xFFFFD9DF),
    )

/**
 * Semantic colours that stay identical across Rose, Sage and dynamic colour:
 * the calendar must read the same way whatever the scheme.
 */
data class MensisColors(
    val period: Color,
    val onPeriod: Color,
    val periodContainer: Color,
    val onPeriodContainer: Color,
    val predTint: Color,
    val predHatch: Color,
    val fertile: Color,
    val fertileContainer: Color,
    val onFertileContainer: Color,
    val ovulation: Color,
    val scrim: Color,
    val shadow: Color,
)

val MensisColorsLight =
    MensisColors(
        period = Color(0xFFAD3553),
        onPeriod = Color(0xFFFFFFFF),
        periodContainer = Color(0xFFF8D9E0),
        onPeriodContainer = Color(0xFF4A0F22),
        predTint = Color(0xFFF8E3E7),
        predHatch = Color(0xFFC9768A),
        fertile = Color(0xFF1F8580),
        fertileContainer = Color(0xFFC8EEEA),
        onFertileContainer = Color(0xFF00332F),
        ovulation = Color(0xFF0B5F5B),
        scrim = Color(0x661C1B1A),
        shadow = Color(0x2E1C1B1A),
    )

val MensisColorsDark =
    MensisColors(
        period = Color(0xFFF08CA4),
        onPeriod = Color(0xFF3D0A1A),
        periodContainer = Color(0xFF5C1A30),
        onPeriodContainer = Color(0xFFFFD9E1),
        predTint = Color(0xFF3A2229),
        predHatch = Color(0xFFB7647A),
        fertile = Color(0xFF6FCFC9),
        fertileContainer = Color(0xFF0F3F3C),
        onFertileContainer = Color(0xFFBDEEEA),
        ovulation = Color(0xFF2FA39C),
        scrim = Color(0x8C000000),
        shadow = Color(0x80000000),
    )
