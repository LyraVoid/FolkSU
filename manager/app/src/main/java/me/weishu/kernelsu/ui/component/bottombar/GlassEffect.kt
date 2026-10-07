package me.weishu.kernelsu.ui.component.bottombar

import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

/**
 * Frosted-glass effect for the floating navigation bar, backed by the Liquid real-time blur.
 *
 * The blur only exists on Android 13+ ([isRealTimeBlurAvailable]); below that the effect degrades
 * to a translucent glass tint so the bar stays legible on every supported device.
 */
private const val DEFAULT_BLUR_STRENGTH = 0.7f
private const val DEFAULT_TRANSPARENCY = 0.3f
private const val DEFAULT_HIGHLIGHT_STRENGTH = 0.5f

fun isRealTimeBlurAvailable(): Boolean = Build.VERSION.SDK_INT >= 33

@Composable
fun rememberNavBarGlassLiquidState(): LiquidState = rememberLiquidState()

/** Marks a content layer as the backdrop that the glass element refracts. */
fun Modifier.navBarLiquefiable(liquidState: LiquidState?): Modifier =
    if (isRealTimeBlurAvailable() && liquidState != null) this.liquefiable(liquidState) else this

@Composable
fun Modifier.navBarGlassEffect(
    shape: Shape = CircleShape,
    blurStrength: Float = DEFAULT_BLUR_STRENGTH,
    transparency: Float = DEFAULT_TRANSPARENCY,
    highlightStrength: Float = DEFAULT_HIGHLIGHT_STRENGTH,
    enableSpecular: Boolean = true,
    enableInnerGlow: Boolean = true,
    enableBorder: Boolean = true,
    liquidState: LiquidState? = null,
): Modifier {
    val isDarkTheme = !(MaterialTheme.colorScheme.background.luminance() > 0.5f)
    val density = LocalDensity.current
    val halfDp = with(density) { 0.5.dp.toPx() }
    val oneDp = with(density) { 1.dp.toPx() }
    val twoDp = with(density) { 2.dp.toPx() }
    val oneAndHalfDp = with(density) { 1.5.dp.toPx() }
    val fourDp = with(density) { 4.dp.toPx() }
    val eightDp = with(density) { 8.dp.toPx() }

    val glassBaseColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.14f + (1f - transparency) * 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.10f + (1f - transparency) * 0.08f)
    }

    val glassModifier = this.then(
        Modifier.drawBehind {
            val w = size.width
            val h = size.height
            val radius = h / 2f
            val cornerRadius = CornerRadius(radius, radius)
            val strokeRadius = CornerRadius((radius - halfDp).coerceAtLeast(0f))

            if (enableBorder) {
                val borderColor = if (isDarkTheme) {
                    Color.White.copy(alpha = 0.08f * highlightStrength)
                } else {
                    Color.White.copy(alpha = 0.25f * highlightStrength)
                }
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(halfDp, halfDp),
                    size = Size(w - oneDp, h - oneDp),
                    cornerRadius = strokeRadius,
                    style = Stroke(width = oneDp),
                )
            }

            val glassHighAlpha = if (isDarkTheme) 0.05f + 0.10f * highlightStrength else 0.08f + 0.18f * highlightStrength
            val glassLowAlpha = if (isDarkTheme) 0.01f + 0.04f * highlightStrength else 0.03f + 0.08f * highlightStrength
            val baseAlpha = 0.45f + (1f - transparency) * 0.35f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glassHighAlpha * baseAlpha),
                        Color.White.copy(alpha = glassLowAlpha * baseAlpha),
                    )
                ),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = cornerRadius,
            )

            if (enableSpecular) {
                val specularAlpha = if (isDarkTheme) 0.10f + 0.12f * highlightStrength else 0.25f + 0.25f * highlightStrength
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = specularAlpha), Color.Transparent),
                        startX = w * 0.15f,
                        endX = w * 0.85f,
                    ),
                    topLeft = Offset(w * 0.15f, twoDp),
                    size = Size(w * 0.7f, oneAndHalfDp),
                    cornerRadius = CornerRadius(oneDp),
                )
            }

            if (enableInnerGlow) {
                val glowAlpha = if (isDarkTheme) 0.02f + 0.04f * highlightStrength else 0.04f + 0.08f * highlightStrength
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = glowAlpha)),
                    ),
                    topLeft = Offset(fourDp, h - eightDp),
                    size = Size(w - eightDp, fourDp),
                    cornerRadius = CornerRadius(twoDp),
                )
            }
        }
    )

    if (isRealTimeBlurAvailable() && liquidState != null) {
        val blurAmount = (blurStrength * 20f).dp
        return glassModifier
            .then(
                Modifier.drawBehind {
                    val radius = size.height / 2f
                    drawRoundRect(color = glassBaseColor, cornerRadius = CornerRadius(radius, radius))
                }
            )
            .liquid(liquidState) {
                this.shape = shape
                this.frost = blurAmount.coerceIn(8.dp, 20.dp)
                this.curve = if (isDarkTheme) 0.34f + blurStrength * 0.10f else 0.40f + blurStrength * 0.10f
                this.refraction = if (isDarkTheme) 0.07f + blurStrength * 0.06f else 0.10f + blurStrength * 0.08f
                this.dispersion = if (isDarkTheme) 0.16f + blurStrength * 0.08f else 0.20f + blurStrength * 0.10f
                this.saturation = if (isDarkTheme) 0.38f + blurStrength * 0.12f else 0.48f + blurStrength * 0.14f
                this.contrast = if (isDarkTheme) 1.7f + blurStrength * 0.2f else 1.55f + blurStrength * 0.2f
            }
    }

    return glassModifier.then(
        Modifier.drawBehind {
            val radius = size.height / 2f
            drawRoundRect(color = glassBaseColor, cornerRadius = CornerRadius(radius, radius))
        }
    )
}
