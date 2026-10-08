package me.weishu.kernelsu.ui.screen.home

import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
 * The photo behind a home card, dimmed with a black scrim so the overlaid content stays readable.
 * Draws nothing until the stored bitmap is decoded. Shared by the grid work card and the focus
 * tiles so every surface paints its image the same way.
 */
@Composable
internal fun SurfaceBackgroundImage(
    uri: String,
    surface: SurfaceConfig,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val isDark = isInDarkTheme()
    val opacity = surface.effectiveOpacity(isDark)
    val path = remember(uri) { uri.toUri().path }
    val file = remember(path) { path?.let { File(it) } }
    if (file != null && isAnimatedImageFile(file)) {
        // Animated images (GIF) play natively; everything else keeps the downsampled bitmap path.
        AnimatedFileImage(
            file = file,
            revision = uri,
            modifier = modifier
                .alpha(opacity),
            scaleType = if (contentScale == ContentScale.FillWidth) {
                ImageView.ScaleType.FIT_CENTER
            } else {
                ImageView.ScaleType.CENTER_CROP
            },
        )
    } else {
        val image by produceState<ImageBitmap?>(initialValue = null, uri) {
            value = null
            value = withContext(Dispatchers.IO) {
                path?.let { WallpaperManager.decodeSampled(File(it), 1600)?.asImageBitmap() }
            }
        }
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = modifier
                    .alpha(opacity),
            )
        }
    }
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = surface.effectiveDim(isDark))),
    )
}
