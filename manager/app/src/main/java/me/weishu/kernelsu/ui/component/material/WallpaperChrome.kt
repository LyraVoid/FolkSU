package me.weishu.kernelsu.ui.component.material

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperMaterial

/** Which screen edge a chrome zone hugs. */
enum class ChromeEdge { Top, Bottom, Start }

/**
 * How opaque a collapsing top bar becomes once it is fully collapsed, no matter what transparency
 * the user picked for the content surfaces.
 */
private const val CollapsedChromeFloor = 0.85f

/**
 * Draws the wallpaper reading platform behind a bar and fades it into the wallpaper *past* the
 * bar's edge, so the bar no longer terminates in a hard color band while its own content (which
 * may sit at the bottom of a large top bar) stays on the solid platform.
 *
 * Only active in wallpaper mode; otherwise [content] is emitted unchanged. [progress] lets a
 * collapsible bar strengthen its platform as it collapses (0 = expanded, 1 = collapsed).
 */
@Composable
fun WallpaperChromeZone(
    edge: ChromeEdge,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 0f },
    tailHeight: Dp = 48.dp,
    material: WallpaperMaterial? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFolkWallpaperTokens.current
    if (tokens == null) {
        content()
        return
    }
    val chrome = material ?: tokens.chrome
    val tailPx = with(LocalDensity.current) { tailHeight.toPx() }
    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier = modifier.drawBehind {
            val collapsed = progress().coerceIn(0f, 1f)
            // The bar honours the user's transparency while it is expanded; once it collapses,
            // scrolled content passes underneath, so the platform strengthens to at least
            // [CollapsedChromeFloor] to keep the labels legible. A bottom bar never collapses and
            // therefore keeps the user's alpha as-is.
            val floor = if (edge == ChromeEdge.Top) CollapsedChromeFloor else chrome.alpha
            val alpha = chrome.alpha + (floor - chrome.alpha).coerceAtLeast(0f) * collapsed
            val solid = chrome.tint.copy(alpha = alpha.coerceAtMost(1f))
            val clear = solid.copy(alpha = 0f)
            val width = size.width
            when (edge) {
                ChromeEdge.Top -> {
                    drawRect(solid, topLeft = Offset.Zero, size = Size(width, size.height))
                    // Fade past the bottom edge, over the (transparent) page canvas below.
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(solid, clear),
                            startY = size.height,
                            endY = size.height + tailPx,
                        ),
                        topLeft = Offset(0f, size.height),
                        size = Size(width, tailPx),
                    )
                }

                ChromeEdge.Bottom -> {
                    // Fade past the top edge, over the (transparent) page canvas above.
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(clear, solid),
                            startY = -tailPx,
                            endY = 0f,
                        ),
                        topLeft = Offset(0f, -tailPx),
                        size = Size(width, tailPx),
                    )
                    drawRect(solid, topLeft = Offset.Zero, size = Size(width, size.height))
                }

                ChromeEdge.Start -> {
                    // A side rail fills the start edge; fade past its far edge (right in LTR, left in
                    // RTL) over the (transparent) page canvas, so the rail does not end in a hard band.
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    drawRect(solid, topLeft = Offset.Zero, size = Size(width, size.height))
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = if (rtl) listOf(clear, solid) else listOf(solid, clear),
                            startX = if (rtl) -tailPx else width,
                            endX = if (rtl) 0f else width + tailPx,
                        ),
                        topLeft = Offset(if (rtl) -tailPx else width, 0f),
                        size = Size(tailPx, size.height),
                    )
                }
            }
        }
    ) {
        content()
    }
}
