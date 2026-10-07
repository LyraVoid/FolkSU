package me.weishu.kernelsu.ui.screen.home

import android.text.format.Formatter
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Speed
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
import me.weishu.kernelsu.data.CpuFrequency
import me.weishu.kernelsu.data.HomeMetrics
import me.weishu.kernelsu.ui.component.chart.ModulePieChart
import me.weishu.kernelsu.ui.component.chart.PieSlice
import me.weishu.kernelsu.ui.component.chart.WaveChart
import me.weishu.kernelsu.ui.component.material.FolkButton
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import java.util.Locale
import kotlin.math.roundToInt

/** The gap between the tiles of every multi-column home layout. */
internal val TileSpacing = 16.dp

/**
 * The width from which a home layout lays its tiles out in columns instead of one long stack.
 *
 * The orientation clause comes from the layout this one is modelled on: a tall screen gains nothing
 * from two narrow columns, so the board only splits when there is more width than height. It reads
 * the window size rather than `BoxWithConstraints`, because these layouts live inside a scrolling
 * column where the available height is unbounded.
 */
@Composable
internal fun isWideLayout(withOrientation: Boolean): Boolean {
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
            FocusFactsTile(
                state = state,
                title = stringResource(R.string.home_tile_manager),
                icon = Icons.Outlined.AdminPanelSettings,
            )
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
            FocusFactsTile(
                state = state,
                title = stringResource(R.string.home_tile_manager),
                icon = Icons.Outlined.AdminPanelSettings,
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
    metrics: HomeMetrics,
) {
    if (isWideLayout(withOrientation = false)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                StatusCard(state = state, actions = actions)
                StatsMonitorTile(metrics = metrics)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                StatsModuleTile(
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                )
                FocusFactsTile(
                    state = state,
                    title = stringResource(R.string.home_tile_system),
                    icon = Icons.Outlined.Info,
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        StatusCard(state = state, actions = actions)
        StatsMonitorTile(metrics = metrics)
        StatsModuleTile(
            superuserCount = superuserCount,
            moduleEnabledCount = moduleEnabledCount,
        )
        FocusFactsTile(
            state = state,
            title = stringResource(R.string.home_tile_system),
            icon = Icons.Outlined.Info,
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

/** The manager build and the policy it runs under, or the system facts the stats board closes with. */
@Composable
private fun FocusFactsTile(
    state: HomeUiState,
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    FocusCard(
        title = title,
        icon = icon,
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

/** The live metrics the stats board is built around: CPU, battery and memory. */
@Composable
private fun StatsMonitorTile(
    metrics: HomeMetrics,
    modifier: Modifier = Modifier,
) {
    val device = metrics.device
    val storage = metrics.storage
    val history = metrics.history
    val cpuTemperature = device?.cpuTemperatureC
    val batteryLevel = device?.batteryLevelPercent
    val clusters = cpuClusters(device?.cpuFrequencies.orEmpty())

    FocusCard(
        title = stringResource(R.string.home_tile_monitor),
        icon = Icons.Outlined.Speed,
        modifier = modifier.fillMaxWidth(),
    ) {
        WaveChart(
            label = stringResource(R.string.home_metric_cpu_temperature),
            value = cpuTemperature?.let { "${it.roundToInt()}°C" } ?: DEFAULT_METRIC,
            samples = history.cpuTemperature,
            color = MaterialTheme.colorScheme.secondary,
        )
        WaveChart(
            label = stringResource(R.string.home_metric_storage_ram),
            value = storage?.ramUsedFraction?.let { "${(it * 100f).roundToInt()}%" } ?: DEFAULT_METRIC,
            samples = history.memoryUsage,
            color = MaterialTheme.colorScheme.primary,
        )
        if (clusters.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_metric_cpu_frequency),
                style = FolkType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            clusters.forEach { cluster ->
                MetricBar(
                    label = cluster.label,
                    value = formatFrequency(cluster.averageFreqKHz),
                    progress = cluster.usedFraction,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        MetricBar(
            label = stringResource(R.string.home_metric_battery_level),
            value = batteryLevel?.let { "$it%" } ?: DEFAULT_METRIC,
            progress = batteryLevel?.let { it / 100f } ?: 0f,
            color = when {
                batteryLevel == null -> MaterialTheme.colorScheme.primary
                batteryLevel <= 20 -> MaterialTheme.colorScheme.error
                batteryLevel <= 50 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
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

/** The counts of the stats board as a donut: modules against superusers. */
@Composable
private fun StatsModuleTile(
    superuserCount: Int,
    moduleEnabledCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val moduleLabel = stringResource(R.string.module)
    val superuserLabel = stringResource(R.string.superuser)
    val slices = listOf(
        PieSlice(label = moduleLabel, value = moduleEnabledCount, color = colors.primary),
        PieSlice(label = superuserLabel, value = superuserCount, color = colors.tertiary),
    )

    FocusCard(
        title = stringResource(R.string.home_tile_stats),
        icon = Icons.Outlined.PieChart,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModulePieChart(
                slices = slices,
                centerLabel = (superuserCount + moduleEnabledCount).toString(),
                modifier = Modifier.size(140.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChartLegend(label = moduleLabel, count = moduleEnabledCount, color = colors.primary)
                ChartLegend(label = superuserLabel, count = superuserCount, color = colors.tertiary)
            }
        }
    }
}

/** One donut legend line: the slice colour, its name and its count. */
@Composable
private fun ChartLegend(
    label: String,
    count: Int,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = FolkType.Summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            style = FolkType.Summary.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
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

    MetricBar(
        label = label,
        value = "${Formatter.formatFileSize(context, usedBytes)} / " +
            Formatter.formatFileSize(context, totalBytes),
        progress = progress,
        color = color,
    )
}

/** One labelled usage bar; every row of the stats board shares this shape. */
@Composable
private fun MetricBar(
    label: String,
    value: String,
    progress: Float,
    color: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = FolkType.Summary)
            Text(
                text = value,
                style = FolkType.Summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
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

/** A group of cores that share a clock ceiling, the way the stats board draws them. */
private data class CpuCluster(
    val label: String,
    val averageFreqKHz: Long,
    val maxFreqKHz: Long,
) {
    val usedFraction: Float
        get() = if (maxFreqKHz > 0L) (averageFreqKHz.toFloat() / maxFreqKHz).coerceIn(0f, 1f) else 0f
}

/** Groups the per-core clocks by their ceiling, so the board shows one bar per cluster. */
private fun cpuClusters(frequencies: List<CpuFrequency>): List<CpuCluster> =
    frequencies
        .groupBy { it.maxFreqKHz }
        .values
        .map { cores ->
            val ordered = cores.sortedBy { it.coreIndex }
            val first = ordered.first()
            val last = ordered.last()
            CpuCluster(
                label = if (first == last) "CPU${first.coreIndex}" else "CPU${first.coreIndex}-${last.coreIndex}",
                averageFreqKHz = ordered.sumOf { it.currentFreqKHz } / ordered.size,
                maxFreqKHz = first.maxFreqKHz,
            )
        }
        .sortedBy { it.label }

/** A clock in the unit that reads best: GHz first, then MHz, then kHz. */
private fun formatFrequency(khz: Long): String = when {
    khz >= 1_000_000L -> String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)
    khz >= 1_000L -> String.format(Locale.US, "%.0f MHz", khz / 1_000.0)
    else -> String.format(Locale.US, "%d kHz", khz)
}

/** The LKM/GKI working mode, or an empty string while the module is not loaded. */
internal fun workingModeLabel(state: HomeUiState): String = when {
    state.ksuVersion == null -> ""
    state.lkmMode == true -> "LKM"
    state.lkmMode == false -> "GKI"
    else -> ""
}
