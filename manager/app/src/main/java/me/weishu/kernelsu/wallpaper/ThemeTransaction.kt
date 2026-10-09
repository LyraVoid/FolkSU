package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.media.MusicConfig
import me.weishu.kernelsu.media.MusicManager
import me.weishu.kernelsu.media.SoundEffectConfig
import me.weishu.kernelsu.media.VisualMediaConfig
import me.weishu.kernelsu.ui.component.bottombar.BottomBarIconConfig
import me.weishu.kernelsu.ui.theme.FontConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import java.io.File

/** Roll back only theme-owned files and preferences if application fails. */
internal class ThemeTransaction(private val context: Context, private val backup: File) {
    private val locales = if (Build.VERSION.SDK_INT >= 33)
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags() else null
    private val preferenceNames = listOf("settings", "wallpaper", "wallpaper_surfaces",
        "font_settings", "music_settings", "sound_effect_settings", "visual_media", "theme_metadata")
    private val preferences = preferenceNames.associateWith {
        context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap()
    }
    private val stems = SurfaceRegistry.themeSlots().mapNotNull { it.storageStem } + listOf(
        "wallpaper", "background", "background_home", "background_superuser", "background_system_module",
        "background_settings", "work_card_background", "custom_font", "video_background", "title_image", "nav_icon",
    )
    private fun owned(file: File): Boolean = file.name in setOf("music", "sound_effects", "theme_passthrough") ||
        stems.any { file.name == it || file.name.startsWith("$it.") || file.name.startsWith("${it}_") }

    init {
        check(backup.mkdirs())
        try {
            context.filesDir.listFiles()?.filter(::owned)?.forEach { source ->
                check(source.copyRecursively(File(backup, source.name), overwrite = true))
            }
        } catch (error: Exception) {
            backup.deleteRecursively()
            throw error
        }
    }

    suspend fun rollback() {
        context.filesDir.listFiles()?.filter(::owned)?.forEach { check(it.deleteRecursively()) }
        backup.listFiles()?.forEach { source ->
            check(source.copyRecursively(File(context.filesDir, source.name), overwrite = true))
        }
        preferences.forEach { (name, values) ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            with(editor) {
                clear()
                values.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }
            check(editor.commit()) { "Could not restore $name" }
        }
        withContext(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= 33 && locales != null) {
                context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(locales)
            }
            WallpaperConfig.load(context)
            SurfaceStore.load(context)
            FontConfig.load(context)
            MusicConfig.load(context)
            SoundEffectConfig.load(context)
            VisualMediaConfig.load(context)
            BottomBarIconConfig.notifyChanged()
            MusicManager.reload()
        }
    }

    fun persist() {
        preferenceNames.forEach { name ->
            check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit()) {
                "Could not persist $name"
            }
        }
    }
}
