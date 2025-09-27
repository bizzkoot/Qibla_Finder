package com.bizzkoot.qiblafinder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp

/**
 * Describes the semantic typography roles used throughout the app.
 */
data class QiblaTypographySet(
    val titlePrimary: TextStyle,
    val titleSecondary: TextStyle,
    val titleTertiary: TextStyle,
    val bodyPrimary: TextStyle,
    val bodySecondary: TextStyle,
    val bodyEmphasis: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
    val badge: TextStyle,
    val caption: TextStyle
) {
    fun toMaterialTypography(): Typography = Typography(
        displayLarge = titlePrimary,
        displayMedium = titlePrimary,
        displaySmall = titleSecondary,
        headlineLarge = titleSecondary,
        headlineMedium = titleTertiary,
        headlineSmall = titleTertiary,
        titleLarge = titleSecondary,
        titleMedium = titleTertiary,
        titleSmall = bodyEmphasis,
        bodyLarge = bodyPrimary,
        bodyMedium = bodySecondary,
        bodySmall = caption,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall
    )
}

private val BaseTypography = QiblaTypographySet(
    titlePrimary = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold
    ),
    titleSecondary = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleTertiary = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyPrimary = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySecondary = TextStyle(
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyEmphasis = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium
    ),
    labelLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    badge = TextStyle(
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold
    ),
    caption = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal
    )
)

private val LocalQiblaTypography = staticCompositionLocalOf { BaseTypography }

object QiblaTypography {
    val current: QiblaTypographySet
        @Composable
        @ReadOnlyComposable
        get() = LocalQiblaTypography.current
}

@Composable
fun QiblaTypographyProvider(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp
    val fontScale = density.fontScale

    val scale = remember(screenWidthDp, fontScale) {
        calculateTypographyScale(screenWidthDp, fontScale)
    }

    val typography = remember(scale) {
        BaseTypography.scale(scale)
    }

    CompositionLocalProvider(LocalQiblaTypography provides typography) {
        content()
    }
}

private fun calculateTypographyScale(screenWidthDp: Int, fontScale: Float): Float {
    if (screenWidthDp <= 0) return 1f

    val widthScale = when {
        screenWidthDp >= 840 -> 1.15f
        screenWidthDp >= 600 -> 1.08f
        else -> 1f
    }

    return if (fontScale > 1f) {
        // Dampen additional scaling when users already run large font sizes
        1f + (widthScale - 1f) / fontScale
    } else {
        widthScale
    }
}

private fun QiblaTypographySet.scale(factor: Float): QiblaTypographySet {
    if (factor == 1f) return this

    return QiblaTypographySet(
        titlePrimary = titlePrimary.scaleTextStyle(factor),
        titleSecondary = titleSecondary.scaleTextStyle(factor),
        titleTertiary = titleTertiary.scaleTextStyle(factor),
        bodyPrimary = bodyPrimary.scaleTextStyle(factor),
        bodySecondary = bodySecondary.scaleTextStyle(factor),
        bodyEmphasis = bodyEmphasis.scaleTextStyle(factor),
        labelLarge = labelLarge.scaleTextStyle(factor),
        labelMedium = labelMedium.scaleTextStyle(factor),
        labelSmall = labelSmall.scaleTextStyle(factor),
        badge = badge.scaleTextStyle(factor),
        caption = caption.scaleTextStyle(factor)
    )
}

private fun TextStyle.scaleTextStyle(factor: Float): TextStyle {
    if (factor == 1f) return this

    val scaledFont = scaleTextUnit(fontSize, factor)
    val scaledLine = scaleTextUnit(lineHeight, factor)

    return this.copy(
        fontSize = scaledFont ?: fontSize,
        lineHeight = scaledLine ?: lineHeight
    )
}

private fun scaleTextUnit(value: TextUnit, factor: Float): TextUnit? {
    if (value == TextUnit.Unspecified) return null
    val scaled = value.value * factor
    return TextUnit(scaled, value.type)
}

@Composable
fun QiblaFinderTheme(content: @Composable () -> Unit) {
    QiblaTypographyProvider {
        val typography = QiblaTypography.current.toMaterialTypography()
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = typography,
            content = content
        )
    }
}
