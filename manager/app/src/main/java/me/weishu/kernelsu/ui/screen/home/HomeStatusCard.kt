package me.weishu.kernelsu.ui.screen.home

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.FolkButton

/**
 * The module states every layout reports, as `label to value` pairs: how the module is loaded and the
 * states that change how it behaves, in a fixed order. The board decides how to draw them. Empty when
 * the module is not loaded, since then it has nothing to report.
 */
@Composable
internal fun statusStateTexts(state: HomeUiState): List<Pair<String, String>> {
    if (state.ksuVersion == null) return emptyList()
    return buildList {
        val workingMode = workingModeLabel(state)
        if (workingMode.isNotEmpty()) {
            add(stringResource(R.string.home_running_mode) to workingMode)
        }
        if (state.isSafeMode) {
            add(stringResource(R.string.safe_mode) to stringResource(R.string.home_state_on))
        }
        if (state.isLateLoadMode) {
            add(stringResource(R.string.jailbreak_mode) to stringResource(R.string.home_state_on))
        }
        if (state.showCustomLkmBadge) {
            add(stringResource(R.string.home_lkm_custom) to stringResource(R.string.home_state_on))
        }
    }
}

/** The install action for a kernel that has the module but never loaded it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatusInstallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FolkButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = ButtonDefaults.MediumContentPadding,
    ) {
        Text(stringResource(R.string.install))
    }
}

/** The jailbreak action, offered while the module is installed but the kernel refuses to load it. */
@Composable
internal fun StatusJailbreakButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FolkButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(stringResource(R.string.home_jailbreak))
    }
}
