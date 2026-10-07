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
import me.weishu.kernelsu.ui.screen.wallpaper.workCardRows
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager

/**
 * The card's long-press options: the same controls as the settings panel, wrapped in a dialog and
 * wired straight to [WallpaperConfig]. Picking here launches the system image picker directly.
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

    val rows = workCardRows(
        enabled = WallpaperConfig.workCardBackgroundEnabled,
        hasImage = !WallpaperConfig.workCardBackgroundUri.isNullOrEmpty(),
        opacity = WallpaperConfig.workCardOpacity,
        dim = WallpaperConfig.workCardDim,
        dualOpacityEnabled = WallpaperConfig.workCardDualOpacityEnabled,
        dayOpacity = WallpaperConfig.workCardDayOpacity,
        nightOpacity = WallpaperConfig.workCardNightOpacity,
        checkHidden = WallpaperConfig.workCardCheckHidden,
        textHidden = WallpaperConfig.workCardTextHidden,
        modeHidden = WallpaperConfig.workCardModeHidden,
        onEnabledChange = { persist(context) { WallpaperConfig.updateWorkCardBackgroundEnabled(it) } },
        onPickImage = { pickImageLauncher.launch("image/*") },
        onClearImage = { WallpaperManager.clearWorkCardBackground(context) },
        onOpacityChange = { persist(context) { WallpaperConfig.updateWorkCardOpacity(it) } },
        onDimChange = { persist(context) { WallpaperConfig.updateWorkCardDim(it) } },
        onDualOpacityChange = { persist(context) { WallpaperConfig.updateWorkCardDualOpacityEnabled(it) } },
        onDayOpacityChange = { persist(context) { WallpaperConfig.updateWorkCardDayOpacity(it) } },
        onNightOpacityChange = { persist(context) { WallpaperConfig.updateWorkCardNightOpacity(it) } },
        onCheckHiddenChange = { persist(context) { WallpaperConfig.updateWorkCardCheckHidden(it) } },
        onTextHiddenChange = { persist(context) { WallpaperConfig.updateWorkCardTextHidden(it) } },
        onModeHiddenChange = { persist(context) { WallpaperConfig.updateWorkCardModeHidden(it) } },
    )

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
