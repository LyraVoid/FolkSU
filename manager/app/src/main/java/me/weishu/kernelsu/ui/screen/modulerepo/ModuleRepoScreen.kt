package me.weishu.kernelsu.ui.screen.modulerepo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.screen.flash.FlashIt
import me.weishu.kernelsu.ui.viewmodel.ModuleRepoViewModel

@Composable
fun ModuleRepoScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<ModuleRepoViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ensureLoaded()
    }

    val actions = ModuleRepoActions(
        onBack = { navigator.pop() },
        onRefresh = viewModel::refresh,
        onSearchTextChange = viewModel::updateSearchText,
        onClearSearch = { viewModel.updateSearchText("") },
        onSelectSourceKind = viewModel::selectSourceKind,
        onSelectRepository = viewModel::selectRepository,
        onDismissRepositoryPicker = viewModel::dismissRepositoryPicker,
        onOpenCustomUrlDialog = viewModel::openCustomUrlDialog,
        onDismissCustomUrlDialog = viewModel::dismissCustomUrlDialog,
        onConfirmCustomUrl = viewModel::confirmCustomUrl,
        onOpenRepoDetail = { module ->
            navigator.push(Route.ModuleRepoDetail(module.toArg()))
        },
        onInstallModule = { uri ->
            navigator.push(Route.Flash(FlashIt.FlashModules(listOf(uri))))
        },
    )

    ModuleRepoScreenMaterial(uiState, actions)
}

@Composable
fun ModuleRepoDetailScreen(module: StoreModuleArg) {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current

    val actions = ModuleRepoDetailActions(
        onBack = { navigator.pop() },
        onOpenUrl = uriHandler::openUri,
        onInstallModule = { uri -> navigator.push(Route.Flash(FlashIt.FlashModules(listOf(uri)))) },
    )

    ModuleRepoDetailScreenMaterial(module, actions)
}
