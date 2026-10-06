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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.Modifier
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
import me.weishu.kernelsu.ui.component.WorkCardBackgroundSettings
import me.weishu.kernelsu.ui.component.rememberSystemCropLauncher
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.wallpaper.FolkThemeIO
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.WallpaperManager
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import kotlin.math.roundToInt

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
    val workCardBackgroundEnabled: Boolean,
    val workCardHasImage: Boolean,
    val workCardOpacity: Float,
    val workCardDim: Float,
    val workCardDualOpacityEnabled: Boolean,
    val workCardDayOpacity: Float,
    val workCardNightOpacity: Float,
    val workCardCheckHidden: Boolean,
    val workCardTextHidden: Boolean,
    val workCardModeHidden: Boolean,
    val isSaving: Boolean,
)

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

    val cropLauncher = rememberSystemCropLauncher(cacheName = "wallpaper_crop_cache") { cropped ->
        persist(cropped)
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
        workCardBackgroundEnabled = WallpaperConfig.workCardBackgroundEnabled,
        workCardHasImage = !WallpaperConfig.workCardBackgroundUri.isNullOrEmpty(),
        workCardOpacity = WallpaperConfig.workCardOpacity,
        workCardDim = WallpaperConfig.workCardDim,
        workCardDualOpacityEnabled = WallpaperConfig.workCardDualOpacityEnabled,
        workCardDayOpacity = WallpaperConfig.workCardDayOpacity,
        workCardNightOpacity = WallpaperConfig.workCardNightOpacity,
        workCardCheckHidden = WallpaperConfig.workCardCheckHidden,
        workCardTextHidden = WallpaperConfig.workCardTextHidden,
        workCardModeHidden = WallpaperConfig.workCardModeHidden,
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
                },
            )

            if (state.enabled && state.hasImage) {
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
                            title = stringResource(R.string.wallpaper_opacity),
                            summary = stringResource(R.string.wallpaper_opacity_summary),
                            value = state.opacity,
                            range = 0.1f..1f,
                            onValueChange = actions.onSetOpacity,
                        )
                        SliderSetting(
                            title = stringResource(R.string.wallpaper_blur),
                            summary = stringResource(R.string.wallpaper_blur_summary),
                            value = state.blur,
                            range = 0f..50f,
                            onValueChange = actions.onSetBlur,
                            format = { "${it.roundToInt()} dp" },
                        )
                        if (state.dualDimEnabled) {
                            SliderSetting(
                                title = stringResource(R.string.wallpaper_day_dim),
                                value = state.dayDim,
                                range = 0f..1f,
                                onValueChange = actions.onSetDayDim,
                            )
                            SliderSetting(
                                title = stringResource(R.string.wallpaper_night_dim),
                                value = state.nightDim,
                                range = 0f..1f,
                                onValueChange = actions.onSetNightDim,
                            )
                        } else {
                            SliderSetting(
                                title = stringResource(R.string.wallpaper_dim),
                                summary = stringResource(R.string.wallpaper_dim_summary),
                                value = state.dim,
                                range = 0f..1f,
                                onValueChange = actions.onSetDim,
                            )
                        }
                    }
                }

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
                            text = stringResource(R.string.wallpaper_work_card_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        WorkCardBackgroundSettings(
                            enabled = state.workCardBackgroundEnabled,
                            hasImage = state.workCardHasImage,
                            opacity = state.workCardOpacity,
                            dim = state.workCardDim,
                            dualOpacityEnabled = state.workCardDualOpacityEnabled,
                            dayOpacity = state.workCardDayOpacity,
                            nightOpacity = state.workCardNightOpacity,
                            checkHidden = state.workCardCheckHidden,
                            textHidden = state.workCardTextHidden,
                            modeHidden = state.workCardModeHidden,
                            onEnabledChange = actions.onToggleWorkCardBackground,
                            onPickImage = actions.onPickWorkCardImage,
                            onClearImage = actions.onClearWorkCardImage,
                            onOpacityChange = actions.onSetWorkCardOpacity,
                            onDimChange = actions.onSetWorkCardDim,
                            onDualOpacityChange = actions.onToggleWorkCardDualOpacity,
                            onDayOpacityChange = actions.onSetWorkCardDayOpacity,
                            onNightOpacityChange = actions.onSetWorkCardNightOpacity,
                            onCheckHiddenChange = actions.onToggleWorkCardCheckHidden,
                            onTextHiddenChange = actions.onToggleWorkCardTextHidden,
                            onModeHiddenChange = actions.onToggleWorkCardModeHidden,
                        )
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
    summary: String? = null,
    format: (Float) -> String = { "${(it * 100).roundToInt()}%" },
) {
    val sliderState = rememberSliderState(value = value, trackRange = range)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
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
