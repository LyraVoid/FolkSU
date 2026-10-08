package me.weishu.kernelsu.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * One crop request: the picked image plus the frame the platform cropper should lock to.
 *
 * [aspectX]/[aspectY] of 0 keep the crop free-form; [outputSize] of 0 keeps the source resolution.
 */
data class CropRequest(
    val uri: Uri,
    val aspectX: Int = 0,
    val aspectY: Int = 0,
    val outputSize: Int = 0,
)

/**
 * Thin wrapper over the platform `com.android.camera.action.CROP` intent.
 *
 * The picked image is copied into the app cache and shared through the app's FileProvider, then
 * the cropped result is written to a cache file and returned as a content URI. Devices without a
 * cropper raise `ActivityNotFoundException` from [ManagedActivityResultLauncher.launch]; callers
 * should catch it and fall back to using the original image.
 */
private class SystemCropContract(
    private val appContext: Context,
    private val cacheName: String,
) : ActivityResultContract<CropRequest, Uri?>() {

    private fun outputFile(): File = File(File(appContext.cacheDir, cacheName), "output.jpg")

    override fun createIntent(context: Context, input: CropRequest): Intent {
        val root = File(appContext.cacheDir, cacheName).apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val extension = when {
            context.contentResolver.getType(input.uri)?.contains("png", true) == true -> ".png"
            context.contentResolver.getType(input.uri)?.contains("webp", true) == true -> ".webp"
            else -> ".jpg"
        }
        val source = File(root, "input$extension")
        context.contentResolver.openInputStream(input.uri)?.use { stream ->
            source.outputStream().use { stream.copyTo(it, DEFAULT_BUFFER_SIZE) }
        }
        val authority = "${appContext.packageName}.fileprovider"
        val sourceUri = FileProvider.getUriForFile(context, authority, source)
        val outputUri = FileProvider.getUriForFile(context, authority, outputFile())
        return Intent("com.android.camera.action.CROP").apply {
            setDataAndType(sourceUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra("crop", "true")
            putExtra("noFaceDetection", true)
            if (input.aspectX > 0 && input.aspectY > 0) {
                putExtra("aspectX", input.aspectX)
                putExtra("aspectY", input.aspectY)
            }
            if (input.outputSize > 0) {
                putExtra("outputX", input.outputSize)
                putExtra("outputY", input.outputSize)
            }
            putExtra("return-data", false)
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        val output = outputFile()
        if (!output.exists() || output.length() == 0L) return null
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", output)
    }
}

@Composable
fun rememberSystemCropLauncher(
    cacheName: String = "wallpaper_crop_cache",
    onCropped: (Uri) -> Unit,
): ManagedActivityResultLauncher<CropRequest, Uri?> {
    val appContext = LocalContext.current.applicationContext
    val contract = remember(cacheName) {
        SystemCropContract(appContext, cacheName)
    }
    return rememberLauncherForActivityResult(contract) { result ->
        if (result != null) onCropped(result)
    }
}
