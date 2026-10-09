package me.weishu.kernelsu.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.data.modulestore.RepoHttp
import me.weishu.kernelsu.data.themestore.DownloadProgress
import me.weishu.kernelsu.data.themestore.DownloadStatus
import me.weishu.kernelsu.data.themestore.LocalTheme
import me.weishu.kernelsu.data.themestore.RemoteTheme
import me.weishu.kernelsu.data.themestore.ThemeDownloader
import me.weishu.kernelsu.data.themestore.normalizeStoreUrl
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.wallpaper.FolkThemeIO
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs the online theme store and the local "my themes" list.
 *
 * Remote themes are listed from the FolkPatch-compatible index at [THEMES_URL]; a downloaded `.fpt`
 * package lands in `filesDir/themes/<author>/<name>/` and is applied through [FolkThemeIO], the
 * same importer the wallpaper screen uses. Downloads are delegated to [ThemeDownloader], which
 * exposes progress as a [StateFlow].
 */
class ThemeStoreViewModel : ViewModel() {

    private val context: Context = ksuApp
    private val themeDownloader = ThemeDownloader(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var allThemes: List<RemoteTheme> = emptyList()

    var themes by mutableStateOf<List<RemoteTheme>>(emptyList())
        private set
    var searchQuery by mutableStateOf("")
        private set
    var filterAuthor by mutableStateOf("")
        private set
    var filterSource by mutableStateOf(SOURCE_ALL)
        private set
    var filterTypePhone by mutableStateOf(true)
        private set
    var filterTypeTablet by mutableStateOf(true)
        private set
    var localThemes by mutableStateOf<List<LocalTheme>>(emptyList())
        private set
    var localSearchQuery by mutableStateOf("")
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isRefreshingLocal by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val downloadJobs = mutableMapOf<String, Job>()

    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = themeDownloader.downloadProgress

    val filteredLocalThemes: List<LocalTheme>
        get() = if (localSearchQuery.isBlank()) {
            localThemes
        } else {
            localThemes.filter { it.matches(localSearchQuery) }
        }

    init {
        // The local library lives on disk, so load it first and let the network refresh follow.
        loadLocalThemes()
        fetchThemes()
    }

    fun refresh() {
        fetchThemes()
    }

    fun fetchThemes() {
        // Always rescan the local library so "my themes" works offline / after a failed refresh.
        loadLocalThemes()
        viewModelScope.launch {
            isRefreshing = true
            errorMessage = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val url = buildThemesUrl(Natives.getApiToken(context))
                    parseThemes(RepoHttp.get(url).getOrThrow())
                }
            }
            result.onSuccess { remote ->
                allThemes = remote
                applyFilters()
            }.onFailure { error ->
                errorMessage = error.message ?: error::class.java.simpleName
            }
            isRefreshing = false
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        applyFilters()
    }

    fun updateFilters(
        author: String = filterAuthor,
        source: String = filterSource,
        phone: Boolean = filterTypePhone,
        tablet: Boolean = filterTypeTablet,
    ) {
        filterAuthor = author
        filterSource = source
        filterTypePhone = phone
        filterTypeTablet = tablet
        applyFilters()
    }

    fun onLocalSearchQueryChange(query: String) {
        localSearchQuery = query
    }

    fun startDownload(theme: RemoteTheme) {
        val id = theme.id
        if (downloadJobs.containsKey(id) || isThemeDownloaded(id)) return
        downloadJobs[id] = viewModelScope.launch {
            try {
                themeDownloader.downloadTheme(theme).collect { progress ->
                    when (progress.status) {
                        DownloadStatus.COMPLETED -> addLocalTheme(theme)
                        DownloadStatus.FAILED, DownloadStatus.PAUSED -> Unit
                        else -> Unit
                    }
                }
            } finally {
                downloadJobs.remove(id)
            }
        }
    }

    fun cancelDownload(id: String) {
        downloadJobs.remove(id)?.cancel()
        viewModelScope.launch { themeDownloader.cancelDownload(id) }
    }

    fun pauseDownload(id: String) {
        themeDownloader.pauseDownload(id)
    }

    fun getDownloadProgress(id: String): DownloadProgress? = themeDownloader.getProgress(id)

    fun isThemeDownloaded(id: String): Boolean = localThemes.any { it.id == id }

    fun isThemeDownloading(id: String): Boolean = downloadJobs.containsKey(id)

    /** Applies a downloaded theme through the shared `.fpt` importer. */
    suspend fun applyTheme(localTheme: LocalTheme): Boolean = withContext(Dispatchers.IO) {
        val file = File(localTheme.localPath)
        if (!file.exists() || !isLikelyThemeFile(file)) return@withContext false
        FolkThemeIO.importBackground(context, Uri.fromFile(file))
    }

    fun deleteTheme(localTheme: LocalTheme) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val file = File(localTheme.localPath)
                val directory = file.parentFile
                file.delete()
                if (directory != null) {
                    directory.listFiles()?.forEach { it.delete() }
                    directory.delete()
                }
            }
            localThemes = localThemes.filterNot { it.id == localTheme.id }
            saveDownloadedThemes()
        }
    }

    private fun applyFilters() {
        val query = searchQuery.trim()
        themes = allThemes.filter { theme ->
            val matchesQuery = query.isEmpty() ||
                theme.name.contains(query, ignoreCase = true) ||
                theme.author.contains(query, ignoreCase = true) ||
                theme.description.contains(query, ignoreCase = true)
            val matchesAuthor = filterAuthor.isBlank() ||
                theme.author.contains(filterAuthor, ignoreCase = true)
            val matchesSource = when (filterSource) {
                SOURCE_OFFICIAL -> theme.source == SOURCE_OFFICIAL
                SOURCE_THIRD_PARTY -> theme.source != SOURCE_OFFICIAL
                else -> true
            }
            val matchesType = (filterTypePhone && theme.type == TYPE_PHONE) ||
                (filterTypeTablet && theme.type == TYPE_TABLET)
            matchesQuery && matchesAuthor && matchesSource && matchesType
        }
    }

    fun loadLocalThemes() {
        viewModelScope.launch {
            isRefreshingLocal = true
            try {
                val scanned = withContext(Dispatchers.IO) { scanLocalThemes() }
                localThemes = scanned
                saveDownloadedThemes()
            } finally {
                isRefreshingLocal = false
            }
        }
    }

    private fun scanLocalThemes(): List<LocalTheme> {
        val themesDir = themeDownloader.getThemesDir()
        if (!themesDir.exists()) return emptyList()
        val result = mutableListOf<LocalTheme>()
        themesDir.listFiles()?.forEach { authorDir ->
            if (!authorDir.isDirectory) return@forEach
            authorDir.listFiles()?.forEach { themeDir ->
                if (!themeDir.isDirectory) return@forEach
                val themeFile = themeDir.listFiles()
                    ?.firstOrNull { it.isFile && it.name.endsWith(".fpt") }
                    ?: return@forEach
                val previewFile = File(themeDir, PREVIEW_FILE_NAME)
                result += readLocalTheme(
                    themeFile = themeFile,
                    previewFile = previewFile.takeIf { it.exists() },
                    author = authorDir.name,
                )
            }
        }
        return result.distinctBy { it.id }
    }

    private fun readLocalTheme(themeFile: File, previewFile: File?, author: String): LocalTheme {
        val name = themeFile.nameWithoutExtension
        val metaFile = File(themeFile.parentFile, META_FILE_NAME)
        if (metaFile.exists()) {
            runCatching {
                val json = JSONObject(metaFile.readText())
                return LocalTheme(
                    id = json.optString("id", "${author}_$name"),
                    name = json.optString("name", name),
                    author = json.optString("author", author),
                    description = json.optString("description", ""),
                    version = json.optString("version", "unknown"),
                    previewUrl = normalizeStoreUrl(json.optString("previewUrl", "")),
                    downloadUrl = normalizeStoreUrl(json.optString("downloadUrl", "")),
                    type = json.optString("type", TYPE_PHONE),
                    source = json.optString("source", SOURCE_THIRD_PARTY),
                    localPath = themeFile.absolutePath,
                    previewImagePath = previewFile?.absolutePath ?: "",
                )
            }
        }
        return LocalTheme(
            id = "${author}_$name",
            name = name,
            author = author,
            description = "",
            version = "unknown",
            previewUrl = "",
            downloadUrl = "",
            type = TYPE_PHONE,
            source = SOURCE_THIRD_PARTY,
            localPath = themeFile.absolutePath,
            previewImagePath = previewFile?.absolutePath ?: "",
        )
    }

    private fun addLocalTheme(remoteTheme: RemoteTheme) {
        val file = themeDownloader.getThemeFilePath(remoteTheme.author, remoteTheme.name)
        val preview = themeDownloader.getPreviewImagePath(remoteTheme.author, remoteTheme.name)
        val local = LocalTheme(
            id = remoteTheme.id,
            name = remoteTheme.name,
            author = remoteTheme.author,
            description = remoteTheme.description,
            version = remoteTheme.version,
            previewUrl = remoteTheme.previewUrl,
            downloadUrl = remoteTheme.downloadUrl,
            type = remoteTheme.type,
            source = remoteTheme.source,
            localPath = file.absolutePath,
            previewImagePath = if (preview.exists()) preview.absolutePath else "",
        )
        localThemes = localThemes.filterNot { it.id == local.id } + local
        saveDownloadedThemes()
    }

    private fun saveDownloadedThemes() {
        prefs.edit()
            .putStringSet(KEY_DOWNLOADED_THEMES, localThemes.map { it.id }.toSet())
            .apply()
    }

    private fun buildThemesUrl(token: String): String =
        if (THEMES_URL.contains('?')) "$THEMES_URL&token=$token" else "$THEMES_URL?token=$token"

    private fun parseThemes(body: String): List<RemoteTheme> {
        val array = JSONArray(body)
        // The index ships duplicate ids and plain-HTTP links; keep one entry per id and rewrite the
        // store's own HTTP assets to HTTPS so Coil can fetch them under the network security config.
        val result = LinkedHashMap<String, RemoteTheme>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id", "").trim()
            if (id.isEmpty() || result.containsKey(id)) continue
            val downloadUrl = normalizeStoreUrl(item.optString("download_url", ""))
            if (downloadUrl.isBlank() || !themeDownloader.isSafeDownloadUrl(downloadUrl)) continue
            result[id] = RemoteTheme(
                id = id,
                name = item.optString("name", ""),
                author = item.optString("author", ""),
                description = item.optString("description", ""),
                version = item.optString("version", ""),
                previewUrl = normalizeStoreUrl(item.optString("preview_url", "")),
                downloadUrl = downloadUrl,
                type = item.optString("type", TYPE_PHONE),
                source = item.optString("source", SOURCE_THIRD_PARTY),
            )
        }
        return result.values.toList()
    }

    private fun isLikelyThemeFile(file: File): Boolean {
        if (file.length() < MIN_THEME_SIZE) return false
        return runCatching {
            file.inputStream().use { stream ->
                val first = stream.read()
                first != '<'.code && first != '{'.code
            }
        }.getOrDefault(false)
    }

    private fun LocalTheme.matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) ||
            author.contains(query, ignoreCase = true) ||
            description.contains(query, ignoreCase = true)

    companion object {
        private const val THEMES_URL = "https://folk.mysqil.com/api/themes"
        private const val PREFS_NAME = "theme_store"
        private const val KEY_DOWNLOADED_THEMES = "downloaded_themes"
        private const val PREVIEW_FILE_NAME = "Theme.webp"
        private const val META_FILE_NAME = "theme_meta.json"
        private const val MIN_THEME_SIZE = 32L

        const val SOURCE_ALL = "all"
        const val SOURCE_OFFICIAL = "official"
        const val SOURCE_THIRD_PARTY = "third_party"
        const val TYPE_PHONE = "phone"
        const val TYPE_TABLET = "tablet"
    }
}
