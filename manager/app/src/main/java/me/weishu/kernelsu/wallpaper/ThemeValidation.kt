package me.weishu.kernelsu.wallpaper

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import me.weishu.kernelsu.wallpaper.surface.SurfaceField
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import me.weishu.kernelsu.wallpaper.surface.isToggle
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Validate all recognized fields and referenced payloads before the confirmation step. */
internal object ThemeValidation {
    internal fun validateBackground(json: JSONObject, names: Set<String>) {
        if (!json.optBoolean("isBackgroundEnabled")) return
        val stems = names.map { it.substringBefore('.') }.toSet()
        val hasBackground = if (json.optBoolean("isMultiBackgroundEnabled")) {
            stems.any { it in setOf("background_home", "background_kernel", "background_superuser",
                "background_system_module", "background_settings") }
        } else "background" in stems
        require(hasBackground) { "Enabled wallpaper requires a background for its selected mode" }
    }

    fun validate(json: JSONObject, files: Map<String, File>): List<String> {
        val warnings = mutableListOf<String>()
        val booleans = mutableSetOf(
            "isBackgroundEnabled", "isDualBackgroundDimEnabled", "isMultiBackgroundEnabled",
            "wallpaper_use_color", "isFontEnabled", "nightModeEnabled", "nightModeFollowSys",
            "useSystemDynamicColor", "isListWorkingCardModeHidden", "navIconCustomEnabled",
            "isMusicEnabled", "isAutoPlayEnabled", "isLoopingEnabled", "isSoundEffectEnabled",
            "isVideoBackgroundEnabled", "isAdvancedTitleStyleEnabled",
        )
        val numbers = mutableMapOf(
            "backgroundOpacity" to 0.0..1.0, "backgroundBlur" to 0.0..100.0,
            "backgroundDim" to 0.0..1.0, "backgroundDayDim" to 0.0..1.0,
            "backgroundNightDim" to 0.0..1.0, "musicVolume" to 0.0..1.0,
            "videoVolume" to 0.0..1.0, "titleImageDayOpacity" to 0.0..1.0,
            "titleImageNightOpacity" to 0.0..1.0, "titleImageDim" to 0.0..1.0,
            "titleImageOffsetX" to -2.0..2.0,
            "folksu_keyColor" to Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble(),
        )
        SurfaceRegistry.themeSlots().forEach { slot ->
            slot.fields.filter { it != SurfaceField.Image }.forEach { field ->
                val keys = listOfNotNull(SurfaceStore.key(slot.id, field), slot.legacyThemeFields[field])
                if (field.isToggle) booleans.addAll(keys) else keys.forEach { numbers[it] = 0.0..1.0 }
            }
            booleans.addAll(slot.legacyThemeFields.filterKeys { it == SurfaceField.Image }.values)
            slot.flags.forEach { booleans += SurfaceStore.key(slot.id, it) }
            booleans.addAll(slot.legacyThemeFlags.values)
        }
        booleans.filter(json::has).forEach { require(json.get(it) is Boolean) { "$it must be boolean" } }
        numbers.filterKeys(json::has).forEach { (key, range) ->
            val value = json.get(key)
            require(value is Number && value.toDouble().isFinite() && value.toDouble() in range) {
                "$key is outside its valid range"
            }
        }
        if (json.has("folksu_keyColor")) require(json.getDouble("folksu_keyColor") == json.getInt("folksu_keyColor").toDouble())
        val enums = mapOf(
            "customColor" to ThemeSettingsMapping.colors.keys,
            "colorGenerationMode" to setOf("classic", "custom"),
            "colorStandard" to setOf("MD3_2021", "M3E_2025"),
            "colorStyle" to ThemeSettingsMapping.styles.keys,
            "colorContrast" to setOf("STANDARD", "MEDIUM", "HIGH"),
            "statsTopLayout" to setOf("list", "grid"),
            "fontMode" to setOf("app", "system", "custom"),
            "homeLayoutStyle" to setOf("kernelsu", "circle", "focus", "dashboard_ui", "stats", "default", "sign"),
            "soundEffectScope" to setOf("global", "bottom_bar"),
        )
        enums.filterKeys(json::has).forEach { (key, values) ->
            require(json.get(key) is String) { "$key must be text" }
            if (json.getString(key) !in values) {
                warnings += "$key=${json.getString(key)}: unsupported; current setting retained"
                json.remove(key)
                if (key == "fontMode") json.remove("isFontEnabled")
            }
        }
        listOf("meta_name", "meta_type", "meta_author", "meta_description", "appLanguage").filter(json::has)
            .forEach { require(json.get(it) is String) { "$it must be text" } }
        if (json.has("meta_version")) require(json.get("meta_version") is String || json.get("meta_version") is Number)
        if (json.has("appLanguage")) {
            val tags = json.getString("appLanguage")
            require(tags.isEmpty() || tags.split(',').all { tag ->
                runCatching { Locale.Builder().setLanguageTag(tag).build() }.isSuccess
            }) { "Invalid appLanguage" }
            if (tags.isNotEmpty()) warnings += "Application language follows $tags; untranslated content uses Android resource fallback"
        }
        if (json.optString("meta_type", "phone") !in setOf("phone", "tablet")) {
            warnings += "Legacy or unknown theme type retained as metadata"
        }
        if (json.has("folksu_keyColor") && json.getInt("folksu_keyColor") !in ThemeSettingsMapping.colors.values &&
            !json.optBoolean("useSystemDynamicColor")) {
            warnings += "FolkSU custom seed has no FP catalog entry; FP uses the exported customColor fallback"
        }
        if (json.optString("homeLayoutStyle") in setOf("default", "sign")) {
            warnings += "${json.getString("homeLayoutStyle")} maps to FolkSU circle layout; original layout token is preserved on export"
        }
        if (json.optString("customColor") == "ink_wash") {
            warnings += "FolkSU retains its fixed semantic error palette for ink_wash"
        }
        fun stem(name: String) = name.substringBefore('.')
        files.keys.groupBy(::stem).filterValues { it.size > 1 }.keys.forEach {
            require(FolkThemeIO.assets.none { asset -> asset.base == it }) { "Ambiguous resource: $it" }
        }
        fun requireAsset(enabled: String, base: String) {
            if (json.optBoolean(enabled)) require(files.keys.any { stem(it) == base }) { "$enabled requires $base" }
        }
        validateBackground(json, files.keys)
        requireAsset("isVideoBackgroundEnabled", "video_background")
        requireAsset("isAdvancedTitleStyleEnabled", "title_image")
        if (json.optString("fontMode") == "custom" || (!json.has("fontMode") && json.optBoolean("isFontEnabled"))) {
            require(files.keys.any { stem(it) == "font" }) { "Custom font is missing" }
        }
        SurfaceRegistry.themeSlots().forEach { slot ->
            if (SurfaceField.Image in slot.fields) {
                val enableKey = slot.legacyThemeFields[SurfaceField.Enabled] ?: slot.legacyThemeFields[SurfaceField.Image]
                if (enableKey != null) requireAsset(enableKey, slot.themeBase ?: slot.id.value)
                if (SurfaceField.Enabled in slot.fields) requireAsset(SurfaceStore.key(slot.id, SurfaceField.Enabled), slot.themeBase ?: slot.id.value)
            }
        }
        listOf("isMusicEnabled" to "musicFilename", "isSoundEffectEnabled" to "soundEffectFilename").forEach { (enabled, key) ->
            if (json.has(key) && !json.isNull(key)) require(json.get(key) is String) { "$key must be text" }
            if (json.optBoolean(enabled)) require(files.containsKey(json.optString(key))) { "$key resource is missing" }
        }
        if (json.has("navIcons")) {
            val icons = json.getJSONObject("navIcons")
            icons.keys().forEach { key ->
                require(icons.get(key) is String && files.containsKey(icons.getString(key))) { "Missing navigation icon: $key" }
                if (key !in setOf("Home", "SuperUser", "AModule", "Settings")) warnings += "Navigation destination $key has no FolkSU page; resource retained"
            }
        }
        files.forEach { (name, file) ->
            val extension = file.extension.lowercase()
            val base = stem(name)
            val images = setOf("png", "jpg", "jpeg", "webp", "gif")
            if (FolkThemeIO.assets.any { it.base == base }) {
                val allowed = when (base) {
                    "font" -> setOf("ttf", "otf")
                    "video_background" -> setOf("mp4", "webm", "mkv")
                    else -> images
                }
                require(extension in allowed) { "Invalid resource format: $name" }
            }
            if (json.optJSONObject("navIcons")?.let { icons -> icons.keys().asSequence().any { icons.optString(it) == name } } == true) {
                require(extension in images) { "Invalid navigation icon: $name" }
            }
            if (name in listOf(json.optString("musicFilename"), json.optString("soundEffectFilename"))) {
                require(extension in setOf("mp3", "wav", "ogg", "m4a", "flac", "aac")) { "Invalid audio format: $name" }
            }
            when (file.extension.lowercase()) {
                "png", "jpg", "jpeg", "webp", "gif" -> {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    require(bounds.outWidth in 1..16384 && bounds.outHeight in 1..16384) { "Invalid image: $name" }
                }
                "ttf", "otf" -> Typeface.createFromFile(file)
                "mp3", "wav", "ogg", "m4a", "flac", "aac", "mp4", "webm", "mkv" -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.path)
                        require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() != null) { "Invalid media: $name" }
                    } finally { retriever.release() }
                }
                else -> warnings += "Unrecognized resource $name retained without rendering"
            }
        }
        return warnings
    }
}
