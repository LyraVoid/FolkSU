package me.weishu.kernelsu.ui.screen.home

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import me.weishu.kernelsu.wallpaper.AnimatedFileImage
import me.weishu.kernelsu.wallpaper.WallpaperManager
import me.weishu.kernelsu.wallpaper.isAnimatedImageFile
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig

/**
 * A surface photo plus the information a card needs to pick its palette.
 *
 * Cards used to switch to white-on-transparent as soon as an image URI was configured, but the
 * bitmap is decoded asynchronously: during that window — and forever when decoding fails — the card
 * painted nothing, leaving white text on the page background. Callers must gate the over-photo
 * palette on [ready] and, once ready, choose [contentColor] from the image's own luminance.
 */
@Immutable
data class SurfacePhoto(
    val uri: String?,
    val animatedFile: File?,
    val bitmap: ImageBitmap?,
    val averageLuminance: Float?,
    val ready: Boolean,
) {
    companion object {
        val None = SurfacePhoto(null, null, null, null, false)
    }
}

/** Minimum scrim under over-photo content, so even a no-dim photo gets some separation. */
private const val PhotoScrimFloor = 0.2f

/** Content colour for light photos; white is used for dark ones. */
private val PhotoForegroundDark = Color(0xFF141218)

/**
 * Decodes the surface photo behind [uri], keyed on the full URI (which carries the revision stamp)
 * so a replaced image of the same path re-decodes. Shared by the card's palette decision and the
 * painter so both agree on [SurfacePhoto.ready].
 */
@Composable
internal fun rememberSurfacePhoto(uri: String?): SurfacePhoto {
    if (uri.isNullOrEmpty()) return SurfacePhoto.None

    val path = remember(uri) { uri.toUri().path }
    val file = remember(path) { path?.let { File(it) } }
    val animatedFile = file?.takeIf { isAnimatedImageFile(it) }
    if (animatedFile != null) {
        // Animated images play natively and are ready as soon as the file resolves.
        return remember(uri, animatedFile) {
            SurfacePhoto(uri, animatedFile, null, null, ready = true)
        }
    }

    val decoded by produceState<SurfacePhoto?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            val bitmap = path?.let { WallpaperManager.decodeSampled(File(it), 1600) }
            SurfacePhoto(
                uri = uri,
                animatedFile = null,
                bitmap = bitmap?.asImageBitmap(),
                averageLuminance = bitmap?.let { averageLuminance(it) },
                ready = bitmap != null,
            )
        }
    }
    return decoded ?: SurfacePhoto(uri, null, null, null, ready = false)
}

/** Samples a 16x16 grid of the bitmap and returns the mean relative luminance in [0, 1]. */
private fun averageLuminance(bitmap: Bitmap): Float {
    val stepX = (bitmap.width / 16).coerceAtLeast(1)
    val stepY = (bitmap.height / 16).coerceAtLeast(1)
    var sum = 0.0
    var count = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            sum += ColorUtils.calculateLuminance(bitmap.getPixel(x, y))
            count++
            x += stepX
        }
        y += stepY
    }
    return if (count == 0) 0f else (sum / count).toFloat()
}

private fun scrimAlpha(surface: SurfaceConfig, isDark: Boolean): Float =
    surface.effectiveDim(isDark).coerceAtLeast(PhotoScrimFloor).coerceAtMost(1f)

/** Whether the composited photo is light enough to need dark content. */
internal fun SurfacePhoto.usesDarkContent(surface: SurfaceConfig, isDark: Boolean): Boolean {
    val luminance = averageLuminance ?: return false
    return luminance * (1f - scrimAlpha(surface, isDark)) > 0.5f
}

/** Content colour that stays readable over this photo: dark on light images, white on dark ones. */
internal fun SurfacePhoto.contentColor(surface: SurfaceConfig, isDark: Boolean): Color =
    if (usesDarkContent(surface, isDark)) PhotoForegroundDark else Color.White

/**
 * Paints [photo] behind card content with an adaptive scrim (dark under white text, light under
 * dark text) so the overlaid content stays readable. Draws nothing until the bitmap is decoded.
 */
@Composable
internal fun SurfaceBackgroundImage(
    photo: SurfacePhoto,
    surface: SurfaceConfig,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val isDark = isInDarkTheme()
    val opacity = surface.effectiveOpacity(isDark)
    val animatedFile = photo.animatedFile
    val bitmap = photo.bitmap
    if (animatedFile != null) {
        // Animated images (GIF) play natively; everything else keeps the downsampled bitmap path.
        AnimatedFileImage(
            file = animatedFile,
            revision = photo.uri.orEmpty(),
            modifier = modifier
                .alpha(opacity),
            scaleType = if (contentScale == ContentScale.FillWidth) {
                ImageView.ScaleType.FIT_CENTER
            } else {
                ImageView.ScaleType.CENTER_CROP
            },
        )
    } else if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
                .alpha(opacity),
        )
    }

    if (photo.ready) {
        val scrim = if (photo.usesDarkContent(surface, isDark)) Color.White else Color.Black
        Box(
            modifier = modifier
                .background(scrim.copy(alpha = scrimAlpha(surface, isDark))),
        )
    }
}
