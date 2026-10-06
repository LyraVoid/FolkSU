package me.weishu.kernelsu.ui.screen.modulerepo

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.modulestore.StoreModule
import me.weishu.kernelsu.data.modulestore.StoreRepository
import me.weishu.kernelsu.data.modulestore.StoreSourceKind
import me.weishu.kernelsu.ui.component.dialog.ConfirmDialogHandle
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.markdown.GithubMarkdown
import me.weishu.kernelsu.ui.component.material.ExpressiveDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveTabRow
import me.weishu.kernelsu.ui.component.material.FolkButton
import me.weishu.kernelsu.ui.component.material.FolkFilledTonalButton
import me.weishu.kernelsu.ui.component.material.FolkFilledTonalIconButton
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.FolkTextButton
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedTextField
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.theme.FolkShape
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.ui.util.PagerNavigationSpringSpec
import me.weishu.kernelsu.ui.util.download
import me.weishu.kernelsu.ui.util.isDownloadAvailable

@Composable
fun ModuleRepoScreenMaterial(
    state: ModuleRepoUiState,
    actions: ModuleRepoActions,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyGridState()
    val searchListState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = { pendingDownload?.invoke() })
    val setPendingDownload: ((() -> Unit)) -> Unit = { pendingDownload = it }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(text = stringResource(R.string.module_repos)) },
                searchText = state.searchStatus.searchText,
                onSearchTextChange = actions.onSearchTextChange,
                onClearClick = actions.onClearSearch,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    var showSourceMenu by remember { mutableStateOf(false) }
                    FolkIconButton(onClick = { showSourceMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = stringResource(R.string.module_repo_source),
                        )
                    }
                    DropdownMenuPopup(
                        expanded = showSourceMenu,
                        onDismissRequest = { showSourceMenu = false },
                    ) {
                        val options = listOf(
                            StoreSourceKind.OFFICIAL to R.string.module_repo_source_official,
                            StoreSourceKind.CLUSTER to R.string.module_repo_source_cluster,
                            StoreSourceKind.CUSTOM to R.string.module_repo_source_custom,
                        )
                        DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                            options.forEachIndexed { index, (kind, labelRes) ->
                                SelectableDropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    selected = state.sourceKind == kind,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        actions.onSelectSourceKind(kind)
                                        showSourceMenu = false
                                    },
                                    shapes = MenuDefaults.itemShape(index = index, count = options.size),
                                    selectedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                searchContent = { bottomPadding, closeSearch ->
                    if (state.searchResults.isEmpty()) {
                        RepoMessageState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.module_repo_no_matches),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        StoreModuleGrid(
                            modules = state.searchResults,
                            listState = searchListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 16.dp + bottomPadding,
                            ),
                            onOpenModule = {
                                closeSearch()
                                actions.onOpenRepoDetail(it)
                            },
                            confirmDialog = confirmDialog,
                            scope = scope,
                            context = context,
                            onInstallModule = { uri -> actions.onInstallModule(uri) },
                            setPendingDownload = setPendingDownload,
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            state.isLoading -> Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            state.error != null -> RepoMessageState(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(
                    if (state.offline) R.string.network_offline else R.string.module_repo_error
                ),
                modifier = contentModifier,
                action = {
                    FolkButton(onClick = actions.onRefresh) {
                        Text(stringResource(R.string.network_retry))
                    }
                },
            )

            state.modules.isEmpty() -> RepoMessageState(
                icon = Icons.Outlined.Extension,
                title = stringResource(R.string.module_repo_empty),
                modifier = contentModifier,
            )

            else -> PullToRefreshBox(
                modifier = contentModifier,
                isRefreshing = state.isRefreshing,
                onRefresh = actions.onRefresh,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = state.isRefreshing,
                        state = pullToRefreshState,
                    )
                },
            ) {
                StoreModuleGrid(
                    modules = state.modules,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    onOpenModule = actions.onOpenRepoDetail,
                    confirmDialog = confirmDialog,
                    scope = scope,
                    context = context,
                    onInstallModule = { uri -> actions.onInstallModule(uri) },
                    setPendingDownload = setPendingDownload,
                )
            }
        }

        if (state.showRepositoryPicker) {
            RepositoryPickerDialog(
                repositories = state.repositories,
                loading = state.repositoryPickerLoading,
                onSelect = actions.onSelectRepository,
                onManualInput = actions.onOpenCustomUrlDialog,
                onDismiss = actions.onDismissRepositoryPicker,
            )
        }

        if (state.showCustomUrlDialog) {
            CustomUrlDialog(
                initialUrl = state.customUrl,
                onConfirm = actions.onConfirmCustomUrl,
                onDismiss = actions.onDismissCustomUrlDialog,
            )
        }
    }
}

@Composable
private fun StoreModuleGrid(
    modules: List<StoreModule>,
    listState: LazyGridState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenModule: (StoreModule) -> Unit,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    context: Context,
    onInstallModule: (Uri) -> Unit,
    setPendingDownload: ((() -> Unit)) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(modules, key = { it.id }, contentType = { "module" }) { module ->
            StoreModuleCard(
                module = module,
                onOpen = onOpenModule,
                confirmDialog = confirmDialog,
                scope = scope,
                context = context,
                onInstallModule = onInstallModule,
                setPendingDownload = setPendingDownload,
            )
        }
    }
}

@Composable
private fun StoreModuleCard(
    module: StoreModule,
    onOpen: (StoreModule) -> Unit,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    context: Context,
    onInstallModule: (Uri) -> Unit,
    setPendingDownload: ((() -> Unit)) -> Unit,
) {
    val release = module.latestRelease
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = FolkShape.Corner20,
        onClick = { onOpen(module) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        style = FolkType.Title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (module.author.isNotBlank()) {
                        Text(
                            text = module.author,
                            style = FolkType.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (release != null) {
                    Spacer(Modifier.width(8.dp))
                    ModuleDownloadButton(
                        downloadUrl = release.downloadUrl,
                        fileName = "${module.name}-${release.version}.zip",
                        confirmDialog = confirmDialog,
                        scope = scope,
                        context = context,
                        onInstallModule = onInstallModule,
                        setPendingDownload = setPendingDownload,
                        iconOnly = true,
                    )
                }
            }
            if (module.version.isNotBlank() || release != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (module.version.isNotBlank()) {
                        StatusTag(
                            label = module.version,
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val date = formatReleaseDate(release?.timestamp ?: 0.0)
                    if (date.isNotBlank()) {
                        Text(
                            text = date,
                            style = FolkType.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (module.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = module.description,
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ModuleDownloadButton(
    downloadUrl: String,
    fileName: String,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    context: Context,
    onInstallModule: (Uri) -> Unit,
    setPendingDownload: ((() -> Unit)) -> Unit,
    iconOnly: Boolean,
) {
    var isDownloading by remember(downloadUrl) { mutableStateOf(false) }
    var progress by remember(downloadUrl) { mutableIntStateOf(0) }
    var downloadedUri by remember(downloadUrl) { mutableStateOf<Uri?>(null) }

    val onDownloadClick: () -> Unit = {
        val startText = context.getString(R.string.module_start_downloading, fileName)
        setPendingDownload {
            isDownloading = true
            scope.launch(Dispatchers.IO) {
                download(
                    url = downloadUrl,
                    fileName = fileName,
                    onDownloaded = { uri ->
                        isDownloading = false
                        downloadedUri = uri
                    },
                    onDownloading = { isDownloading = true },
                    onProgress = { value -> scope.launch(Dispatchers.Main) { progress = value } },
                )
                isDownloading = false
            }
        }
        confirmDialog.showConfirm(
            title = context.getString(R.string.module_install),
            content = startText,
        )
    }

    val onInstallClick: () -> Unit = {
        val uri = downloadedUri
        if (uri != null) {
            scope.launch {
                if (isDownloadAvailable(uri)) {
                    onInstallModule(uri)
                } else {
                    downloadedUri = null
                }
            }
        }
    }

    val isDownloaded = downloadedUri != null
    val onClick = if (isDownloaded) onInstallClick else onDownloadClick

    if (iconOnly) {
        FolkFilledTonalIconButton(onClick = onClick, enabled = !isDownloading) {
            when {
                isDownloading -> CircularWavyProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(20.dp),
                )

                isDownloaded -> Icon(
                    imageVector = Icons.Outlined.InstallMobile,
                    contentDescription = stringResource(R.string.install),
                    modifier = Modifier.size(20.dp),
                )

                else -> Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = stringResource(R.string.download),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    } else {
        FolkFilledTonalButton(
            onClick = onClick,
            enabled = !isDownloading,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            when {
                isDownloading -> CircularWavyProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(20.dp),
                )

                isDownloaded -> {
                    Icon(
                        imageVector = Icons.Outlined.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.install),
                        modifier = Modifier.padding(start = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                else -> {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.download),
                        modifier = Modifier.padding(start = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepoMessageState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = FolkType.Summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
private fun RepositoryPickerDialog(
    repositories: List<StoreRepository>,
    loading: Boolean,
    onSelect: (StoreRepository) -> Unit,
    onManualInput: () -> Unit,
    onDismiss: () -> Unit,
) {
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        shape = FolkShape.Dialog,
        title = { Text(stringResource(R.string.module_repo_pick_repository)) },
        text = {
            when {
                loading && repositories.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }

                repositories.isEmpty() -> Text(
                    text = stringResource(R.string.network_offline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SegmentedColumn(
                        content = repositories.map { repository ->
                            {
                                SegmentedListItem(
                                    headlineContent = { Text(repository.name, style = FolkType.Title) },
                                    supportingContent = {
                                        Column {
                                            Text(
                                                text = stringResource(
                                                    R.string.module_repo_modules_count,
                                                    repository.modulesCount,
                                                ),
                                                style = FolkType.Caption,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (repository.description.isNotBlank()) {
                                                Text(
                                                    text = repository.description,
                                                    style = FolkType.Caption,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onSelect(repository) },
                                )
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            FolkTextButton(onClick = onManualInput) {
                Text(stringResource(R.string.module_repo_manual_url))
            }
        },
        dismissButton = {
            FolkTextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun CustomUrlDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    val normalized = url.trim()
    val isValid = normalized.startsWith("http://", ignoreCase = true) ||
        normalized.startsWith("https://", ignoreCase = true)

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        shape = FolkShape.Dialog,
        title = { Text(stringResource(R.string.module_repo_custom_url)) },
        text = {
            SegmentedColumn(
                content = listOf({
                    SegmentedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text(stringResource(R.string.module_repo_custom_url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }),
            )
        },
        confirmButton = {
            FolkTextButton(
                onClick = { if (isValid) onConfirm(normalized) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            FolkTextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
fun ModuleRepoDetailScreenMaterial(
    module: StoreModuleArg,
    actions: ModuleRepoDetailActions,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
    val confirmDialog = rememberConfirmDialog(onConfirm = { pendingDownload?.invoke() })
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = module.name) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val tabs = listOf(
            stringResource(R.string.tab_readme),
            stringResource(R.string.tab_releases),
            stringResource(R.string.tab_info),
        )
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
        val layoutDirection = LocalLayoutDirection.current
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                overscrollEffect = null,
            ) { page ->
                val paddedInnerPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 56.dp + 8.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding() +
                        WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                )
                when (page) {
                    0 -> ReadmeTab(
                        description = module.description,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                    )

                    1 -> ReleasesTab(
                        module = module,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                        confirmDialog = confirmDialog,
                        scope = scope,
                        context = context,
                        onInstallModule = actions.onInstallModule,
                        setPendingDownload = { pendingDownload = it },
                    )

                    2 -> InfoTab(
                        module = module,
                        innerPadding = paddedInnerPadding,
                        scrollBehavior = scrollBehavior,
                        onOpenUrl = actions.onOpenUrl,
                    )
                }
            }
            ExpressiveTabRow(
                selectedTabIndex = pagerState.currentPage,
                tabs = tabs,
                onTabClick = { scope.launch {
                    pagerState.animateScrollToPage(
                        page = it,
                        animationSpec = PagerNavigationSpringSpec,
                    )
                } },
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            )
        }
    }
}

@Composable
private fun ReadmeTab(
    description: String,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding(),
        ),
    ) {
        item {
            if (description.isBlank()) {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.module_repo_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                GithubMarkdown(
                    content = description,
                    isMarkdown = true,
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}

@Composable
private fun ReleasesTab(
    module: StoreModuleArg,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    confirmDialog: ConfirmDialogHandle,
    scope: CoroutineScope,
    context: Context,
    onInstallModule: (Uri) -> Unit,
    setPendingDownload: ((() -> Unit)) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val releases = remember(module.releases) { module.releases.sortedByDescending { it.versionCode } }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = innerPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        if (releases.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.module_repo_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(releases) { release ->
                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    content = listOf({
                        SegmentedListItem(
                            headlineContent = {
                                Text(
                                    text = release.version.ifBlank { module.version },
                                    style = FolkType.Title,
                                )
                            },
                            supportingContent = formatReleaseDate(release.timestamp)
                                .takeIf { it.isNotBlank() }
                                ?.let { date ->
                                    {
                                        Text(
                                            text = date,
                                            style = FolkType.Caption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            trailingContent = {
                                ModuleDownloadButton(
                                    downloadUrl = release.downloadUrl,
                                    fileName = "${module.name}-${release.version}.zip",
                                    confirmDialog = confirmDialog,
                                    scope = scope,
                                    context = context,
                                    onInstallModule = onInstallModule,
                                    setPendingDownload = setPendingDownload,
                                    iconOnly = false,
                                )
                            },
                        )
                    }),
                )
            }
        }
    }
}

private data class InfoRowData(
    val label: String,
    val value: String,
    val linkable: Boolean,
)

@Composable
private fun InfoTab(
    module: StoreModuleArg,
    innerPadding: PaddingValues,
    scrollBehavior: TopAppBarScrollBehavior,
    onOpenUrl: (String) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val authorLabel = stringResource(R.string.module_author)
    val licenseLabel = stringResource(R.string.module_repo_license)
    val homepageLabel = stringResource(R.string.module_repo_homepage)
    val sourceLabel = stringResource(R.string.module_repos_source_code)
    val supportLabel = stringResource(R.string.module_repo_support)

    val license = module.license.orEmpty().takeIf { it.isNotBlank() }
    val homepage = module.homepage.orEmpty().takeIf { it.isNotBlank() }
    val source = module.source.orEmpty().takeIf { it.isNotBlank() }
    val support = module.support.orEmpty().takeIf { it.isNotBlank() }

    val rows = buildList {
        if (module.author.isNotBlank()) {
            add(InfoRowData(authorLabel, module.author, linkable = false))
        }
        license?.let { add(InfoRowData(licenseLabel, it, linkable = false)) }
        homepage?.let { add(InfoRowData(homepageLabel, it, linkable = true)) }
        source?.let { add(InfoRowData(sourceLabel, it, linkable = true)) }
        support?.let { add(InfoRowData(supportLabel, it, linkable = true)) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = innerPadding.calculateBottomPadding(),
        ),
    ) {
        if (rows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.module_repo_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item {
                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    content = rows.map { row ->
                        { InfoRow(row = row, onOpenUrl = onOpenUrl) }
                    },
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    row: InfoRowData,
    onOpenUrl: (String) -> Unit,
) {
    SegmentedListItem(
        headlineContent = {
            Text(
                text = row.value,
                style = FolkType.Summary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = row.label,
                style = FolkType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (row.linkable) {
            {
                FolkFilledTonalIconButton(
                    onClick = { onOpenUrl(row.value) },
                    content = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = stringResource(R.string.module_repo_open),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        } else {
            null
        },
    )
}

private fun formatReleaseDate(timestamp: Double): String {
    if (timestamp <= 0.0) return ""
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date((timestamp * 1000).toLong()))
    }.getOrDefault("")
}
