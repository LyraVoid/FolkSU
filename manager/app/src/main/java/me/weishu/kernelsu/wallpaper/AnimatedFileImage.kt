package me.weishu.kernelsu.wallpaper

import android.graphics.ImageDecoder
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** File extensions that may carry an animated image. */
private val ANIMATED_EXTENSIONS = setOf("gif")

/** True when [file] may animate, so callers can pick the animated renderer over a still bitmap. */
fun isAnimatedImageFile(file: File): Boolean =
    file.extension.lowercase() in ANIMATED_EXTENSIONS

/**
 * Renders a possibly-animated image file (GIF) with the platform decoder, so the frames actually
 * play. Static images keep using the downsampled bitmap path instead; this is only for the
 * animated case, which no library in this project can play.
 *
 * The drawable is decoded off the main thread, started while composed and stopped on dispose. A
 * [blurRadiusPx] greater than zero is applied with a render effect on the view itself.
 */
@Composable
fun AnimatedFileImage(
    file: File,
    modifier: Modifier = Modifier,
    blurRadiusPx: Float = 0f,
    contentDescription: String? = null,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP,
    revision: String = file.lastModified().toString(),
    dim: Float = 0f,
) {
    val drawable by produceState<Drawable?>(initialValue = null, file.path, revision) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
            }.getOrNull()
        }
    }

    val current = drawable ?: return
    DisposableEffect(current) {
        val animated = current as? AnimatedImageDrawable
        animated?.start()
        onDispose { animated?.stop() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                this.scaleType = scaleType
                this.contentDescription = contentDescription
            }
        },
        update = { view ->
            view.scaleType = scaleType
            view.contentDescription = contentDescription
            view.setImageDrawable(current)
            view.colorFilter = if (dim > 0f) android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix().apply {
                    setScale(1f - dim, 1f - dim, 1f - dim, 1f)
                }
            ) else null
            view.setRenderEffect(
                if (blurRadiusPx > 0f) {
                    RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
        },
        onReset = { view -> view.setImageDrawable(null) },
    )
}
