package me.weishu.kernelsu.data.modulestore

import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl

/**
 * Resolves the active [ModuleStoreSource] from the persisted source selection.
 *
 * The selection is stored through [SettingsRepository]; an unrecognised persisted value falls back
 * to [StoreSourceKind.OFFICIAL], while any other failure reading the selection is surfaced to the
 * caller instead of being silently replaced.
 */
object ModuleStoreRegistry {

    /** Builds the source for an explicit selection. */
    fun sourceFor(
        kind: StoreSourceKind,
        customUrl: String = "",
        repositoryUrl: String = "",
        language: String = storeLanguage(),
    ): ModuleStoreSource = when (kind) {
        StoreSourceKind.OFFICIAL -> OfficialStoreSource(language)
        StoreSourceKind.CLUSTER -> MmrlClusterSource(repositoryUrl.ifBlank { DEFAULT_REPOSITORY_URL })
        StoreSourceKind.CUSTOM -> CustomRepoSource(customUrl)
    }

    /** Builds the cluster source for an explicit repository URL. */
    fun clusterFor(repositoryUrl: String): ModuleStoreClusterSource =
        MmrlClusterSource(repositoryUrl.ifBlank { DEFAULT_REPOSITORY_URL })

    /** Resolves the source for the current persisted selection. */
    fun current(settings: SettingsRepository = SettingsRepositoryImpl()): ModuleStoreSource =
        sourceFor(
            kind = settings.repoSourceKind,
            customUrl = settings.repoCustomUrl,
            repositoryUrl = settings.repoSelectedRepositoryUrl,
        )

    /** Resolves the cluster source for the current persisted selection. */
    fun currentCluster(settings: SettingsRepository = SettingsRepositoryImpl()): ModuleStoreClusterSource =
        clusterFor(settings.repoSelectedRepositoryUrl)
}
