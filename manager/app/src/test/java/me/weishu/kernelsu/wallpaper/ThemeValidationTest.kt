package me.weishu.kernelsu.wallpaper

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThemeValidationTest {
    @Test fun multiPageWallpaperDoesNotRequireSingleBackground() {
        val json = JSONObject().put("isBackgroundEnabled", true).put("isMultiBackgroundEnabled", true)
        ThemeValidation.validateBackground(json, setOf("background_home.png", "background_settings.png"))
        assertThrows(Exception::class.java) { ThemeValidation.validateBackground(json, emptySet()) }
        json.put("isMultiBackgroundEnabled", false)
        assertThrows(Exception::class.java) {
            ThemeValidation.validateBackground(json, setOf("background_home.png"))
        }
        ThemeValidation.validateBackground(json, setOf("background.png"))
    }

    @Test fun invalidSettingsAndMissingPayloadsFailBeforeApplication() {
        listOf(
            JSONObject().put("nightModeEnabled", "true"),
            JSONObject().put("musicVolume", 2),
            JSONObject().put("titleImageOffsetX", 3),
            JSONObject().put("folksu_keyColor", 1.5),
            JSONObject().put("isBackgroundEnabled", true),
            JSONObject().put("fontMode", "custom"),
            JSONObject().put("isMusicEnabled", true).put("musicFilename", "missing.mp3"),
            JSONObject().put("navIcons", JSONObject().put("Home", "missing.png")),
            JSONObject().put("appLanguage", "not_a_locale"),
        ).forEach { json ->
            assertThrows(Exception::class.java) { ThemeValidation.validate(json, emptyMap()) }
        }
    }

    @Test fun unknownEnumsPreserveLocalValuesAndExplainLayoutMapping() {
        val json = JSONObject().put("colorContrast", "FUTURE").put("fontMode", "future")
            .put("isFontEnabled", true).put("homeLayoutStyle", "sign")
        val warnings = ThemeValidation.validate(json, emptyMap())
        assertFalse(json.has("colorContrast"))
        assertFalse(json.has("fontMode"))
        assertFalse(json.has("isFontEnabled"))
        assertEquals("sign", json.getString("homeLayoutStyle"))
        assertTrue(warnings.any { it.contains("circle") })
        assertTrue(warnings.any { it.contains("FUTURE") })
    }
}
