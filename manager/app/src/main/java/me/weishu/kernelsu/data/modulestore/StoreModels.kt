package me.weishu.kernelsu.data.modulestore

/**
 * Identifies which store backend produced a [StoreModule].
 *
 * The persisted [value] strings are stable across builds and are intentionally decoupled from the
 * enum constant names.
 */
enum class StoreSourceKind(val value: String) {
    OFFICIAL("folk"),
    CLUSTER("mmrl"),
    CUSTOM("custom");

    companion object {
        /** Resolves [value] to a kind, falling back to [OFFICIAL] for unrecognised input. */
        fun fromValue(value: String?): StoreSourceKind =
            entries.firstOrNull { it.value == value } ?: OFFICIAL
    }
}

/** A single downloadable release of a [StoreModule]. */
data class StoreRelease(
    val version: String,
    val versionCode: Long,
    val downloadUrl: String,
    val changelog: String = "",
    val timestamp: Double = 0.0,
)

/**
 * A module entry normalised across all store backends.
 *
 * [source], [homepage], [license] and [support] map to the repository metadata fields and may be
 * absent depending on the backend. [parameter] is reserved for kernel-module entries and is always
 * `null` for system modules.
 */
data class StoreModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val license: String? = null,
    val homepage: String? = null,
    val source: String? = null,
    val support: String? = null,
    val releases: List<StoreRelease> = emptyList(),
    val parameter: Boolean? = null,
) {
    /**
     * The release offered for download: the first release whose [StoreRelease.versionCode] equals
     * [versionCode], otherwise the release with the highest version code. Returns `null` when no
     * release carries a download URL.
     */
    val latestRelease: StoreRelease?
        get() {
            val downloadable = releases.filter { it.downloadUrl.isNotBlank() }
            if (downloadable.isEmpty()) return null
            return downloadable.firstOrNull { it.versionCode == versionCode }
                ?: downloadable.maxByOrNull { it.versionCode }
        }
}

/** A community repository entry advertised by the cluster index. */
data class StoreRepository(
    val name: String,
    val url: String,
    val description: String = "",
    val modulesCount: Int = 0,
    val cover: String = "",
)
