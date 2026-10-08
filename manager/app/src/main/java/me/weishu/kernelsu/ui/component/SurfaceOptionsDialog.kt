package me.weishu.kernelsu.ui.component

import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.screen.wallpaper.SurfaceCallbacks
import me.weishu.kernelsu.ui.screen.wallpaper.surfaceRows
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore

/**
 * The long-press editor for any registered surface: the same controls as the settings panel,
 * wrapped in a dialog and wired straight to [SurfaceStore].
 *
 * Picking an image here goes through [SurfaceImagePickerHost], so the user gets the same
 * crop-to-fit choice as the settings panel and the stored file matches the card exactly.
 *
 * The rows come from the surface descriptor, so the dialog and the settings panel can never drift
 * apart.
 */
@Composable
fun SurfaceOptionsDialog(
    surfaceId: SurfaceId,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val descriptor = SurfaceRegistry.descriptor(surfaceId) ?: return

    var request by remember(surfaceId) { mutableStateOf<SurfaceId?>(null) }
    var isSaving by remember(surfaceId) { mutableStateOf(false) }

    SurfaceImagePickerHost(
        request = request,
        onDismissRequest = { request = null },
        onPicked = { id, uri ->
            request = null
            isSaving = true
            scope.launch {
                try {
                    if (!WallpaperManager.saveSurfaceImage(context, id, uri)) {
                        Toast.makeText(context, R.string.wallpaper_save_failed, Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isSaving = false
                }
            }
        },
    )
    if (request != null) return

    val callbacks = SurfaceCallbacks(
        onToggle = { field, value ->
            SurfaceStore.update(surfaceId) { it.withToggle(field, value) }
            WallpaperConfig.save(context)
        },
        onFlagChange = { flag, value ->
            SurfaceStore.update(surfaceId) { it.withFlag(flag, value) }
            WallpaperConfig.save(context)
        },
        onSlider = { field, value ->
            SurfaceStore.update(surfaceId) { it.withScalar(field, value.coerceIn(0f, 1f)) }
            WallpaperConfig.save(context)
        },
        onPickImage = { if (!isSaving) request = surfaceId },
        onClearImage = {
            if (!isSaving) scope.launch { WallpaperManager.clearSurfaceImage(context, surfaceId) }
        },
    )
    val rows = surfaceRows(descriptor, SurfaceStore.config(surfaceId), callbacks)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(descriptor.titleRes)) },
        text = {
            if (isSaving) {
                CircularProgressIndicator()
            } else {
                SegmentedColumn(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    content = rows,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
