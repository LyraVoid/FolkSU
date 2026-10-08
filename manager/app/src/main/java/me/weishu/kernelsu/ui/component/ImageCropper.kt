package me.weishu.kernelsu.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R

private const val MAX_CROP_SIDE = 1600
private const val MAX_SOURCE_SIDE = 2048

/** A single transformed preview; the same geometry maps the frame back to source pixels. */
@Composable
fun SurfaceCropDialog(
    source: Uri,
    aspect: Float,
    onDismiss: () -> Unit,
    onCropped: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(source) { mutableStateOf(true) }
    var saving by remember(source) { mutableStateOf(false) }
    var failed by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        bitmap = withContext(Dispatchers.IO) { decodeSource(context, source) }
        loading = false
        failed = bitmap == null
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val image = bitmap
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (image != null) {
                SurfaceCropEditor(
                    bitmap = image,
                    aspect = aspect,
                    saving = saving,
                    failed = failed,
                    onCancel = onDismiss,
                    onCropped = { region ->
                        if (!saving) {
                            saving = true
                            failed = false
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    writeCropped(context, image, region)
                                }
                                saving = false
                                if (result != null) onCropped(result) else failed = true
                            }
                        }
                    },
                )
            } else {
                Text(
                    stringResource(R.string.wallpaper_save_failed),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text(stringResource(android.R.string.cancel), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SurfaceCropEditor(
    bitmap: Bitmap,
    aspect: Float,
    saving: Boolean,
    failed: Boolean,
    onCancel: () -> Unit,
    onCropped: (CropRegion) -> Unit,
) {
    val density = LocalDensity.current
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val buttonBarHeight = 72.dp
        val areaWidth = with(density) { maxWidth.toPx() }
        val areaHeight = with(density) { (maxHeight - buttonBarHeight).toPx() }
        if (areaWidth <= 0f || areaHeight <= 0f) return@BoxWithConstraints
        val geometry = remember(bitmap, aspect, areaWidth, areaHeight) {
            CropGeometry(bitmap.width, bitmap.height, areaWidth, areaHeight, aspect)
        }
        var zoom by remember(geometry) { mutableFloatStateOf(1f) }
        var offset by remember(geometry) { mutableStateOf(Offset.Zero) }

        Canvas(
            Modifier.fillMaxSize().padding(bottom = buttonBarHeight).clipToBounds()
                .pointerInput(geometry, saving) {
                    if (!saving) {
                        detectTransformGestures { _, pan, scale, _ ->
                            val nextZoom = (zoom * scale).coerceIn(1f, 6f)
                            offset = Offset(
                                geometry.clampX(offset.x + pan.x, nextZoom),
                                geometry.clampY(offset.y + pan.y, nextZoom),
                            )
                            zoom = nextZoom
                        }
                    }
                },
        ) {
            // Draw exactly once: nested Image layouts would constrain the source to the frame.
            withTransform({
                translate(size.width / 2f + offset.x, size.height / 2f + offset.y)
                scale(geometry.fitScale * zoom, geometry.fitScale * zoom, pivot = Offset.Zero)
            }) {
                drawImage(image, topLeft = Offset(-bitmap.width / 2f, -bitmap.height / 2f))
            }
            val left = geometry.windowLeft
            val top = geometry.windowTop
            val width = geometry.windowWidth
            val height = geometry.windowHeight
            val scrim = Color.Black.copy(alpha = 0.6f)
            drawRect(scrim, size = Size(size.width, top))
            drawRect(scrim, Offset(0f, top + height), Size(size.width, (size.height - top - height).coerceAtLeast(0f)))
            drawRect(scrim, Offset(0f, top), Size(left, height))
            drawRect(scrim, Offset(left + width, top), Size((size.width - left - width).coerceAtLeast(0f), height))
            drawRect(Color.White, Offset(left, top), Size(width, height), style = Stroke(2.dp.toPx()))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) {
                Text(stringResource(android.R.string.cancel), color = Color.White)
            }
            if (saving) CircularProgressIndicator()
            if (failed) {
                Text(stringResource(R.string.wallpaper_save_failed), color = Color.White, modifier = Modifier.weight(1f))
            }
            TextButton(
                enabled = !saving,
                onClick = { onCropped(geometry.crop(zoom, offset.x, offset.y)) },
            ) {
                Text(stringResource(android.R.string.ok), color = Color.White)
            }
        }
    }
}

private fun decodeSource(context: Context, uri: Uri): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        if (longest > MAX_SOURCE_SIDE) {
            decoder.setTargetSampleSize((longest + MAX_SOURCE_SIDE - 1) / MAX_SOURCE_SIDE)
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

private fun writeCropped(context: Context, source: Bitmap, region: CropRegion): Uri? {
    var cropped: Bitmap? = null
    var output: Bitmap? = null
    var file: File? = null
    try {
        cropped = Bitmap.createBitmap(source, region.left, region.top, region.width, region.height)
        val longest = max(cropped.width, cropped.height)
        output = if (longest > MAX_CROP_SIDE) {
            val ratio = MAX_CROP_SIDE.toFloat() / longest
            cropped.scale(
                (cropped.width * ratio).roundToInt().coerceAtLeast(1),
                (cropped.height * ratio).roundToInt().coerceAtLeast(1),
            )
        } else cropped
        val directory = File(context.cacheDir, "surface_crop_cache").apply { mkdirs() }
        file = File.createTempFile("cropped_", ".jpg", directory)
        file.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
        return Uri.fromFile(file)
    } catch (e: CancellationException) {
        file?.delete()
        throw e
    } catch (_: Exception) {
        file?.delete()
        return null
    } finally {
        if (output !== source && output !== cropped) output?.recycle()
        if (cropped !== source) cropped?.recycle()
    }
}
