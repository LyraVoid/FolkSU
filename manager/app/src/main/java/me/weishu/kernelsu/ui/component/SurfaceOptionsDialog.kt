package me.weishu.kernelsu.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
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
 * wrapped in a dialog and wired straight to [SurfaceStore]. Picking here launches the system image
 * picker and stores the file through [WallpaperManager].
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

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch { WallpaperManager.saveSurfaceImage(context, surfaceId, uri) }
        }
    }

    val descriptor = SurfaceRegistry.descriptor(surfaceId) ?: return
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
        onPickImage = { pickImageLauncher.launch("image/*") },
        onClearImage = { WallpaperManager.clearSurfaceImage(context, surfaceId) },
    )
    val rows = surfaceRows(descriptor, SurfaceStore.config(surfaceId), callbacks)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(descriptor.titleRes)) },
        text = {
            SegmentedColumn(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                content = rows,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
