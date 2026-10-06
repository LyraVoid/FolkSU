package me.weishu.kernelsu.data.modulestore

import java.util.Locale

/** A backend that can list modules and load an individual module's details. */
interface ModuleStoreSource {
    /** Lists modules, optionally narrowed to [query]. */
    suspend fun list(query: String?): Result<List<StoreModule>>

    /** Loads the module identified by [moduleId]. */
    suspend fun detail(moduleId: String): Result<StoreModule>
}

/** A backend that also exposes the list of community repositories it aggregates. */
interface ModuleStoreClusterSource {
    /** Lists the repositories advertised by the cluster index. */
    suspend fun listRepositories(): Result<List<StoreRepository>>
}

/** The language understood by the official index: `zh` for a Chinese locale, otherwise `en`. */
fun storeLanguage(): String = if (Locale.getDefault().language == "zh") "zh" else "en"

/** Applies the shared client-side search over module names and descriptions. */
internal fun List<StoreModule>.filterByQuery(query: String?): List<StoreModule> {
    val text = query?.trim().orEmpty()
    if (text.isEmpty()) return this
    return filter {
        it.name.contains(text, ignoreCase = true) || it.description.contains(text, ignoreCase = true)
    }
}
