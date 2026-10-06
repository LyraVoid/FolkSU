package me.weishu.kernelsu.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import me.weishu.kernelsu.R
import java.io.File

/**
 * How the application renders text.
 *
 * [APP_DEFAULT] uses the font bundled with FolkSU, [SYSTEM_DEFAULT] leaves the platform font in
 * place and [CUSTOM] uses a font the user imported.
 */
enum class FontMode {
    APP_DEFAULT,
    SYSTEM_DEFAULT,
    CUSTOM;

    /** Value written to theme.json; kept stable for cross-version compatibility. */
    val serializedName: String
        get() = when (this) {
            APP_DEFAULT -> "app"
            SYSTEM_DEFAULT -> "system"
            CUSTOM -> "custom"
        }

    companion object {
        fun fromName(name: String?): FontMode? = entries.firstOrNull { it.name == name }

        fun fromSerializedName(value: String?): FontMode? = when (value) {
            "app" -> APP_DEFAULT
            "system" -> SYSTEM_DEFAULT
            "custom" -> CUSTOM
            else -> null
        }
    }
}

/**
 * The app's font selection: one of the three [FontMode]s plus the imported file that CUSTOM uses.
 *
 * Replacement state lives here so the theme can rebuild its typography the moment the mode changes.
 */
object FontConfig {
    private const val PREFS_NAME = "font_settings"
    private const val KEY_FONT_MODE = "font_mode"
    private const val KEY_CUSTOM_FONT_ENABLED = "custom_font_enabled"
    private const val KEY_CUSTOM_FONT_PATH = "custom_font_path"
    private const val TAG = "FontConfig"

    var fontMode: FontMode by mutableStateOf(FontMode.SYSTEM_DEFAULT)
        private set

    var isCustomFontEnabled: Boolean by mutableStateOf(false)
        private set

    var customFontFilename: String? by mutableStateOf(null)
        private set

    /** Selects a font mode and persists it. Only [FontMode.CUSTOM] keeps a user file. */
    fun setFontMode(context: Context, mode: FontMode) {
        fontMode = mode
        isCustomFontEnabled = mode == FontMode.CUSTOM
        save(context)
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        customFontFilename = prefs.getString(KEY_CUSTOM_FONT_PATH, null)

        // Migrate the legacy boolean to the three-mode setting when font_mode was never written: an
        // enabled custom font becomes CUSTOM, everything else keeps the platform font.
        val storedMode = FontMode.fromName(prefs.getString(KEY_FONT_MODE, null))
        val legacyEnabled = prefs.getBoolean(KEY_CUSTOM_FONT_ENABLED, false)
        fontMode = storedMode ?: if (legacyEnabled) FontMode.CUSTOM else FontMode.SYSTEM_DEFAULT
        isCustomFontEnabled = fontMode == FontMode.CUSTOM
        var needsPersist = storedMode == null

        // Migration: if enabled but no filename, try to migrate from the old fixed filename.
        if (isCustomFontEnabled && customFontFilename == null) {
            val oldFixedFile = File(context.filesDir, "custom_font.ttf")
            if (oldFixedFile.exists()) {
                val newName = "custom_font_${System.currentTimeMillis()}.ttf"
                if (oldFixedFile.renameTo(File(context.filesDir, newName))) {
                    customFontFilename = newName
                    needsPersist = true
                }
            }
        }

        // A missing custom file must not leave the app in a broken state, so fall back to the
        // platform font.
        if (isCustomFontEnabled) {
            val file = customFontFilename?.let { File(context.filesDir, it) }
            if (file == null || !file.exists()) {
                customFontFilename = null
                fontMode = FontMode.SYSTEM_DEFAULT
                isCustomFontEnabled = false
                needsPersist = true
            }
        }

        if (needsPersist) {
            save(context)
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FONT_MODE, fontMode.name)
            .putBoolean(KEY_CUSTOM_FONT_ENABLED, isCustomFontEnabled)
            .putString(KEY_CUSTOM_FONT_PATH, customFontFilename)
            .apply()
    }

    fun applyCustomFont(context: Context, sourceFile: File) {
        val newFilename = "custom_font_${System.currentTimeMillis()}.ttf"
        val oldFilename = customFontFilename

        try {
            val destFile = File(context.filesDir, newFilename)
            sourceFile.copyTo(destFile, overwrite = true)
            deleteOldFile(context, oldFilename, newFilename)

            fontMode = FontMode.CUSTOM
            isCustomFontEnabled = true
            customFontFilename = newFilename
            invalidateFontCache()
            save(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply custom font", e)
        }
    }

    fun saveFontFile(context: Context, uri: Uri): Boolean {
        return try {
            val newFilename = "custom_font_${System.currentTimeMillis()}.ttf"
            val oldFilename = customFontFilename

            context.contentResolver.openInputStream(uri)?.use { input ->
                val file = File(context.filesDir, newFilename)
                file.outputStream().use { output -> input.copyTo(output) }
            }

            deleteOldFile(context, oldFilename, newFilename)

            fontMode = FontMode.CUSTOM
            isCustomFontEnabled = true
            customFontFilename = newFilename
            invalidateFontCache()
            save(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save font file", e)
            false
        }
    }

    fun clearFont(context: Context) {
        val file = customFontFilename?.let { File(context.filesDir, it) }
        if (file != null && file.exists()) {
            file.delete()
        }
        customFontFilename = null
        // Removing the imported font returns to the app's bundled default.
        fontMode = FontMode.APP_DEFAULT
        isCustomFontEnabled = false
        invalidateFontCache()
        save(context)
    }

    private fun deleteOldFile(context: Context, oldFilename: String?, newFilename: String) {
        if (oldFilename != null && oldFilename != newFilename) {
            val oldFile = File(context.filesDir, oldFilename)
            if (oldFile.exists()) {
                oldFile.delete()
            }
        }
    }

    // Font cache, so Typeface.createFromFile / resource lookups do not run on every recomposition.
    private var cachedFontFamily: FontFamily? = null
    private var cachedFilename: String? = null
    private var cachedMode: FontMode? = null

    fun getFontFamily(context: Context): FontFamily {
        if (fontMode == cachedMode && customFontFilename == cachedFilename && cachedFontFamily != null) {
            return cachedFontFamily!!
        }

        val family = when (fontMode) {
            FontMode.APP_DEFAULT -> loadBundledFont(context)
            FontMode.SYSTEM_DEFAULT -> FontFamily.Default
            FontMode.CUSTOM -> loadCustomFont(context)
        }

        cachedMode = fontMode
        cachedFilename = customFontFilename
        cachedFontFamily = family
        return family
    }

    private fun loadBundledFont(context: Context): FontFamily = try {
        ResourcesCompat.getFont(context, R.font.xiaolai)?.let { FontFamily(it) } ?: FontFamily.Default
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load bundled font", e)
        FontFamily.Default
    }

    private fun loadCustomFont(context: Context): FontFamily {
        val filename = customFontFilename ?: return FontFamily.Default
        val file = File(context.filesDir, filename)
        if (!file.exists()) return FontFamily.Default
        return try {
            FontFamily(Typeface.createFromFile(file))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom font", e)
            FontFamily.Default
        }
    }

    fun invalidateFontCache() {
        cachedFilename = null
        cachedFontFamily = null
        cachedMode = null
    }
}
