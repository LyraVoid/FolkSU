package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.HomeMetrics
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore

/**
 * FocusUI: a board of tiles - status, manager, system facts and counts - that becomes a 2x2 grid on
 * a wide screen and a plain stack otherwise.
 *
 * Every fact is told once: the status tile owns the working state and the mode, the manager tile the
 * build and policy, and the device and storage tiles carry the live device metrics.
 */
@Composable
internal fun FocusHomeContent(
    state: HomeUiState,
    actions: HomeActions,
    metrics: HomeMetrics,
) {
    val fullFeatured = Natives.isFullFeatured()
    val focusStyle = SurfaceStore.config(SurfaceRegistry.FOCUS)
    fun focusBackground(id: SurfaceId): SurfaceConfig? {
        val card = SurfaceStore.config(id)
        return if (card.hasImage) focusStyle.copy(imageUri = card.imageUri) else null
    }

    if (!fullFeatured || !isWideLayout(withOrientation = true)) {
        Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
            FocusStatusTile(
                state = state,
                actions = actions,
                background = focusBackground(SurfaceRegistry.FOCUS_CARD_KERNEL),
            )
            HomeFactsTile(
                state = state,
                title = stringResource(R.string.home_tile_manager),
                icon = Icons.Outlined.AdminPanelSettings,
                background = focusBackground(SurfaceRegistry.FOCUS_CARD_APP),
            )
            FocusDeviceTile(
                metrics = metrics,
                background = focusBackground(SurfaceRegistry.FOCUS_CARD_DEVICE),
            )
            FocusStorageTile(
                metrics = metrics,
                background = focusBackground(SurfaceRegistry.FOCUS_CARD_STORAGE),
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            FocusStatusTile(
                state = state,
                actions = actions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                background = focusBackground(SurfaceRegistry.FOCUS_CARD_KERNEL),
            )
            HomeFactsTile(
                state = state,
                title = stringResource(R.string.home_tile_manager),
                icon = Icons.Outlined.AdminPanelSettings,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                background = focusBackground(SurfaceRegistry.FOCUS_CARD_APP),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            FocusDeviceTile(
                metrics = metrics,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                background = focusBackground(SurfaceRegistry.FOCUS_CARD_DEVICE),
            )
            FocusStorageTile(
                metrics = metrics,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                background = focusBackground(SurfaceRegistry.FOCUS_CARD_STORAGE),
            )
        }
    }
}

/**
 * The Focus board's status card: the state of the module, its version and how it is loaded.
 */
@Composable
private fun FocusStatusTile(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    background: SurfaceConfig? = null,
) {
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()
    val jailbreak = notInstalled && state.isSELinuxPermissive

    val statusTitle = when {
        ksuActive -> stringResource(R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }

    val cardAction: (@Composable () -> Unit)? = when {
        jailbreak -> {
            { StatusJailbreakButton(onClick = actions.onJailbreakClick) }
        }

        notInstalled -> {
            { StatusInstallButton(onClick = actions.onInstallClick) }
        }

        else -> null
    }

    HomeTileCard(
        title = stringResource(R.string.home_tile_status),
        iconRes = R.drawable.ic_kernelsu_foreground,
        modifier = modifier.fillMaxWidth(),
        action = cardAction,
        background = background,
    ) {
        HomeFactRow(
            label = stringResource(R.string.home_running_status),
            value = statusTitle,
        )
        HomeFactRow(
            label = stringResource(R.string.home_version),
            value = if (ksuActive) {
                "${state.ksuVersion}-${state.kernelUAPIVersion}"
            } else {
                stringResource(R.string.home_not_installed)
            },
        )
        statusStateTexts(state).forEach { (label, value) ->
            HomeFactRow(label = label, value = value)
        }
    }
}

/** The live battery and CPU metrics of the device the module is running on. */
@Composable
private fun FocusDeviceTile(
    metrics: HomeMetrics,
    modifier: Modifier = Modifier,
    background: SurfaceConfig? = null,
) {
    val device = metrics.device
    val cpuTemperature = device?.cpuTemperatureC

    HomeTileCard(
        title = stringResource(R.string.home_tile_device),
        icon = Icons.Outlined.Memory,
        modifier = modifier.fillMaxWidth(),
        background = background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusCircle(
                value = device?.batteryTemperatureC?.let { "${it.roundToInt()}°C" } ?: DEFAULT_METRIC,
                label = stringResource(R.string.home_metric_battery_temperature),
                progress = device?.batteryTemperatureC?.let { (it / BATTERY_TEMPERATURE_MAX_C).coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
            )
            StatusCircle(
                value = cpuTemperature?.let { "${it.roundToInt()}°C" } ?: DEFAULT_METRIC,
                label = stringResource(R.string.home_metric_cpu_temperature),
                progress = cpuTemperature?.let { (it / CPU_TEMPERATURE_MAX_C).coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.secondary,
            )
            StatusCircle(
                value = device?.batteryLevelPercent?.let { "$it%" } ?: DEFAULT_METRIC,
                label = stringResource(R.string.home_metric_battery_level),
                progress = device?.batteryLevelPercent?.let { it / 100f },
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** The live internal storage and memory usage, plus zram and swap when the device uses them. */
@Composable
private fun FocusStorageTile(
    metrics: HomeMetrics,
    modifier: Modifier = Modifier,
    background: SurfaceConfig? = null,
) {
    val storage = metrics.storage

    HomeTileCard(
        title = stringResource(R.string.home_tile_storage),
        icon = Icons.Outlined.SdStorage,
        modifier = modifier.fillMaxWidth(),
        background = background,
    ) {
        StorageBar(
            label = stringResource(R.string.home_metric_storage_internal),
            usedBytes = storage?.dataUsedBytes ?: 0L,
            totalBytes = storage?.dataTotalBytes ?: 0L,
            color = MaterialTheme.colorScheme.primary,
        )
        StorageBar(
            label = stringResource(R.string.home_metric_storage_ram),
            usedBytes = storage?.ramUsedBytes ?: 0L,
            totalBytes = storage?.ramTotalBytes ?: 0L,
            color = MaterialTheme.colorScheme.secondary,
        )
        if ((storage?.zramTotalBytes ?: 0L) > 0L) {
            StorageBar(
                label = stringResource(R.string.home_metric_storage_zram),
                usedBytes = storage?.zramUsedBytes ?: 0L,
                totalBytes = storage?.zramTotalBytes ?: 0L,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if ((storage?.swapTotalBytes ?: 0L) > 0L) {
            StorageBar(
                label = stringResource(R.string.home_metric_storage_swap),
                usedBytes = storage?.swapUsedBytes ?: 0L,
                totalBytes = storage?.swapTotalBytes ?: 0L,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
