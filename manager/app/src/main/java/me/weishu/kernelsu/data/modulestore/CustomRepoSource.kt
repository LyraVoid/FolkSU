package me.weishu.kernelsu.data.modulestore

/**
 * A repository client bound to a user-supplied base URL.
 *
 * The URL must be absolute http(s); the module list is read using the Magisk module list format.
 */
class CustomRepoSource(
    private val baseUrl: String,
    http: RepoHttp = RepoHttp,
) : ModuleStoreSource {

    private val modules = MagiskRepoSource(baseUrl, http)

    override suspend fun list(query: String?): Result<List<StoreModule>> {
        if (!baseUrl.trim().isHttpUrl()) {
            return Result.failure(RepoHttpException("custom repository must be http(s): $baseUrl"))
        }
        return modules.list(query)
    }

    override suspend fun detail(moduleId: String): Result<StoreModule> {
        val modules = list(null).getOrElse { return Result.failure(it) }
        val module = modules.firstOrNull { it.id == moduleId }
            ?: return Result.failure(NoSuchElementException("module not found: $moduleId"))
        return Result.success(module)
    }
}
