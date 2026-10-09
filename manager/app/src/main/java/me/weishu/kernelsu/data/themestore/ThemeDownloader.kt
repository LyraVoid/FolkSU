package me.weishu.kernelsu.data.themestore

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Downloads online themes into `filesDir/themes/<author>/<name>/`.
 *
 * The `.fpt` payload counts for 70% of the progress and the `Theme.webp` preview for the remaining
 * 30%; the preview failing is not fatal. Downloads can be paused, resumed (HTTP `Range`) and
 * cancelled, and every finished theme writes a `theme_meta.json` so it can be listed offline.
 *
 * Ported from FolkPatch's `ThemeDownloader`, minus its external-storage backup: FolkSU keeps themes
 * inside its own private storage.
 */
class ThemeDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(DOWNLOAD_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(DOWNLOAD_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val semaphore = Semaphore(MAX_CONCURRENT)

    private val downloadTasks = ConcurrentHashMap<String, DownloadTask>()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    fun getThemesDir(): File = File(context.filesDir, THEMES_DIR_NAME)

    fun getThemePath(author: String, name: String): File =
        File(getThemesDir(), "${sanitizeFilename(author)}/${sanitizeFilename(name)}")

    fun getThemeFilePath(author: String, name: String): File =
        File(getThemePath(author, name), "${sanitizeFilename(name)}.fpt")

    fun getPreviewImagePath(author: String, name: String): File =
        File(getThemePath(author, name), PREVIEW_FILE_NAME)

    fun isSafeDownloadUrl(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("http://")

    fun getProgress(themeId: String): DownloadProgress? = _downloadProgress.value[themeId]

    fun pauseDownload(themeId: String) {
        downloadTasks[themeId]?.isPaused = true
    }

    suspend fun cancelDownload(themeId: String) {
        val task = downloadTasks.remove(themeId)
        // Signal first so the running writer stops at its next chunk, then drop the partial files.
        task?.isCancelled = true
        if (task != null) {
            val dir = getThemePath(task.theme.author, task.theme.name)
            if (dir.exists()) dir.deleteRecursively()
        }
        _downloadProgress.update { it - themeId }
    }

    fun downloadTheme(theme: RemoteTheme): Flow<DownloadProgress> = flow {
        val themeId = theme.id
        val existing = downloadTasks[themeId]
        if (existing != null && !existing.isPaused) {
            emit(DownloadProgress(themeId, 0f, 0f, 0f, DownloadStatus.PENDING, "Already downloading"))
            return@flow
        }

        semaphore.acquire()
        try {
            if (!isSafeDownloadUrl(theme.downloadUrl)) {
                emit(DownloadProgress(themeId, 0f, 0f, 0f, DownloadStatus.FAILED, "Invalid download URL"))
                return@flow
            }

            val task = existing ?: DownloadTask(theme).also { downloadTasks[themeId] = it }
            task.isPaused = false

            updateProgress(themeId, 0f, 0f, 0f, DownloadStatus.DOWNLOADING)
            emit(_downloadProgress.value.getValue(themeId))

            val themeResult = downloadFileWithRetry(
                url = theme.downloadUrl,
                file = getThemeFilePath(theme.author, theme.name),
                taskId = themeId,
                isThemeFile = true,
            ) { progress ->
                updateProgress(themeId, progress * 0.7f, 0f, progress * 0.7f, DownloadStatus.DOWNLOADING)
            }

            if (themeResult.paused) {
                updateProgress(themeId, 0f, 0f, 0f, DownloadStatus.PAUSED)
                emit(_downloadProgress.value.getValue(themeId))
                return@flow
            }
            if (!themeResult.success) {
                updateProgress(themeId, 0f, 0f, 0f, DownloadStatus.FAILED, themeResult.error)
                emit(_downloadProgress.value.getValue(themeId))
                // Drop the task so a later retry starts clean instead of reporting "already downloading".
                downloadTasks.remove(themeId)
                return@flow
            }

            if (theme.previewUrl.isNotBlank() && isSafeDownloadUrl(theme.previewUrl)) {
                val previewResult = downloadFileWithRetry(
                    url = theme.previewUrl,
                    file = getPreviewImagePath(theme.author, theme.name),
                    taskId = themeId,
                    isThemeFile = false,
                ) { progress ->
                    updateProgress(themeId, 1f, progress, 0.7f + progress * 0.3f, DownloadStatus.DOWNLOADING)
                }
                if (previewResult.paused) {
                    updateProgress(themeId, 1f, 0f, 0.7f, DownloadStatus.PAUSED)
                    emit(_downloadProgress.value.getValue(themeId))
                    return@flow
                }
                // A failed preview is not fatal; the theme file is what matters.
            }

            saveThemeMetadata(theme)
            updateProgress(themeId, 1f, 1f, 1f, DownloadStatus.COMPLETED)
            emit(_downloadProgress.value.getValue(themeId))
            downloadTasks.remove(themeId)
        } catch (e: Exception) {
            updateProgress(theme.id, 0f, 0f, 0f, DownloadStatus.FAILED, describeException(e))
            emit(_downloadProgress.value.getValue(theme.id))
            downloadTasks.remove(theme.id)
        } finally {
            semaphore.release()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadFileWithRetry(
        url: String,
        file: File,
        taskId: String,
        isThemeFile: Boolean,
        onProgress: (Float) -> Unit,
    ): DownloadFileResult {
        if (url.isBlank()) return DownloadFileResult(false, "Empty url")
        // Download to a temp sibling first: a killed process then leaves a `.tmp` that the local
        // scanner ignores, instead of a half-written `.fpt` that looks installed.
        val temp = File(file.parentFile, file.name + TEMP_SUFFIX)
        var lastError: String? = null
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                val expected = downloadOnce(url, temp, taskId, onProgress)
                validateDownloadedFile(temp, isThemeFile, expected)
                commitDownload(temp, file)
                return DownloadFileResult(true)
            } catch (e: DownloadPausedException) {
                // Keep the temp file so a resume can continue from its length.
                return DownloadFileResult(false, paused = true)
            } catch (e: Exception) {
                lastError = describeException(e)
                attempt++
                if (downloadTasks[taskId]?.isCancelled == true) {
                    temp.delete()
                    return DownloadFileResult(false, "Download cancelled")
                }
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        temp.delete()
        return DownloadFileResult(false, lastError)
    }

    private fun commitDownload(temp: File, target: File) {
        ensureDirectory(target.parentFile ?: throw IOException("No parent directory for $target"))
        if (target.exists()) target.delete()
        if (temp.renameTo(target)) return
        temp.copyTo(target, overwrite = true)
        temp.delete()
    }

    private fun downloadOnce(
        url: String,
        file: File,
        taskId: String,
        onProgress: (Float) -> Unit,
    ): Long {
        ensureDirectory(file.parentFile ?: throw IOException("No parent directory for $file"))
        if (file.isDirectory) file.deleteRecursively()

        val downloadedBytes = file.length()
        val requestBuilder = Request.Builder().url(url)
        if (downloadedBytes > 0) {
            requestBuilder.header("Range", "bytes=$downloadedBytes-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val resume = response.code == 206 && downloadedBytes > 0
            val contentLength = response.body.contentLength()
            val total = if (contentLength > 0) {
                if (resume) downloadedBytes + contentLength else contentLength
            } else {
                -1L
            }

            FileOutputStream(file, resume).use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = if (resume) downloadedBytes else 0L
                    while (true) {
                        if (downloadTasks[taskId]?.isCancelled == true) throw IOException("Download cancelled")
                        if (downloadTasks[taskId]?.isPaused == true) throw DownloadPausedException()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        val progress =
                            if (total > 0) written.toFloat() / total else estimateUnknownProgress(written)
                        onProgress(progress.coerceIn(0f, 1f))
                    }
                }
            }
            return total
        }
    }

    private fun validateDownloadedFile(file: File, isThemeFile: Boolean, expectedSize: Long) {
        if (!file.exists() || file.length() == 0L) throw IOException("Downloaded file is empty")
        if (expectedSize > 0 && file.length() != expectedSize) throw IOException("Size mismatch")
        if (isThemeFile) {
            if (file.length() < MIN_THEME_SIZE) throw IOException("Theme file too small")
            val header = ByteArray(HEADER_SIZE)
            file.inputStream().use { it.read(header) }
            val first = header[0].toInt()
            if (first == '<'.code || first == '{'.code) throw IOException("Invalid theme file")
            if (header.all { it == 0.toByte() }) throw IOException("Incomplete theme file")
        }
    }

    private fun estimateUnknownProgress(downloaded: Long): Float =
        (1.0 - 1.0 / (1.0 + downloaded / 256.0 / 1024.0)).toFloat().coerceIn(0f, 0.99f)

    private fun updateProgress(
        themeId: String,
        fileProgress: Float,
        imageProgress: Float,
        overallProgress: Float,
        status: DownloadStatus,
        errorMessage: String? = null,
    ) {
        _downloadProgress.update { current ->
            current + (themeId to DownloadProgress(
                themeId, fileProgress, imageProgress, overallProgress, status, errorMessage,
            ))
        }
    }
    private fun saveThemeMetadata(theme: RemoteTheme) {
        val json = JSONObject().apply {
            put("id", theme.id)
            put("name", theme.name)
            put("author", theme.author)
            put("description", theme.description)
            put("version", theme.version)
            put("type", theme.type)
            put("source", theme.source)
            put("previewUrl", theme.previewUrl)
            put("downloadUrl", theme.downloadUrl)
        }
        val metaFile = File(getThemePath(theme.author, theme.name), META_FILE_NAME)
        ensureDirectory(metaFile.parentFile ?: return)
        metaFile.writeText(json.toString(2))
    }

    private fun ensureDirectory(dir: File) {
        if (dir.isDirectory) return
        if (dir.exists()) dir.delete()
        if (!dir.mkdirs() && !dir.isDirectory) {
            throw IOException("Cannot create directory: ${dir.absolutePath}")
        }
    }

    private fun describeException(e: Exception): String =
        e.message ?: e::class.simpleName ?: "Unknown error"

    private fun sanitizeFilename(name: String): String {
        val cleaned = name
            .replace(Regex("""[<>:"/\\|?*\u0000-\u001f]"""), "_")
            .trim()
            .trim('.')
        return cleaned.ifBlank { "unnamed" }
    }

    private data class DownloadFileResult(
        val success: Boolean,
        val error: String? = null,
        val paused: Boolean = false,
    )

    private class DownloadTask(val theme: RemoteTheme) {
        @Volatile var isCancelled = false
        @Volatile var isPaused = false
    }

    private class DownloadPausedException : IOException("Download paused")

    private companion object {
        const val THEMES_DIR_NAME = "themes"
        const val META_FILE_NAME = "theme_meta.json"
        const val PREVIEW_FILE_NAME = "Theme.webp"
        const val DOWNLOAD_TIMEOUT_SEC = 300L
        const val BUFFER_SIZE = 8192
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 2000L
        const val MAX_CONCURRENT = 3
        const val MIN_THEME_SIZE = 32L
        const val HEADER_SIZE = 16
        const val TEMP_SUFFIX = ".tmp"
    }
}
