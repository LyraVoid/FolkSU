package me.weishu.kernelsu.ui.screen.wallpaper

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.FolkWallpaperSurface
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.rememberSystemCropLauncher
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.wallpaper.FolkThemeIO
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import kotlin.math.roundToInt

/** Sentinel for "no per-page background is being picked". */
private const val NO_PAGE = -1

@Immutable
data class WallpaperUiState(
    val enabled: Boolean,
    val hasImage: Boolean,
    val uri: String?,
    val opacity: Float,
    val blur: Float,
    val dim: Float,
    val dualDimEnabled: Boolean,
    val dayDim: Float,
    val nightDim: Float,
    val useWallpaperColor: Boolean,
    val multiBackgroundEnabled: Boolean,
    val homeBackgroundSelected: Boolean,
    val superuserBackgroundSelected: Boolean,
    val moduleBackgroundSelected: Boolean,
    val settingsBackgroundSelected: Boolean,
    val workCardSurface: SurfaceConfig,
    val isSaving: Boolean,
) {
    /** The config of the surface [id], or null when this state does not carry it. */
    fun surfaceFor(id: SurfaceId): SurfaceConfig? = when (id) {
        SurfaceRegistry.GRID_WORK_CARD -> workCardSurface
        else -> null
    }
}

@Immutable
data class WallpaperScreenActions(
    val onBack: () -> Unit,
    val onToggleEnabled: (Boolean) -> Unit,
    val onPickImage: () -> Unit,
    val onClear: () -> Unit,
    val onSetOpacity: (Float) -> Unit,
    val onSetBlur: (Float) -> Unit,
    val onSetDim: (Float) -> Unit,
    val onToggleDualDim: (Boolean) -> Unit,
    val onSetDayDim: (Float) -> Unit,
    val onSetNightDim: (Float) -> Unit,
    val onToggleUseWallpaperColor: (Boolean) -> Unit,
    val onToggleMultiBackground: (Boolean) -> Unit,
    val onPickHomeBackground: () -> Unit,
    val onPickSuperuserBackground: () -> Unit,
    val onPickModuleBackground: () -> Unit,
    val onPickSettingsBackground: () -> Unit,
    val onToggleWorkCardBackground: (Boolean) -> Unit,
    val onPickWorkCardImage: () -> Unit,
    val onClearWorkCardImage: () -> Unit,
    val onSetWorkCardOpacity: (Float) -> Unit,
    val onSetWorkCardDim: (Float) -> Unit,
    val onToggleWorkCardDualOpacity: (Boolean) -> Unit,
    val onSetWorkCardDayOpacity: (Float) -> Unit,
    val onSetWorkCardNightOpacity: (Float) -> Unit,
    val onToggleWorkCardCheckHidden: (Boolean) -> Unit,
    val onToggleWorkCardTextHidden: (Boolean) -> Unit,
    val onToggleWorkCardModeHidden: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
)

@Composable
fun WallpaperScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var isSaving by remember { mutableStateOf(false) }
    var pendingCrop by remember { mutableStateOf<Uri?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }
    var pendingWorkCardCrop by remember { mutableStateOf<Uri?>(null) }
    var showWorkCardCropDialog by remember { mutableStateOf(false) }
    var pendingPage by remember { mutableStateOf(NO_PAGE) }
    var pendingPageCrop by remember { mutableStateOf<Uri?>(null) }
    var showPageCropDialog by remember { mutableStateOf(false) }

    val persist: (Uri) -> Unit = { picked ->
        scope.launch {
            isSaving = true
            val ok = WallpaperManager.save(context, picked)
            isSaving = false
            snackbarHost.showSnackbar(
                context.getString(if (ok) R.string.wallpaper_saved else R.string.wallpaper_save_failed)
            )
        }
    }

    val persistWorkCard: (Uri) -> Unit = { picked ->
        scope.launch {
            isSaving = true
            val ok = WallpaperManager.saveWorkCardBackground(context, picked)
            isSaving = false
            snackbarHost.showSnackbar(
                context.getString(
                    if (ok) R.string.wallpaper_work_card_saved else R.string.wallpaper_work_card_save_failed
                )
            )
        }
    }

    val persistPage: (Int, Uri) -> Unit = { page, picked ->
        scope.launch {
            isSaving = true
            val ok = WallpaperManager.savePageBackground(context, page, picked)
            isSaving = false
            snackbarHost.showSnackbar(
                context.getString(if (ok) R.string.wallpaper_saved else R.string.wallpaper_save_failed)
            )
        }
    }

    val cropLauncher = rememberSystemCropLauncher(cacheName = "wallpaper_crop_cache") { cropped ->
        persist(cropped)
    }

    val pageCropLauncher = rememberSystemCropLauncher(cacheName = "wallpaper_page_crop_cache") { cropped ->
        persistPage(pendingPage, cropped)
    }

    val workCardCropLauncher = rememberSystemCropLauncher(cacheName = "work_card_crop_cache") { cropped ->
        persistWorkCard(cropped)
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingCrop = uri
            showCropDialog = true
        }
    }

    val pickWorkCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingWorkCardCrop = uri
            showWorkCardCropDialog = true
        }
    }

    val pickPageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingPageCrop = uri
            showPageCropDialog = true
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = FolkThemeIO.exportBackground(context, uri, FolkThemeIO.FILE_NAME)
                snackbarHost.showSnackbar(
                    context.getString(
                        if (ok) R.string.wallpaper_export_success else R.string.wallpaper_export_failed
                    )
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = FolkThemeIO.importBackground(context, uri)
                snackbarHost.showSnackbar(
                    context.getString(
                        if (ok) R.string.wallpaper_import_success else R.string.wallpaper_import_failed
                    )
                )
            }
        }
    }

    if (showCropDialog) {
        val target = pendingCrop
        AlertDialog(
            onDismissRequest = {
                showCropDialog = false
                pendingCrop = null
            },
            title = { Text(stringResource(R.string.wallpaper_crop_title)) },
            text = { Text(stringResource(R.string.wallpaper_crop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCropDialog = false
                    pendingCrop = null
                    if (target != null) {
                        val launched = runCatching { cropLauncher.launch(target) }.isSuccess
                        if (!launched) {
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    context.getString(R.string.wallpaper_crop_unsupported)
                                )
                            }
                            persist(target)
                        }
                    }
                }) {
                    Text(stringResource(R.string.wallpaper_crop_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCropDialog = false
                    pendingCrop = null
                    if (target != null) persist(target)
                }) {
                    Text(stringResource(R.string.wallpaper_crop_direct))
                }
            },
        )
    }

    if (showWorkCardCropDialog) {
        val target = pendingWorkCardCrop
        AlertDialog(
            onDismissRequest = {
                showWorkCardCropDialog = false
                pendingWorkCardCrop = null
            },
            title = { Text(stringResource(R.string.wallpaper_crop_title)) },
            text = { Text(stringResource(R.string.wallpaper_crop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showWorkCardCropDialog = false
                    pendingWorkCardCrop = null
                    if (target != null) {
                        val launched = runCatching { workCardCropLauncher.launch(target) }.isSuccess
                        if (!launched) {
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    context.getString(R.string.wallpaper_crop_unsupported)
                                )
                            }
                            persistWorkCard(target)
                        }
                    }
                }) {
                    Text(stringResource(R.string.wallpaper_crop_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showWorkCardCropDialog = false
                    pendingWorkCardCrop = null
                    if (target != null) persistWorkCard(target)
                }) {
                    Text(stringResource(R.string.wallpaper_crop_direct))
                }
            },
        )
    }

    if (showPageCropDialog) {
        val target = pendingPageCrop
        AlertDialog(
            onDismissRequest = {
                showPageCropDialog = false
                pendingPageCrop = null
                pendingPage = NO_PAGE
            },
            title = { Text(stringResource(R.string.wallpaper_crop_title)) },
            text = { Text(stringResource(R.string.wallpaper_crop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPageCropDialog = false
                    pendingPageCrop = null
                    if (target != null) {
                        val launched = runCatching { pageCropLauncher.launch(target) }.isSuccess
                        if (!launched) {
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    context.getString(R.string.wallpaper_crop_unsupported)
                                )
                            }
                            persistPage(pendingPage, target)
                        }
                    }
                }) {
                    Text(stringResource(R.string.wallpaper_crop_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPageCropDialog = false
                    pendingPageCrop = null
                    if (target != null) persistPage(pendingPage, target)
                }) {
                    Text(stringResource(R.string.wallpaper_crop_direct))
                }
            },
        )
    }

    val state = WallpaperUiState(
        enabled = WallpaperConfig.enabled,
        hasImage = !WallpaperConfig.uri.isNullOrEmpty(),
        uri = WallpaperConfig.uri,
        opacity = WallpaperConfig.opacity,
        blur = WallpaperConfig.blur,
        dim = WallpaperConfig.dim,
        dualDimEnabled = WallpaperConfig.dualDimEnabled,
        dayDim = WallpaperConfig.dayDim,
        nightDim = WallpaperConfig.nightDim,
        useWallpaperColor = WallpaperConfig.useWallpaperColor,
        multiBackgroundEnabled = WallpaperConfig.multiBackgroundEnabled,
        homeBackgroundSelected = !WallpaperConfig.homeBackgroundUri.isNullOrEmpty(),
        superuserBackgroundSelected = !WallpaperConfig.superuserBackgroundUri.isNullOrEmpty(),
        moduleBackgroundSelected = !WallpaperConfig.moduleBackgroundUri.isNullOrEmpty(),
        settingsBackgroundSelected = !WallpaperConfig.settingsBackgroundUri.isNullOrEmpty(),
        workCardSurface = WallpaperConfig.workCardSurface,
        isSaving = isSaving,
    )

    val actions = WallpaperScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onToggleEnabled = {
            WallpaperConfig.updateEnabled(it)
            WallpaperConfig.save(context)
        },
        onPickImage = { pickImageLauncher.launch("image/*") },
        onClear = {
            WallpaperManager.clear(context)
            scope.launch { snackbarHost.showSnackbar(context.getString(R.string.wallpaper_removed)) }
        },
        onSetOpacity = {
            WallpaperConfig.updateOpacity(it)
            WallpaperConfig.save(context)
        },
        onSetBlur = {
            WallpaperConfig.updateBlur(it)
            WallpaperConfig.save(context)
        },
        onSetDim = {
            WallpaperConfig.updateDim(it)
            WallpaperConfig.save(context)
        },
        onToggleDualDim = {
            WallpaperConfig.updateDualDimEnabled(it)
            WallpaperConfig.save(context)
        },
        onSetDayDim = {
            WallpaperConfig.updateDayDim(it)
            WallpaperConfig.save(context)
        },
        onSetNightDim = {
            WallpaperConfig.updateNightDim(it)
            WallpaperConfig.save(context)
        },
        onToggleUseWallpaperColor = { enabled ->
            WallpaperConfig.updateUseWallpaperColor(enabled)
            WallpaperConfig.save(context)
            if (enabled) {
                scope.launch(Dispatchers.IO) { WallpaperManager.refreshDerivedIfMissing(context) }
            }
        },
        onToggleMultiBackground = { enabled ->
            WallpaperConfig.updateMultiBackgroundEnabled(enabled)
            WallpaperConfig.save(context)
        },
        onPickHomeBackground = {
            pendingPage = WallpaperConfig.PAGE_HOME
            pickPageLauncher.launch("image/*")
        },
        onPickSuperuserBackground = {
            pendingPage = WallpaperConfig.PAGE_SUPERUSER
            pickPageLauncher.launch("image/*")
        },
        onPickModuleBackground = {
            pendingPage = WallpaperConfig.PAGE_MODULE
            pickPageLauncher.launch("image/*")
        },
        onPickSettingsBackground = {
            pendingPage = WallpaperConfig.PAGE_SETTINGS
            pickPageLauncher.launch("image/*")
        },
        onToggleWorkCardBackground = { enabled ->
            WallpaperConfig.updateWorkCardBackgroundEnabled(enabled)
            WallpaperConfig.save(context)
        },
        onPickWorkCardImage = { pickWorkCardLauncher.launch("image/*") },
        onClearWorkCardImage = {
            WallpaperManager.clearWorkCardBackground(context)
            scope.launch { snackbarHost.showSnackbar(context.getString(R.string.wallpaper_work_card_removed)) }
        },
        onSetWorkCardOpacity = {
            WallpaperConfig.updateWorkCardOpacity(it)
            WallpaperConfig.save(context)
        },
        onSetWorkCardDim = {
            WallpaperConfig.updateWorkCardDim(it)
            WallpaperConfig.save(context)
        },
        onToggleWorkCardDualOpacity = {
            WallpaperConfig.updateWorkCardDualOpacityEnabled(it)
            WallpaperConfig.save(context)
        },
        onSetWorkCardDayOpacity = {
            WallpaperConfig.updateWorkCardDayOpacity(it)
            WallpaperConfig.save(context)
        },
        onSetWorkCardNightOpacity = {
            WallpaperConfig.updateWorkCardNightOpacity(it)
            WallpaperConfig.save(context)
        },
        onToggleWorkCardCheckHidden = {
            WallpaperConfig.updateWorkCardCheckHidden(it)
            WallpaperConfig.save(context)
        },
        onToggleWorkCardTextHidden = {
            WallpaperConfig.updateWorkCardTextHidden(it)
            WallpaperConfig.save(context)
        },
        onToggleWorkCardModeHidden = {
            WallpaperConfig.updateWorkCardModeHidden(it)
            WallpaperConfig.save(context)
        },
        onExport = { exportLauncher.launch(FolkThemeIO.FILE_NAME) },
        onImport = { importLauncher.launch("*/*") },
    )

    WallpaperScreenMaterial(state, actions, snackbarHost)
}

@Composable
fun WallpaperScreenMaterial(
    state: WallpaperUiState,
    actions: WallpaperScreenActions,
    snackbarHostState: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                title = { Text(stringResource(R.string.wallpaper_title)) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content = buildList {
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Wallpaper,
                            title = stringResource(R.string.wallpaper_enable),
                            summary = stringResource(R.string.wallpaper_enable_summary),
                            checked = state.enabled,
                            onCheckedChange = actions.onToggleEnabled,
                        )
                    }
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.GridView,
                            title = stringResource(R.string.wallpaper_multi_background_mode),
                            summary = stringResource(R.string.wallpaper_multi_background_mode_summary),
                            checked = state.multiBackgroundEnabled,
                            onCheckedChange = actions.onToggleMultiBackground,
                        )
                    }
                    if (state.multiBackgroundEnabled) {
                        add {
                            WallpaperPagePickItem(
                                titleRes = R.string.wallpaper_select_home_background,
                                selected = state.homeBackgroundSelected,
                                enabled = !state.isSaving,
                                onClick = actions.onPickHomeBackground,
                            )
                        }
                        add {
                            WallpaperPagePickItem(
                                titleRes = R.string.wallpaper_select_superuser_background,
                                selected = state.superuserBackgroundSelected,
                                enabled = !state.isSaving,
                                onClick = actions.onPickSuperuserBackground,
                            )
                        }
                        add {
                            WallpaperPagePickItem(
                                titleRes = R.string.wallpaper_select_module_background,
                                selected = state.moduleBackgroundSelected,
                                enabled = !state.isSaving,
                                onClick = actions.onPickModuleBackground,
                            )
                        }
                        add {
                            WallpaperPagePickItem(
                                titleRes = R.string.wallpaper_select_settings_background,
                                selected = state.settingsBackgroundSelected,
                                enabled = !state.isSaving,
                                onClick = actions.onPickSettingsBackground,
                            )
                        }
                    } else {
                        add {
                            SegmentedListItem(
                                onClick = actions.onPickImage,
                                enabled = !state.isSaving,
                                headlineContent = {
                                    Text(stringResource(if (state.hasImage) R.string.wallpaper_change else R.string.wallpaper_pick))
                                },
                                leadingContent = { Icon(Icons.Filled.Image, null) },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                },
                            )
                        }
                        if (state.hasImage) {
                            add {
                                SegmentedListItem(
                                    onClick = actions.onClear,
                                    headlineContent = { Text(stringResource(R.string.wallpaper_clear)) },
                                    leadingContent = { Icon(Icons.Filled.Delete, null) },
                                )
                            }
                        }
                    }
                },
            )

            // The appearance controls apply to every page, so they must stay reachable in multi mode
            // even though the single wallpaper is unused there.
            if (state.enabled && (state.hasImage || state.multiBackgroundEnabled)) {
                FolkWallpaperSurface(
                    role = WallpaperSurfaceRole.Group,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    shape = MaterialTheme.shapes.large,
                    fallbackColor = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.wallpaper_section_appearance),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        SliderSetting(
                            icon = Icons.Filled.Opacity,
                            title = stringResource(R.string.wallpaper_opacity),
                            summary = stringResource(R.string.wallpaper_opacity_summary),
                            value = state.opacity,
                            range = 0f..1f,
                            onValueChange = actions.onSetOpacity,
                        )
                        SliderSetting(
                            icon = Icons.Filled.BlurOn,
                            title = stringResource(R.string.wallpaper_blur),
                            summary = stringResource(R.string.wallpaper_blur_summary),
                            value = state.blur,
                            range = 0f..50f,
                            onValueChange = actions.onSetBlur,
                            format = { "${it.roundToInt()} dp" },
                        )
                        if (state.dualDimEnabled) {
                            SliderSetting(
                                icon = Icons.Filled.Contrast,
                                title = stringResource(R.string.wallpaper_day_dim),
                                value = state.dayDim,
                                range = 0f..1f,
                                onValueChange = actions.onSetDayDim,
                            )
                            SliderSetting(
                                icon = Icons.Filled.Contrast,
                                title = stringResource(R.string.wallpaper_night_dim),
                                value = state.nightDim,
                                range = 0f..1f,
                                onValueChange = actions.onSetNightDim,
                            )
                        } else {
                            SliderSetting(
                                icon = Icons.Filled.Brightness6,
                                title = stringResource(R.string.wallpaper_dim),
                                summary = stringResource(R.string.wallpaper_dim_summary),
                                value = state.dim,
                                range = 0f..1f,
                                onValueChange = actions.onSetDim,
                            )
                        }
                    }
                }

                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    content = buildList {
                        add {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Palette,
                                title = stringResource(R.string.wallpaper_use_color),
                                summary = stringResource(R.string.wallpaper_use_color_summary),
                                checked = state.useWallpaperColor,
                                onCheckedChange = actions.onToggleUseWallpaperColor,
                            )
                        }
                        add {
                            SegmentedSwitchItem(
                                icon = Icons.Filled.Contrast,
                                title = stringResource(R.string.wallpaper_dual_dim),
                                summary = stringResource(R.string.wallpaper_dual_dim_summary),
                                checked = state.dualDimEnabled,
                                onCheckedChange = actions.onToggleDualDim,
                            )
                        }
                    },
                )
            }

            SurfaceSettingsHost(state = state, actions = actions)

            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                title = stringResource(R.string.wallpaper_section_interop),
                content = buildList {
                    add {
                        SegmentedListItem(
                            onClick = actions.onExport,
                            headlineContent = { Text(stringResource(R.string.wallpaper_export)) },
                            supportingContent = { Text(stringResource(R.string.wallpaper_export_summary)) },
                            leadingContent = { Icon(Icons.Filled.FileDownload, null) },
                        )
                    }
                    add {
                        SegmentedListItem(
                            onClick = actions.onImport,
                            headlineContent = { Text(stringResource(R.string.wallpaper_import)) },
                            supportingContent = { Text(stringResource(R.string.wallpaper_import_summary)) },
                            leadingContent = { Icon(Icons.Filled.FileUpload, null) },
                        )
                    }
                },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    icon: ImageVector? = null,
    summary: String? = null,
    format: (Float) -> String = { "${(it * 100).roundToInt()}%" },
) {
    val sliderState = rememberSliderState(value = value, trackRange = range)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = format(sliderState.value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            state = sliderState,
            onValueChangeFinished = { onValueChange(sliderState.value) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A row that picks the background image for one main page. */
@Composable
private fun WallpaperPagePickItem(
    titleRes: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = if (selected) {
            { Text(stringResource(R.string.wallpaper_background_selected)) }
        } else {
            null
        },
        leadingContent = { Icon(Icons.Filled.Image, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}
