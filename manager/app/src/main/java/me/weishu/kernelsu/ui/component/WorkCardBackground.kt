package me.weishu.kernelsu.ui.component

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveSwitch
import me.weishu.kernelsu.ui.component.material.folkPressScale
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager
import kotlin.math.roundToInt

/**
 * The controls for the grid work card's photo background.
 *
 * Stateless so both the wallpaper settings panel and the card's long-press dialog can drive it:
 * the caller owns the pick/clear flows and persists each edit, exactly like the appearance panel.
 */
@Composable
fun WorkCardBackgroundSettings(
    enabled: Boolean,
    hasImage: Boolean,
    opacity: Float,
    dim: Float,
    dualOpacityEnabled: Boolean,
    dayOpacity: Float,
    nightOpacity: Float,
    checkHidden: Boolean,
    textHidden: Boolean,
    modeHidden: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDimChange: (Float) -> Unit,
    onDualOpacityChange: (Boolean) -> Unit,
    onDayOpacityChange: (Float) -> Unit,
    onNightOpacityChange: (Float) -> Unit,
    onCheckHiddenChange: (Boolean) -> Unit,
    onTextHiddenChange: (Boolean) -> Unit,
    onModeHiddenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkCardSwitchRow(
            title = stringResource(R.string.wallpaper_work_card_enable),
            summary = stringResource(R.string.wallpaper_work_card_enable_summary),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
        WorkCardActionRow(
            icon = Icons.Filled.Image,
            title = stringResource(
                if (hasImage) R.string.wallpaper_work_card_change else R.string.wallpaper_work_card_pick
            ),
            onClick = onPickImage,
        )
        if (hasImage) {
            WorkCardActionRow(
                icon = Icons.Filled.Delete,
                title = stringResource(R.string.wallpaper_work_card_clear),
                onClick = onClearImage,
            )
        }
        WorkCardSlider(
            title = stringResource(R.string.wallpaper_work_card_opacity),
            value = opacity,
            onValueChange = onOpacityChange,
        )
        WorkCardSlider(
            title = stringResource(R.string.wallpaper_work_card_dim),
            value = dim,
            onValueChange = onDimChange,
        )
        WorkCardSwitchRow(
            title = stringResource(R.string.wallpaper_work_card_dual_opacity),
            checked = dualOpacityEnabled,
            onCheckedChange = onDualOpacityChange,
        )
        if (dualOpacityEnabled) {
            WorkCardSlider(
                title = stringResource(R.string.wallpaper_work_card_day_opacity),
                value = dayOpacity,
                onValueChange = onDayOpacityChange,
            )
            WorkCardSlider(
                title = stringResource(R.string.wallpaper_work_card_night_opacity),
                value = nightOpacity,
                onValueChange = onNightOpacityChange,
            )
        }
        WorkCardSwitchRow(
            title = stringResource(R.string.wallpaper_work_card_hide_check),
            checked = checkHidden,
            onCheckedChange = onCheckHiddenChange,
        )
        WorkCardSwitchRow(
            title = stringResource(R.string.wallpaper_work_card_hide_text),
            checked = textHidden,
            onCheckedChange = onTextHiddenChange,
        )
        WorkCardSwitchRow(
            title = stringResource(R.string.wallpaper_work_card_hide_mode),
            checked = modeHidden,
            onCheckedChange = onModeHiddenChange,
        )
    }
}

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallpaper_work_card_options_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                WorkCardBackgroundSettings(
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
            }
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

@Composable
private fun WorkCardSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExpressiveSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WorkCardActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .folkPressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WorkCardSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val sliderState = rememberSliderState(value = value, trackRange = 0f..1f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${(sliderState.value * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            state = sliderState,
            onValueChangeFinished = { onValueChange(sliderState.value) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
