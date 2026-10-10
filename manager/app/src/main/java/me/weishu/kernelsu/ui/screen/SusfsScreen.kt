package me.weishu.kernelsu.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.SusfsViewModel
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun susfsManagementLabel(status: String): String = stringResource(when (status) {
    "applied" -> R.string.susfs_applied
    "failed" -> R.string.susfs_failed
    "pending_reboot" -> R.string.susfs_pending
    else -> R.string.susfs_saved
})

private data class SusfsEditor(val title: Int, val fields: List<Pair<Int, String>>, val save: (List<String>) -> Unit)

private fun ruleEditor(
    kind: String,
    existing: JSONObject?,
    status: me.weishu.kernelsu.data.susfs.SusfsStatus,
    save: (JSONObject) -> Unit,
): SusfsEditor {
    val fields = mutableListOf(R.string.susfs_path to existing?.optString("path").orEmpty())
    if (kind == "redirect") fields.addAll(listOf(
        R.string.susfs_destination to existing?.optString("destination").orEmpty(),
        R.string.susfs_uid to (existing?.optInt("uid_scheme") ?: 0).toString(),
    ))
    if (kind == "path") fields.add(R.string.susfs_loop to (existing?.optBoolean("looping") ?: false).toString())
    if (kind == "kstat") fields.add(R.string.susfs_clone to (existing?.optBoolean("full_clone") ?: false).toString())
    return SusfsEditor(if (existing == null) R.string.susfs_add_rule else R.string.susfs_edit_rule, fields) { values ->
        val rule = JSONObject().put("kind", kind).put("path", values[0])
        when (kind) {
            "path" -> rule.put("looping", values[1].toBooleanStrict())
            "kstat" -> {
                val clone = values[1].toBooleanStrict()
                require(!clone || status.implementation != "lkm" || status.proc("susfs_kstat")) { "Full clone requires proc control" }
                rule.put("full_clone", clone)
            }
            "redirect" -> {
                val uid = values[2].toInt()
                require(status.implementation != "lkm" || uid in 0..2) { "LKM supports UID schemes 0-2" }
                rule.put("destination", values[1]).put("uid_scheme", uid)
            }
        }
        save(rule)
    }
}

@Composable
fun SusfsScreen() {
    val vm = viewModel<SusfsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var editor by remember { mutableStateOf<SusfsEditor?>(null) }
    var rulePicker by remember { mutableStateOf(false) }
    val status = state.status
    val writable = status.writable && !state.busy && state.config != null
    val config = remember(state.config) { state.config?.let(::JSONObject) }
    val settings = config?.optJSONObject("settings")
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        // InputStream.readNBytes is not available on every supported Android API.
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(4096)
                        while (buffer.size() <= 128 * 1024) {
                            val count = it.read(chunk, 0, minOf(chunk.size, 128 * 1024 + 1 - buffer.size()))
                            if (count < 0) break
                            buffer.write(chunk, 0, count)
                        }
                        val bytes = buffer.toByteArray()
                        require(bytes.size <= 128 * 1024) { "Configuration exceeds 128 KiB" }
                        bytes.toString(Charsets.UTF_8)
                    } ?: error("Cannot read configuration")
                }
                vm.save(text)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { vm.message(e.message ?: "Import failed") }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = state.config
        if (uri != null && text != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
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
            actions = { TextButton(onClick = vm::refresh) { Text(stringResource(R.string.susfs_refresh)) } },
            colors = expressiveTopAppBarColors(),
            scrollBehavior = scrollBehavior,
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding).wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 840.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SegmentedColumn(title = stringResource(R.string.susfs_overview)) {
                item {
                    SegmentedListItem(headlineContent = { Text(stringResource(when (status.protocol) {
                        "compatible" -> R.string.susfs_compatible
                        "not_detected" -> R.string.susfs_absent
                        "unknown_protocol" -> R.string.susfs_readonly
                        "root_unavailable" -> R.string.susfs_root
                        else -> R.string.susfs_probe
                    })) }, supportingContent = { Text(listOf(status.version, status.implementation, status.variant).filter { it.isNotEmpty() }.joinToString(" · ")) })
                }
                item { SegmentedListItem(headlineContent = { Text(susfsManagementLabel(status.management)) }, supportingContent = { Text(stringResource(R.string.susfs_ownership)) }) }
                item { SegmentedListItem(checked = status.automatic, enabled = writable, onCheckedChange = { value -> vm.change { it.put("automatic", value) } }, headlineContent = { Text(stringResource(R.string.susfs_automatic)) }, supportingContent = { Text(stringResource(R.string.susfs_automatic_hint)) }, trailingContent = { Switch(status.automatic, null, enabled = writable) }) }
                item { SegmentedListItem(enabled = writable && !Natives.isSafeMode, onClick = vm::apply, headlineContent = { Text(stringResource(R.string.susfs_apply)) }, supportingContent = { Text(stringResource(R.string.susfs_apply_hint)) }) }
            }
            status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            SegmentedColumn(title = stringResource(R.string.susfs_capabilities)) {
                status.features.forEach { feature -> item(key = feature) { SegmentedListItem(headlineContent = { Text(feature.removePrefix("CONFIG_KSU_SUSFS_")) }) } }
                if (status.features.isEmpty()) item { SegmentedListItem(headlineContent = { Text(stringResource(R.string.susfs_unsupported)) }) }
            }
            SegmentedColumn(title = stringResource(R.string.susfs_settings)) {
                listOf(
                    Triple("hide_mounts", R.string.susfs_hide_mounts, status.supports("SUS_MOUNT")),
                    Triple("logging", R.string.susfs_logging, status.supports("ENABLE_LOG")),
                    Triple("avc_spoof", R.string.susfs_avc, status.proc("susfs_avc_spoof") || (status.implementation != "lkm" && status.supports("AVC_LOG_SPOOFING"))),
                ).forEach { (key, title, supported) -> item(key = key) {
                    val checked = settings?.optBoolean(key) == true
                    SegmentedListItem(checked = checked, enabled = writable && supported,
                        onCheckedChange = { value -> vm.change { it.getJSONObject("settings").put(key, value) } },
                        headlineContent = { Text(stringResource(title)) },
                        supportingContent = if (!supported) ({ Text(stringResource(R.string.susfs_unsupported)) }) else null,
                        trailingContent = { Switch(checked, null, enabled = writable && supported) })
                } }
                item { SegmentedListItem(enabled = writable && status.supports("SPOOF_UNAME"), onClick = {
                    val uname = settings?.optJSONObject("uname")
                    editor = SusfsEditor(R.string.susfs_uname, listOf(R.string.susfs_release to uname?.optString("release").orEmpty(), R.string.susfs_version to uname?.optString("version").orEmpty())) { values -> vm.change { it.getJSONObject("settings").put("uname", JSONObject().put("release", values[0]).put("version", values[1])) } }
                }, headlineContent = { Text(stringResource(R.string.susfs_uname)) }) }
                item { SegmentedListItem(enabled = writable && status.supports("SPOOF_CMDLINE_OR_BOOTCONFIG"), onClick = {
                    editor = SusfsEditor(R.string.susfs_cmdline, listOf(R.string.susfs_cmdline to settings?.optString("cmdline").orEmpty())) { values -> vm.change { it.getJSONObject("settings").put("cmdline", values[0]) } }
                }, headlineContent = { Text(stringResource(R.string.susfs_cmdline)) }) }
            }
            SegmentedColumn(title = stringResource(R.string.susfs_rules)) {
                item { SegmentedListItem(enabled = writable, onClick = { rulePicker = true }, headlineContent = { Text(stringResource(R.string.susfs_add_rule)) }, supportingContent = { Text(stringResource(R.string.susfs_kstat_hint)) }) }
                val rules = config?.optJSONArray("rules") ?: JSONArray()
                for (index in 0 until rules.length()) item(key = index) {
                    val rule = rules.getJSONObject(index)
                    SegmentedListItem(enabled = writable, onClick = {
                        editor = ruleEditor(rule.getString("kind"), rule, status) { replacement ->
                            vm.change { it.getJSONArray("rules").put(index, replacement) }
                        }
                    }, headlineContent = { Text("${rule.getString("kind")} · ${rule.getString("path")}") }, supportingContent = { Text(rule.toString()) }, trailingContent = {
                        TextButton(enabled = writable, onClick = { vm.change { it.getJSONArray("rules").remove(index) } }) { Text(stringResource(R.string.susfs_remove)) }
                    })
                }
                if (rules.length() == 0) item { SegmentedListItem(headlineContent = { Text(stringResource(R.string.susfs_empty)) }) }
            }
            Text(stringResource(R.string.susfs_removal_hint), style = MaterialTheme.typography.bodySmall)
            if (status.implementation == "lkm") SegmentedColumn(title = stringResource(R.string.susfs_lkm)) {
                listOf(Triple("hidden_modules", R.string.susfs_modules, "susfs_hide_modules"), Triple("mount_prefixes", R.string.susfs_prefixes, "susfs_hide_mounts")).forEach { (key, title, node) -> item(key = key) {
                    SegmentedListItem(enabled = writable && status.proc(node), onClick = {
                        val array = config?.getJSONObject("lkm")?.getJSONArray(key) ?: JSONArray()
                        editor = SusfsEditor(title, listOf(R.string.susfs_lines to List(array.length()) { array.getString(it) }.joinToString("\n"))) { values -> vm.change { it.getJSONObject("lkm").put(key, JSONArray(values[0].lines().filter(String::isNotBlank))) } }
                    }, headlineContent = { Text(stringResource(title)) }, supportingContent = { Text(stringResource(R.string.susfs_proc_hint)) })
                } }
            }
            SegmentedColumn(title = stringResource(R.string.susfs_config)) {
                item { SegmentedListItem(enabled = status.protocol == "compatible" && !state.busy, onClick = { import.launch(arrayOf("application/json", "text/plain")) }, headlineContent = { Text(stringResource(R.string.susfs_import)) }) }
                item { SegmentedListItem(enabled = state.config != null && !state.busy, onClick = { export.launch("folksu-susfs.json") }, headlineContent = { Text(stringResource(R.string.susfs_export)) }) }
            }
        }
    }
    if (rulePicker) AlertDialog(onDismissRequest = { rulePicker = false }, title = { Text(stringResource(R.string.susfs_add_rule)) }, text = {
        Column {
            listOf("path" to "SUS_PATH", "kstat" to "SUS_KSTAT", "redirect" to "OPEN_REDIRECT", "map" to "SUS_MAP").forEach { (kind, capability) ->
                TextButton(enabled = status.supports(capability), onClick = {
                    rulePicker = false
                    editor = ruleEditor(kind, null, status) { rule ->
                        vm.change { it.getJSONArray("rules").put(rule) }
                    }
                }) { Text(kind) }
            }
        }
    }, confirmButton = { TextButton(onClick = { rulePicker = false }) { Text(stringResource(R.string.susfs_cancel)) } })
    editor?.let { value -> SusfsEditDialog(value, { editor = null }, { values ->
        try { value.save(values); editor = null } catch (e: Exception) { vm.message(e.message ?: "Invalid field") }
    }) }
}

@Composable
private fun SusfsEditDialog(editor: SusfsEditor, dismiss: () -> Unit, save: (List<String>) -> Unit) {
    var values by remember(editor) { mutableStateOf(editor.fields.map { it.second }) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(editor.title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            editor.fields.forEachIndexed { index, field ->
                if (field.first == R.string.susfs_clone || field.first == R.string.susfs_loop) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(field.first), Modifier.weight(1f))
                        Switch(values[index].toBoolean(), { checked -> values = values.toMutableList().also { it[index] = checked.toString() } })
                    }
                } else OutlinedTextField(values[index], { text -> values = values.toMutableList().also { it[index] = text } }, label = { Text(stringResource(field.first)) })
            }
        }
    }, confirmButton = { TextButton(onClick = { save(values) }) { Text(stringResource(R.string.susfs_save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.susfs_cancel)) } })
}
