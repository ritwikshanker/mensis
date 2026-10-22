package com.deutschdreamers.mensis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.deutschdreamers.mensis.R

// Both families are variable fonts fetched at build time (see fetchDesignAssets in app/build.gradle.kts).

private fun variable(
    resId: Int,
    weight: FontWeight,
) = Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Roboto Flex for all UI text. */
val RobotoFlex =
    FontFamily(
        variable(R.font.roboto_flex, FontWeight.Normal),
        variable(R.font.roboto_flex, FontWeight.Medium),
        variable(R.font.roboto_flex, FontWeight.SemiBold),
        variable(R.font.roboto_flex, FontWeight.Bold),
    )

/** Source Serif 4, weight 400 only: numerals, range figures, stats and the wordmark. */
val SourceSerif = FontFamily(variable(R.font.source_serif_4, FontWeight.Normal))

private val defaults = Typography()

/** The Material 3 scale in Roboto Flex. */
val MensisTypography =
    Typography(
        displayLarge = defaults.displayLarge.copy(fontFamily = RobotoFlex),
        displayMedium = defaults.displayMedium.copy(fontFamily = RobotoFlex),
        displaySmall = defaults.displaySmall.copy(fontFamily = RobotoFlex),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = RobotoFlex),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = RobotoFlex),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = RobotoFlex),
        titleLarge = defaults.titleLarge.copy(fontFamily = RobotoFlex),
        titleMedium = defaults.titleMedium.copy(fontFamily = RobotoFlex),
        titleSmall = defaults.titleSmall.copy(fontFamily = RobotoFlex),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = RobotoFlex),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = RobotoFlex),
        bodySmall = defaults.bodySmall.copy(fontFamily = RobotoFlex),
        labelLarge = defaults.labelLarge.copy(fontFamily = RobotoFlex),
        labelMedium = defaults.labelMedium.copy(fontFamily = RobotoFlex),
        labelSmall = defaults.labelSmall.copy(fontFamily = RobotoFlex),
    )

/** The serif figures from the design tokens (size/line height). */
object SerifStyles {
    /** Cycle-day numeral on Today. */
    val numeral =
        TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.Normal, fontSize = 96.sp, lineHeight = 88.sp)

    /** Range figures in the prediction cards. */
    val range =
        TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp)

    /** Stat figures (Insights). */
    val stat = TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp)

    /** Smaller stat figures: stepper values, pregnancy card. */
    val statSmall =
        TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp)

    /** The onboarding wordmark. */
    val wordmark =
        TextStyle(fontFamily = SourceSerif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 52.sp)
}
