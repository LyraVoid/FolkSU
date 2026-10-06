package me.weishu.kernelsu.wallpaper

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.theme.isInDarkTheme
import java.io.File

/**
 * Paints the active wallpaper as the bottom-most layer, with an optional blur and black scrim.
 *
 * Renders nothing when no wallpaper is active, so the caller can keep it unconditionally in the
 * tree. The scrim uses [LocalWallpaperDim] so the theme's contrast guard is honored.
 */
@Composable
fun WallpaperBackgroundLayer(modifier: Modifier = Modifier) {
    val uri = WallpaperConfig.activeUri ?: return
    val blurRadius = WallpaperConfig.blur
    val scrim = (LocalWallpaperDim.current ?: WallpaperConfig.effectiveDim(isInDarkTheme()))
        .coerceIn(0f, 1f)
    val path = remember(uri) { Uri.parse(uri).path }
    val file = remember(path) { path?.let { File(it) } }
    val animated = file?.let { isAnimatedImageFile(it) } == true

    val image by produceState<ImageBitmap?>(initialValue = null, path, animated) {
        value = if (animated) {
            null
        } else {
            withContext(Dispatchers.IO) {
                path?.let { WallpaperManager.decodeSampled(File(it), 2560)?.asImageBitmap() }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (animated && file != null) {
            // Animated wallpapers cannot go through the downsampled bitmap path, so they are handed
            // to the drawable view; the blur is applied as a render effect there.
            val blurPx = with(LocalDensity.current) { blurRadius.dp.toPx() }
            AnimatedFileImage(
                file = file,
                modifier = Modifier.fillMaxSize(),
                blurRadiusPx = blurPx,
            )
        } else {
            image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurRadius.dp),
                )
            }
        }
        if (scrim > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        }
    }
}
