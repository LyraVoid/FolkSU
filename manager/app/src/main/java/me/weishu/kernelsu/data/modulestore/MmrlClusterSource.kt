package me.weishu.kernelsu.data.modulestore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The community repository cluster.
 *
 * The cluster exposes a repository index; each entry is then served through the Magisk module list
 * format. When the index is unavailable the built-in default repository is still available through
 * [withDefaultRepository].
 */
class MmrlClusterSource(
    val repositoryUrl: String = DEFAULT_REPOSITORY_URL,
    private val http: RepoHttp = RepoHttp,
) : ModuleStoreSource, ModuleStoreClusterSource {

    private val modules = MagiskRepoSource(repositoryUrl, http)

    override suspend fun list(query: String?): Result<List<StoreModule>> = modules.list(query)

    override suspend fun detail(moduleId: String): Result<StoreModule> = modules.detail(moduleId)

    override suspend fun listRepositories(): Result<List<StoreRepository>> = withContext(Dispatchers.IO) {
        if (!CLUSTER_INDEX_URL.isHttpUrl()) {
            return@withContext Result.failure(RepoHttpException("invalid cluster index url"))
        }
        http.get(CLUSTER_INDEX_URL).mapCatching { body -> withDefaultRepository(parseRepositories(body)) }
    }

    private companion object {
        private const val CLUSTER_INDEX_URL = "https://mmrl.dev/api/repositories.json"
    }
}
