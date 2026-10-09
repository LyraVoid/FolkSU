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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import me.weishu.kernelsu.R
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
        topBar = { TopBar() },
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
                InfoCard(systemInfo = state.systemInfo)
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
private fun TopBar() {
    TopAppBar(
        title = { me.weishu.kernelsu.ui.component.HomeTitleImage() },
        actions = { RebootListPopup() },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )
}
