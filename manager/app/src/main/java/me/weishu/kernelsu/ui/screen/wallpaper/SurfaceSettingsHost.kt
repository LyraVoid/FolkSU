package me.weishu.kernelsu.ui.screen.wallpaper

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSliderItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.screen.home.LocalHomeLayoutStyle
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import kotlin.math.roundToInt

/**
 * Renders the settings of every surface the active home layout actually uses.
 *
 * [SurfaceRegistry.forLayout] is the single availability gate, so a layout-exclusive surface such
 * as the grid work card can never leak into the settings of another layout.
 */
@Composable
fun SurfaceSettingsHost(
    state: WallpaperUiState,
    actions: WallpaperScreenActions,
) {
    val layout = LocalHomeLayoutStyle.current
    SurfaceRegistry.forLayout(layout).forEach { descriptor ->
        when (descriptor.id) {
            SurfaceRegistry.GRID_WORK_CARD -> SurfaceSettingsGroup(
                titleRes = descriptor.titleRes,
                rows = workCardRows(
                    enabled = state.workCardBackgroundEnabled,
                    hasImage = state.workCardHasImage,
                    opacity = state.workCardOpacity,
                    dim = state.workCardDim,
                    dualOpacityEnabled = state.workCardDualOpacityEnabled,
                    dayOpacity = state.workCardDayOpacity,
                    nightOpacity = state.workCardNightOpacity,
                    checkHidden = state.workCardCheckHidden,
                    textHidden = state.workCardTextHidden,
                    modeHidden = state.workCardModeHidden,
                    onEnabledChange = actions.onToggleWorkCardBackground,
                    onPickImage = actions.onPickWorkCardImage,
                    onClearImage = actions.onClearWorkCardImage,
                    onOpacityChange = actions.onSetWorkCardOpacity,
                    onDimChange = actions.onSetWorkCardDim,
                    onDualOpacityChange = actions.onToggleWorkCardDualOpacity,
                    onDayOpacityChange = actions.onSetWorkCardDayOpacity,
                    onNightOpacityChange = actions.onSetWorkCardNightOpacity,
                    onCheckHiddenChange = actions.onToggleWorkCardCheckHidden,
                    onTextHiddenChange = actions.onToggleWorkCardTextHidden,
                    onModeHiddenChange = actions.onToggleWorkCardModeHidden,
                ),
            )
        }
    }
}

/**
 * The segmented rows of the grid work-card surface.
 *
 * Shared by the settings host and the card's long-press dialog so both expose the same controls,
 * and every row carries a semantic icon.
 */
fun workCardRows(
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
): List<@Composable () -> Unit> = buildList {
    add {
        SegmentedSwitchItem(
            icon = Icons.Filled.Wallpaper,
            title = stringResource(R.string.wallpaper_work_card_enable),
            summary = stringResource(R.string.wallpaper_work_card_enable_summary),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
    }
    // The tuning controls and the image rows only matter once the card background is on; the order
    // matches the reference implementation: appearance first, then the image itself.
    if (enabled) {
        add {
            SegmentedSwitchItem(
                icon = Icons.Filled.Contrast,
                title = stringResource(R.string.wallpaper_work_card_dual_opacity),
                checked = dualOpacityEnabled,
                onCheckedChange = onDualOpacityChange,
            )
        }
        if (dualOpacityEnabled) {
            add {
                SegmentedSliderItem(
                    icon = Icons.Filled.Contrast,
                    title = stringResource(R.string.wallpaper_work_card_day_opacity),
                    value = dayOpacity,
                    valueRange = 0f..1f,
                    valueText = percentText,
                    onValueChangeFinished = onDayOpacityChange,
                )
            }
            add {
                SegmentedSliderItem(
                    icon = Icons.Filled.Contrast,
                    title = stringResource(R.string.wallpaper_work_card_night_opacity),
                    value = nightOpacity,
                    valueRange = 0f..1f,
                    valueText = percentText,
                    onValueChangeFinished = onNightOpacityChange,
                )
            }
        } else {
            add {
                SegmentedSliderItem(
                    icon = Icons.Filled.Opacity,
                    title = stringResource(R.string.wallpaper_work_card_opacity),
                    value = opacity,
                    valueRange = 0f..1f,
                    valueText = percentText,
                    onValueChangeFinished = onOpacityChange,
                )
            }
        }
        add {
            SegmentedSliderItem(
                icon = Icons.Filled.Brightness6,
                title = stringResource(R.string.wallpaper_work_card_dim),
                value = dim,
                valueRange = 0f..1f,
                valueText = percentText,
                onValueChangeFinished = onDimChange,
            )
        }
        add {
            SegmentedListItem(
                onClick = onPickImage,
                headlineContent = {
                    Text(
                        stringResource(
                            if (hasImage) R.string.wallpaper_work_card_change else R.string.wallpaper_work_card_pick
                        )
                    )
                },
                leadingContent = { Icon(Icons.Filled.Image, null) },
            )
        }
        if (hasImage) {
            add {
                SegmentedListItem(
                    onClick = onClearImage,
                    headlineContent = { Text(stringResource(R.string.wallpaper_work_card_clear)) },
                    leadingContent = { Icon(Icons.Filled.Delete, null) },
                )
            }
        }
    }
    add {
        SegmentedSwitchItem(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.wallpaper_work_card_hide_check),
            checked = checkHidden,
            onCheckedChange = onCheckHiddenChange,
        )
    }
    add {
        SegmentedSwitchItem(
            icon = Icons.Filled.TextFields,
            title = stringResource(R.string.wallpaper_work_card_hide_text),
            checked = textHidden,
            onCheckedChange = onTextHiddenChange,
        )
    }
    add {
        SegmentedSwitchItem(
            icon = Icons.AutoMirrored.Filled.Label,
            title = stringResource(R.string.wallpaper_work_card_hide_mode),
            checked = modeHidden,
            onCheckedChange = onModeHiddenChange,
        )
    }
}

private val percentText: (Float) -> String = { "${(it * 100).roundToInt()}%" }
