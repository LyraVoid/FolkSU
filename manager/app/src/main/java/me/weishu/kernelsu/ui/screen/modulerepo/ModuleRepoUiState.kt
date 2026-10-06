package me.weishu.kernelsu.ui.screen.modulerepo

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.data.modulestore.StoreModule
import me.weishu.kernelsu.data.modulestore.StoreRepository
import me.weishu.kernelsu.data.modulestore.StoreSourceKind
import me.weishu.kernelsu.ui.component.SearchStatus

/**
 * The state of the module store list.
 *
 * [modules] always holds the unfiltered result of the active source; [searchResults] holds the
 * client-side search over it. [isLoading] covers the first load of a source, while [isRefreshing]
 * covers a pull-to-refresh over content that is already on screen.
 */
data class ModuleRepoUiState(
    val sourceKind: StoreSourceKind = StoreSourceKind.OFFICIAL,
    val customUrl: String = "",
    val selectedRepositoryUrl: String = "",
    val repositories: List<StoreRepository> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val repositoryPickerLoading: Boolean = false,
    val offline: Boolean = false,
    val error: Throwable? = null,
    val modules: List<StoreModule> = emptyList(),
    val searchStatus: SearchStatus = SearchStatus(""),
    val searchResults: List<StoreModule> = emptyList(),
    val showRepositoryPicker: Boolean = false,
    val showCustomUrlDialog: Boolean = false,
)

@Immutable
data class ModuleRepoActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onSearchTextChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onSelectSourceKind: (StoreSourceKind) -> Unit,
    val onSelectRepository: (StoreRepository) -> Unit,
    val onDismissRepositoryPicker: () -> Unit,
    val onOpenCustomUrlDialog: () -> Unit,
    val onDismissCustomUrlDialog: () -> Unit,
    val onConfirmCustomUrl: (String) -> Unit,
    val onOpenRepoDetail: (StoreModule) -> Unit,
    val onInstallModule: (android.net.Uri) -> Unit,
)

@Immutable
data class ModuleRepoDetailActions(
    val onBack: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onInstallModule: (android.net.Uri) -> Unit,
)
