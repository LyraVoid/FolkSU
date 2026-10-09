package me.weishu.kernelsu.ui.screen.themestore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.themestore.LocalTheme
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.ui.viewmodel.ThemeStoreViewModel

/**
 * The local theme library: everything already downloaded into app storage, each entry applyable
 * with a tap and removable with a long press. Applying goes through
 * [ThemeStoreViewModel.applyTheme], i.e. the same `.fpt` importer the rest of the app uses.
 */
@Composable
fun MyThemesScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<ThemeStoreViewModel>()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val gridState = rememberLazyStaggeredGridState()

    val appliedMessage = stringResource(R.string.theme_store_applied)
    val applyFailedMessage = stringResource(R.string.theme_store_apply_failed)

    var selectedTheme by remember { mutableStateOf<LocalTheme?>(null) }
    var deleteTheme by remember { mutableStateOf<LocalTheme?>(null) }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(stringResource(R.string.my_themes_title)) },
                searchText = viewModel.localSearchQuery,
                onSearchTextChange = viewModel::onLocalSearchQueryChange,
                onClearClick = { viewModel.onLocalSearchQueryChange("") },
                scrollBehavior = scrollBehavior,
                topSearch = true,
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                searchContent = { bottomPadding, _ ->
                    MyThemeResults(
                        themes = viewModel.filteredLocalThemes,
                        contentPadding = searchContentPadding(bottomPadding),
                        onSelect = { selectedTheme = it },
                        onDelete = { deleteTheme = it },
                    )
                },
                defaultContent = { bottomPadding, _ ->
                    MyThemeResults(
                        themes = viewModel.filteredLocalThemes,
                        contentPadding = searchContentPadding(bottomPadding),
                        onSelect = { selectedTheme = it },
                        onDelete = { deleteTheme = it },
                    )
                },
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (viewModel.filteredLocalThemes.isEmpty()) {
                EmptyThemes(
                    message = stringResource(
                        if (viewModel.localThemes.isEmpty()) R.string.my_themes_empty
                        else R.string.theme_store_empty,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PullToRefreshBox(
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = viewModel.isRefreshingLocal,
                    onRefresh = { viewModel.loadLocalThemes() },
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            isRefreshing = viewModel.isRefreshingLocal,
                            state = pullToRefreshState,
                        )
                    },
                ) {
                    MyThemeResults(
                        themes = viewModel.filteredLocalThemes,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        gridState = gridState,
                        onSelect = { selectedTheme = it },
                        onDelete = { deleteTheme = it },
                    )
                }
            }
        }
    }

    val detail = selectedTheme
    if (detail != null) {
        AlertDialog(
            onDismissRequest = { selectedTheme = null },
            title = { Text(detail.name) },
            text = {
                Column {
                    Text(stringResource(R.string.theme_store_author, detail.author), style = FolkType.Summary)
                    if (detail.version.isNotBlank()) {
                        Text(stringResource(R.string.theme_store_version, detail.version), style = FolkType.Caption)
                    }
                    if (detail.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(detail.description, style = FolkType.Summary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val ok = viewModel.applyTheme(detail)
                        snackbarHostState.showSnackbar(if (ok) appliedMessage else applyFailedMessage)
                    }
                    selectedTheme = null
                }) {
                    Text(stringResource(R.string.theme_store_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTheme = null }) {
                    Text(stringResource(R.string.theme_store_cancel))
                }
            },
        )
    }

    val toDelete = deleteTheme
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTheme = null },
            title = { Text(stringResource(R.string.my_themes_delete_title)) },
            text = { Text(stringResource(R.string.my_themes_delete_message, toDelete.name)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteTheme(toDelete)
                    deleteTheme = null
                }) {
                    Text(stringResource(R.string.my_themes_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTheme = null }) {
                    Text(stringResource(R.string.theme_store_cancel))
                }
            },
        )
    }
}

@Composable
private fun MyThemeResults(
    themes: List<LocalTheme>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onSelect: (LocalTheme) -> Unit,
    onDelete: (LocalTheme) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(128.dp),
        modifier = modifier.fillMaxSize(),
        state = gridState,
        contentPadding = contentPadding,
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(themes, key = { it.id }) { theme ->
            ThemePreviewCard(
                name = theme.name,
                model = File(theme.previewImagePath).takeIf { it.exists() }
                    ?: theme.previewUrl.takeIf { it.isNotBlank() },
                onClick = { onSelect(theme) },
                onLongClick = { onDelete(theme) },
            )
        }
    }
}
