package me.weishu.kernelsu.ui.screen.home

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.HomeMetrics
import me.weishu.kernelsu.ui.component.material.FolkButton
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import kotlin.math.roundToInt

/** The gap between the tiles of every multi-column home layout. */
private val TileSpacing = 16.dp

/**
 * The width from which a home layout lays its tiles out in columns instead of one long stack.
 *
 * The orientation clause comes from the layout this one is modelled on: a tall screen gains nothing
 * from two narrow columns, so the board only splits when there is more width than height. It reads
 * the window size rather than `BoxWithConstraints`, because these layouts live inside a scrolling
 * column where the available height is unbounded.
 */
@Composable
private fun isWideLayout(withOrientation: Boolean): Boolean {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    return width >= 600 && (!withOrientation || width > height)
}

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
    if (!fullFeatured || !isWideLayout(withOrientation = true)) {
        Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
            FocusStatusTile(state = state, actions = actions)
            FocusManagerTile(state = state)
            FocusDeviceTile(metrics = metrics)
            FocusStorageTile(metrics = metrics)
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
            )
            FocusManagerTile(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
            )
            FocusStorageTile(
                metrics = metrics,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * DashboardUI: one wide hero banner over the counters and the system facts.
 *
 * The facts card is the one every layout ends with, so the wide form only has to decide whether it
 * sits beside the counters or under them.
 */
@Composable
internal fun DashboardHomeContent(
    state: HomeUiState,
    actions: HomeActions,
    superuserCount: Int,
    moduleEnabledCount: Int,
) {
    val fullFeatured = Natives.isFullFeatured()
    Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        DashboardHeroCard(state = state, actions = actions)
        if (fullFeatured && isWideLayout(withOrientation = false)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                CountCardPair(
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                    onOpenSuperUser = actions.onOpenSuperUser,
                    onOpenModule = actions.onOpenModule,
                    layout = CountCardLayout.Vertical,
                    modifier = Modifier.weight(1f),
                    emphasis = CountCardEmphasis.Value,
                )
                InfoCard(
                    systemInfo = state.systemInfo,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            if (fullFeatured) {
                CountCardPair(
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                    onOpenSuperUser = actions.onOpenSuperUser,
                    onOpenModule = actions.onOpenModule,
                    layout = CountCardLayout.Horizontal,
                    emphasis = CountCardEmphasis.Value,
                )
            }
            InfoCard(systemInfo = state.systemInfo)
        }
    }
}

/**
 * StatsUI: the status card over the two counters read as figures, then the system facts.
 *
 * The monitoring charts this layout was modelled on are not part of this batch, so the counters are
 * what carries the layout until they arrive.
 */
@Composable
internal fun StatsHomeContent(
    state: HomeUiState,
    actions: HomeActions,
    superuserCount: Int,
    moduleEnabledCount: Int,
) {
    val fullFeatured = Natives.isFullFeatured()
    if (fullFeatured && isWideLayout(withOrientation = false)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                StatusCard(state = state, actions = actions)
                CountCardPair(
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                    onOpenSuperUser = actions.onOpenSuperUser,
                    onOpenModule = actions.onOpenModule,
                    layout = CountCardLayout.Horizontal,
                    emphasis = CountCardEmphasis.Value,
                )
            }
            InfoCard(
                systemInfo = state.systemInfo,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        StatusCard(state = state, actions = actions)
        if (fullFeatured) {
            CountCardPair(
                superuserCount = superuserCount,
                moduleEnabledCount = moduleEnabledCount,
                onOpenSuperUser = actions.onOpenSuperUser,
                onOpenModule = actions.onOpenModule,
                layout = CountCardLayout.Horizontal,
                emphasis = CountCardEmphasis.Value,
            )
        }
        InfoCard(systemInfo = state.systemInfo)
    }
}

/**
 * The Dashboard banner: the state icon and title over the three facts the hero was built to carry
 * (the working version, the kernel and SELinux).
 *
 * It is a plain card, exactly like the ones under it: the state is read from the icon and the
 * wording, so the surface never has to switch to an accent role.
 */
@Composable
private fun DashboardHeroCard(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()

    val statusIcon = when {
        ksuActive -> Icons.Rounded.CheckCircle
        notInstalled -> Icons.Rounded.Warning
        else -> Icons.Rounded.Block
    }
    val statusTitle = when {
        ksuActive -> stringResource(R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val statusSummary = when {
        ksuActive -> stringResource(R.string.home_working_version, "${state.ksuVersion}-${state.kernelUAPIVersion}")
        notInstalled -> stringResource(R.string.home_click_to_install)
        else -> stringResource(R.string.home_unsupported_reason)
    }
    val workingMode = workingModeLabel(state)

    HomeCard(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            if (!state.isLateLoadMode) {
                actions.onInstallClick()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusTitle,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = statusTitle, style = FolkType.Title)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = statusSummary,
                        style = FolkType.Summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (workingMode.isNotEmpty()) {
                    Spacer(Modifier.width(16.dp))
                    StatusTag(
                        label = workingMode,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        backgroundColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeroFact(
                    label = stringResource(R.string.module_version),
                    value = if (ksuActive) "${state.ksuVersion}-${state.kernelUAPIVersion}" else "—",
                    modifier = Modifier.weight(1f),
                )
                HeroFact(
                    label = stringResource(R.string.home_kernel),
                    value = state.systemInfo.kernelVersion,
                    modifier = Modifier.weight(1f),
                )
                HeroFact(
                    label = stringResource(R.string.home_selinux_status),
                    value = selinuxDisplayName(state.systemInfo.selinuxStatus),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One label over its value, the column unit of the hero banner. */
@Composable
private fun HeroFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = FolkType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = FolkType.Summary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One card of the Focus board: the icon-and-title header, a hairline, then the rows. The action
 * button in the header is the card's only control.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = contentColorFor(containerColor),
    wallpaperRole: WallpaperSurfaceRole? =
        if (containerColor == MaterialTheme.colorScheme.surfaceBright) WallpaperSurfaceRole.Group else null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    actionText: String? = null,
    onActionClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    HomeCard(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        wallpaperRole = wallpaperRole,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerIconModifier = Modifier.size(32.dp)
                when {
                    iconRes != null -> Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = headerIconModifier,
                        tint = iconTint,
                    )

                    icon != null -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = headerIconModifier,
                        tint = iconTint,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                if (actionText != null) {
                    FolkButton(
                        onClick = onActionClick,
                        contentPadding = ButtonDefaults.MediumContentPadding,
                    ) {
                        Text(text = actionText)
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
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
) {
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()

    val statusTitle = when {
        ksuActive -> stringResource(R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val workingMode = workingModeLabel(state)
    val lateLoad = ksuActive && state.isLateLoadMode

    FocusCard(
        title = if (lateLoad) stringResource(R.string.jailbreak_mode) else stringResource(R.string.home_tile_status),
        iconRes = R.drawable.ic_kernelsu_foreground,
        modifier = modifier.fillMaxWidth(),
        actionText = if (notInstalled) stringResource(R.string.install) else null,
        onActionClick = actions.onInstallClick,
    ) {
        FocusInfoRow(
            label = stringResource(R.string.home_running_status),
            value = statusTitle,
        )
        FocusInfoRow(
            label = stringResource(R.string.home_version),
            value = if (ksuActive) {
                "${state.ksuVersion}-${state.kernelUAPIVersion}"
            } else {
                stringResource(R.string.home_not_installed)
            },
        )
        if (workingMode.isNotEmpty()) {
            FocusInfoRow(
                label = stringResource(R.string.home_running_mode),
                value = workingMode,
            )
        }
    }
}

/** The manager build and the policy it runs under. */
@Composable
private fun FocusManagerTile(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    FocusCard(
        title = stringResource(R.string.home_tile_manager),
        icon = Icons.Outlined.AdminPanelSettings,
        modifier = modifier.fillMaxWidth(),
    ) {
        FocusInfoRow(
            label = stringResource(R.string.home_manager_version),
            value = state.systemInfo.managerVersion,
        )
        FocusInfoRow(
            label = stringResource(R.string.home_kernel),
            value = state.systemInfo.kernelVersion,
        )
        FocusInfoRow(
            label = stringResource(R.string.home_device_model),
            value = state.systemInfo.deviceModel,
        )
        FocusInfoRow(
            label = stringResource(R.string.home_fingerprint),
            value = state.systemInfo.fingerprint,
        )
        FocusInfoRow(
            label = stringResource(R.string.home_selinux_status),
            value = selinuxDisplayName(state.systemInfo.selinuxStatus),
        )
        FocusInfoRow(
            label = stringResource(R.string.home_seccomp_status),
            value = seccompDisplayName(state.systemInfo.seccompStatus),
        )
    }
}

/** The live battery and CPU metrics of the device the module is running on. */
@Composable
private fun FocusDeviceTile(
    metrics: HomeMetrics,
    modifier: Modifier = Modifier,
) {
    val device = metrics.device
    val cpuTemperature = device?.cpuTemperatureC

    FocusCard(
        title = stringResource(R.string.home_tile_device),
        icon = Icons.Outlined.Memory,
        modifier = modifier.fillMaxWidth(),
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

/** A single live metric drawn as a wavy ring with its value in the middle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCircle(
    value: String,
    label: String,
    progress: Float?,
    color: Color,
) {
    val fraction = (progress ?: 0f).coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            if (fraction > 0f) {
                CircularWavyProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = color.copy(alpha = 0.2f),
                    amplitude = { 1f },
                    wavelength = 24.dp,
                )
            } else {
                CircularWavyProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = color.copy(alpha = 0.2f),
                    amplitude = { 1f },
                    wavelength = 24.dp,
                )
            }
            Text(text = value, style = MaterialTheme.typography.titleLargeEmphasized)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The live internal storage and memory usage, plus zram and swap when the device uses them. */
@Composable
private fun FocusStorageTile(
    metrics: HomeMetrics,
    modifier: Modifier = Modifier,
) {
    val storage = metrics.storage

    FocusCard(
        title = stringResource(R.string.home_tile_storage),
        icon = Icons.Outlined.SdStorage,
        modifier = modifier.fillMaxWidth(),
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

/** A labelled usage bar with its used and total size. */
@Composable
private fun StorageBar(
    label: String,
    usedBytes: Long,
    totalBytes: Long,
    color: Color,
) {
    val context = LocalContext.current
    val progress = if (totalBytes > 0L) {
        (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = FolkType.Summary)
            Text(
                text = "${Formatter.formatFileSize(context, usedBytes)} / " +
                    Formatter.formatFileSize(context, totalBytes),
                style = FolkType.Summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

/** One `label: value` line, the shape every row of the Focus board takes. */
@Composable
private fun FocusInfoRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label: ",
            style = FolkType.Summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = FolkType.Summary.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val DEFAULT_METRIC = "—"
private const val BATTERY_TEMPERATURE_MAX_C = 50f
private const val CPU_TEMPERATURE_MAX_C = 80f

/** The LKM/GKI working mode, or an empty string while the module is not loaded. */
private fun workingModeLabel(state: HomeUiState): String = when {
    state.ksuVersion == null -> ""
    state.lkmMode == true -> "LKM"
    state.lkmMode == false -> "GKI"
    else -> ""
}
