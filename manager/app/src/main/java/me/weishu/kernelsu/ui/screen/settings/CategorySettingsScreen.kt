package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.settings.SettingsCategory
import me.weishu.kernelsu.ui.component.settings.SettingsContentMaxWidth
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.SettingsViewModel

/**
 * A pushed screen for one settings category, opened from the hub's category grid. Shares the
 * secondary-screen scaffold used by Font/About and the same [SettingsViewModel] actions builder.
 */
@Composable
fun CategorySettingsScreen(categoryKey: String) {
    val navigator = LocalNavigator.current
    val category = SettingsCategory.fromKey(categoryKey)
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberSettingsActions(navigator, viewModel)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(category?.titleRes ?: R.string.settings)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = SettingsContentMaxWidth)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            when (category) {
                SettingsCategory.GENERAL -> GeneralCategoryContent(uiState, actions)
                SettingsCategory.APPEARANCE -> AppearanceCategoryContent(uiState, actions)
                SettingsCategory.BEHAVIOR -> BehaviorCategoryContent(uiState, actions)
                SettingsCategory.FUNCTION -> FunctionCategoryContent(uiState, actions)
                SettingsCategory.SECURITY -> SecurityCategoryContent(uiState, actions)
                null -> Unit
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
