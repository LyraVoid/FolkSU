package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig

/** The manager build and the policy it runs under, or the system facts the stats board closes with. */
@Composable
internal fun HomeFactsTile(
    state: HomeUiState,
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: SurfaceConfig? = null,
) {
    HomeTileCard(
        title = title,
        icon = icon,
        modifier = modifier.fillMaxWidth(),
        background = background,
    ) {
        HomeFactRow(
            label = stringResource(R.string.home_manager_version),
            value = state.systemInfo.managerVersion,
        )
        HomeFactRow(
            label = stringResource(R.string.home_kernel),
            value = state.systemInfo.kernelVersion,
        )
        HomeFactRow(
            label = stringResource(R.string.home_device_model),
            value = state.systemInfo.deviceModel,
        )
        HomeFactRow(
            label = stringResource(R.string.home_fingerprint),
            value = state.systemInfo.fingerprint,
        )
        HomeFactRow(
            label = stringResource(R.string.home_selinux_status),
            value = selinuxDisplayName(state.systemInfo.selinuxStatus),
        )
        HomeFactRow(
            label = stringResource(R.string.home_seccomp_status),
            value = seccompDisplayName(state.systemInfo.seccompStatus),
        )
    }
}

/** One `label: value` line, the shape every row of the Focus board takes. */
@Composable
internal fun HomeFactRow(
    label: String,
    value: String,
) {
    val overImage = LocalHomeTileCardOverImage.current
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label: ",
            style = FolkType.Summary,
            color = if (overImage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = FolkType.Summary.copy(fontWeight = FontWeight.Medium),
            color = if (overImage) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun InfoCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {
    val selinuxDisplay = selinuxDisplayName(systemInfo.selinuxStatus)
    val seccompDisplay = seccompDisplayName(systemInfo.seccompStatus)

    HomeCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InfoRow(
                icon = Icons.Filled.Tag,
                label = stringResource(R.string.home_manager_version),
                value = systemInfo.managerVersion,
            )
            InfoRow(
                icon = Icons.Filled.DeveloperBoard,
                label = stringResource(R.string.home_kernel),
                value = systemInfo.kernelVersion,
            )
            InfoRow(
                icon = Icons.Filled.Smartphone,
                label = stringResource(R.string.home_device_model),
                value = systemInfo.deviceModel,
            )
            InfoRow(
                icon = Icons.Filled.Fingerprint,
                label = stringResource(R.string.home_fingerprint),
                value = systemInfo.fingerprint,
            )
            InfoRow(
                icon = Icons.Filled.Security,
                label = stringResource(R.string.home_selinux_status),
                value = selinuxDisplay,
            )
            InfoRow(
                icon = Icons.Filled.FilterList,
                label = stringResource(R.string.home_seccomp_status),
                value = seccompDisplay,
            )
        }
    }
}

@Composable
internal fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = label, style = FolkType.Summary)
            Text(
                text = value,
                style = FolkType.Machine,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
