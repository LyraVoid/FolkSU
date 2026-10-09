package me.weishu.kernelsu.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Complete the copy before callers publish a new filename or remove the previous resource. */
internal object MediaFiles {
    suspend fun import(context: Context, uri: Uri, directory: File, stem: String, fallback: String): File =
        withContext(Dispatchers.IO) {
            check(directory.isDirectory || directory.mkdirs())
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: fallback
            val target = File.createTempFile("${stem}_", ".$extension", directory)
            try {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                require(target.length() > 0) { "Empty media file" }
                target
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        }
}
