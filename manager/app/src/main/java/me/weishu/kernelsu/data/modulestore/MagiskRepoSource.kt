package me.weishu.kernelsu.data.modulestore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A repository client for the Magisk module list format.
 *
 * The list lives at `<repoBase>/json/modules.json` (or at [repoBase] when it already ends in
 * `.json`); relative download URLs are resolved against the repository base.
 */
class MagiskRepoSource(
    private val repoBase: String,
    private val http: RepoHttp = RepoHttp,
) : ModuleStoreSource {

    override suspend fun list(query: String?): Result<List<StoreModule>> = withContext(Dispatchers.IO) {
        val url = resolveRepoModulesUrl(repoBase)
        if (!url.isHttpUrl()) {
            return@withContext Result.failure(RepoHttpException("invalid repository url: $repoBase"))
        }
        http.get(url).mapCatching { body -> parseMagiskModules(body, repoBase).filterByQuery(query) }
    }

    override suspend fun detail(moduleId: String): Result<StoreModule> {
        val modules = list(null).getOrElse { return Result.failure(it) }
        val module = modules.firstOrNull { it.id == moduleId }
            ?: return Result.failure(NoSuchElementException("module not found: $moduleId"))
        return Result.success(module)
    }
}
