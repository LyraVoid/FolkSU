package me.weishu.kernelsu.ui.component.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.ExpressiveDialog

@Composable
fun DownloadDialog(
    show: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DownloadDialogMaterial(show, onConfirm, onDismiss)
}

@Composable
private fun DownloadDialogMaterial(
    show: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    var url by remember { mutableStateOf("") }
    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_dialog_title)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text(stringResource(R.string.download_dialog_msg)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValidUrl(url.trim()),
                onClick = { onConfirm(url.trim()) }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private fun isValidUrl(url: String): Boolean {
    if (url.isEmpty()) return false
    val uri = url.toUri()
    return uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrEmpty() &&
        !uri.path.isNullOrEmpty()
}
