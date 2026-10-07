package me.weishu.kernelsu.ui.screen.wallpaper

import androidx.compose.runtime.Composable
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.WorkCardBackgroundSettings
import me.weishu.kernelsu.ui.screen.home.LocalHomeLayoutStyle
import me.weishu.kernelsu.wallpaper.surface.SurfaceDescriptor
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry

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
            SurfaceRegistry.GRID_WORK_CARD -> GridWorkCardSurface(state, actions, descriptor)
        }
    }
}

@Composable
private fun GridWorkCardSurface(
    state: WallpaperUiState,
    actions: WallpaperScreenActions,
    descriptor: SurfaceDescriptor,
) {
    SurfaceSettingsGroup(titleRes = descriptor.titleRes) {
        WorkCardBackgroundSettings(
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
        )
    }
}
