package me.weishu.kernelsu.ui.viewmodel

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.modulestore.ModuleStoreRegistry
import me.weishu.kernelsu.data.modulestore.StoreRepository
import me.weishu.kernelsu.data.modulestore.StoreSourceKind
import me.weishu.kernelsu.data.modulestore.isHttpUrl
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.screen.modulerepo.ModuleRepoUiState
import me.weishu.kernelsu.ui.util.isNetworkAvailable

/**
 * Drives the module store list from [ModuleStoreRegistry].
 *
 * The active source is resolved from the persisted selection on every load, so switching the
 * source through the UI only has to update the selection and request a reload. Search is applied
 * client-side over the already fetched list.
 */
class ModuleRepoViewModel(
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl(),
) : ViewModel() {

    companion object {
        private const val TAG = "ModuleRepoViewModel"
    }

    private val _uiState = MutableStateFlow(ModuleRepoUiState())
    val uiState: StateFlow<ModuleRepoUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    init {
        _uiState.update {
            it.copy(
                sourceKind = settingsRepo.repoSourceKind,
                customUrl = settingsRepo.repoCustomUrl,
                selectedRepositoryUrl = settingsRepo.repoSelectedRepositoryUrl,
            )
        }
    }

    /** Loads the store once, on first composition of the screen. */
    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        reload(initialLoading = true)
    }

    /** Reloads the current source while keeping the current content visible. */
    fun refresh() = reload(initialLoading = false)

    private fun reload(initialLoading: Boolean) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initialLoading,
                    isRefreshing = !initialLoading,
                    error = null,
                    offline = !isNetworkAvailable(ksuApp),
                    modules = if (initialLoading) emptyList() else it.modules,
                    searchResults = if (initialLoading) emptyList() else it.searchResults,
                )
            }
            val source = ModuleStoreRegistry.sourceFor(
                kind = state.sourceKind,
                customUrl = state.customUrl,
                repositoryUrl = state.selectedRepositoryUrl,
            )
            val result = withContext(Dispatchers.IO) { source.list(null) }
            result.onSuccess { modules ->
                _uiState.update {
                    it.copy(
                        modules = modules,
                        isLoading = false,
                        isRefreshing = false,
                        offline = !isNetworkAvailable(ksuApp),
                    )
                }
                applySearch(_uiState.value.searchStatus.searchText)
            }.onFailure { error ->
                Log.e(TAG, "list modules failed", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error,
                        offline = !isNetworkAvailable(ksuApp),
                    )
                }
                Toast.makeText(
                    ksuApp,
                    ksuApp.getString(R.string.module_repo_error),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /** Handles a selection from the source switcher. */
    fun selectSourceKind(kind: StoreSourceKind) {
        when (kind) {
            StoreSourceKind.OFFICIAL -> {
                if (_uiState.value.sourceKind == kind) return
                settingsRepo.repoSourceKind = kind
                _uiState.update { it.copy(sourceKind = kind) }
                reload(initialLoading = true)
            }

            StoreSourceKind.CLUSTER -> {
                _uiState.update { it.copy(showRepositoryPicker = true) }
                loadRepositories()
            }

            StoreSourceKind.CUSTOM -> {
                _uiState.update { it.copy(showRepositoryPicker = false, showCustomUrlDialog = true) }
            }
        }
    }

    private fun loadRepositories() {
        if (_uiState.value.repositories.isNotEmpty() || _uiState.value.repositoryPickerLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(repositoryPickerLoading = true) }
            val cluster = ModuleStoreRegistry.clusterFor(_uiState.value.selectedRepositoryUrl)
            val result = withContext(Dispatchers.IO) { cluster.listRepositories() }
            result.onSuccess { repositories ->
                _uiState.update { it.copy(repositories = repositories, repositoryPickerLoading = false) }
            }.onFailure { error ->
                Log.e(TAG, "list repositories failed", error)
                _uiState.update { it.copy(repositoryPickerLoading = false) }
            }
        }
    }

    fun selectRepository(repository: StoreRepository) {
        settingsRepo.repoSourceKind = StoreSourceKind.CLUSTER
        settingsRepo.repoSelectedRepositoryUrl = repository.url
        _uiState.update {
            it.copy(
                sourceKind = StoreSourceKind.CLUSTER,
                selectedRepositoryUrl = repository.url,
                showRepositoryPicker = false,
            )
        }
        reload(initialLoading = true)
    }

    fun confirmCustomUrl(url: String) {
        val normalized = url.trim()
        if (!normalized.isHttpUrl()) return
        settingsRepo.repoSourceKind = StoreSourceKind.CUSTOM
        settingsRepo.repoCustomUrl = normalized
        _uiState.update {
            it.copy(
                sourceKind = StoreSourceKind.CUSTOM,
                customUrl = normalized,
                showCustomUrlDialog = false,
            )
        }
        reload(initialLoading = true)
    }

    fun dismissRepositoryPicker() {
        _uiState.update { it.copy(showRepositoryPicker = false) }
    }

    fun openCustomUrlDialog() {
        _uiState.update { it.copy(showRepositoryPicker = false, showCustomUrlDialog = true) }
    }

    fun dismissCustomUrlDialog() {
        _uiState.update { it.copy(showCustomUrlDialog = false) }
    }

    fun updateSearchText(text: String) {
        _uiState.update { it.copy(searchStatus = it.searchStatus.copy(searchText = text)) }
        applySearch(text)
    }

    private fun applySearch(text: String) {
        val query = text.trim()
        val results = if (query.isEmpty()) {
            emptyList()
        } else {
            _uiState.value.modules.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
        }
        _uiState.update {
            it.copy(
                searchResults = results,
                searchStatus = it.searchStatus.copy(
                    resultStatus = when {
                        query.isEmpty() -> SearchStatus.ResultStatus.DEFAULT
                        results.isEmpty() -> SearchStatus.ResultStatus.EMPTY
                        else -> SearchStatus.ResultStatus.SHOW
                    }
                ),
            )
        }
    }
}
