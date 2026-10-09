package me.weishu.kernelsu.ui.screen.wallpaper

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSliderItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.screen.home.LocalHomeLayoutStyle
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceDescriptor
import me.weishu.kernelsu.wallpaper.surface.SurfaceField
import me.weishu.kernelsu.wallpaper.surface.SurfaceFlag
import me.weishu.kernelsu.wallpaper.surface.SurfaceImageRow
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceScalarRow
import me.weishu.kernelsu.wallpaper.surface.SurfaceSliderRow
import me.weishu.kernelsu.wallpaper.surface.SurfaceToggleRow
import kotlin.math.roundToInt

/**
 * The mutation callbacks one rendered surface needs.
 *
 * A descriptor describes what to show; this groups how to apply the edits, so the renderer stays
 * independent of which surface it is drawing.
 */
class SurfaceCallbacks(
    val onToggle: (SurfaceField, Boolean) -> Unit,
    val onFlagChange: (SurfaceFlag, Boolean) -> Unit,
    val onSlider: (SurfaceField, Float) -> Unit,
    val onPickImage: () -> Unit,
    val onClearImage: () -> Unit,
)

/**
 * Renders the settings of every surface the active home layout actually uses.
 *
 * [SurfaceRegistry.forLayout] is the single availability gate, so a layout-exclusive surface such
 * as the grid work card can never leak into the settings of another layout. The rows themselves
 * come from each descriptor, so registering a surface is a data change, not a new UI branch.
 */
@Composable
fun SurfaceSettingsHost(
    state: WallpaperUiState,
    actions: WallpaperScreenActions,
) {
    val layout = LocalHomeLayoutStyle.current
    SurfaceRegistry.forLayout(layout).forEach { descriptor ->
        // A grouped child (the focus cards) only appears once its shared master switch is on,
        // mirroring the theme's own nesting so a card can never be configured while its parent is off.
        val parent = descriptor.parentId?.let { SurfaceRegistry.descriptor(it) }
        if (parent != null && state.surfaceFor(parent.id)?.enabled != true) return@forEach
        val config = state.surfaceFor(descriptor.id) ?: return@forEach
        SurfaceSettingsGroup(
            titleRes = descriptor.titleRes,
            rows = surfaceRows(descriptor, config, surfaceCallbacks(descriptor.id, actions)),
        )
    }
}

/**
 * The segmented rows of one surface, built from its descriptor.
 *
 * Shared by the settings host and each card's long-press dialog so both expose the same controls,
 * and every row carries a semantic icon declared by the descriptor.
 */
@Composable
fun surfaceRows(
    descriptor: SurfaceDescriptor,
    config: SurfaceConfig,
    callbacks: SurfaceCallbacks,
): List<@Composable () -> Unit> = buildList {
    descriptor.rows.forEach { row ->
        if (row.showWhenEnabled && !config.enabled) return@forEach
        when (row) {
            is SurfaceToggleRow -> add {
                val checked = row.field?.let { config.toggle(it) }
                    ?: row.flag?.let { config.hasFlag(it) }
                    ?: false
                SegmentedSwitchItem(
                    icon = row.icon,
                    title = stringResource(row.titleRes),
                    summary = row.summaryRes?.let { stringResource(it) },
                    checked = checked,
                    onCheckedChange = { value ->
                        row.field?.let { callbacks.onToggle(it, value) }
                        row.flag?.let { callbacks.onFlagChange(it, value) }
                    },
                )
            }

            is SurfaceSliderRow -> add {
                SegmentedSliderItem(
                    icon = row.icon,
                    title = stringResource(row.titleRes),
                    value = config.scalar(row.field),
                    valueRange = 0f..1f,
                    valueText = percentText,
                    onValueChangeFinished = { callbacks.onSlider(row.field, it) },
                )
            }

            is SurfaceScalarRow -> {
                if (config.toggle(row.dualField)) {
                    add {
                        SegmentedSliderItem(
                            icon = row.dayNightIcon,
                            title = stringResource(row.dayTitleRes),
                            value = config.scalar(row.dayField),
                            valueRange = 0f..1f,
                            valueText = percentText,
                            onValueChangeFinished = { callbacks.onSlider(row.dayField, it) },
                        )
                    }
                    add {
                        SegmentedSliderItem(
                            icon = row.dayNightIcon,
                            title = stringResource(row.nightTitleRes),
                            value = config.scalar(row.nightField),
                            valueRange = 0f..1f,
                            valueText = percentText,
                            onValueChangeFinished = { callbacks.onSlider(row.nightField, it) },
                        )
                    }
                } else {
                    add {
                        SegmentedSliderItem(
                            icon = row.singleIcon,
                            title = stringResource(row.singleTitleRes),
                            value = config.scalar(row.singleField),
                            valueRange = 0f..1f,
                            valueText = percentText,
                            onValueChangeFinished = { callbacks.onSlider(row.singleField, it) },
                        )
                    }
                }
            }

            is SurfaceImageRow -> {
                add {
                    SegmentedListItem(
                        onClick = callbacks.onPickImage,
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (config.hasImage) row.changeTitleRes else row.pickTitleRes
                                )
                            )
                        },
                        leadingContent = { Icon(row.pickIcon, null) },
                    )
                }
                if (config.hasImage) {
                    add {
                        SegmentedListItem(
                            onClick = callbacks.onClearImage,
                            headlineContent = { Text(stringResource(row.clearTitleRes)) },
                            leadingContent = { Icon(row.clearIcon, null) },
                        )
                    }
                }
            }
        }
    }
}

/** Wires a descriptor's rows to the wallpaper actions for the surface [id]. */
private fun surfaceCallbacks(id: SurfaceId, actions: WallpaperScreenActions): SurfaceCallbacks = SurfaceCallbacks(
    onToggle = { field, value -> actions.onSetSurfaceToggle(id, field, value) },
    onFlagChange = { flag, value -> actions.onSetSurfaceFlag(id, flag, value) },
    onSlider = { field, value -> actions.onSetSurfaceScalar(id, field, value) },
    onPickImage = { actions.onPickSurfaceImage(id) },
    onClearImage = { actions.onClearSurfaceImage(id) },
)

private val percentText: (Float) -> String = { "${(it * 100).roundToInt()}%" }
