package me.weishu.kernelsu.ui.component

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import me.weishu.kernelsu.ui.theme.ContinuousCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import android.app.Activity
import android.os.Build
import me.weishu.kernelsu.R
import me.weishu.kernelsu.wallpaper.PreparedTheme
import me.weishu.kernelsu.wallpaper.ThemeImportService
import me.weishu.kernelsu.wallpaper.ThemeMetadata

/** Shared picker/store entry point; prepared resources belong to this UI lifetime. */
@Composable
fun rememberThemeImportRequest(onResult: (Result<Unit>) -> Unit): (Uri) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PreparedTheme?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (!busy) pending?.close() } }
    pending?.let { theme ->
        ThemeImportDialog(
            theme = theme,
            busy = busy,
            onDismiss = { if (!busy) { pending = null; theme.close() } },
            onApply = {
                if (!busy) scope.launch {
                    busy = true
                    try {
                        val result = ThemeImportService.apply(context.applicationContext, theme)
                        onResult(result)
                        if (result.isSuccess && Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
                    } finally {
                        pending = null
                        theme.close()
                        busy = false
                    }
                }
            },
        )
    }
    return { uri ->
        if (!busy) scope.launch {
            busy = true
            try {
                ThemeImportService.prepare(context.applicationContext, uri).onSuccess {
                    pending?.close()
                    pending = it
                }.onFailure { onResult(Result.failure(it)) }
            } finally { busy = false }
        }
    }
}

@Composable
private fun ThemeImportDialog(theme: PreparedTheme, busy: Boolean, onDismiss: () -> Unit, onApply: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fpt_import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                theme.previewFile?.let {
                    AsyncImage(model = it, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(ContinuousCornerShape(20.dp)))
                }
                ThemeMetadataSummary(theme.metadata)
                Text(stringResource(R.string.fpt_resources, theme.resourceCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onApply, enabled = !busy) { Text(stringResource(R.string.theme_store_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.theme_store_cancel)) } },
    )
}

@Composable
fun ThemeMetadataSummary(metadata: ThemeMetadata, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (metadata.name.isNotBlank()) {
            Text(metadata.name, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val typeLabel = when (metadata.type) {
                "phone" -> stringResource(R.string.theme_type_phone)
                "tablet" -> stringResource(R.string.theme_type_tablet)
                else -> metadata.type
            }
            Surface(shape = ContinuousCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (metadata.type == "phone" || metadata.type == "tablet") {
                        Icon(if (metadata.type == "tablet") Icons.Default.TabletAndroid else Icons.Default.PhoneAndroid,
                            contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Text(typeLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (metadata.version.isNotBlank()) {
                Text(stringResource(R.string.theme_store_version, metadata.version),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (metadata.author.isNotBlank()) {
            ThemeMetadataRow(stringResource(R.string.fpt_author), metadata.author)
        }
        if (metadata.description.isNotBlank()) {
            Text(metadata.description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThemeMetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ThemeMetadataEditor(initial: ThemeMetadata, onDismiss: () -> Unit, onExport: (ThemeMetadata) -> Unit) {
    var metadata by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallpaper_export)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(metadata.name, { metadata = metadata.copy(name = it) },
                    label = { Text(stringResource(R.string.fpt_name)) }, singleLine = true)
                ThemeDeviceTypeSelector(metadata.type) { metadata = metadata.copy(type = it) }
                OutlinedTextField(metadata.version, { metadata = metadata.copy(version = it) },
                    label = { Text(stringResource(R.string.fpt_version)) }, singleLine = true)
                OutlinedTextField(metadata.author, { metadata = metadata.copy(author = it) },
                    label = { Text(stringResource(R.string.fpt_author)) }, singleLine = true)
                OutlinedTextField(metadata.description, { metadata = metadata.copy(description = it) },
                    label = { Text(stringResource(R.string.fpt_description)) })
            }
        },
        confirmButton = { TextButton(onClick = { onExport(metadata) }, enabled = metadata.name.isNotBlank()) {
            Text(stringResource(R.string.wallpaper_export))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.theme_store_cancel)) } },
    )
}

@Composable
private fun ThemeDeviceTypeSelector(type: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        listOf(
            Triple("phone", Icons.Default.PhoneAndroid, R.string.theme_type_phone),
            Triple("tablet", Icons.Default.TabletAndroid, R.string.theme_type_tablet),
        ).forEachIndexed { index, (value, icon, label) ->
            val isSelected = type == value
            Surface(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f).height(56.dp).semantics {
                    role = Role.RadioButton
                    selected = isSelected
                },
                shape = if (index == 0) ContinuousCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    else ContinuousCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MetadataPreview() { ThemeMetadataSummary(ThemeMetadata("Sample", author = "Author")) }

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MetadataDarkPreview() { ThemeMetadataSummary(ThemeMetadata("Sample", type = "tablet")) }

@Preview
@Composable
private fun EditorPreview() { ThemeMetadataEditor(ThemeMetadata("Sample"), {}, {}) }
