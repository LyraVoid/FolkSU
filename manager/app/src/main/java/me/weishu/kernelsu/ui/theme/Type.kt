package me.weishu.kernelsu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Material's sizes with our line breaking. The sizes stay Material's: a screen that wants one of
 * our three levels asks [FolkType] for it.
 */
fun getTypography(fontFamily: FontFamily): Typography {
    fun heading(style: TextStyle) = style.copy(fontFamily = fontFamily).wrapAware(LineBreak.Heading)
    fun paragraph(style: TextStyle) = style.copy(fontFamily = fontFamily).wrapAware()

    // The M3E emphasized slots match the standard ones in sizing; the weight and tracking
    // treatment is defined here. Our line breaking has to be carried over either way.
    fun emphasize(style: TextStyle, isHeading: Boolean): TextStyle {
        val base = if (isHeading) heading(style) else paragraph(style)
        val tracking = (style.letterSpacing.value - if (isHeading) 0.2f else 0.1f).sp
        return if (isHeading) {
            base.copy(fontWeight = FontWeight.W700, letterSpacing = tracking)
        } else {
            base.copy(fontWeight = FontWeight.W600, letterSpacing = tracking)
        }
    }

    return Typography(
        displayLarge = heading(Typography.displayLarge),
        displayMedium = heading(Typography.displayMedium),
        displaySmall = heading(Typography.displaySmall),
        headlineLarge = heading(Typography.headlineLarge),
        headlineMedium = heading(Typography.headlineMedium),
        headlineSmall = heading(Typography.headlineSmall),
        titleLarge = heading(Typography.titleLarge),
        titleMedium = heading(Typography.titleMedium),
        titleSmall = paragraph(Typography.titleSmall),
        bodyLarge = paragraph(Typography.bodyLarge),
        bodyMedium = paragraph(Typography.bodyMedium),
        bodySmall = paragraph(Typography.bodySmall),
        labelLarge = paragraph(Typography.labelLarge),
        labelMedium = paragraph(Typography.labelMedium),
        labelSmall = paragraph(Typography.labelSmall),
        displayLargeEmphasized = emphasize(Typography.displayLargeEmphasized, true),
        displayMediumEmphasized = emphasize(Typography.displayMediumEmphasized, true),
        displaySmallEmphasized = emphasize(Typography.displaySmallEmphasized, true),
        headlineLargeEmphasized = emphasize(Typography.headlineLargeEmphasized, true),
        headlineMediumEmphasized = emphasize(Typography.headlineMediumEmphasized, true),
        headlineSmallEmphasized = emphasize(Typography.headlineSmallEmphasized, true),
        titleLargeEmphasized = emphasize(Typography.titleLargeEmphasized, true),
        titleMediumEmphasized = emphasize(Typography.titleMediumEmphasized, true),
        titleSmallEmphasized = emphasize(Typography.titleSmallEmphasized, true),
        bodyLargeEmphasized = emphasize(Typography.bodyLargeEmphasized, false),
        bodyMediumEmphasized = emphasize(Typography.bodyMediumEmphasized, false),
        bodySmallEmphasized = emphasize(Typography.bodySmallEmphasized, false),
        labelLargeEmphasized = emphasize(Typography.labelLargeEmphasized, false),
        labelMediumEmphasized = emphasize(Typography.labelMediumEmphasized, false),
        labelSmallEmphasized = emphasize(Typography.labelSmallEmphasized, false)
    )
}
