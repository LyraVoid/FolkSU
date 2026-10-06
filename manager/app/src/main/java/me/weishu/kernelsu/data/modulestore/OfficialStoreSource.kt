package me.weishu.kernelsu.data.modulestore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The project's own module index.
 *
 * The index is a single JSON array that carries one ZIP URL per module and no version history, so
 * every entry is adapted to a [StoreModule] with a single synthesised release.
 */
class OfficialStoreSource(
    private val language: String = storeLanguage(),
    private val token: String = "",
    private val http: RepoHttp = RepoHttp,
) : ModuleStoreSource {

    override suspend fun list(query: String?): Result<List<StoreModule>> = withContext(Dispatchers.IO) {
        val url = buildIndexUrl(language, token)
            ?: return@withContext Result.failure(RepoHttpException("invalid index url"))
        http.get(url).mapCatching { body -> parseOfficialModules(body, language).filterByQuery(query) }
    }

    override suspend fun detail(moduleId: String): Result<StoreModule> {
        val modules = list(null).getOrElse { return Result.failure(it) }
        val module = modules.firstOrNull { it.id == moduleId }
            ?: return Result.failure(NoSuchElementException("module not found: $moduleId"))
        return Result.success(module)
    }

    private fun buildIndexUrl(language: String, token: String): String? =
        INDEX_URL.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("type", "apm")
            ?.addQueryParameter("lang", language)
            ?.addQueryParameter("token", token)
            ?.build()
            ?.toString()

    private companion object {
        private const val INDEX_URL = "https://folk.mysqil.com/api/modules"
    }
}
