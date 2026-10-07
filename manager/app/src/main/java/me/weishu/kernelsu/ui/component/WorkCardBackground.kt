package me.weishu.kernelsu.ui.component

import android.content.Context
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
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.screen.wallpaper.SurfaceCallbacks
import me.weishu.kernelsu.ui.screen.wallpaper.surfaceRows
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager
import me.weishu.kernelsu.wallpaper.surface.SurfaceField
import me.weishu.kernelsu.wallpaper.surface.SurfaceFlag
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry

/**
 * The card's long-press options: the same controls as the settings panel, wrapped in a dialog and
 * wired straight to [WallpaperConfig]. Picking here launches the system image picker directly.
 *
 * The rows come from the work-card descriptor, so the dialog and the settings panel can never
 * drift apart.
 */
@Composable
fun WorkCardBackgroundDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch { WallpaperManager.saveWorkCardBackground(context, uri) }
        }
    }

    val descriptor = SurfaceRegistry.descriptor(SurfaceRegistry.GRID_WORK_CARD)!!
    val callbacks = SurfaceCallbacks(
        onToggle = { field, value ->
            when (field) {
                SurfaceField.Enabled -> persist(context) { WallpaperConfig.updateWorkCardBackgroundEnabled(value) }
                SurfaceField.DualOpacity -> persist(context) { WallpaperConfig.updateWorkCardDualOpacityEnabled(value) }
                else -> Unit
            }
        },
        onFlagChange = { flag, value ->
            when (flag) {
                SurfaceFlag.HideIcon -> persist(context) { WallpaperConfig.updateWorkCardCheckHidden(value) }
                SurfaceFlag.HideText -> persist(context) { WallpaperConfig.updateWorkCardTextHidden(value) }
                SurfaceFlag.HideMode -> persist(context) { WallpaperConfig.updateWorkCardModeHidden(value) }
            }
        },
        onSlider = { field, value ->
            when (field) {
                SurfaceField.Opacity -> persist(context) { WallpaperConfig.updateWorkCardOpacity(value) }
                SurfaceField.Dim -> persist(context) { WallpaperConfig.updateWorkCardDim(value) }
                SurfaceField.DayOpacity -> persist(context) { WallpaperConfig.updateWorkCardDayOpacity(value) }
                SurfaceField.NightOpacity -> persist(context) { WallpaperConfig.updateWorkCardNightOpacity(value) }
                else -> Unit
            }
        },
        onPickImage = { pickImageLauncher.launch("image/*") },
        onClearImage = { WallpaperManager.clearWorkCardBackground(context) },
    )
    val rows = surfaceRows(descriptor, WallpaperConfig.workCardSurface, callbacks)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallpaper_work_card_options_title)) },
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

private inline fun persist(context: Context, block: () -> Unit) {
    block()
    WallpaperConfig.save(context)
}
