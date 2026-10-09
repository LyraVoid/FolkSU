package me.weishu.kernelsu.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.theme.FolkType

@Composable
internal fun HomeWarnings(
    state: HomeUiState,
    actions: HomeActions,
) {
    if (state.checkUpdateEnabled) {
        UpdateCard(state = state, actions = actions)
    }
    if (state.showManagerPrBuildWarning) {
        WarningCard(stringResource(id = R.string.home_pr_build_warning), level = WarningLevel.Notice)
    } else if (state.showKernelPrBuildWarning) {
        WarningCard(stringResource(id = R.string.home_pr_kernel_warning), level = WarningLevel.Notice)
    }
    if (state.showGkiWarning) {
        WarningCard(stringResource(id = R.string.home_gki_warning), level = WarningLevel.Notice)
    }
    if (state.requiresNewKernel) {
        WarningCard(
            stringResource(
                id = if (state.canInstallKernelUpdate) R.string.require_kernel_version else R.string.require_kernel_version_gki
            ),
            onClick = if (state.canInstallKernelUpdate) actions.onInstallClick else null
        )
    }
    if (state.requiresNewManager) {
        WarningCard(
            stringResource(
                id = R.string.require_manager_version
            )
        )
    }
    if (state.showLkmUpdate) {
        WarningCard(
            message = stringResource(R.string.home_lkm_update_available),
            level = WarningLevel.Notice,
            onClick = actions.onInstallClick,
        )
    }
    if (state.showRootRecovering) {
        WarningCard(stringResource(id = R.string.root_recovering), level = WarningLevel.Notice)
    }
    if (state.showRootWarning) {
        WarningCard(
            message = stringResource(id = R.string.grant_root_failed),
            onClick = actions.onRetryRoot,
        )
    }
}

@Composable
private fun UpdateCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val newVersion = state.latestVersionInfo
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = state.hasUpdate,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { actions.onOpenUrl(newVersion.downloadUrl) })
        WarningCard(
            message = stringResource(id = R.string.new_version_available, newVersion.versionCode),
            level = WarningLevel.Notice
        ) {
            if (newVersion.changelog.isEmpty()) {
                actions.onOpenUrl(newVersion.downloadUrl)
            } else {
                updateDialog.showConfirm(
                    title = title,
                    content = newVersion.changelog,
                    markdown = true,
                    confirm = updateText
                )
            }
        }
    }
}

@Composable
private fun WarningCard(
    message: String,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null
) {
    val containerColor = when (level) {
        WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
        WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                style = FolkType.Summary,
                color = MaterialTheme.colorScheme.contentColorFor(containerColor)
            )
        }
    }
    if (onClick != null) {
        HomeCard(containerColor = containerColor, onClick = onClick, content = content)
    } else {
        HomeCard(containerColor = containerColor, content = content)
    }
}
