package me.weishu.kernelsu.wallpaper

import me.weishu.kernelsu.wallpaper.surface.SurfaceField
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import me.weishu.kernelsu.wallpaper.surface.isToggle
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Normalize optional settings like FP's opt accessors; container safety is checked separately. */
internal object ThemeValidation {
    internal fun validateBackground(json: JSONObject, names: Set<String>): Boolean {
        if (!json.optBoolean("isBackgroundEnabled")) return true
        val stems = names.map { it.substringBefore('.') }.toSet()
        val hasBackground = if (json.optBoolean("isMultiBackgroundEnabled")) {
            stems.any { it in setOf("background_home", "background_kernel", "background_superuser",
                "background_system_module", "background_settings") }
        } else "background" in stems
        return hasBackground
    }

    fun validate(json: JSONObject, files: Map<String, File>): List<String> {
        val warnings = mutableListOf<String>()
        ThemeSettingsMapping.importDefaults.forEach { (key, value) ->
            if (!json.has(key)) json.put(key, value)
        }
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
        booleans.filter(json::has).forEach { key ->
            val value = json.opt(key)
            when {
                value is Boolean -> Unit
                value is String && value.equals("true", true) -> json.put(key, true)
                value is String && value.equals("false", true) -> json.put(key, false)
                else -> {
                    json.remove(key)
                    warnings += "$key: invalid boolean; default used"
                }
            }
        }
        numbers.filterKeys(json::has).forEach { (key, range) ->
            val value = json.optDouble(key, Double.NaN)
            if (!value.isFinite()) {
                json.remove(key)
                warnings += "$key: invalid number; default used"
            } else {
                val normalized = value.coerceIn(range.start, range.endInclusive)
                json.put(key, if (key == "folksu_keyColor") normalized.toInt() else normalized)
                if (normalized != value) warnings += "$key: adjusted to supported range"
            }
        }
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
            json.put(key, json.optString(key))
            if (json.getString(key) !in values) {
                warnings += "$key=${json.getString(key)}: unsupported; current setting retained"
                json.remove(key)
            }
        }
        listOf("meta_name", "meta_type", "meta_author", "meta_description", "appLanguage").filter(json::has)
            .forEach { json.put(it, json.optString(it)) }
        if (json.has("appLanguage")) {
            val tags = json.getString("appLanguage")
            if (!(tags.isEmpty() || tags.split(',').all { tag ->
                runCatching { Locale.Builder().setLanguageTag(tag).build() }.isSuccess
            })) {
                json.remove("appLanguage")
                warnings += "Invalid appLanguage; current language retained"
            } else if (tags.isNotEmpty()) warnings += "Application language follows $tags; untranslated content uses Android resource fallback"
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
            warnings += "Multiple resources for $it; FP format priority used"
        }
        fun requireAsset(enabled: String, base: String) {
            if (json.optBoolean(enabled) && ThemeResourcePolicy.select(base, files) == null) {
                warnings += "$enabled: $base is missing; resource fallback used"
            }
        }
        if (!validateBackground(json, files.keys)) warnings += "Wallpaper image is missing; resource fallback used"
        requireAsset("isVideoBackgroundEnabled", "video_background")
        requireAsset("isAdvancedTitleStyleEnabled", "title_image")
        if ((json.optString("fontMode") == "custom" || json.optBoolean("isFontEnabled")) &&
            ThemeResourcePolicy.select("font", files) == null) {
            warnings += "Custom font is missing; the app font is used"
        }
        SurfaceRegistry.themeSlots().forEach { slot ->
            if (SurfaceField.Image in slot.fields) {
                val enableKey = slot.legacyThemeFields[SurfaceField.Enabled] ?: slot.legacyThemeFields[SurfaceField.Image]
                if (enableKey != null) requireAsset(enableKey, slot.themeBase ?: slot.id.value)
                if (SurfaceField.Enabled in slot.fields) requireAsset(SurfaceStore.key(slot.id, SurfaceField.Enabled), slot.themeBase ?: slot.id.value)
            }
        }
        listOf("isMusicEnabled" to "musicFilename", "isSoundEffectEnabled" to "soundEffectFilename").forEach { (enabled, key) ->
            if (json.has(key)) json.put(key, json.optString(key))
            if (json.optBoolean(enabled) && !files.containsKey(json.optString(key))) warnings += "$key resource is missing; no audio file installed"
        }
        if (json.has("navIcons")) {
            val icons = json.optJSONObject("navIcons")
            if (icons == null) {
                json.remove("navIcons")
                warnings += "Invalid navIcons; current icons retained"
            }
            icons?.keys()?.forEach { key ->
                if (!files.containsKey(icons.optString(key))) warnings += "Missing navigation icon: $key; current icon retained"
                if (key !in setOf("Home", "SuperUser", "AModule", "Settings")) warnings += "Navigation destination $key has no FolkSU page; resource retained"
            }
        }
        // FP copies resources without probing every image, font and media file. Unknown/unused
        // entries remain in passthrough storage; renderer failures must not reject the whole theme.
        return warnings
    }
}
