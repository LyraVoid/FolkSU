package me.weishu.kernelsu.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.R
import me.weishu.kernelsu.wallpaper.surface.SurfaceId
import me.weishu.kernelsu.wallpaper.surface.surfaceAspect

/**
 * Shared image-upload flow for a card surface, used by both the wallpaper settings page and the
 * long-press options dialog.
 *
 * Set [request] to the surface being edited to start it: the photo picker opens, then a small
 * dialog lets the user crop the picture to the card's exact bounds or use it as is. Cropping runs
 * in-app with a frame locked to [surfaceAspect], so whatever is framed is exactly what the card
 * shows. [onPicked] receives the final image; the caller must keep [request] set until then.
 */
@Composable
fun SurfaceImagePickerHost(
    request: SurfaceId?,
    onDismissRequest: () -> Unit,
    onPicked: (SurfaceId, Uri) -> Unit,
) {
    var picked by rememberSaveable(request?.value) { mutableStateOf<Uri?>(null) }
    var cropping by rememberSaveable(request?.value) { mutableStateOf(false) }
    var launched by rememberSaveable(request?.value) { mutableStateOf(false) }
    val aspect = remember(request) { request?.let { surfaceAspect(it) } ?: 1f }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (request != null) {
            if (uri != null) picked = uri else onDismissRequest()
        }
    }

    val id = request ?: return
    LaunchedEffect(id) {
        if (!launched && picked == null) {
            launched = true
            pickLauncher.launch("image/*")
        }
    }

    val target = picked ?: return
    if (cropping) {
        SurfaceCropDialog(
            source = target,
            aspect = aspect,
            onDismiss = { cropping = false },
            onCropped = { onPicked(id, it) },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.wallpaper_crop_title)) },
        text = { Text(stringResource(R.string.wallpaper_crop_message)) },
        dismissButton = {
            TextButton(onClick = { onPicked(id, target) }) {
                Text(stringResource(R.string.wallpaper_crop_direct))
            }
        },
        confirmButton = {
            TextButton(onClick = { cropping = true }) {
                Text(stringResource(R.string.wallpaper_crop_confirm))
            }
        },
    )
}
