package me.weishu.kernelsu.ui.screen.modulerepo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.weishu.kernelsu.data.modulestore.StoreModule
import me.weishu.kernelsu.data.modulestore.StoreRelease

/**
 * A single release of the selected store module, carried across the detail route.
 */
@Parcelize
data class StoreReleaseArg(
    val version: String,
    val versionCode: Long,
    val downloadUrl: String,
    val changelog: String,
    val timestamp: Double,
) : Parcelable

/**
 * The store module carried across the detail route.
 *
 * The detail destination renders entirely from this value: the release list comes from [releases]
 * and the info rows from the licence and link fields, so no additional lookup is required.
 */
@Parcelize
data class StoreModuleArg(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val license: String?,
    val homepage: String?,
    val source: String?,
    val support: String?,
    val releases: List<StoreReleaseArg>,
) : Parcelable

/** Adapts a store module to the parcelable value passed to the detail destination. */
fun StoreModule.toArg(): StoreModuleArg = StoreModuleArg(
    id = id,
    name = name,
    version = version,
    author = author,
    description = description,
    license = license,
    homepage = homepage,
    source = source,
    support = support,
    releases = releases.map { it.toArg() },
)

private fun StoreRelease.toArg(): StoreReleaseArg = StoreReleaseArg(
    version = version,
    versionCode = versionCode,
    downloadUrl = downloadUrl,
    changelog = changelog,
    timestamp = timestamp,
)
