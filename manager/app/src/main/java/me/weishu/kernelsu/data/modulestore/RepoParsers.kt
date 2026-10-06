package me.weishu.kernelsu.data.modulestore

import org.json.JSONArray
import org.json.JSONObject

/** Repository used when the cluster index is unavailable or empty. */
const val DEFAULT_REPOSITORY_URL = "https://gr.dergoogler.com/gmr/"

/** Built-in fallback repository entry. */
val DEFAULT_REPOSITORY = StoreRepository(
    name = "GMR",
    url = DEFAULT_REPOSITORY_URL,
)

/** Returns `true` when the receiver is an absolute http(s) URL. */
internal fun String.isHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

/**
 * Resolves the module list endpoint for a repository base.
 *
 * A base that already points at a `.json` document is used verbatim; any other base gets the
 * standard `<base>/json/modules.json` suffix.
 */
fun resolveRepoModulesUrl(repoBase: String): String {
    val base = repoBase.trim()
    return if (base.endsWith(".json", ignoreCase = true)) {
        base
    } else {
        base.trimEnd('/') + "/json/modules.json"
    }
}

/** Normalises a repository base to an absolute directory URL ending in `/`. */
fun resolveRepoBaseUrl(repoBase: String): String {
    val base = repoBase.trim()
    val directory = if (base.endsWith(".json", ignoreCase = true)) {
        base.substringBeforeLast('/')
    } else {
        base
    }
    return directory.trimEnd('/') + "/"
}

/** Resolves a module download URL, leaving absolute URLs untouched. */
fun resolveDownloadUrl(repoBase: String, zipUrl: String): String {
    val raw = zipUrl.trim()
    if (raw.isHttpUrl()) return raw
    return resolveRepoBaseUrl(repoBase) + raw.trimStart('/')
}

/**
 * Parses the official index (a top-level JSON array) into [StoreModule]s.
 *
 * Entries whose `url` is not http(s) are dropped. The index has no version history, so each module
 * carries a single synthesised release.
 */
fun parseOfficialModules(json: String, language: String): List<StoreModule> {
    val array = JSONArray(json)
    val modules = ArrayList<StoreModule>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val url = item.optString("url", "").trim()
        if (!url.isHttpUrl()) continue
        val name = item.optString("name", "").trim()
        if (name.isEmpty()) continue
        val version = item.optString("version", "")
        modules += StoreModule(
            id = name,
            name = name,
            version = version,
            versionCode = 0L,
            author = "",
            description = selectDescription(item, language),
            releases = listOf(StoreRelease(version = version, versionCode = 0L, downloadUrl = url)),
            parameter = if (item.has("parameter")) item.optInt("parameter", 0) == 1 else null,
        )
    }
    return modules
}

/** Parses the cluster index (a top-level JSON array) into [StoreRepository]s. */
fun parseRepositories(json: String): List<StoreRepository> {
    val array = JSONArray(json)
    val repositories = ArrayList<StoreRepository>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val url = item.optString("url", "").trim()
        if (url.isEmpty()) continue
        repositories += StoreRepository(
            name = item.optString("name", ""),
            url = url,
            description = item.optString("description", ""),
            modulesCount = item.optInt("modules_count", 0),
            cover = item.optString("cover", ""),
        )
    }
    return repositories
}

/**
 * Prepends [DEFAULT_REPOSITORY] when it is missing. The fetched list itself is never de-duplicated.
 */
fun withDefaultRepository(repositories: List<StoreRepository>): List<StoreRepository> =
    if (repositories.any { it.url == DEFAULT_REPOSITORY_URL }) {
        repositories
    } else {
        listOf(DEFAULT_REPOSITORY) + repositories
    }

/** Parses a Magisk-format module list document and resolves relative download URLs. */
fun parseMagiskModules(json: String, repoBase: String): List<StoreModule> {
    val root = JSONObject(json)
    val array = root.optJSONArray("modules") ?: return emptyList()
    val modules = ArrayList<StoreModule>(array.length())
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val name = item.optString("name", "").trim()
        val id = item.optString("id", "").trim().ifEmpty { name }
        if (id.isEmpty()) continue
        val track = item.optJSONObject("track")
        val releases = parseMagiskReleases(item.optJSONArray("versions"), repoBase)
        val versionCode = item.optLong("versionCode", 0L)
        val latest = releases.firstOrNull { it.versionCode == versionCode }
            ?: releases.maxByOrNull { it.versionCode }
        modules += StoreModule(
            id = id,
            name = name.ifEmpty { id },
            version = item.optString("version", "").ifEmpty { latest?.version.orEmpty() },
            versionCode = versionCode,
            author = item.optString("author", ""),
            description = item.optString("description", ""),
            license = trackValue(track, item, "license"),
            homepage = trackValue(track, item, "homepage"),
            source = trackValue(track, item, "source"),
            support = trackValue(track, item, "support"),
            releases = releases,
        )
    }
    return modules
}

private fun parseMagiskReleases(versions: JSONArray?, repoBase: String): List<StoreRelease> {
    if (versions == null) return emptyList()
    val releases = ArrayList<StoreRelease>(versions.length())
    for (index in 0 until versions.length()) {
        val version = versions.optJSONObject(index) ?: continue
        val zipUrl = version.optString("zipUrl", "").trim()
        if (zipUrl.isEmpty()) continue
        releases += StoreRelease(
            version = version.optString("version", ""),
            versionCode = version.optLong("versionCode", 0L),
            downloadUrl = resolveDownloadUrl(repoBase, zipUrl),
            changelog = version.optString("changelog", ""),
            timestamp = version.optDouble("timestamp", 0.0),
        )
    }
    return releases
}

/** Prefers a non-blank value from the nested `track` object over the module-level fallback. */
private fun trackValue(track: JSONObject?, module: JSONObject, key: String): String? {
    val tracked = track?.optString(key, "")?.takeIf { it.isNotBlank() }
    if (tracked != null) return tracked
    return module.optString(key, "").takeIf { it.isNotBlank() }
}

/** Selects the description for [language], preferring `description_en` for non-Chinese locales. */
private fun selectDescription(item: JSONObject, language: String): String {
    val localized = item.optString("description", "")
    val english = item.optString("description_en", "")
    return if (language == "zh") {
        localized.ifBlank { english }
    } else {
        english.ifBlank { localized }
    }
}
