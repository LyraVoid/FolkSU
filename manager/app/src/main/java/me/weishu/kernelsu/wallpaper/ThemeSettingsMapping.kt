package me.weishu.kernelsu.wallpaper

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import me.weishu.kernelsu.ksuApp
import com.materialkolor.PaletteStyle
import me.weishu.kernelsu.data.repository.SettingsRepository
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.theme.ColorMode
import org.json.JSONObject

/** Protocol names map onto the existing settings repository; no second settings store. */
internal object ThemeSettingsMapping {
    val colors = linkedMapOf(
        "indigo" to 0xFF4355B9, "blue" to 0xFF0061A4, "light_blue" to 0xFF006493,
        "cyan" to 0xFF006876, "teal" to 0xFF006A60, "green" to 0xFF006E1A,
        "light_green" to 0xFF006C48, "lime" to 0xFF5B6300, "yellow" to 0xFF695F00,
        "amber" to 0xFF785900, "orange" to 0xFF8B5000, "deep_orange" to 0xFFB02F00,
        "red" to 0xFFBB1614, "pink" to 0xFFBC004B, "purple" to 0xFF9A25AE,
        "deep_purple" to 0xFF6F43C0, "brown" to 0xFF9A4522, "blue_grey" to 0xFF00668A,
        "sakura" to 0xFF9B404F, "ink_wash" to 0xFF424242,
    ).mapValues { it.value.toInt() }
    val styles = PaletteStyle.entries.associateBy { style ->
        style.name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
    }

    fun write(json: JSONObject, repo: SettingsRepository = SettingsRepositoryImpl()) {
        val mode = ColorMode.fromValue(repo.themeMode)
        val color = colors.entries.firstOrNull { it.value == repo.keyColor }?.key
        json.put("customColor", color ?: "indigo")
        // Arbitrary FolkSU seeds have no FP catalog token. Keep the exact value in an extension.
        json.put("folksu_keyColor", repo.keyColor)
        json.put("nightModeEnabled", mode.isDark)
        json.put("nightModeFollowSys", mode.isSystem)
        json.put("useSystemDynamicColor", repo.useSystemDynamicColor)
        json.put("colorGenerationMode", repo.colorGenerationMode)
        json.put("colorStandard", if (repo.colorSpec == "SPEC_2025") "M3E_2025" else "MD3_2021")
        json.put("colorStyle", styles.entries.firstOrNull { it.value.name == repo.colorStyle }?.key ?: "TONAL_SPOT")
        json.put("colorContrast", repo.colorContrast)
        json.put("statsTopLayout", repo.statsTopLayout)
        json.put("isListWorkingCardModeHidden", repo.listWorkingCardModeHidden)
        json.put("appLanguage", if (Build.VERSION.SDK_INT >= 33)
            ksuApp.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            else ksuApp.getSharedPreferences("settings", 0).getString("fpt_app_language", ""))
    }

    fun apply(json: JSONObject, repo: SettingsRepository = SettingsRepositoryImpl()) {
        if (json.has("customColor")) colors[json.optString("customColor")]?.let { repo.keyColor = it }
        if (json.has("folksu_keyColor")) repo.keyColor = json.getInt("folksu_keyColor")
        if (json.has("nightModeEnabled") || json.has("nightModeFollowSys")) {
            val current = ColorMode.fromValue(repo.themeMode)
            repo.themeMode = when {
                json.optBoolean("nightModeFollowSys", current.isSystem) -> ColorMode.SYSTEM.value
                json.optBoolean("nightModeEnabled", current.isDark) -> ColorMode.DARK.value
                else -> ColorMode.LIGHT.value
            }
        }
        if (json.has("useSystemDynamicColor")) repo.useSystemDynamicColor = json.getBoolean("useSystemDynamicColor")
        if (json.has("colorGenerationMode")) repo.colorGenerationMode = json.getString("colorGenerationMode")
        if (json.has("colorStandard")) repo.colorSpec = when (json.getString("colorStandard")) {
            "M3E_2025" -> "SPEC_2025"
            else -> "SPEC_2021"
        }
        if (json.has("colorStyle")) styles[json.getString("colorStyle")]?.let { repo.colorStyle = it.name }
        if (json.has("colorContrast")) repo.colorContrast = json.getString("colorContrast")
        if (json.has("statsTopLayout")) repo.statsTopLayout = json.getString("statsTopLayout")
        if (json.has("isListWorkingCardModeHidden")) repo.listWorkingCardModeHidden = json.getBoolean("isListWorkingCardModeHidden")
    }

    fun applyLanguage(json: JSONObject) {
        if (!json.has("appLanguage")) return
        val tags = json.getString("appLanguage")
        if (Build.VERSION.SDK_INT >= 33) {
            ksuApp.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
        } else {
            check(ksuApp.getSharedPreferences("settings", 0).edit().putString("fpt_app_language", tags).commit())
        }
    }
}
