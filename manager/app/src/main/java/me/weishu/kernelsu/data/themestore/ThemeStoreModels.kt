package me.weishu.kernelsu.data.themestore

/** Host whose plain-HTTP assets the store reports; only this one is safe to upgrade to HTTPS. */
private const val STORE_HTTP_HOST = "folk.mysqil.com"

/**
 * The store index advertises previews (and occasionally downloads) over plain HTTP, which Android
 * blocks for every host except localhost, so Coil fails to load them. Rewrite our own store's HTTP
 * links to HTTPS; leave other hosts untouched rather than silently changing third-party URLs.
 */
fun normalizeStoreUrl(url: String): String {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true)) return trimmed
    val authority = trimmed.substring(7).substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfter('@').substringBefore(':')
    if (host.equals(STORE_HTTP_HOST, ignoreCase = true) ||
        host.endsWith(".$STORE_HTTP_HOST", ignoreCase = true)
    ) {
        return "https://" + trimmed.substring(7)
    }
    return trimmed
}

/** A theme as advertised by the online store. */
data class RemoteTheme(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val previewUrl: String,
    val downloadUrl: String,
    val type: String,
    val source: String,
)

/** A theme already on disk, plus its live download state. */
data class LocalTheme(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val previewUrl: String,
    val downloadUrl: String,
    val type: String,
    val source: String,
    val localPath: String,
    val previewImagePath: String,
    val downloadProgress: Float = 1f,
    val isDownloading: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.COMPLETED,
)

data class DownloadProgress(
    val themeId: String,
    val fileProgress: Float,
    val imageProgress: Float,
    val overallProgress: Float,
    val status: DownloadStatus,
    val errorMessage: String? = null,
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    RETRYING,
}
