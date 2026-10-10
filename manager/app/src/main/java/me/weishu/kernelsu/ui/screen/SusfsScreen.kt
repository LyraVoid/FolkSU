package me.weishu.kernelsu.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.susfs.SusfsInputs
import me.weishu.kernelsu.data.susfs.SusfsStatus
import me.weishu.kernelsu.ui.component.material.*
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.theme.FolkMotion
import me.weishu.kernelsu.ui.theme.FolkShape
import me.weishu.kernelsu.ui.viewmodel.SusfsUiState
import me.weishu.kernelsu.ui.viewmodel.SusfsViewModel
import org.json.JSONArray
import org.json.JSONObject
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

@Composable
fun susfsManagementLabel(status: String): String = stringResource(when (status) {
    "applied" -> R.string.susfs_applied
    "failed" -> R.string.susfs_failed
    "pending_reboot" -> R.string.susfs_pending
    else -> R.string.susfs_saved
})

private val susfsPages = listOf(R.string.susfs_overview, R.string.susfs_settings,
    R.string.susfs_tab_paths, R.string.susfs_tab_spoof, R.string.susfs_tab_redirect, R.string.susfs_tab_extensions)

@Composable
fun SusfsScreen() {
    val vm = viewModel<SusfsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pager = rememberPagerState { susfsPages.size }
    // Keep all six positions outside pager composition: distant pages may be disposed.
    val scrolls = susfsPages.map { rememberScrollState() }
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    val config = remember(state.config) { state.config?.let(::JSONObject) }
    val writable = state.status.writable && !state.busy && config != null
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(4096)
                        while (buffer.size() <= 128 * 1024) {
                            val count = input.read(chunk, 0, minOf(chunk.size, 128 * 1024 + 1 - buffer.size()))
                            if (count < 0) break
                            buffer.write(chunk, 0, count)
                        }
                        require(buffer.size() <= 128 * 1024) { "Configuration exceeds 128 KiB" }
                        buffer.toByteArray().toString(Charsets.UTF_8)
                    } ?: error("Cannot read configuration")
                }
                SusfsInputs.preview(text)
                draft = text
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { vm.message(e.message ?: "Import failed") }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                // Export a fresh saved file, not the screen's potentially obsolete snapshot.
                val text = me.weishu.kernelsu.data.susfs.SusfsRepository().export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("Cannot write configuration")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { vm.message(e.message ?: "Export failed") }
        }
    }
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }
    ExpressiveScaffold(topBar = {
        LargeFlexibleTopAppBar(
            title = { Text(stringResource(R.string.susfs_title)) },
            navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
            actions = { TextButton(enabled = !state.busy, onClick = vm::refresh) { Text(stringResource(R.string.susfs_refresh)) } },
            colors = expressiveTopAppBarColors(), scrollBehavior = scrollBehavior,
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            SusfsNavigation(pager.currentPage) { page -> scope.launch { pager.animateScrollToPage(page) } }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                SusfsPage(scrolls[page]) {
                    SusfsNotice(state)
                    when (page) {
                        0 -> SusfsOverview(state, config, writable, vm,
                            { import.launch(arrayOf("application/json", "text/plain")) },
                            { export.launch("folksu-susfs.json") })
                        1 -> SusfsBasicPage(config, state.status, writable, vm)
                        2 -> {
                            SusfsRuleSection("path", config, state.status, writable, vm)
                            SusfsRuleSection("map", config, state.status, writable, vm)
                        }
                        3 -> {
                            SusfsUnameSection(config, state.status, writable, vm)
                            SusfsCmdlineSection(config, state.status, writable, vm)
                            SusfsRuleSection("kstat", config, state.status, writable, vm)
                        }
                        4 -> SusfsRuleSection("redirect", config, state.status, writable, vm)
                        5 -> SusfsExtensionsPage(config, state.status, writable, vm)
                    }
                }
            }
        }
    }
    draft?.let { text ->
        val preview = remember(text) { SusfsInputs.preview(text) }
        AlertDialog(onDismissRequest = { draft = null }, title = { Text(stringResource(R.string.susfs_import_preview)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.susfs_import_counts, preview.getJSONArray("rules").length(),
                    preview.getJSONObject("lkm").getJSONArray("hidden_modules").length(),
                    preview.getJSONObject("lkm").getJSONArray("mount_prefixes").length()))
                Text(stringResource(if (preview.getBoolean("automatic")) R.string.susfs_import_auto_on else R.string.susfs_import_auto_off))
                Text(stringResource(R.string.susfs_import_warning))
            } }, confirmButton = { TextButton(enabled = state.status.protocol == "compatible" && !state.busy,
                onClick = { vm.save(text); draft = null }) { Text(stringResource(R.string.susfs_save)) } },
            dismissButton = { TextButton(onClick = { draft = null }) { Text(stringResource(R.string.susfs_cancel)) } })
    }
}

@Composable
private fun SusfsNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val wallpaper = LocalFolkWallpaperTokens.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        susfsPages.forEachIndexed { index, title ->
            val source = remember { MutableInteractionSource() }
            val requester = remember { BringIntoViewRequester() }
            val pressed by source.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) FolkMotion.PressedScale else 1f,
                animationSpec = if (pressed) FolkMotion.PressDown else FolkMotion.PressScale, label = "susfsTabPress")
            val active = selected == index
            LaunchedEffect(active) { if (active) requester.bringIntoView() }
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                .bringIntoViewRequester(requester)
                .background(if (active) wallpaper?.raised?.fill ?: MaterialTheme.colorScheme.secondaryContainer
                    else wallpaper?.group?.fill ?: MaterialTheme.colorScheme.surfaceContainerLow, FolkShape.CornerFull)
                .selectable(active, interactionSource = source, indication = null, role = Role.Tab) {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onSelect(index)
                }.heightIn(min = 48.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(title), style = MaterialTheme.typography.labelLarge,
                    color = if (active) wallpaper?.raised?.contentColor ?: MaterialTheme.colorScheme.onSecondaryContainer
                        else wallpaper?.group?.supportingColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SusfsPage(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 840.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}

@Composable
internal fun susfsDisabledReason(status: SusfsStatus, supported: Boolean): String? = when {
    status.protocol == "root_unavailable" -> stringResource(R.string.susfs_root)
    status.protocol == "unknown_protocol" -> stringResource(R.string.susfs_readonly)
    status.protocol == "not_detected" -> stringResource(R.string.susfs_absent)
    status.protocol != "compatible" -> stringResource(R.string.susfs_probe)
    status.error != null -> stringResource(R.string.susfs_config_invalid)
    !supported -> stringResource(R.string.susfs_unsupported)
    else -> null
}

@Composable
private fun SusfsNotice(state: SusfsUiState) {
    val reason = susfsDisabledReason(state.status, true)
    if (reason != null || state.message != null || state.savedNotice) {
        SegmentedColumn {
            reason?.let { item { SegmentedListItem(headlineContent = { Text(it) },
                supportingContent = state.status.error?.let { detail -> ({ Text(detail) }) },
                leadingContent = { Icon(Icons.Rounded.Info, null) }) } }
            state.message?.let { item { SegmentedListItem(headlineContent = { Text(it, color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(stringResource(R.string.susfs_error_hint)) }) } }
            if (state.savedNotice) item { SegmentedListItem(headlineContent = { Text(stringResource(R.string.susfs_save_success)) },
                supportingContent = { Text(stringResource(R.string.susfs_save_success_hint)) }) }
        }
    }
}

@Composable
private fun SusfsOverview(state: SusfsUiState, config: JSONObject?, writable: Boolean, vm: SusfsViewModel,
    import: () -> Unit, export: () -> Unit) {
    val status = state.status
    SegmentedColumn(title = stringResource(R.string.susfs_overview)) {
        item { SegmentedListItem(headlineContent = { Text(stringResource(when (status.protocol) {
            "compatible" -> R.string.susfs_compatible
            "not_detected" -> R.string.susfs_absent
            "unknown_protocol" -> R.string.susfs_readonly
            "root_unavailable" -> R.string.susfs_root
            else -> R.string.susfs_probe
        })) }, supportingContent = { Text(listOf(status.version, status.implementation, status.variant).filter(String::isNotEmpty).joinToString(" · ")) },
            leadingContent = { Icon(Icons.Rounded.VisibilityOff, null) }) }
        item { SegmentedListItem(headlineContent = { Text(susfsManagementLabel(status.management)) },
            supportingContent = { Text(stringResource(R.string.susfs_ack_hint)) }) }
        item { SegmentedListItem(headlineContent = { Text(stringResource(R.string.susfs_saved_counts,
            config?.optJSONArray("rules")?.length() ?: 0)) }, supportingContent = { Text(stringResource(R.string.susfs_ownership)) }) }
        item { SegmentedSwitchItem(title = stringResource(R.string.susfs_automatic),
            summary = stringResource(R.string.susfs_automatic_hint), checked = status.automatic, enabled = writable,
            onCheckedChange = { value -> vm.change { it.put("automatic", value) } }) }
        item { SegmentedListItem(enabled = writable && !Natives.isSafeMode, onClick = vm::apply,
            headlineContent = { Text(stringResource(R.string.susfs_apply)) },
            supportingContent = { Text(stringResource(if (Natives.isSafeMode) R.string.susfs_safe_mode else R.string.susfs_apply_hint)) },
            leadingContent = { Icon(Icons.Rounded.PlayArrow, null) }) }
    }
    state.report?.let { report ->
        SegmentedColumn(title = stringResource(R.string.susfs_last_report)) {
            item { SegmentedListItem(headlineContent = { Text(susfsManagementLabel(report.status)) },
                supportingContent = { Text(stringResource(R.string.susfs_report_hint)) }) }
            report.applied.forEach { key -> item(key = "ok:$key") { SegmentedListItem(
                headlineContent = { Text(susfsOperationName(key)) }, supportingContent = { Text(stringResource(R.string.susfs_acknowledged)) },
                leadingContent = { Icon(Icons.Rounded.CheckCircle, null) }) } }
            report.failures.forEach { (key, error) -> item(key = "error:$key") { SegmentedListItem(
                headlineContent = { Text(susfsOperationName(key)) }, supportingContent = { Text(error, color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) }) } }
            report.pendingReboot.forEach { key -> item(key = "pending:$key") { SegmentedListItem(
                headlineContent = { Text(susfsOperationName(key)) }, supportingContent = { Text(stringResource(R.string.susfs_pending)) },
                leadingContent = { Icon(Icons.Rounded.Schedule, null) }) } }
        }
    }
    SegmentedColumn(title = stringResource(R.string.susfs_config)) {
        item { SegmentedListItem(enabled = status.protocol == "compatible" && !state.busy, onClick = import,
            headlineContent = { Text(stringResource(R.string.susfs_import)) }, leadingContent = { Icon(Icons.Rounded.FileOpen, null) }) }
        item { SegmentedListItem(enabled = config != null && !state.busy, onClick = export,
            headlineContent = { Text(stringResource(R.string.susfs_export)) }, leadingContent = { Icon(Icons.Rounded.SaveAlt, null) }) }
    }
    SegmentedColumn(title = stringResource(R.string.susfs_capabilities)) {
        status.features.forEach { feature -> item(key = feature) { SegmentedListItem(headlineContent = { Text(feature.removePrefix("CONFIG_KSU_SUSFS_")) }) } }
        if (status.features.isEmpty()) item { SegmentedListItem(headlineContent = { Text(stringResource(R.string.susfs_unsupported)) }) }
    }
}

@Composable
private fun susfsOperationName(key: String): String {
    val prefix = key.substringBefore(':')
    val name = stringResource(when (prefix) {
        "path" -> R.string.susfs_path_rules
        "map" -> R.string.susfs_map_rules
        "kstat" -> R.string.susfs_kstat_rules
        "redirect" -> R.string.susfs_redirect_rules
        "module" -> R.string.susfs_modules
        "prefix" -> R.string.susfs_prefixes
        "uname" -> R.string.susfs_uname
        "cmdline" -> R.string.susfs_cmdline
        "hide_mounts" -> R.string.susfs_hide_mounts
        "logging" -> R.string.susfs_logging
        "avc_spoof" -> R.string.susfs_avc
        else -> R.string.susfs_rules
    })
    return if (':' in key) "$name · ${key.substringAfter(':')}" else name
}
