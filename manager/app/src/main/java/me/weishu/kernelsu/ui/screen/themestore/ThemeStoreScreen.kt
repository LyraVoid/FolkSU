package me.weishu.kernelsu.ui.screen.themestore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.io.File
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.themestore.DownloadProgress
import me.weishu.kernelsu.data.themestore.DownloadStatus
import me.weishu.kernelsu.data.themestore.LocalTheme
import me.weishu.kernelsu.data.themestore.RemoteTheme
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.theme.FolkShape
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.ui.viewmodel.ThemeStoreViewModel

/**
 * The online theme store: a staggered grid of remote `.fpt` themes served by the project store, with
 * a top search bar, author/source/type filters, a download flow and a jump to the local themes.
 *
 * Downloaded themes are applied through [ThemeStoreViewModel.applyTheme], which reuses the app's
 * existing `.fpt` importer, so anything shown here is already in FolkSU's own theme format.
 */
@Composable
fun ThemeStoreScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<ThemeStoreViewModel>()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val gridState = rememberLazyStaggeredGridState()

    val progressMap by viewModel.downloadProgress.collectAsStateWithLifecycle()

    val appliedMessage = stringResource(R.string.theme_store_applied)
    val applyFailedMessage = stringResource(R.string.theme_store_apply_failed)
    val downloadFailedMessage = stringResource(R.string.theme_store_download_failed)

    var selectedTheme by remember { mutableStateOf<RemoteTheme?>(null) }
    var downloadingTheme by remember { mutableStateOf<RemoteTheme?>(null) }
    var completedTheme by remember { mutableStateOf<RemoteTheme?>(null) }
    var showFilter by remember { mutableStateOf(false) }

    val downloading = downloadingTheme
    LaunchedEffect(progressMap, downloading) {
        if (downloading == null) return@LaunchedEffect
        when (progressMap[downloading.id]?.status) {
            DownloadStatus.COMPLETED -> {
                downloadingTheme = null
                completedTheme = downloading
            }

            DownloadStatus.FAILED -> {
                downloadingTheme = null
                snackbarHostState.showSnackbar(
                    progressMap[downloading.id]?.errorMessage ?: downloadFailedMessage,
                )
            }

            else -> Unit
        }
    }

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(stringResource(R.string.theme_store_title)) },
                searchText = viewModel.searchQuery,
                onSearchTextChange = viewModel::onSearchQueryChange,
                onClearClick = { viewModel.onSearchQueryChange("") },
                scrollBehavior = scrollBehavior,
                topSearch = true,
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                actions = {
                    IconButton(onClick = { navigator.push(Route.MyThemes) }) {
                        Icon(
                            Icons.Filled.ColorLens,
                            contentDescription = stringResource(R.string.my_themes_title),
                        )
                    }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.theme_store_filter_title),
                        )
                    }
                },
                searchContent = { bottomPadding, _ ->
                    ThemeResults(
                        themes = viewModel.themes,
                        localThemes = viewModel.localThemes,
                        contentPadding = searchContentPadding(bottomPadding),
                        onSelect = { selectedTheme = it },
                    )
                },
                defaultContent = { bottomPadding, _ ->
                    ThemeResults(
                        themes = viewModel.themes,
                        localThemes = viewModel.localThemes,
                        contentPadding = searchContentPadding(bottomPadding),
                        onSelect = { selectedTheme = it },
                    )
                },
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                viewModel.isRefreshing && viewModel.themes.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                viewModel.errorMessage != null && viewModel.themes.isEmpty() ->
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(viewModel.errorMessage.orEmpty(), style = FolkType.Summary)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.theme_store_retry))
                        }
                    }

                viewModel.themes.isEmpty() -> EmptyThemes(
                    stringResource(R.string.theme_store_empty),
                    Modifier.fillMaxSize(),
                )

                else -> PullToRefreshBox(
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = viewModel.isRefreshing,
                    onRefresh = viewModel::refresh,
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            isRefreshing = viewModel.isRefreshing,
                            state = pullToRefreshState,
                        )
                    },
                ) {
                    ThemeResults(
                        themes = viewModel.themes,
                        localThemes = viewModel.localThemes,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        gridState = gridState,
                        onSelect = { selectedTheme = it },
                    )
                }
            }
        }
    }

    val detail = selectedTheme
    if (detail != null) {
        ThemeDetailDialog(
            theme = detail,
            isDownloaded = viewModel.isThemeDownloaded(detail.id),
            isDownloading = viewModel.isThemeDownloading(detail.id),
            onApply = {
                scope.launch {
                    val local = viewModel.localThemes.firstOrNull { it.id == detail.id }
                    val ok = local != null && viewModel.applyTheme(local)
                    snackbarHostState.showSnackbar(if (ok) appliedMessage else applyFailedMessage)
                }
                selectedTheme = null
            },
            onDownload = {
                viewModel.startDownload(detail)
                if (viewModel.isThemeDownloading(detail.id)) downloadingTheme = detail
                selectedTheme = null
            },
            onDismiss = { selectedTheme = null },
        )
    }

    val progressTheme = downloadingTheme
    if (progressTheme != null) {
        DownloadProgressDialog(
            theme = progressTheme,
            progress = progressMap[progressTheme.id],
            onCancel = {
                viewModel.cancelDownload(progressTheme.id)
                downloadingTheme = null
            },
            onPause = {
                if (progressMap[progressTheme.id]?.status == DownloadStatus.PAUSED) {
                    viewModel.startDownload(progressTheme)
                } else {
                    viewModel.pauseDownload(progressTheme.id)
                }
            },
        )
    }

    val completed = completedTheme
    if (completed != null) {
        AlertDialog(
            onDismissRequest = { completedTheme = null },
            title = { Text(stringResource(R.string.theme_store_download_complete)) },
            text = { Text(stringResource(R.string.theme_store_download_complete_message, completed.name)) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val local = viewModel.localThemes.firstOrNull { it.id == completed.id }
                        val ok = local != null && viewModel.applyTheme(local)
                        snackbarHostState.showSnackbar(if (ok) appliedMessage else applyFailedMessage)
                    }
                    completedTheme = null
                }) {
                    Text(stringResource(R.string.theme_store_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    completedTheme = null
                    navigator.push(Route.MyThemes)
                }) {
                    Text(stringResource(R.string.theme_store_go_my_themes))
                }
            },
        )
    }

    if (showFilter) {
        ThemeFilterDialog(
            currentAuthor = viewModel.filterAuthor,
            currentSource = viewModel.filterSource,
            currentTypePhone = viewModel.filterTypePhone,
            currentTypeTablet = viewModel.filterTypeTablet,
            onApply = { author, source, phone, tablet ->
                viewModel.updateFilters(author, source, phone, tablet)
                showFilter = false
            },
            onReset = {
                viewModel.updateFilters("", ThemeStoreViewModel.SOURCE_ALL, true, true)
                showFilter = false
            },
            onDismiss = { showFilter = false },
        )
    }
}

fun searchContentPadding(bottomPadding: androidx.compose.ui.unit.Dp) = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = 8.dp,
    bottom = 16.dp + bottomPadding,
)

/**
 * The shared results grid, used both as the page body and inside the expanded search overlay. The
 * query is already applied upstream, so the same list backs both.
 */
@Composable
private fun ThemeResults(
    themes: List<RemoteTheme>,
    localThemes: List<LocalTheme>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onSelect: (RemoteTheme) -> Unit,
) {
    if (themes.isEmpty()) {
        EmptyThemes(stringResource(R.string.theme_store_empty), Modifier.fillMaxSize())
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(128.dp),
        modifier = modifier.fillMaxSize(),
        state = gridState,
        contentPadding = contentPadding,
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(themes, key = { it.id }) { theme ->
            val local = localThemes.firstOrNull { it.id == theme.id }
            ThemePreviewCard(
                name = theme.name,
                model = local?.previewImagePath
                    ?.let { File(it) }
                    ?.takeIf { it.exists() }
                    ?: theme.previewUrl.takeIf { it.isNotBlank() },
                onClick = { onSelect(theme) },
            )
        }
    }
}

@Composable
private fun ThemeDetailDialog(
    theme: RemoteTheme,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onApply: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(theme.name) },
        text = {
            Column {
                Text(stringResource(R.string.theme_store_author, theme.author), style = FolkType.Summary)
                if (theme.version.isNotBlank()) {
                    Text(stringResource(R.string.theme_store_version, theme.version), style = FolkType.Caption)
                }
                Text(stringResource(R.string.theme_store_source_value, theme.source), style = FolkType.Caption)
                if (theme.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(theme.description, style = FolkType.Summary)
                }
            }
        },
        confirmButton = {
            when {
                isDownloaded -> Button(onClick = onApply) {
                    Text(stringResource(R.string.theme_store_apply))
                }

                isDownloading -> LoadingIndicator(modifier = Modifier.width(24.dp))

                else -> Button(onClick = onDownload) {
                    Text(stringResource(R.string.theme_store_download))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.theme_store_cancel))
            }
        },
    )
}

@Composable
private fun DownloadProgressDialog(
    theme: RemoteTheme,
    progress: DownloadProgress?,
    onCancel: () -> Unit,
    onPause: () -> Unit,
) {
    val fraction = progress?.overallProgress ?: 0f
    val fileFraction = progress?.fileProgress ?: 0f
    val imageFraction = progress?.imageProgress ?: 0f
    val paused = progress?.status == DownloadStatus.PAUSED
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "DownloadProgress",
    )
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.theme_download_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(theme.previewUrl)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = theme.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(FolkShape.Corner12),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(theme.name, style = FolkType.Title)
                        Text(theme.author, style = FolkType.Caption)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.theme_download_progress, (fraction * 100).toInt()),
                    style = FolkType.Caption,
                )
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.theme_download_file, (fileFraction * 100).toInt()),
                        style = FolkType.Caption,
                    )
                    Text(
                        stringResource(R.string.theme_download_image, (imageFraction * 100).toInt()),
                        style = FolkType.Caption,
                    )
                }
                if (paused) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.theme_download_paused),
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (progress?.errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        progress.errorMessage,
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onCancel) {
                Text(stringResource(R.string.theme_store_cancel))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onPause,
                enabled = paused || progress?.status == DownloadStatus.DOWNLOADING,
            ) {
                Text(
                    stringResource(
                        if (paused) R.string.theme_download_resume else R.string.theme_download_pause,
                    ),
                )
            }
        },
    )
}

@Composable
private fun ThemeFilterDialog(
    currentAuthor: String,
    currentSource: String,
    currentTypePhone: Boolean,
    currentTypeTablet: Boolean,
    onApply: (String, String, Boolean, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var author by remember { mutableStateOf(currentAuthor) }
    var source by remember { mutableStateOf(currentSource) }
    var phone by remember { mutableStateOf(currentTypePhone) }
    var tablet by remember { mutableStateOf(currentTypeTablet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_store_filter_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.theme_store_filter_author)) },
                    singleLine = true,
                    shape = FolkShape.Corner16,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.theme_store_filter_source), style = FolkType.Title)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = source == ThemeStoreViewModel.SOURCE_ALL,
                        onClick = { source = ThemeStoreViewModel.SOURCE_ALL },
                        label = { Text(stringResource(R.string.theme_store_filter_all)) },
                    )
                    FilterChip(
                        selected = source == ThemeStoreViewModel.SOURCE_OFFICIAL,
                        onClick = { source = ThemeStoreViewModel.SOURCE_OFFICIAL },
                        label = { Text(stringResource(R.string.theme_source_official)) },
                    )
                    FilterChip(
                        selected = source == ThemeStoreViewModel.SOURCE_THIRD_PARTY,
                        onClick = { source = ThemeStoreViewModel.SOURCE_THIRD_PARTY },
                        label = { Text(stringResource(R.string.theme_source_third_party)) },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.theme_store_filter_type), style = FolkType.Title)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = phone,
                        onClick = { phone = !phone },
                        label = { Text(stringResource(R.string.theme_type_phone)) },
                    )
                    FilterChip(
                        selected = tablet,
                        onClick = { tablet = !tablet },
                        label = { Text(stringResource(R.string.theme_type_tablet)) },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(author, source, phone, tablet) }) {
                Text(stringResource(R.string.theme_store_filter_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.theme_store_filter_reset))
            }
        },
    )
}
