package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import org.json.JSONObject
import java.io.File

/** Asset registry and compatibility facade. Container, validation and transactions are separate. */
object FolkThemeIO {
    const val FILE_NAME = "wallpaper.fpt"
    internal val assets: List<ThemedAsset> = buildList {
        add(VisualMediaThemeAsset(true))
        add(VisualMediaThemeAsset(false))
        add(MainWallpaperAsset)
        SurfaceRegistry.themeSlots().forEach { add(SurfaceBackgroundAsset(it)) }
        add(HomeBackgroundAsset)
        add(SuperuserBackgroundAsset)
        add(ModuleBackgroundAsset)
        add(SettingsBackgroundAsset)
        add(FontAsset)
        add(HomeLayoutAsset)
    }
    internal val groups = listOf(NavIconsAsset, MusicThemeAsset, SoundThemeAsset)

    suspend fun exportBackground(context: Context, target: Uri, name: String): Boolean =
        ThemeExportService.export(context, target, ThemeMetadata.current(context).copy(name = name)).isSuccess

    suspend fun importBackground(context: Context, source: Uri): Boolean {
        val prepared = ThemeImportService.prepare(context, source).getOrNull() ?: return false
        return prepared.use { ThemeImportService.apply(context, it).isSuccess }
    }

    internal suspend fun applyAssets(context: Context, json: JSONObject, imported: Map<String, File>) {
        assets.forEach { asset ->
            asset.apply(context, json, ThemeResourcePolicy.select(asset.base, imported))
        }
        // Page import helpers enable the feature for interactive picks. Theme switches are
        // independent of the files, so restore them after all page resources have been copied.
        WallpaperConfig.updateEnabled(json.optBoolean("isBackgroundEnabled", false))
        WallpaperConfig.updateMultiBackgroundEnabled(json.optBoolean("isMultiBackgroundEnabled", false))
        groups.forEach { it.apply(context, json, imported) }
        WallpaperConfig.save(context)
    }

    suspend fun resetTheme(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            assets.forEach { it.reset(context) }
            groups.forEach { it.reset(context) }
            WallpaperConfig.reset()
            WallpaperConfig.save(context)
            true
        }.getOrDefault(false)
    }
}
