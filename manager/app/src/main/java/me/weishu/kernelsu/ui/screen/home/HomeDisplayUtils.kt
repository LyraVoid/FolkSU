package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.CpuFrequency

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

internal const val DEFAULT_METRIC = "—"

internal const val BATTERY_TEMPERATURE_MAX_C = 50f

internal const val CPU_TEMPERATURE_MAX_C = 80f

/** A group of cores that share a clock ceiling, the way the stats board draws them. */
internal data class CpuCluster(
    val label: String,
    val averageFreqKHz: Long,
    val maxFreqKHz: Long,
) {
    val usedFraction: Float
        get() = if (maxFreqKHz > 0L) (averageFreqKHz.toFloat() / maxFreqKHz).coerceIn(0f, 1f) else 0f
}

/** Groups the per-core clocks by their ceiling, so the board shows one bar per cluster. */
internal fun cpuClusters(frequencies: List<CpuFrequency>): List<CpuCluster> =
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
internal fun formatFrequency(khz: Long): String = when {
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

/** The localised SELinux state, shared by the facts card and the layouts that show it inline. */
@Composable
internal fun selinuxDisplayName(status: String): String = when (status) {
    "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
    "Permissive" -> stringResource(R.string.selinux_status_permissive)
    "Disabled" -> stringResource(R.string.selinux_status_disabled)
    else -> stringResource(R.string.selinux_status_unknown)
}

/** The localised Seccomp state, shared by the facts card and the layouts that show it inline. */
@Composable
internal fun seccompDisplayName(status: Int): String = when (status) {
    -1 -> stringResource(R.string.seccomp_status_not_supported)
    0 -> stringResource(R.string.seccomp_status_disabled)
    1 -> stringResource(R.string.seccomp_status_strict)
    2 -> stringResource(R.string.seccomp_status_filter)
    else -> stringResource(R.string.seccomp_status_unknown)
}
