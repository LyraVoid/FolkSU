package me.weishu.kernelsu.wallpaper

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    // In multi mode each page paints its own image, so the master switch (which gates the single
    // global wallpaper) must not blank out pages that carry their own background.
    if (!WallpaperConfig.enabled && !WallpaperConfig.multiBackgroundEnabled) return
    val uri = WallpaperConfig.pageUri(LocalWallpaperPage.current) ?: return
    val blurRadius = WallpaperConfig.blur
    val scrim = (LocalWallpaperDim.current ?: WallpaperConfig.effectiveDim(isInDarkTheme()))
        .coerceIn(0f, 1f)
    val path = remember(uri) { Uri.parse(uri).path }
    val file = remember(path) { path?.let { File(it) } }
    val animatedFile = file?.takeIf { isAnimatedImageFile(it) }

    // Key on the whole URI (it carries the ?t= revision), not the query-less path: replacing a
    // wallpaper with another file of the same name and format must re-decode instead of keeping
    // the old frame next to freshly derived brightness/seed colours.
    val image by produceState<ImageBitmap?>(initialValue = null, uri, animatedFile) {
        value = if (animatedFile != null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                path?.let { WallpaperManager.decodeSampled(File(it), 2560)?.asImageBitmap() }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (animatedFile != null) {
            // Animated wallpapers cannot go through the downsampled bitmap path, so they are handed
            // to the drawable view; the blur is applied as a render effect there.
            val blurPx = with(LocalDensity.current) { blurRadius.dp.toPx() }
            AnimatedFileImage(
                file = animatedFile,
                revision = uri,
                modifier = Modifier.fillMaxSize(),
                blurRadiusPx = blurPx,
            )
        } else {
            // The previous frame stays on screen while the next one decodes, so a page swap fades
            // into the new image instead of cutting to black.
            StackedFadeImage(
                bitmap = image,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius.dp),
            )
        }
        if (scrim > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
        }
    }
}

/**
 * Draws [bitmap], fading a newly decoded frame in *over* the previous one.
 *
 * A symmetric crossfade lets the layer under the wallpaper show through while both frames are only
 * partially opaque, which reads as a dark flash on every page switch. Keeping the outgoing frame
 * fully opaque underneath means the base is never visible and only the luminance of the two images
 * themselves blends.
 */
@Composable
private fun StackedFadeImage(
    bitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(bitmap) }
    var incoming by remember { mutableStateOf<ImageBitmap?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(bitmap) {
        if (bitmap != null && bitmap !== shown) {
            if (shown == null) {
                shown = bitmap
            } else {
                incoming = bitmap
                progress.snapTo(0f)
                progress.animateTo(1f, tween(durationMillis = 400, easing = LinearEasing))
                shown = bitmap
                incoming = null
            }
        }
    }

    Box(modifier) {
        shown?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        incoming?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(progress.value),
            )
        }
    }
}
