package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import me.weishu.kernelsu.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.media.MusicConfig
import me.weishu.kernelsu.media.MusicManager
import me.weishu.kernelsu.data.HomeMetrics
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.rebootlistpopup.RebootListPopup


@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
    superuserCount: Int = 0,
    moduleEnabledCount: Int = 0,
    metrics: HomeMetrics = HomeMetrics(),
) {
    ExpressiveScaffold(
        topBar = { TopBar(state = state, actions = actions) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeWarnings(state = state, actions = actions)
            val layout = LocalHomeLayoutStyle.current
            when (layout) {
                HomeLayoutStyle.GRID -> GridHomeContent(
                    state = state,
                    actions = actions,
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                )

                HomeLayoutStyle.FOCUS -> FocusHomeContent(
                    state = state,
                    actions = actions,
                    metrics = metrics,
                )

                HomeLayoutStyle.DASHBOARD -> DashboardHomeContent(
                    state = state,
                    actions = actions,
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                )

                HomeLayoutStyle.STATS -> StatsHomeContent(
                    state = state,
                    actions = actions,
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                    metrics = metrics,
                )

                else -> CircleHomeContent(
                    state = state,
                    actions = actions,
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                )
            }
            // The tile layouts place the facts card inside their own board.
            if (layout == HomeLayoutStyle.CIRCLE || layout == HomeLayoutStyle.GRID) {
                InfoCard(systemInfo = state.systemInfo, susfs = state.susfs, onSusfs = actions.onSusfs)
            }
            SupportLinks(onOpenUrl = actions.onOpenUrl)
            Spacer(
                Modifier.height(
                    bottomInnerPadding + if (!useFullFeaturedLayout())
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
                )
            )
        }
    }
}

@Composable
private fun TopBar(state: HomeUiState, actions: HomeActions) {
    TopAppBar(
        title = { me.weishu.kernelsu.ui.component.HomeTitleImage() },
        actions = {
            if (state.susfs.detected) {
                IconButton(onClick = actions.onSusfs) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.VisibilityOff, stringResource(R.string.susfs_title))
                }
            }
            if (MusicConfig.isMusicEnabled && MusicConfig.getMusicFile(LocalContext.current) != null) {
                val playing by MusicManager.isPlaying.collectAsStateWithLifecycle()
                IconButton(onClick = { MusicManager.toggle() }) {
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.media_pause else R.string.media_play),
                    )
                }
            }
            IconButton(onClick = actions.onInstallClick, enabled = !state.isLateLoadMode) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = stringResource(R.string.install),
                )
            }
            RebootListPopup()
        },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )
}
