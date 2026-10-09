package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.net.Uri
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.wallpaper.surface.SurfaceDescriptor
import me.weishu.kernelsu.wallpaper.surface.SurfaceField
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import me.weishu.kernelsu.wallpaper.surface.isToggle
import me.weishu.kernelsu.ui.theme.FontConfig
import me.weishu.kernelsu.ui.theme.FontMode
import org.json.JSONObject
import java.io.File

/**
 * The user's main background wallpaper and its opacity/blur/scrim settings.
 */
internal object MainWallpaperAsset : ThemedAsset {
    override val base = "background"

    override fun currentFile(context: Context): File? = WallpaperManager.currentFile(context)

    override fun writeConfig(json: JSONObject) {
        json.put("isBackgroundEnabled", WallpaperConfig.enabled)
        json.put("backgroundOpacity", WallpaperConfig.opacity.toDouble())
        json.put("backgroundBlur", WallpaperConfig.blur.toDouble())
        json.put("backgroundDim", WallpaperConfig.dim.toDouble())
        json.put("isDualBackgroundDimEnabled", WallpaperConfig.dualDimEnabled)
        json.put("backgroundDayDim", WallpaperConfig.dayDim.toDouble())
        json.put("backgroundNightDim", WallpaperConfig.nightDim.toDouble())
        json.put("isMultiBackgroundEnabled", WallpaperConfig.multiBackgroundEnabled)
        json.put("wallpaper_use_color", WallpaperConfig.useWallpaperColor)
    }

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        val enabled = json.optBoolean("isBackgroundEnabled", false)
        if (enabled && file != null) {
            val extension = WallpaperManager.resolveExtension(context, Uri.fromFile(file))
            val target = WallpaperManager.replaceFile(context, file, extension)
            WallpaperManager.applyFile(context, target)
        } else {
            WallpaperManager.clear(context)
        }
        // Apply before the per-page assets so a multi-mode theme is remembered even when no page
        // image survives; each page asset still turns multi mode on when it restores an image.
        WallpaperConfig.updateMultiBackgroundEnabled(
            json.optBoolean("isMultiBackgroundEnabled", WallpaperConfig.multiBackgroundEnabled)
        )
        WallpaperConfig.updateOpacity(
            json.optDouble("backgroundOpacity", WallpaperConfig.opacity.toDouble()).toFloat()
        )
        WallpaperConfig.updateBlur(
            json.optDouble("backgroundBlur", WallpaperConfig.blur.toDouble()).toFloat()
        )
        WallpaperConfig.updateDim(
            json.optDouble("backgroundDim", WallpaperConfig.dim.toDouble()).toFloat()
        )
        WallpaperConfig.updateDualDimEnabled(
            json.optBoolean("isDualBackgroundDimEnabled", WallpaperConfig.dualDimEnabled)
        )
        WallpaperConfig.updateDayDim(
            json.optDouble("backgroundDayDim", WallpaperConfig.dayDim.toDouble()).toFloat()
        )
        WallpaperConfig.updateNightDim(
            json.optDouble("backgroundNightDim", WallpaperConfig.nightDim.toDouble()).toFloat()
        )
        WallpaperConfig.updateUseWallpaperColor(
            json.optBoolean("wallpaper_use_color", WallpaperConfig.useWallpaperColor)
        )
    }

    override suspend fun reset(context: Context) {
        WallpaperManager.clear(context)
    }
}

/**
 * A slot's own background image and its opacity/dim/hide settings.
 *
 * It round-trips the registry keys (`surface.<id>.<field>`) so a surface theme reloads under the
 * same names the store uses, plus the wider-ecosystem legacy aliases the descriptor declares so a
 * theme exported elsewhere still imports here (and vice versa).
 */
internal class SurfaceBackgroundAsset(private val descriptor: SurfaceDescriptor) : ThemedAsset {
    private val id = descriptor.id

    /** Whether this surface stores a background file at all (the focus parent does not). */
    private val ownsImage = SurfaceField.Image in descriptor.fields

    /** Whether the surface has a per-surface enable flag, as opposed to a bare image slot. */
    private val hasEnabledField = SurfaceField.Enabled in descriptor.fields

    /** Surfaces whose shared master switch this one owns; empty for a leaf surface. */
    private val children = SurfaceRegistry.all.filter { it.parentId == id }

    override val base: String = descriptor.themeBase ?: id.value

    override fun currentFile(context: Context): File? =
        if (ownsImage) WallpaperManager.currentSurfaceFile(context, id) else null

    override fun writeConfig(json: JSONObject) {
        val surface = SurfaceStore.config(id)
        descriptor.fields.forEach { field ->
            if (field == SurfaceField.Image) return@forEach
            val key = SurfaceStore.key(id, field)
            if (field.isToggle) json.put(key, surface.toggle(field))
            else json.put(key, surface.scalar(field).toDouble())
        }
        descriptor.flags.forEach { flag ->
            json.put(SurfaceStore.key(id, flag), surface.hasFlag(flag))
        }
        descriptor.legacyThemeFields.forEach { (field, legacyKey) ->
            when {
                field == SurfaceField.Image -> json.put(legacyKey, !surface.imageUri.isNullOrEmpty())
                field.isToggle -> json.put(legacyKey, surface.toggle(field))
                else -> json.put(legacyKey, surface.scalar(field).toDouble())
            }
        }
        descriptor.legacyThemeFlags.forEach { (flag, legacyKey) ->
            json.put(legacyKey, surface.hasFlag(flag))
        }
    }

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        val current = SurfaceStore.config(id)
        var parsed = current
        descriptor.fields.forEach { field ->
            if (field == SurfaceField.Image) return@forEach
            val legacyKey = descriptor.legacyThemeFields[field]
            if (field.isToggle) {
                // A grouped parent (the focus master) has no legacy image of its own, so when the
                // theme carries no explicit master bit it follows the wider ecosystem's default:
                // enabled as soon as any child card ships an image.
                val inherited = if (field == SurfaceField.Enabled && children.isNotEmpty()) {
                    children.any { child ->
                        val childLegacy = child.legacyThemeFields[SurfaceField.Enabled]
                        json.optBoolean(SurfaceStore.key(child.id, SurfaceField.Enabled), false) ||
                            (childLegacy != null && json.optBoolean(childLegacy, false))
                    }
                } else {
                    current.toggle(field)
                }
                val fallback = if (legacyKey != null) {
                    json.optBoolean(legacyKey, inherited)
                } else {
                    inherited
                }
                parsed = parsed.withToggle(field, json.optBoolean(SurfaceStore.key(id, field), fallback))
            } else {
                val fallback = if (legacyKey != null) {
                    json.optDouble(legacyKey, current.scalar(field).toDouble())
                } else {
                    current.scalar(field).toDouble()
                }
                parsed = parsed.withScalar(
                    field,
                    json.optDouble(SurfaceStore.key(id, field), fallback).toFloat(),
                )
            }
        }
        descriptor.flags.forEach { flag ->
            val legacyKey = descriptor.legacyThemeFlags[flag]
            val fallback = if (legacyKey != null) {
                json.optBoolean(legacyKey, current.hasFlag(flag))
            } else {
                current.hasFlag(flag)
            }
            parsed = parsed.withFlag(flag, json.optBoolean(SurfaceStore.key(id, flag), fallback))
        }
        if (ownsImage) {
            // A surface with its own enable flag follows that flag; a bare image slot follows the
            // file, because the wider ecosystem stores such cards as an entry plus a presence bool.
            val wantsImage = if (hasEnabledField) parsed.enabled else file != null
            if (wantsImage && file != null) {
                // Import must not flip a group parent on as a side effect; the parent's own bit is
                // restored below from the theme (or inferred from the children).
                check(WallpaperManager.saveSurfaceImage(context, id, Uri.fromFile(file), enableParent = false))
            } else {
                WallpaperManager.clearSurfaceImage(context, id)
            }
        }
        // Saving/clearing the file already set the image and, for image-backed surfaces, the enable
        // flag; carry the numbers and flags over, and the enable flag only when there is no image.
        SurfaceStore.update(id) { s ->
            s.copy(
                enabled = if (ownsImage) s.enabled else parsed.enabled,
                opacity = parsed.opacity,
                dim = parsed.dim,
                dualOpacity = parsed.dualOpacity,
                dayOpacity = parsed.dayOpacity,
                nightOpacity = parsed.nightOpacity,
                dualDim = parsed.dualDim,
                dayDim = parsed.dayDim,
                nightDim = parsed.nightDim,
                flags = parsed.flags,
            )
        }
    }

    override suspend fun reset(context: Context) {
        if (ownsImage) WallpaperManager.clearSurfaceImage(context, id)
    }
}

/**
 * The stored file for a page background, or null when multi mode is off or the page is unset.
 *
 * The page assets below carry only a zip entry each: the shared theme format stores no per-page
 * numbers, and their base names are shared with the wider theme ecosystem for interop.
 */
private fun currentPageFile(uri: String?): File? {
    if (!WallpaperConfig.multiBackgroundEnabled) return null
    val path = uri?.let { Uri.parse(it).path } ?: return null
    val file = File(path)
    return if (file.exists() && file.length() > 0L) file else null
}

/** Restores a page background from [file], or clears that page when the theme has none. */
private suspend fun applyPageBackground(context: Context, page: Int, file: File?) {
    if (file != null) {
        check(WallpaperManager.savePageBackground(context, page, Uri.fromFile(file)))
    } else {
        WallpaperManager.clearPageBackground(context, page)
    }
}

internal object HomeBackgroundAsset : ThemedAsset {
    override val base = "background_home"

    override fun currentFile(context: Context): File? =
        currentPageFile(WallpaperConfig.homeBackgroundUri)

    override fun writeConfig(json: JSONObject) = Unit

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        applyPageBackground(context, WallpaperConfig.PAGE_HOME, file)
    }

    override suspend fun reset(context: Context) {
        WallpaperManager.clearPageBackground(context, WallpaperConfig.PAGE_HOME)
    }
}

internal object SuperuserBackgroundAsset : ThemedAsset {
    override val base = "background_superuser"

    override fun currentFile(context: Context): File? =
        currentPageFile(WallpaperConfig.superuserBackgroundUri)

    override fun writeConfig(json: JSONObject) = Unit

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        applyPageBackground(context, WallpaperConfig.PAGE_SUPERUSER, file)
    }

    override suspend fun reset(context: Context) {
        WallpaperManager.clearPageBackground(context, WallpaperConfig.PAGE_SUPERUSER)
    }
}

internal object ModuleBackgroundAsset : ThemedAsset {
    override val base = "background_system_module"

    override fun currentFile(context: Context): File? =
        currentPageFile(WallpaperConfig.moduleBackgroundUri)

    override fun writeConfig(json: JSONObject) = Unit

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        applyPageBackground(context, WallpaperConfig.PAGE_MODULE, file)
    }

    override suspend fun reset(context: Context) {
        WallpaperManager.clearPageBackground(context, WallpaperConfig.PAGE_MODULE)
    }
}

internal object SettingsBackgroundAsset : ThemedAsset {
    override val base = "background_settings"

    override fun currentFile(context: Context): File? =
        currentPageFile(WallpaperConfig.settingsBackgroundUri)

    override fun writeConfig(json: JSONObject) = Unit

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        applyPageBackground(context, WallpaperConfig.PAGE_SETTINGS, file)
    }

    override suspend fun reset(context: Context) {
        WallpaperManager.clearPageBackground(context, WallpaperConfig.PAGE_SETTINGS)
    }
}

/**
 * The app font. Only [FontMode.CUSTOM] owns a file; the other two modes are pure configuration, so
 * they round-trip through `theme.json` alone.
 *
 * The key names and the `font.ttf` asset name are shared with the wider theme ecosystem, so a theme
 * exported elsewhere imports here (and vice versa).
 */
internal object FontAsset : ThemedAsset {
    override val base = "font"

    override fun extension(file: File): String = "ttf"

    override fun currentFile(context: Context): File? {
        if (FontConfig.fontMode != FontMode.CUSTOM) return null
        val filename = FontConfig.customFontFilename ?: return null
        val file = File(context.filesDir, filename)
        return if (file.exists() && file.length() > 0L) file else null
    }

    override fun writeConfig(json: JSONObject) {
        json.put("fontMode", FontConfig.fontMode.serializedName)
        json.put("isFontEnabled", FontConfig.isCustomFontEnabled)
    }

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        // Themes written before the three-mode setting only carry the legacy boolean.
        if (!json.has("fontMode") && !json.has("isFontEnabled")) return
        val mode = FontMode.fromSerializedName(json.optString("fontMode").ifEmpty { null })
            ?: if (json.optBoolean("isFontEnabled", false)) FontMode.CUSTOM else FontMode.SYSTEM_DEFAULT

        when {
            mode != FontMode.CUSTOM -> FontConfig.setFontMode(context, mode)
            file != null -> FontConfig.applyCustomFont(context, file)
            else -> FontConfig.setFontMode(context, FontMode.APP_DEFAULT)
        }
    }

    override suspend fun reset(context: Context) {
        FontConfig.clearFont(context)
    }
}

/**
 * The home layout style (the token persisted under `home_layout_style`).
 *
 * It is pure configuration with no payload file, so it only round-trips through `theme.json`. The
 * key `homeLayoutStyle` and the tokens (`kernelsu` for the grid UI, `circle` for the single column,
 * `focus`/`dashboard_ui`/`stats` for the tile layouts) are shared with the wider theme ecosystem, so
 * a theme exported elsewhere imports here.
 *
 * The `default` (ListUI) and `sign` (SignUI) tokens of that ecosystem have no counterpart here;
 * they are close enough to the circle layout that they are mapped onto it rather than dropped or
 * reimplemented. Any other token is left untouched instead of forcing a layout the user did not
 * pick.
 */
internal object HomeLayoutAsset : ThemedAsset {
    private const val KEY = "homeLayoutStyle"

    override val base = "home_layout"

    override fun currentFile(context: Context): File? = null

    override fun writeConfig(json: JSONObject) {
        json.put(KEY, SettingsRepositoryImpl().homeLayoutStyle)
    }

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        if (!json.has(KEY)) return
        val token = when (json.optString(KEY)) {
            HomeLayoutStyle.GRID -> HomeLayoutStyle.GRID
            HomeLayoutStyle.FOCUS -> HomeLayoutStyle.FOCUS
            HomeLayoutStyle.DASHBOARD -> HomeLayoutStyle.DASHBOARD
            HomeLayoutStyle.STATS -> HomeLayoutStyle.STATS
            HomeLayoutStyle.CIRCLE, "default", "sign" -> HomeLayoutStyle.CIRCLE
            else -> return
        }
        SettingsRepositoryImpl().homeLayoutStyle = token
    }

    override suspend fun reset(context: Context) {
        SettingsRepositoryImpl().homeLayoutStyle = HomeLayoutStyle.DEFAULT
    }
}

/** The file-name stem of a zip entry: `nav_icon_Home.png` -> `nav_icon_Home`. */
internal fun zipStem(entryName: String): String =
    entryName.substringBefore('.').substringAfterLast('/')
